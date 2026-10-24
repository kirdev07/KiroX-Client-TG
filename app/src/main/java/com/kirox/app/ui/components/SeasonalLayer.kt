package com.kirox.app.ui.components

import android.provider.Settings
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import java.util.Calendar
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Whether screen headers draw seasonal particles (KiroX setting «Сезонные эффекты»). */
val LocalSeasonalEffects = staticCompositionLocalOf { true }

enum class Season { Halloween, Winter, Plain }

fun currentSeason(calendar: Calendar = Calendar.getInstance()): Season {
    val month = calendar.get(Calendar.MONTH)
    val day = calendar.get(Calendar.DAY_OF_MONTH)
    return when {
        (month == Calendar.OCTOBER && day >= 10) || (month == Calendar.NOVEMBER && day <= 3) -> Season.Halloween
        month == Calendar.DECEMBER || month == Calendar.JANUARY || month == Calendar.FEBRUARY -> Season.Winter
        else -> Season.Plain
    }
}

/**
 * Light decorative particles behind a header, as in the old KiroX: cobwebs and pumpkins around
 * Halloween, snow in winter, a few drifting dots otherwise. Static when system animations are off;
 * Compose stops the frame clock in the background, so it costs nothing there.
 */
@Composable
fun SeasonalLayer(modifier: Modifier = Modifier) {
    if (!LocalSeasonalEffects.current) return
    val context = LocalContext.current
    val systemAnimations = remember { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f }
    val animate = systemAnimations && com.kirox.app.ui.theme.LocalMotionFactor.current > 0f
    val season = remember { currentSeason() }
    val phase = if (animate) {
        val transition = rememberInfiniteTransition(label = "seasonal")
        val value by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(14_000, easing = LinearEasing), RepeatMode.Restart), label = "seasonal_phase")
        value
    } else 0.3f
    Canvas(modifier) {
        if (season == Season.Halloween) { cobweb(Offset.Zero, flip = false); cobweb(Offset(size.width, 0f), flip = true) }
        repeat(PARTICLES) { i ->
            val seed = (i * 7919) % 1000 / 1000f
            val x = size.width * ((i * 37 % 101) / 100f) + sin((phase + seed) * 2 * PI).toFloat() * 6f
            val y = ((seed + phase) % 1f) * size.height
            val alpha = 0.25f + (i % 3) * 0.12f
            if (season == Season.Halloween && i % 4 == 0) pumpkin(Offset(x, y), 7f + (i % 3) * 2f, alpha + .3f)
            else drawCircle(Color.White.copy(alpha = alpha), radius = if (season == Season.Winter) 2.2f + (i % 3) else 1.6f + (i % 2), center = Offset(x, y))
        }
    }
}

private const val PARTICLES = 14

private fun DrawScope.cobweb(corner: Offset, flip: Boolean) {
    val color = Color.White.copy(alpha = 0.16f)
    val radius = size.height.coerceAtMost(220f) * 0.75f
    val dir = if (flip) -1f else 1f
    val angles = (0..4).map { it * (PI / 2) / 4 }
    angles.forEach { a -> drawLine(color, corner, corner + Offset(dir * cos(a).toFloat() * radius, sin(a).toFloat() * radius), 1.5f) }
    listOf(.35f, .6f, .85f).forEach { r ->
        val path = Path()
        angles.forEachIndexed { index, a ->
            val point = corner + Offset(dir * cos(a).toFloat() * radius * r, sin(a).toFloat() * radius * r)
            if (index == 0) path.moveTo(point.x, point.y) else path.lineTo(point.x, point.y)
        }
        drawPath(path, color, style = Stroke(1.2f))
    }
}

private fun DrawScope.pumpkin(center: Offset, r: Float, alpha: Float) {
    val body = Color(0xFFC8742A).copy(alpha = alpha.coerceAtMost(.85f))
    drawOval(body, topLeft = Offset(center.x - r * 1.2f, center.y - r * .9f), size = Size(r * 2.4f, r * 1.8f))
    drawLine(Color(0xFF5B4A2A).copy(alpha = alpha), center + Offset(0f, -r * .9f), center + Offset(r * .25f, -r * 1.4f), r * .35f)
}
