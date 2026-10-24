package com.kirox.app.ui.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.scale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Snowflake shapes: branched dendrite, six-point star, hexagonal plate, simple six-arm cross. */
enum class FlakeShape { Dendrite, Star, Plate, Simple }

private val Ice = Color(0xFFEAF4FF)
private val IceShade = Color(0xFFB8D4F0)
private val SnowWhite = Color(0xFFF4F8FC)
private val SnowShadow = Color(0xFFC9DAEA)
private val PineDark = Color(0xFF1F3A2E)
private val PineMid = Color(0xFF2C5141)

/** A six-fold snowflake of radius [r] centred at the origin. */
private fun DrawScope.drawFlake(r: Float, shape: FlakeShape) {
    val stroke = (r * 0.11f).coerceAtLeast(1.2f)
    repeat(6) { i ->
        rotate(i * 60f, pivot = Offset.Zero) {
            when (shape) {
                FlakeShape.Dendrite -> {
                    drawLine(Ice, Offset.Zero, Offset(0f, -r), stroke, StrokeCap.Round)
                    listOf(0.38f to 0.32f, 0.62f to 0.26f, 0.82f to 0.16f).forEach { (at, len) ->
                        drawLine(Ice, Offset(0f, -r * at), Offset(-r * len, -r * (at + len * 0.75f)), stroke * 0.8f, StrokeCap.Round)
                        drawLine(Ice, Offset(0f, -r * at), Offset(r * len, -r * (at + len * 0.75f)), stroke * 0.8f, StrokeCap.Round)
                    }
                }
                FlakeShape.Star -> {
                    drawPath(Path().apply {
                        moveTo(-r * 0.14f, 0f); lineTo(0f, -r); lineTo(r * 0.14f, 0f); close()
                    }, Ice)
                    drawCircle(IceShade, r * 0.1f, Offset(0f, -r * 0.55f))
                }
                FlakeShape.Plate -> {
                    drawLine(Ice, Offset.Zero, Offset(0f, -r * 0.95f), stroke, StrokeCap.Round)
                    drawLine(Ice, Offset(-r * 0.3f, -r * 0.5f), Offset(r * 0.3f, -r * 0.5f), stroke * 0.8f, StrokeCap.Round)
                    drawCircle(Ice, stroke * 0.9f, Offset(0f, -r * 0.95f))
                }
                FlakeShape.Simple -> {
                    drawLine(Ice, Offset.Zero, Offset(0f, -r), stroke, StrokeCap.Round)
                    drawLine(Ice, Offset(0f, -r * 0.6f), Offset(-r * 0.2f, -r * 0.82f), stroke * 0.8f, StrokeCap.Round)
                    drawLine(Ice, Offset(0f, -r * 0.6f), Offset(r * 0.2f, -r * 0.82f), stroke * 0.8f, StrokeCap.Round)
                }
            }
        }
    }
    if (shape == FlakeShape.Plate) {
        // Hexagonal plate in the middle.
        val hex = Path()
        repeat(6) { i ->
            val a = i * PI / 3 - PI / 2
            val p = Offset(cos(a).toFloat() * r * 0.32f, sin(a).toFloat() * r * 0.32f)
            if (i == 0) hex.moveTo(p.x, p.y) else hex.lineTo(p.x, p.y)
        }
        hex.close()
        drawPath(hex, IceShade.copy(alpha = 0.9f))
    }
    drawCircle(Ice, stroke * 1.1f, Offset.Zero)
}

/** Cached snowflake sprite drawn at [center], rotated by [rotationDeg]. */
fun DrawScope.drawFlakeCached(center: Offset, r: Float, shape: FlakeShape, rotationDeg: Float, alpha: Float) {
    val rr = r.toInt().coerceAtLeast(2)
    val box = rr * 2 + 4
    val sprite = SpriteCache.get(this, SpriteKey("flake", rr, shape.ordinal, false), box, box) {
        translate(box / 2f, box / 2f) { drawFlake(rr.toFloat(), shape) }
    }
    translate(center.x, center.y) {
        rotate(rotationDeg, pivot = Offset.Zero) { drawImage(sprite, Offset(-box / 2f, -box / 2f), alpha = alpha) }
    }
}

