package com.kirox.app.ui.chat

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.camera.core.CameraSelector
import androidx.camera.core.MirrorMode
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import com.kirox.app.ui.theme.Fusion
import kotlinx.coroutines.delay
import java.io.File

/** Telegram limits video notes to one minute. */
private const val MAX_NOTE_MS = 60_000L
/** Side of the square video sent; Telegram's own clients record at about this size. */
internal const val NOTE_SIDE = 384

/** A finished video note ready to send. */
class RecordedVideoNote(val file: File, val durationSeconds: Int, val length: Int)

/**
 * Full-screen recorder for round video messages: circular camera preview, tap the button to start
 * and again to send, flip between cameras before recording, cancel at any time. Recording stops by
 * itself after a minute. The clip is cropped to a [NOTE_SIDE] square before sending.
 */
@SuppressLint("MissingPermission") // The caller asks for CAMERA and RECORD_AUDIO before opening it.
@OptIn(UnstableApi::class)
@Composable
internal fun VideoNoteRecorder(onDone: (RecordedVideoNote) -> Unit, onCancel: () -> Unit, onError: (String) -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var front by remember { mutableStateOf(true) }
    var recording by remember { mutableStateOf<Recording?>(null) }
    var startedAt by remember { mutableLongStateOf(0L) }
    var elapsed by remember { mutableLongStateOf(0L) }
    var processing by remember { mutableStateOf(false) }
    val preview = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FILL_CENTER; implementationMode = PreviewView.ImplementationMode.COMPATIBLE } }
    val capture = remember {
        VideoCapture.Builder(Recorder.Builder().setQualitySelector(QualitySelector.from(Quality.SD, androidx.camera.video.FallbackStrategy.lowerQualityOrHigherThan(Quality.SD))).build())
            .setMirrorMode(MirrorMode.MIRROR_MODE_ON_FRONT_ONLY)
            .build()
    }
    val raw = remember { File(context.cacheDir, "notes").apply { mkdirs() }.let { File(it, "note_raw_${System.currentTimeMillis()}.mp4") } }
    var discard by remember { mutableStateOf(false) }

    DisposableEffect(front) {
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            val provider = runCatching { future.get() }.getOrNull() ?: return@addListener onError("Камера недоступна")
            val selector = if (front) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
            val surface = Preview.Builder().build().also { it.setSurfaceProvider(preview.surfaceProvider) }
            runCatching {
                provider.unbindAll()
                provider.bindToLifecycle(lifecycleOwner, selector, surface, capture)
            }.onFailure { onError("Не удалось открыть камеру") }
        }, ContextCompat.getMainExecutor(context))
        onDispose { runCatching { future.get().unbindAll() } }
    }
    DisposableEffect(Unit) { onDispose { recording?.close(); if (!processing) raw.delete() } }

    fun finish(cancelled: Boolean) {
        val active = recording ?: return
        discard = cancelled
        if (!cancelled) processing = true
        active.stop()
        recording = null
    }

    fun start() {
        VoicePlayer.stop()
        discard = false
        startedAt = System.currentTimeMillis()
        elapsed = 0
        recording = capture.output.prepareRecording(context, FileOutputOptions.Builder(raw).build())
            .withAudioEnabled()
            .start(ContextCompat.getMainExecutor(context)) { event ->
                if (event !is VideoRecordEvent.Finalize) return@start
                val durationMs = event.recordingStats.recordedDurationNanos / 1_000_000
                when {
                    discard -> { raw.delete(); onCancel() }
                    event.hasError() && event.error != VideoRecordEvent.Finalize.ERROR_DURATION_LIMIT_REACHED -> { raw.delete(); processing = false; onError("Запись прервалась") }
                    durationMs < 1_000 -> { raw.delete(); processing = false; onError("Слишком короткое видеосообщение") }
                    else -> cropToSquare(context, raw, durationMs, onDone = { note -> processing = false; onDone(note) }, onError = { processing = false; onError(it) })
                }
            }
    }

    LaunchedEffect(recording) {
        while (recording != null) {
            elapsed = System.currentTimeMillis() - startedAt
            if (elapsed >= MAX_NOTE_MS) finish(cancelled = false)
            delay(100)
        }
    }

    Dialog(onDismissRequest = { if (recording != null) finish(cancelled = true) else if (!processing) onCancel() }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(Modifier.fillMaxSize().background(Color.Black).navigationBarsPadding(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Box(contentAlignment = Alignment.Center) {
                // Ring fills as the minute runs out.
                CircularProgressIndicator(
                    progress = { (elapsed.toFloat() / MAX_NOTE_MS).coerceIn(0f, 1f) },
                    modifier = Modifier.size(300.dp), color = Fusion.colors.danger, strokeWidth = 4.dp, trackColor = Color(0x33FFFFFF)
                )
                AndroidView({ preview }, Modifier.size(284.dp).clip(CircleShape))
                if (processing) CircularProgressIndicator(Modifier.size(36.dp), color = Color.White, strokeWidth = 3.dp)
            }
            Spacer(Modifier.height(20.dp))
            Text(
                when {
                    processing -> "Обработка…"
                    recording != null -> "%d:%02d".format(elapsed / 60_000, elapsed / 1000 % 60)
                    else -> "Нажмите, чтобы записать"
                },
                color = Color.White, fontSize = 16.sp
            )
            Spacer(Modifier.height(32.dp))
            Row(Modifier.fillMaxWidth().padding(horizontal = 32.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Отмена", color = Color.White, fontSize = 15.sp,
                    modifier = Modifier.clip(CircleShape).clickable(enabled = !processing) { if (recording != null) finish(cancelled = true) else onCancel() }.padding(12.dp)
                )
                Box(
                    Modifier.size(76.dp).border(4.dp, Color.White, CircleShape).padding(8.dp).clip(CircleShape)
                        .background(if (recording != null) Fusion.colors.danger else Color.White)
                        .clickable(enabled = !processing, onClickLabel = if (recording != null) "Отправить" else "Записать") {
                            if (recording != null) finish(cancelled = false) else start()
                        }
                )
                Text(
                    "↻", color = if (recording == null && !processing) Color.White else Color(0x55FFFFFF), fontSize = 26.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clip(CircleShape).clickable(enabled = recording == null && !processing, onClickLabel = "Сменить камеру") { front = !front }.padding(12.dp)
                )
            }
        }
    }
}

