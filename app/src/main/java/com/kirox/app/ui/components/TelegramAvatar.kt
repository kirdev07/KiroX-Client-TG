package com.kirox.app.ui.components

import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
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
    val files = LocalTelegramFiles.current
    val pathFlow = remember(photo?.id, files) {
        if (photo != null && files != null) files.observe(photo) else MutableStateFlow(photo?.localPath)
    }
    val path by pathFlow.collectAsState()
    val sizePx = with(LocalDensity.current) { size.roundToPx() }
    val bitmap by produceState(AvatarCache.peek(path, sizePx), path, sizePx) {
        if (path != null && value == null) value = withContext(Dispatchers.IO) { AvatarCache.load(path!!, sizePx) }
    }
    Box(modifier.size(size).clip(CircleShape).background(Fusion.colors.avatar), contentAlignment = Alignment.Center) {
        val image = bitmap
        if (image != null) {
            Image(image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Text(initial(title), color = Fusion.colors.textPrimary, fontSize = (size.value * .38f).sp, fontWeight = FontWeight.Bold)
        }
    }
}

internal fun initial(title: String): String {
    val trimmed = title.trim()
    if (trimmed.isEmpty()) return "?"
    return trimmed.substring(0, trimmed.offsetByCodePoints(0, 1)).uppercase()
}

/** Downsampled, memory-bounded bitmap cache for avatars. */
private object AvatarCache {
    private val cache = object : LruCache<String, ImageBitmap>(16 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ImageBitmap) = value.width * value.height * 4
    }

    fun peek(path: String?, sizePx: Int): ImageBitmap? = path?.let { cache.get("$it@$sizePx") }

    fun load(path: String, sizePx: Int): ImageBitmap? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= sizePx && bounds.outHeight / (sample * 2) >= sizePx) sample *= 2
        BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
    }.getOrNull()?.also { cache.put("$path@$sizePx", it) }
}
