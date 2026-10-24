package com.kirox.app.ui.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import kotlin.math.cos
import kotlin.math.sin

private val Blossom = Color(0xFFFFB7CE)
private val BlossomDeep = Color(0xFFF48FB1)
private val BlossomCore = Color(0xFFFFE08A)
private val Bark = Color(0xFF5A3E36)
private val Grass = Color(0xFF4E9F5A)
private val GrassDark = Color(0xFF2F7041)

/** Five-petal blossom of radius [r] centred at the origin. */
private fun DrawScope.drawBlossom(r: Float, color: Color = Blossom) {
    repeat(5) { i ->
        rotate(i * 72f, pivot = Offset.Zero) {
            drawOval(color, Offset(-r * 0.32f, -r), Size(r * 0.64f, r * 0.72f))
        }
    }
    drawCircle(BlossomCore, r * 0.2f, Offset.Zero)
}

/** One falling petal, cached; [shade] 0 or 1 picks light or deep pink. */
fun DrawScope.drawPetalCached(center: Offset, r: Float, shade: Int, rotationDeg: Float, flip: Float, alpha: Float) {
    val rr = r.toInt().coerceAtLeast(2)
    val box = rr * 2 + 2
    val sprite = SpriteCache.get(this, SpriteKey("petal", rr, shade, false), box, box) {
        val c = box / 2f
        val petal = Path().apply {
            moveTo(c, c - rr)
            cubicTo(c + rr * 0.9f, c - rr * 0.5f, c + rr * 0.6f, c + rr * 0.8f, c, c + rr)
            cubicTo(c - rr * 0.6f, c + rr * 0.8f, c - rr * 0.9f, c - rr * 0.5f, c, c - rr)
            close()
        }
        drawPath(petal, if (shade == 0) Blossom else BlossomDeep)
        drawLine(Color.White.copy(alpha = 0.4f), Offset(c, c - rr * 0.6f), Offset(c, c + rr * 0.5f), rr * 0.12f, StrokeCap.Round)
    }
    translate(center.x, center.y) {
        rotate(rotationDeg, pivot = Offset.Zero) {
            // Tumbling: the petal narrows and widens as it turns in the air.
            scale(flip.coerceIn(0.2f, 1f), 1f, pivot = Offset.Zero) { drawImage(sprite, Offset(-box / 2f, -box / 2f), alpha = alpha) }
        }
    }
}

/** A blossoming cherry branch growing from a top corner; cached. */
fun DrawScope.drawBlossomBranchCached(corner: Offset, length: Float, flip: Boolean) {
    val s = length.toInt().coerceAtLeast(8)
    val sprite = SpriteCache.get(this, SpriteKey("branch", s, 0, flip), s, s) {
        val dir = if (flip) -1f else 1f
        val ox = if (flip) s.toFloat() else 0f
        fun p(x: Float, y: Float) = Offset(ox + dir * x * s, y * s)
        val main = Path().apply {
            val a = p(0f, 0.08f); moveTo(a.x, a.y)
            val c = p(0.45f, 0.05f); val e = p(0.92f, 0.38f); quadraticTo(c.x, c.y, e.x, e.y)
        }
        drawPath(main, Bark, style = Stroke(width = s * 0.035f, cap = StrokeCap.Round))
        listOf(Triple(p(0.3f, 0.09f), p(0.42f, 0.42f), 0.022f), Triple(p(0.6f, 0.15f), p(0.68f, 0.02f), 0.018f), Triple(p(0.72f, 0.22f), p(0.62f, 0.6f), 0.016f))
            .forEach { (a, b, w) -> drawLine(Bark, a, b, s * w, StrokeCap.Round) }
        val flowers = listOf(0.08f to 0.12f, 0.22f to 0.04f, 0.36f to 0.2f, 0.42f to 0.42f, 0.5f to 0.1f, 0.58f to 0.26f, 0.68f to 0.03f, 0.64f to 0.58f, 0.78f to 0.3f, 0.9f to 0.4f, 0.3f to 0.3f, 0.82f to 0.18f)
        flowers.forEachIndexed { i, (x, y) ->
            val c = p(x, y)
            translate(c.x, c.y) { drawBlossom(s * (0.05f + (i % 3) * 0.012f), if (i % 4 == 0) BlossomDeep else Blossom) }
        }
        // Buds.
        listOf(0.15f to 0.17f, 0.48f to 0.33f, 0.74f to 0.12f, 0.86f to 0.48f).forEach { (x, y) -> drawCircle(BlossomDeep, s * 0.014f, p(x, y)) }
    }
    drawImage(sprite, if (flip) Offset(corner.x - s, corner.y) else corner)
}