/** Snowdrifts with a few snowy firs along the bottom edge, cached for the given size. */
fun DrawScope.drawDriftsCached(width: Float, height: Float, withTrees: Boolean) {
    val w = width.toInt().coerceAtLeast(2)
    val h = height.toInt().coerceAtLeast(2)
    val sprite = SpriteCache.get(this, SpriteKey("drifts", w, h, withTrees), w, h) {
        if (withTrees) listOf(0.12f to 0.9f, 0.2f to 0.65f, 0.83f to 1f, 0.92f to 0.7f).forEach { (x, s) ->
            drawFir(Offset(w * x, h * 0.7f), h * 0.6f * s)
        }
        // Back drift (shadowed) and front drift.
        val back = Path().apply {
            moveTo(0f, h * 0.55f)
            cubicTo(w * 0.2f, h * 0.25f, w * 0.38f, h * 0.6f, w * 0.55f, h * 0.42f)
            cubicTo(w * 0.72f, h * 0.24f, w * 0.88f, h * 0.5f, w.toFloat(), h * 0.38f)
            lineTo(w.toFloat(), h.toFloat()); lineTo(0f, h.toFloat()); close()
        }
        drawPath(back, SnowShadow)
        val front = Path().apply {
            moveTo(0f, h * 0.78f)
            cubicTo(w * 0.25f, h * 0.55f, w * 0.45f, h * 0.85f, w * 0.68f, h * 0.66f)
            cubicTo(w * 0.82f, h * 0.55f, w * 0.92f, h * 0.7f, w.toFloat(), h * 0.62f)
            lineTo(w.toFloat(), h.toFloat()); lineTo(0f, h.toFloat()); close()
        }
        drawPath(front, Brush.verticalGradient(listOf(SnowWhite, SnowShadow), startY = h * 0.55f, endY = h.toFloat()))
        // Sparkles on the snow.
        repeat(18) { i ->
            val x = w * ((i * 37 % 100) / 100f)
            val y = h * (0.72f + (i * 13 % 25) / 100f)
            drawCircle(Color.White, 1.4f, Offset(x, y))
        }
    }
    drawImage(sprite, Offset(0f, this.size.height - h))
}

/** A fir with snow on its tiers; [base] is the trunk foot, [height] the tree height. */
private fun DrawScope.drawFir(base: Offset, height: Float) {
    drawRect(Color(0xFF4A3426), topLeft = Offset(base.x - height * 0.04f, base.y - height * 0.12f), size = Size(height * 0.08f, height * 0.14f))
    listOf(0.0f to 0.42f, 0.26f to 0.34f, 0.5f to 0.25f).forEachIndexed { i, (lift, half) ->
        val bottom = base.y - height * 0.1f - height * lift
        val top = bottom - height * 0.42f
        val tier = Path().apply { moveTo(base.x, top); lineTo(base.x - height * half, bottom); lineTo(base.x + height * half, bottom); close() }
        drawPath(tier, if (i % 2 == 0) PineDark else PineMid)
        // Snow cap on each tier.
        val cap = Path().apply {
            moveTo(base.x, top)
            lineTo(base.x - height * half * 0.55f, top + height * 0.24f)
            quadraticTo(base.x, top + height * 0.3f, base.x + height * half * 0.55f, top + height * 0.24f)
            close()
        }
        drawPath(cap, SnowWhite)
    }
}

/** Frost bloom in a top corner: a soft icy glow with a cluster of small crystals; cached. */
fun DrawScope.drawFrostCached(corner: Offset, radius: Float, flip: Boolean) {
    val s = radius.toInt().coerceAtLeast(2)
    val sprite = SpriteCache.get(this, SpriteKey("frost", s, 1, flip), s, s) {
        val origin = if (flip) Offset(s.toFloat(), 0f) else Offset.Zero
        val dir = if (flip) -1f else 1f
        drawCircle(
            Brush.radialGradient(listOf(Ice.copy(alpha = 0.22f), Ice.copy(alpha = 0.06f), Color.Transparent), center = origin, radius = s * 0.95f),
            s * 0.95f, origin
        )
        // Crystals along an arc, biggest in the corner.
        val crystals = listOf(0.18f to 0.2f, 0.42f to 0.12f, 0.3f to 0.55f, 0.62f to 0.3f, 0.12f to 0.7f, 0.75f to 0.1f, 0.5f to 0.5f)
        crystals.forEachIndexed { i, (fx, fy) ->
            val r = s * (0.16f - i * 0.014f)
            translate(origin.x + dir * s * fx, origin.y + s * fy) {
                scale(0.9f) { drawFlake(r, if (i % 2 == 0) FlakeShape.Dendrite else FlakeShape.Star) }
            }
        }
    }
    drawImage(sprite, if (flip) Offset(corner.x - s, corner.y) else corner, alpha = 0.85f)
}