/** Crops the recording to a centred square and re-encodes it as H.264, as video notes must be. */
@OptIn(UnstableApi::class)
private fun cropToSquare(context: Context, raw: File, durationMs: Long, onDone: (RecordedVideoNote) -> Unit, onError: (String) -> Unit) {
    val out = File(raw.parentFile, raw.name.replace("note_raw_", "note_"))
    val item = EditedMediaItem.Builder(MediaItem.fromUri(Uri.fromFile(raw)))
        .setEffects(Effects(emptyList(), listOf(Presentation.createForWidthAndHeight(NOTE_SIDE, NOTE_SIDE, Presentation.LAYOUT_SCALE_TO_FIT_WITH_CROP))))
        .build()
    val transformer = Transformer.Builder(context.applicationContext)
        .setVideoMimeType(MimeTypes.VIDEO_H264)
        .setAudioMimeType(MimeTypes.AUDIO_AAC)
        .addListener(object : Transformer.Listener {
            override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                raw.delete()
                onDone(RecordedVideoNote(out, ((durationMs + 500) / 1000).toInt().coerceIn(1, 60), NOTE_SIDE))
            }

            override fun onError(composition: Composition, exportResult: ExportResult, exportException: ExportException) {
                raw.delete()
                out.delete()
                onError("Не удалось подготовить видеосообщение")
            }
        })
        .build()
    transformer.start(item, out.absolutePath)
}
