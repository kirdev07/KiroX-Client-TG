package com.kirox.app.ui.chat

import android.net.Uri
import androidx.annotation.OptIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.kirox.app.telegram.TelegramFileRef
import com.kirox.app.telegram.TelegramFileState
import com.kirox.app.telegram.TelegramMedia
import com.kirox.app.ui.components.LocalTelegramFiles
import com.kirox.app.ui.components.rememberTelegramBitmap
import com.kirox.app.ui.icons.KiroXIcon
import com.kirox.app.ui.icons.KiroXIcon as IconGlyph
import com.kirox.app.ui.theme.Fusion
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.io.File

// Media inside message bubbles. Files download on tap (videos, music, documents) or by themselves
// when small (GIFs, stickers, previews); playback uses one Media3 player per visible item.

/** GIFs up to this size download and loop by themselves, like in Telegram. */
private const val AUTO_ANIMATION_BYTES = 8L * 1024 * 1024

/** Download state of [file], or an empty state outside a chat screen (previews, tests). */
@Composable
private fun rememberFileState(file: TelegramFileRef): TelegramFileState {
    val files = LocalTelegramFiles.current
    val flow = remember(file.id, files) { files?.state(file) ?: MutableStateFlow(TelegramFileState(path = file.localPath)) }
    return flow.collectAsState().value
}

private fun formatDuration(seconds: Int): String = "%d:%02d".format(seconds / 60, seconds % 60)

private fun mediaAspect(media: TelegramMedia): Float =
    if (media.width > 0 && media.height > 0) (media.width.toFloat() / media.height).coerceIn(0.5f, 2.2f) else 16f / 9f

