package com.kirox.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import com.kirox.app.ui.theme.Fusion

/**
 * Theme artwork behind conversations, drawn in code (no bitmap assets, nothing to license):
 * Expressive — soft violet abstract flowers, Glass — evening mountains over a lake. Static; a scrim
 * on top keeps message bubbles readable.
 */
@Composable
fun FusionArt(modifier: Modifier = Modifier) {
    val colors = Fusion.colors
    Canvas(modifier) {
        if (colors.glass) drawMountains() else drawFlowers(colors.chatWallpaper ?: listOf(colors.background, colors.background))
    }
}

/** Glass profile header: the same mountains fading into the screen background. */
@Composable
fun FusionArtHeader(modifier: Modifier = Modifier) {
    val colors = Fusion.colors
    Canvas(modifier) {
        drawMountains(scrim = false)
        drawRect(Brush.verticalGradient(0.35f to Color.Transparent, 1f to colors.background))
    }
}

private fun DrawScope.drawFlowers(base: List<Color>) {
    drawRect(Brush.verticalGradient(base))
    val w = size.width
    val h = size.height
    // Flower centre (fractions of the size), radius in widths, petal colour.
    val flowers = listOf(
        Triple(Offset(0.18f, 0.16f), 0.42f, Color(0xFF8A55E8)),
        Triple(Offset(0.92f, 0.38f), 0.50f, Color(0xFF6C3FD6)),
        Triple(Offset(0.22f, 0.66f), 0.46f, Color(0xFF9A4FD8)),
        Triple(Offset(0.86f, 0.90f), 0.40f, Color(0xFF5E3BC4)),
        Triple(Offset(0.55f, 0.02f), 0.30f, Color(0xFF7448D2))
    )
    flowers.forEachIndexed { index, (centreFraction, radiusFraction, tint) ->
        val centre = Offset(centreFraction.x * w, centreFraction.y * h)
        val r = radiusFraction * w
        val petals = 7 + index % 3
        repeat(petals) { i ->
            rotate(360f / petals * i + index * 17f, centre) {
                val petal = Offset(centre.x, centre.y - r * 0.48f)
                drawOval(
                    Brush.radialGradient(listOf(tint.copy(alpha = 0.55f), tint.copy(alpha = 0.18f), Color.Transparent), center = petal, radius = r * 0.5f),
                    topLeft = Offset(petal.x - r * 0.22f, petal.y - r * 0.5f), size = Size(r * 0.44f, r)
                )
            }
        }
        drawCircle(Brush.radialGradient(listOf(Color(0x66E0B8FF), Color.Transparent), center = centre, radius = r * 0.22f), r * 0.22f, centre)
    }
    // Dark veil: the art stays a background, bubbles stay readable.
    drawRect(Color(0x40070512))
}

private fun DrawScope.drawMountains(scrim: Boolean = true) {
    val w = size.width
    val h = size.height
    val horizon = h * 0.46f
    // Evening sky: deep blue down to a warm glow at the horizon.
    // Glow sits at the height of the ridge tops, so it shows between the peaks.
    drawRect(Brush.verticalGradient(0f to Color(0xFF16244C), 0.12f to Color(0xFF3A4884), 0.22f to Color(0xFF9A6F98), 0.3f to Color(0xFFE09A80), horizon / h to Color(0xFFB98A8E), 1f to Color(0xFF0A1428)))
    fun ridge(points: List<Pair<Float, Float>>, base: Float): Path = Path().apply {
        moveTo(0f, base)
        points.forEach { (x, y) -> lineTo(x * w, y * h) }
        lineTo(w, base); close()
    }
    val far = listOf(0f to 0.38f, 0.12f to 0.33f, 0.24f to 0.36f, 0.4f to 0.25f, 0.52f to 0.18f, 0.64f to 0.27f, 0.78f to 0.31f, 0.9f to 0.28f, 1f to 0.33f)
    val near = listOf(0f to 0.43f, 0.1f to 0.39f, 0.22f to 0.44f, 0.34f to 0.37f, 0.47f to 0.42f, 0.6f to 0.35f, 0.72f to 0.41f, 0.86f to 0.38f, 1f to 0.43f)
    drawPath(ridge(far, horizon), Brush.verticalGradient(listOf(Color(0xFF5A5F8E), Color(0xFF2C3563)), startY = 0.18f * h, endY = horizon))
    // Snow on the far peaks: small light caps under each summit.
    far.zipWithNext().zipWithNext().forEach { (a, b) ->
        val peak = a.second
        if (peak.second < a.first.second && peak.second < b.second.second) {
            val px = peak.first * w; val py = peak.second * h; val cap = h * 0.035f
            drawPath(Path().apply { moveTo(px, py); lineTo(px - cap * 0.9f, py + cap); lineTo(px + cap * 0.9f, py + cap); close() }, Color(0xCCE4E8F7))
        }
    }
    drawPath(ridge(near, horizon), Brush.verticalGradient(listOf(Color(0xFF2E3B66), Color(0xFF151E3A)), startY = 0.3f * h, endY = horizon))
    // Lake: a dimmed mirror of the ridges.
    drawRect(Brush.verticalGradient(listOf(Color(0xFF26355E), Color(0xFF0A1326)), startY = horizon, endY = h), topLeft = Offset(0f, horizon), size = Size(w, h - horizon))
    fun mirror(points: List<Pair<Float, Float>>) = points.map { (x, y) -> x to (2 * horizon / h - y) }
    drawPath(ridge(mirror(far), horizon), Color(0x553A4778))
    drawPath(ridge(mirror(near), horizon), Color(0x66141D36))
    drawRect(Color(0x33D9A08A), topLeft = Offset(0f, horizon), size = Size(w, h * 0.006f))
    // Fir silhouettes on both shores.
    fun fir(x: Float, height: Float) {
        val base = horizon + h * 0.012f
        drawPath(Path().apply { moveTo(x, base - height); lineTo(x - height * 0.22f, base); lineTo(x + height * 0.22f, base); close() }, Color(0xFF0A1122))
    }
    listOf(0.02f, 0.06f, 0.1f, 0.15f, 0.84f, 0.89f, 0.93f, 0.97f).forEachIndexed { i, x -> fir(x * w, h * (0.05f + (i % 3) * 0.015f)) }
    if (scrim) drawRect(Brush.verticalGradient(listOf(Color(0x10071424), Color(0x66071424))))
}
