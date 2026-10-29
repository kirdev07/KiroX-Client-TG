package com.kirox.app.ui.chat

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * Records a voice note as OGG/Opus — the format Telegram expects — with the platform MediaRecorder
 * (Android 10+). Keeps a rough level history for the waveform Telegram shows.
 */
class VoiceRecorder(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var file: File? = null
    private var startedAt = 0L
    private val levels = ArrayList<Int>()

    val isRecording: Boolean get() = recorder != null
    var isPaused = false
        private set
    private var pausedAt = 0L
    private var pausedTotal = 0L

    /** Pauses recording; the time paused is not counted in the note. */
    fun pause() {
        val active = recorder ?: return
        if (isPaused || !runCatching { active.pause() }.isSuccess) return
        isPaused = true
        pausedAt = android.os.SystemClock.elapsedRealtime()
    }

    fun resume() {
        val active = recorder ?: return
        if (!isPaused || !runCatching { active.resume() }.isSuccess) return
        pausedTotal += android.os.SystemClock.elapsedRealtime() - pausedAt
        isPaused = false
    }
    /** Recorded time, without pauses. */
    val elapsedMs: Long get() {
        if (startedAt == 0L) return 0
        val now = if (isPaused) pausedAt else android.os.SystemClock.elapsedRealtime()
        return now - startedAt - pausedTotal
    }

    fun start(): Boolean {
        stop(discard = true)
        val target = File(context.cacheDir, "voice").apply { mkdirs() }.let { File(it, "voice_${System.currentTimeMillis()}.ogg") }
        val created = if (android.os.Build.VERSION.SDK_INT >= 31) MediaRecorder(context) else @Suppress("DEPRECATION") MediaRecorder()
        return runCatching {
            created.setAudioSource(MediaRecorder.AudioSource.VOICE_COMMUNICATION)
            created.setOutputFormat(MediaRecorder.OutputFormat.OGG)
            created.setAudioEncoder(MediaRecorder.AudioEncoder.OPUS)
            created.setAudioSamplingRate(48_000)
            created.setAudioEncodingBitRate(32_000)
            created.setAudioChannels(1)
            created.setOutputFile(target)
            created.prepare()
            created.start()
            recorder = created
            file = target
            startedAt = android.os.SystemClock.elapsedRealtime()
            isPaused = false
            pausedTotal = 0
            levels.clear()
            true
        }.getOrElse {
            created.release()
            target.delete()
            false
        }
    }

    /** Samples the input level (call ~10 times a second while recording). */
    fun sampleLevel(): Float {
        if (isPaused) return 0f
        val amplitude = runCatching { recorder?.maxAmplitude ?: 0 }.getOrDefault(0)
        levels += amplitude
        return (amplitude / 32767f).coerceIn(0f, 1f)
    }

    /** Stops recording; returns the file and duration, or null when discarded or too short. */
    fun stop(discard: Boolean = false): Recording? {
        val active = recorder ?: return null
        val durationMs = elapsedMs
        recorder = null
        startedAt = 0
        isPaused = false
        pausedTotal = 0
        val ok = runCatching { active.stop() }.isSuccess
        active.release()
        val out = file
        file = null
        if (discard || !ok || out == null || durationMs < MIN_DURATION_MS) {
            out?.delete()
            return null
        }
        return Recording(out, (durationMs / 1000).toInt().coerceAtLeast(1), waveform())
    }

    /** Telegram's waveform: 100 five-bit samples packed into bytes. */
    private fun waveform(): ByteArray {
        if (levels.isEmpty()) return ByteArray(0)
        val count = 100
        val peak = levels.maxOrNull()?.coerceAtLeast(1) ?: 1
        val samples = IntArray(count) { i ->
            val from = i * levels.size / count
            val to = ((i + 1) * levels.size / count).coerceAtLeast(from + 1).coerceAtMost(levels.size)
            val max = levels.subList(from, to).maxOrNull() ?: 0
            (max * 31 / peak).coerceIn(0, 31)
        }
        val bytes = ByteArray((count * 5 + 7) / 8)
        samples.forEachIndexed { i, value ->
            val bit = i * 5
            val index = bit / 8
            val shift = bit % 8
            val packed = value shl shift
            bytes[index] = (bytes[index].toInt() or (packed and 0xFF)).toByte()
            if (index + 1 < bytes.size) bytes[index + 1] = (bytes[index + 1].toInt() or (packed shr 8)).toByte()
        }
        return bytes
    }

    class Recording(val file: File, val durationSeconds: Int, val waveform: ByteArray)

    private companion object { const val MIN_DURATION_MS = 700L }
}

/**
 * Plays one voice note at a time, on the main thread. Holds transient audio focus while playing, so
 * music pauses and resumes, and stops when another app takes focus (a call, a video).
 * Every row observes [playingPath]: starting one note shows the previous one as stopped.
 */
object VoicePlayer {
    private var player: MediaPlayer? = null
    private var audio: AudioManager? = null
    private var focus: AudioFocusRequest? = null

    private val _playingPath = MutableStateFlow<String?>(null)
    val playingPath: StateFlow<String?> = _playingPath.asStateFlow()

    /** Playback speed for voice notes and music, kept between notes like in Telegram. */
    private val _speed = MutableStateFlow(1f)
    val speed: StateFlow<Float> = _speed.asStateFlow()
    private val speeds = listOf(1f, 1.5f, 2f)

    /** 0–1 position of the playing file; 0 when nothing plays. */
    val progress: Float get() = player?.let { p -> runCatching { if (p.duration > 0) p.currentPosition.toFloat() / p.duration else 0f }.getOrDefault(0f) } ?: 0f

    /** Milliseconds played of the current file. */
    val positionMs: Int get() = player?.let { runCatching { it.currentPosition }.getOrDefault(0) } ?: 0

    fun cycleSpeed() {
        _speed.value = speeds[(speeds.indexOf(_speed.value) + 1) % speeds.size]
        player?.let { applySpeed(it) }
    }

    /** Jumps to [fraction] of [path] if it is the file playing now. */
    fun seek(path: String, fraction: Float) {
        val p = player ?: return
        if (_playingPath.value != path) return
        runCatching { p.seekTo((p.duration * fraction.coerceIn(0f, 1f)).toInt()) }
    }

    private fun applySpeed(p: MediaPlayer) {
        runCatching { p.playbackParams = p.playbackParams.setSpeed(_speed.value) }
    }

    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
        .build()

    fun toggle(context: Context, path: String) {
        if (_playingPath.value == path) { stop(); return }
        stop()
        val manager = context.applicationContext.getSystemService(AudioManager::class.java)
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(attributes)
            .setOnAudioFocusChangeListener { change -> if (change < 0) stop() }
            .build()
        if (manager?.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_FAILED) return
        audio = manager
        focus = request
        val created = MediaPlayer()
        player = runCatching {
            created.apply {
                setAudioAttributes(attributes)
                setDataSource(path)
                setOnCompletionListener { stop() }
                setOnErrorListener { _, _, _ -> stop(); true }
                prepare()
                start()
                applySpeed(this)
            }
        }.getOrElse { created.release(); null }
        if (player == null) stop() else _playingPath.value = path
    }

    fun stop() {
        player?.runCatching { stop() }
        player?.release()
        player = null
        focus?.let { request -> audio?.abandonAudioFocusRequest(request) }
        focus = null
        audio = null
        _playingPath.value = null
    }
}