/** Round button over media: download arrow, progress with cancel, or play. */
@Composable
private fun MediaStateBadge(state: TelegramFileState, playIcon: Boolean, modifier: Modifier = Modifier) {
    Box(modifier.size(44.dp).clip(CircleShape).background(Color(0x99000000)), contentAlignment = Alignment.Center) {
        when {
            state.path != null -> if (playIcon) Text("▶", color = Color.White, fontSize = 16.sp)
            state.downloading -> {
                CircularProgressIndicator(progress = { state.fraction.coerceAtLeast(0.02f) }, modifier = Modifier.size(38.dp), color = Color.White, strokeWidth = 2.dp, trackColor = Color(0x33FFFFFF))
                Text("×", color = Color.White, fontSize = 18.sp)
            }
            else -> Text("↓", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
    }
}

/** Preview frame of a video or GIF in a stable aspect box. */
@Composable
private fun MediaFrame(thumbnail: TelegramFileRef?, aspect: Float, content: @Composable () -> Unit) {
    val width = with(LocalDensity.current) { 280.dp.roundToPx() }
    val bitmap = rememberTelegramBitmap(thumbnail, width, priority = 16)
    Box(
        Modifier.widthIn(max = 280.dp).fillMaxWidth().aspectRatio(aspect).clip(RoundedCornerShape(12.dp)).background(Fusion.colors.surface3),
        contentAlignment = Alignment.Center
    ) {
        bitmap?.let { Image(it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
        content()
    }
}

@Composable
internal fun VideoMessage(media: TelegramMedia, thumbnail: TelegramFileRef?) {
    val files = LocalTelegramFiles.current
    val state = rememberFileState(media.file)
    var wantPlay by remember(media.file.id) { mutableStateOf(false) }
    var playing by remember(media.file.id) { mutableStateOf<String?>(null) }
    // A tap on a video that is still downloading opens it as soon as it is complete.
    LaunchedEffect(state.path, wantPlay) { if (wantPlay && state.path != null) { playing = state.path; wantPlay = false } }
    Box(Modifier.clickable {
        when {
            state.path != null -> playing = state.path
            state.downloading -> { files?.cancel(media.file); wantPlay = false }
            else -> { files?.download(media.file); wantPlay = true }
        }
    }) {
        MediaFrame(thumbnail, mediaAspect(media)) { MediaStateBadge(state, playIcon = true) }
        val label = listOf(formatDuration(media.duration), if (state.downloading) formatFileSize(state.downloaded) + " / " + formatFileSize(state.total) else formatFileSize(media.size))
            .filter { it.isNotBlank() }.joinToString(" · ")
        Text(label, color = Color.White, fontSize = 11.sp, modifier = Modifier.align(Alignment.TopStart).padding(6.dp).clip(RoundedCornerShape(8.dp)).background(Color(0x99000000)).padding(horizontal = 6.dp, vertical = 2.dp))
    }
    playing?.let { path -> FullscreenVideo(path) { playing = null } }
}

@Composable
internal fun AnimationMessage(media: TelegramMedia, thumbnail: TelegramFileRef?) {
    val files = LocalTelegramFiles.current
    val state = rememberFileState(media.file)
    LaunchedEffect(media.file.id, files) {
        if (state.path == null && !state.downloading && media.size in 1..AUTO_ANIMATION_BYTES) files?.download(media.file)
    }
    Box(Modifier.clickable(enabled = state.path == null) { if (state.downloading) files?.cancel(media.file) else files?.download(media.file) }) {
        MediaFrame(thumbnail, mediaAspect(media)) {
            val path = state.path
            if (path != null) LoopingVideo(path, Modifier.fillMaxSize()) else MediaStateBadge(state, playIcon = false)
        }
        Text("GIF", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.TopStart).padding(6.dp).clip(RoundedCornerShape(8.dp)).background(Color(0x99000000)).padding(horizontal = 6.dp, vertical = 2.dp))
    }
}

/** Round video message: tap downloads it, then plays and stops it inside the circle with sound. */
@Composable
internal fun VideoNoteMessage(media: TelegramMedia, thumbnail: TelegramFileRef?) {
    val files = LocalTelegramFiles.current
    val state = rememberFileState(media.file)
    var playing by remember(media.file.id) { mutableStateOf(false) }
    var wantPlay by remember(media.file.id) { mutableStateOf(false) }
    LaunchedEffect(state.path, wantPlay) { if (wantPlay && state.path != null) { playing = true; wantPlay = false } }
    val size = 220.dp
    val bitmap = rememberTelegramBitmap(thumbnail, with(LocalDensity.current) { size.roundToPx() }, priority = 16)
    Box(
        Modifier.size(size).clip(CircleShape).background(Fusion.colors.surface3).clickable {
            when {
                state.path != null -> playing = !playing
                state.downloading -> { files?.cancel(media.file); wantPlay = false }
                else -> { files?.download(media.file); wantPlay = true }
            }
        },
        contentAlignment = Alignment.Center
    ) {
        val path = state.path
        if (playing && path != null) OneShotVideo(path, Modifier.fillMaxSize()) { playing = false }
        else {
            bitmap?.let { Image(it, contentDescription = "Видеосообщение", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
            MediaStateBadge(state, playIcon = true)
            Text(formatDuration(media.duration), color = Color.White, fontSize = 11.sp, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 14.dp).clip(RoundedCornerShape(8.dp)).background(Color(0x99000000)).padding(horizontal = 6.dp, vertical = 2.dp))
        }
    }
}

/** Music file: plays through the shared voice player once downloaded. */
@Composable
internal fun AudioMessage(media: TelegramMedia) {
    val context = LocalContext.current
    val files = LocalTelegramFiles.current
    val state = rememberFileState(media.file)
    val playingPath by VoicePlayer.playingPath.collectAsState()
    var wantPlay by remember(media.file.id) { mutableStateOf(false) }
    LaunchedEffect(state.path, wantPlay) { val path = state.path; if (wantPlay && path != null) { VoicePlayer.toggle(context, path); wantPlay = false } }
    val playing = state.path != null && playingPath == state.path
    Row(
        Modifier.clip(RoundedCornerShape(12.dp)).clickable {
            val path = state.path
            when {
                path != null -> VoicePlayer.toggle(context, path)
                state.downloading -> { files?.cancel(media.file); wantPlay = false }
                else -> { files?.download(media.file); wantPlay = true }
            }
        },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(42.dp).clip(CircleShape).background(Fusion.colors.surface3), contentAlignment = Alignment.Center) {
            when {
                playing -> Text("❚❚", color = Fusion.colors.textPrimary, fontSize = 13.sp)
                state.path != null -> Text("▶", color = Fusion.colors.textPrimary, fontSize = 14.sp)
                state.downloading -> CircularProgressIndicator(progress = { state.fraction.coerceAtLeast(0.02f) }, modifier = Modifier.size(34.dp), color = Fusion.colors.textPrimary, strokeWidth = 2.dp)
                else -> IconGlyph(KiroXIcon.Music, "Музыка", Modifier.size(20.dp), Fusion.colors.textPrimary)
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.widthIn(max = 220.dp)) {
            Text(media.title.ifBlank { "Аудио" }, color = Fusion.colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val details = listOf(media.performer, formatDuration(media.duration), formatFileSize(media.size)).filter { it.isNotBlank() }.joinToString(" · ")
            Text(details, color = Fusion.colors.textSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Document: tap downloads it (tap again cancels), then opens it in another app. */
@Composable
internal fun DocumentMessage(media: TelegramMedia, onNotice: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val files = LocalTelegramFiles.current
    val state = rememberFileState(media.file)
    Row(
        Modifier.clip(RoundedCornerShape(12.dp)).clickable {
            val path = state.path
            when {
                path != null -> scope.launch { if (!openFile(context, path, media.title, media.mimeType)) onNotice("Нет приложения, чтобы открыть этот файл") }
                state.downloading -> files?.cancel(media.file)
                else -> files?.download(media.file)
            }
        },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(42.dp).clip(CircleShape).background(Fusion.colors.surface3), contentAlignment = Alignment.Center) {
            when {
                state.downloading -> {
                    CircularProgressIndicator(progress = { state.fraction.coerceAtLeast(0.02f) }, modifier = Modifier.size(34.dp), color = Fusion.colors.textPrimary, strokeWidth = 2.dp)
                    Text("×", color = Fusion.colors.textPrimary, fontSize = 15.sp)
                }
                state.path != null -> IconGlyph(KiroXIcon.File, "Файл", Modifier.size(20.dp), Fusion.colors.textPrimary)
                else -> Text("↓", color = Fusion.colors.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.widthIn(max = 220.dp)) {
            Text(media.title.ifBlank { "Файл" }, color = Fusion.colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val size = if (state.downloading) "${formatFileSize(state.downloaded)} из ${formatFileSize(state.total)}" else formatFileSize(media.size)
            Text(size, color = Fusion.colors.textSecondary, fontSize = 12.sp)
        }
    }
}

/**
 * Sticker: static WEBP as an image, TGS through Lottie, WEBM as a muted looping video. Animated files
 * are small and download by themselves; until then the preview is shown.
 */
@Composable
internal fun StickerMessage(media: TelegramMedia, thumbnail: TelegramFileRef?, size: androidx.compose.ui.unit.Dp = 160.dp, animate: Boolean = true) {
    val files = LocalTelegramFiles.current
    val state = rememberFileState(media.file)
    val sizePx = with(LocalDensity.current) { size.roundToPx() }
    LaunchedEffect(media.file.id, files, animate) { if (animate && state.path == null && !state.downloading) files?.download(media.file, priority = 12) }
    val path = state.path
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        when {
            animate && path != null && media.lottieSticker -> LottieSticker(path, Modifier.fillMaxSize())
            animate && path != null && media.animatedSticker -> LoopingVideo(path, Modifier.fillMaxSize())
            else -> {
                val bitmap = rememberTelegramBitmap(if (media.animatedSticker) thumbnail else media.file, sizePx, priority = 16)
                if (bitmap != null) Image(bitmap, contentDescription = "Стикер ${media.title}", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
                else Text(media.title.ifBlank { "Стикер" }, fontSize = (size.value / 3).sp)
            }
        }
    }
}

/** TGS sticker: gzip-compressed Lottie JSON, played in a loop. */
@Composable
private fun LottieSticker(path: String, modifier: Modifier) {
    val json by androidx.compose.runtime.produceState<String?>(null, path) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching { java.util.zip.GZIPInputStream(File(path).inputStream()).bufferedReader().use { it.readText() } }.getOrNull()
        }
    }
    val source = json ?: return
    val composition by com.airbnb.lottie.compose.rememberLottieComposition(com.airbnb.lottie.compose.LottieCompositionSpec.JsonString(source), cacheKey = path)
    com.airbnb.lottie.compose.LottieAnimation(composition, iterations = com.airbnb.lottie.compose.LottieConstants.IterateForever, modifier = modifier)
}

/** Photo opened full screen: pinch to zoom, drag when zoomed, double tap to reset, tap × to close. */
@Composable
internal fun FullscreenPhoto(file: TelegramFileRef, onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val width = LocalConfiguration.current.screenWidthDp
        val bitmap = rememberTelegramBitmap(file, with(LocalDensity.current) { (width * 2).dp.roundToPx() }, priority = 32)
        var scale by remember { mutableFloatStateOf(1f) }
        var offset by remember { mutableStateOf(Offset.Zero) }
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            if (bitmap != null) Image(
                bitmap, contentDescription = "Фотография", contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
                    .pointerInput(Unit) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(1f, 6f)
                            offset = if (scale == 1f) Offset.Zero else offset + pan
                        }
                    }
                    .pointerInput(Unit) { detectTapGestures(onDoubleTap = { scale = if (scale > 1f) 1f else 2.5f; offset = Offset.Zero }) }
                    .graphicsLayer { scaleX = scale; scaleY = scale; translationX = offset.x; translationY = offset.y }
            ) else CircularProgressIndicator(Modifier.align(Alignment.Center).size(28.dp), color = Color.White, strokeWidth = 2.dp)
            CloseButton(onClose, Modifier.align(Alignment.TopEnd))
        }
    }
}

@Composable
private fun CloseButton(onClose: () -> Unit, modifier: Modifier) {
    Text(
        "×", color = Color.White, fontSize = 30.sp,
        modifier = modifier.statusBarsPadding().padding(8.dp).clip(CircleShape).background(Color(0x66000000)).clickable(onClick = onClose).padding(horizontal = 16.dp, vertical = 4.dp)
    )
}

/** Video opened full screen with the Media3 controls (seek, pause); sound takes audio focus. */
@OptIn(UnstableApi::class)
@Composable
internal fun FullscreenVideo(path: String, onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        val player = rememberPlayer(path, loop = false, muted = false, autoPlay = true)
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            AndroidView(
                factory = { context -> PlayerView(context).apply { useController = true; this.player = player; setShowNextButton(false); setShowPreviousButton(false) } },
                modifier = Modifier.fillMaxSize()
            )
            CloseButton(onClose, Modifier.align(Alignment.TopEnd))
        }
    }
}

/** Muted endless playback, for GIFs. */
@OptIn(UnstableApi::class)
@Composable
internal fun LoopingVideo(path: String, modifier: Modifier) {
    val player = rememberPlayer(path, loop = true, muted = true, autoPlay = true)
    AndroidView(
        factory = { context -> PlayerView(context).apply { useController = false; resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM; this.player = player } },
        modifier = modifier
    )
}

/** One play-through with sound, for video notes; [onEnd] when it finishes. */
@OptIn(UnstableApi::class)
@Composable
private fun OneShotVideo(path: String, modifier: Modifier, onEnd: () -> Unit) {
    val player = rememberPlayer(path, loop = false, muted = false, autoPlay = true)
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) { if (state == Player.STATE_ENDED) onEnd() }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }
    AndroidView(
        factory = { context -> PlayerView(context).apply { useController = false; resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM; this.player = player } },
        modifier = modifier
    )
}

/**
 * ExoPlayer for one local file, released when it leaves the screen. Players with sound take audio
 * focus (music pauses) and stop the voice player; muted GIF players never touch audio focus.
 */
@Composable
private fun rememberPlayer(path: String, loop: Boolean, muted: Boolean, autoPlay: Boolean): ExoPlayer {
    val context = LocalContext.current
    val player = remember(path) {
        if (!muted) VoicePlayer.stop()
        ExoPlayer.Builder(context.applicationContext).build().apply {
            setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(),
                /* handleAudioFocus = */ !muted
            )
            volume = if (muted) 0f else 1f
            repeatMode = if (loop) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
            setMediaItem(MediaItem.fromUri(Uri.fromFile(File(path))))
            playWhenReady = autoPlay
            prepare()
        }
    }
    DisposableEffect(player) { onDispose { player.release() } }
    return player
}
