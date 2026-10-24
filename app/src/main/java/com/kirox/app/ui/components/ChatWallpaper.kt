package com.kirox.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import com.kirox.app.settings.ChatWallpaper
import com.kirox.app.ui.theme.Fusion

/** Chat background drawn in the current theme's colours; quiet enough to keep messages readable. */
@Composable
fun ChatWallpaperLayer(wallpaper: ChatWallpaper, modifier: Modifier = Modifier, /** Smaller pattern for thumbnails. */ patternScale: Float = 1f) {
    val colors = Fusion.colors
    when (wallpaper) {
        ChatWallpaper.None -> Box(modifier.background(colors.background))
        ChatWallpaper.Gradient -> Box(modifier.background(Brush.verticalGradient(listOf(colors.surface2, colors.background, colors.surface1))))
        ChatWallpaper.Dots -> Canvas(modifier.background(colors.background)) {
            val step = 18.dp.toPx() * patternScale
            val r = 1.1.dp.toPx() * patternScale.coerceAtLeast(0.7f)
            var y = step / 2
            var row = 0
            while (y < size.height) {
                var x = if (row % 2 == 0) step / 2 else step
                while (x < size.width) { drawCircle(colors.outlineStrong.copy(alpha = 0.55f), r, Offset(x, y)); x += step }
                y += step; row++
            }
        }
        ChatWallpaper.Pattern -> Canvas(modifier.background(colors.background)) {
            // Small "X" marks and rings on a staggered grid — a quiet kiroX motif.
            val step = 46.dp.toPx() * patternScale
            val arm = 4.dp.toPx() * patternScale.coerceAtLeast(0.6f)
            val stroke = 1.3.dp.toPx() * patternScale.coerceAtLeast(0.7f)
            val tint = colors.outlineStrong.copy(alpha = 0.45f)
            var y = step / 2
            var row = 0
            while (y < size.height + step) {
                var x = if (row % 2 == 0) step / 2 else step
                var col = 0
                while (x < size.width + step) {
                    if ((row + col) % 2 == 0) {
                        drawLine(tint, Offset(x - arm, y - arm), Offset(x + arm, y + arm), stroke, StrokeCap.Round)
                        drawLine(tint, Offset(x + arm, y - arm), Offset(x - arm, y + arm), stroke, StrokeCap.Round)
                    } else drawCircle(tint, arm * 0.8f, Offset(x, y), style = androidx.compose.ui.graphics.drawscope.Stroke(stroke))
                    x += step; col++
                }
                y += step; row++
            }
        }
    }
}