/** Spring meadow along the bottom: two hills, grass blades and small flowers; cached. */
fun DrawScope.drawMeadowCached(width: Float, height: Float) {
    val w = width.toInt().coerceAtLeast(2)
    val h = height.toInt().coerceAtLeast(2)
    val sprite = SpriteCache.get(this, SpriteKey("meadow", w, h, false), w, h) {
        val back = Path().apply {
            moveTo(0f, h * 0.5f)
            cubicTo(w * 0.25f, h * 0.2f, w * 0.5f, h * 0.55f, w * 0.7f, h * 0.35f)
            cubicTo(w * 0.85f, h * 0.22f, w * 0.95f, h * 0.4f, w.toFloat(), h * 0.36f)
            lineTo(w.toFloat(), h.toFloat()); lineTo(0f, h.toFloat()); close()
        }
        drawPath(back, GrassDark)
        val front = Path().apply {
            moveTo(0f, h * 0.72f)
            cubicTo(w * 0.3f, h * 0.5f, w * 0.55f, h * 0.8f, w * 0.8f, h * 0.6f)
            cubicTo(w * 0.9f, h * 0.54f, w * 0.96f, h * 0.62f, w.toFloat(), h * 0.6f)
            lineTo(w.toFloat(), h.toFloat()); lineTo(0f, h.toFloat()); close()
        }
        drawPath(front, Brush.verticalGradient(listOf(Grass, GrassDark), startY = h * 0.5f, endY = h.toFloat()))
        // Grass blades.
        repeat(46) { i ->
            val x = w * ((i * 37 % 100) / 100f)
            val y = h * (0.66f + (i * 13 % 30) / 100f)
            val len = h * (0.08f + (i % 4) * 0.025f)
            drawLine(if (i % 2 == 0) Grass else GrassDark, Offset(x, y), Offset(x + (i % 3 - 1) * len * 0.3f, y - len), h * 0.012f + 1f, StrokeCap.Round)
        }
        // Flowers: daisies, tulips and blue bells.
        repeat(16) { i ->
            val x = w * ((i * 53 % 97) / 97f)
            val y = h * (0.62f + (i * 17 % 30) / 100f)
            val r = h * 0.04f
            drawLine(GrassDark, Offset(x, y), Offset(x, y + r * 2.2f), r * 0.25f)
            when (i % 3) {
                0 -> translate(x, y) { repeat(8) { k -> rotate(k * 45f, pivot = Offset.Zero) { drawOval(Color.White, Offset(-r * 0.22f, -r), Size(r * 0.44f, r * 0.75f)) } }; drawCircle(BlossomCore, r * 0.3f, Offset.Zero) }
                1 -> drawPath(Path().apply { moveTo(x - r * 0.6f, y - r); lineTo(x - r * 0.3f, y - r * 0.4f); lineTo(x, y - r); lineTo(x + r * 0.3f, y - r * 0.4f); lineTo(x + r * 0.6f, y - r); quadraticTo(x + r * 0.7f, y + r * 0.3f, x, y + r * 0.3f); quadraticTo(x - r * 0.7f, y + r * 0.3f, x - r * 0.6f, y - r); close() }, Color(0xFFFF6B7A))
                else -> { drawCircle(Color(0xFF8AA8FF), r * 0.5f, Offset(x - r * 0.35f, y)); drawCircle(Color(0xFF8AA8FF), r * 0.5f, Offset(x + r * 0.35f, y - r * 0.2f)) }
            }
        }
    }
    drawImage(sprite, Offset(0f, this.size.height - h))
}

/** Butterfly: two wing pairs that flap by squeezing horizontally; [flap] in -1..1. */
fun DrawScope.drawButterfly(center: Offset, span: Float, flap: Float, color: Color) {
    val open = 0.25f + 0.75f * (flap * 0.5f + 0.5f)
    translate(center.x, center.y) {
        scale(open, 1f, pivot = Offset.Zero) {
            listOf(-1f, 1f).forEach { side ->
                drawOval(color, Offset(if (side < 0) -span * 0.5f else 0f, -span * 0.38f), Size(span * 0.5f, span * 0.42f))
                drawOval(color.copy(alpha = 0.8f), Offset(if (side < 0) -span * 0.38f else 0f, -span * 0.02f), Size(span * 0.38f, span * 0.32f))
                drawCircle(Color.White.copy(alpha = 0.7f), span * 0.06f, Offset(side * span * 0.26f, -span * 0.2f))
            }
        }
        drawLine(Color(0xFF3B2B2B), Offset(0f, -span * 0.3f), Offset(0f, span * 0.25f), span * 0.06f, StrokeCap.Round)
        drawLine(Color(0xFF3B2B2B), Offset(0f, -span * 0.3f), Offset(-span * 0.12f, -span * 0.48f), span * 0.025f)
        drawLine(Color(0xFF3B2B2B), Offset(0f, -span * 0.3f), Offset(span * 0.12f, -span * 0.48f), span * 0.025f)
    }
}

/** Warm sun glow in a top corner; cached. */
fun DrawScope.drawSunGlowCached(center: Offset, radius: Float) {
    val r = radius.toInt().coerceAtLeast(2)
    val sprite = SpriteCache.get(this, SpriteKey("sunglow", r, 0, false), r * 2, r * 2) {
        drawCircle(Brush.radialGradient(listOf(Color(0x55FFE7A0), Color(0x1AFFD27A), Color.Transparent), Offset(r.toFloat(), r.toFloat()), r.toFloat()), r.toFloat())
    }
    drawImage(sprite, Offset(center.x - r, center.y - r))
}

internal fun butterflyPath(t: Float, i: Int, w: Float, h: Float): Offset {
    val a = t * (0.25f + i * 0.07f) + i * 2.1f
    return Offset(w * (0.5f + 0.38f * sin(a) * cos(a * 0.6f + i)), h * (0.45f + 0.15f * sin(a * 1.7f + i)) + sin(t * 3f + i) * h * 0.015f)
}
