package com.kirox.app.ui.components

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import com.kirox.app.telegram.TelegramFileRef
import com.kirox.app.telegram.TelegramFileRepository
import com.kirox.app.ui.theme.Fusion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext

val LocalTelegramFiles = staticCompositionLocalOf<TelegramFileRepository?> { null }

/** Circular avatar: Telegram photo when downloaded, otherwise the first letter of [title]. */
@Composable
fun TelegramAvatar(title: String, photo: TelegramFileRef?, size: Dp, modifier: Modifier = Modifier) {
    val bitmap = rememberTelegramBitmap(photo, with(LocalDensity.current) { size.roundToPx() })
    val tone = avatarTone(title)
    Box(
        modifier.size(size).clip(CircleShape)
            .background(androidx.compose.ui.graphics.Brush.verticalGradient(listOf(tone.copy(alpha = 1f).lighten(0.10f), tone))),
        contentAlignment = Alignment.Center
    ) {
        val image = bitmap
        if (image != null) {
            Image(image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Text(initial(title), color = Fusion.colors.textPrimary, fontSize = (size.value * .38f).sp, fontWeight = FontWeight.Bold)
        }
    }
}

/** Downloads [file] if needed and decodes it downsampled to about [sizePx]; null until ready. */
@Composable
fun rememberTelegramBitmap(file: TelegramFileRef?, sizePx: Int, priority: Int = 8): ImageBitmap? {
    val files = LocalTelegramFiles.current
    val pathFlow = remember(file?.id, files) {
        if (file != null && files != null) files.observe(file, priority) else MutableStateFlow(file?.localPath)
    }
    val path by pathFlow.collectAsState()
    val bitmap by produceState(AvatarCache.peek(path, sizePx), path, sizePx) {
        // A new photo (changed avatar, next story) must not keep showing the previous one.
        val current = path
        value = AvatarCache.peek(current, sizePx)
        if (current != null && value == null) value = withContext(Dispatchers.IO) { AvatarCache.load(current, sizePx) }
    }
    return bitmap
}

/** Message photo with a stable aspect-ratio box, so the list does not jump while it loads. */
@Composable
fun TelegramPhoto(file: TelegramFileRef, aspect: Float, modifier: Modifier = Modifier) {
    val ratio = aspect.takeIf { it > 0f }?.coerceIn(0.5f, 2.2f) ?: 1.3f
    val bitmap = rememberTelegramBitmap(file, with(LocalDensity.current) { 280.dp.roundToPx() }, priority = 16)
    Box(modifier.widthIn(max = 280.dp).fillMaxWidth().aspectRatio(ratio).clip(RoundedCornerShape(12.dp)).background(Fusion.colors.surface3)) {
        bitmap?.let { Image(it, contentDescription = "Фотография", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
    }
}

/**
 * A muted tone per name, so people without a photo are told apart at a glance while the list stays
 * in the monochrome Fusion look (desaturated slate, plum, moss, sand, steel, rose, teal).
 */
private val AvatarTones = listOf(
    androidx.compose.ui.graphics.Color(0xFF3A4652), androidx.compose.ui.graphics.Color(0xFF4A3F57), androidx.compose.ui.graphics.Color(0xFF3E5045),
    androidx.compose.ui.graphics.Color(0xFF574B3C), androidx.compose.ui.graphics.Color(0xFF3B4B60), androidx.compose.ui.graphics.Color(0xFF573E48),
    androidx.compose.ui.graphics.Color(0xFF365353)
)

internal fun avatarTone(title: String): androidx.compose.ui.graphics.Color =
    AvatarTones[Math.floorMod(title.trim().lowercase().hashCode(), AvatarTones.size)]

private fun androidx.compose.ui.graphics.Color.lighten(amount: Float) =
    androidx.compose.ui.graphics.Color(red + (1f - red) * amount, green + (1f - green) * amount, blue + (1f - blue) * amount, alpha)

internal fun initial(title: String): String {
    val trimmed = title.trim()
    if (trimmed.isEmpty()) return "?"
    return trimmed.substring(0, trimmed.offsetByCodePoints(0, 1)).uppercase()
}

/** Downsampled, memory-bounded bitmap cache for avatars. */
private object AvatarCache {
    private val cache = object : LruCache<String, ImageBitmap>(32 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ImageBitmap) = value.width * value.height * 4
    }

    fun peek(path: String?, sizePx: Int): ImageBitmap? = path?.let { cache.get("$it@$sizePx") }

    fun load(path: String, requestedPx: Int): ImageBitmap? = runCatching {
        val sizePx = requestedPx.coerceAtLeast(16)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= sizePx && bounds.outHeight / (sample * 2) >= sizePx) sample *= 2
        // Very long or tall pictures: the long side is limited too, so one image cannot take tens of MB.
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > sizePx * 4) sample *= 2
        BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
    }.getOrNull()?.also { cache.put("$path@$requestedPx", it) }
}
