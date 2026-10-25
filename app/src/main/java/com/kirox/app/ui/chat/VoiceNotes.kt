package com.kirox.app.ui.chat

import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
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
    val elapsedMs: Long get() = if (startedAt == 0L) 0 else android.os.SystemClock.elapsedRealtime() - startedAt

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

/** Plays one voice note at a time. */
object VoicePlayer {
    private var player: MediaPlayer? = null
    var playingPath: String? = null
        private set

    fun toggle(path: String, onDone: () -> Unit) {
        if (playingPath == path) { stop(); onDone(); return }
        stop()
        player = runCatching {
            MediaPlayer().apply {
                setDataSource(path)
                setOnCompletionListener { stop(); onDone() }
                prepare()
                start()
            }
        }.getOrNull()
        playingPath = if (player != null) path else null
    }

    fun stop() {
        player?.runCatching { stop(); release() }
        player = null
        playingPath = null
    }
}
