package com.kirox.app.ui.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.sin

private val BulbColors = listOf(Color(0xFFFF4D5E), Color(0xFFFFC94D), Color(0xFF4DD6FF), Color(0xFF7CFF7A), Color(0xFFD36BFF))
private val Wire = Color(0xFF3A4A3F)
private val Gold = Color(0xFFFFD45C)
private val TreeDark = Color(0xFF1E4D36)
private val TreeMid = Color(0xFF2A6446)

/** Soft round glow of [color]; cached per size and colour. */
private fun DrawScope.drawGlowCached(center: Offset, radius: Float, color: Color, alpha: Float) {
    val r = radius.toInt().coerceAtLeast(2)
    val sprite = SpriteCache.get(this, SpriteKey("glow", r, color.toArgb(), false), r * 2, r * 2) {
        drawCircle(Brush.radialGradient(listOf(color.copy(alpha = 0.7f), color.copy(alpha = 0.18f), Color.Transparent), Offset(r.toFloat(), r.toFloat()), r.toFloat()), r.toFloat())
    }
    drawImage(sprite, Offset(center.x - r, center.y - r), alpha = alpha.coerceIn(0f, 1f))
}

/** A garland sagging between [x0] and [x1] from height [y]; bulbs twinkle out of phase. */
fun DrawScope.drawGarland(x0: Float, x1: Float, y: Float, sag: Float, t: Float, bulbR: Float, light: Boolean) {
    if (x1 - x0 < bulbR * 4) return
    val segments = max(1, ((x1 - x0) / (bulbR * 13f)).toInt())
    val segW = (x1 - x0) / segments
    val wire = Path().apply {
        moveTo(x0, y)
        repeat(segments) { i -> val a = x0 + segW * i; quadraticTo(a + segW / 2, y + sag * 2, a + segW, y) }
    }
    drawPath(wire, Wire, style = Stroke(width = (bulbR * 0.28f).coerceAtLeast(1f)))
    repeat(segments) { i ->
        val cx = x0 + segW * (i + 0.5f)
        val cy = y + sag + bulbR * 1.1f
        val color = BulbColors[i % BulbColors.size]
        val on = if (light) 1f else 0.5f + 0.5f * (0.5f + 0.5f * sin(t * 2.4f + i * 1.7f))
        if (!light) drawGlowCached(Offset(cx, cy), bulbR * 3.2f, color, on * 0.75f)
        drawRect(Wire, Offset(cx - bulbR * 0.35f, cy - bulbR * 1.35f), Size(bulbR * 0.7f, bulbR * 0.55f))
        drawOval(color.copy(alpha = 0.55f + 0.45f * on), Offset(cx - bulbR * 0.68f, cy - bulbR * 0.9f), Size(bulbR * 1.36f, bulbR * 2f))
        drawCircle(Color.White.copy(alpha = 0.55f * on), bulbR * 0.24f, Offset(cx - bulbR * 0.24f, cy - bulbR * 0.3f))
    }
}

private fun DrawScope.drawGift(bottomCenter: Offset, side: Float, box: Color, ribbon: Color) {
    val left = bottomCenter.x - side / 2
    val top = bottomCenter.y - side
    drawRect(box, Offset(left, top), Size(side, side))
    drawRect(Color.Black.copy(alpha = 0.18f), Offset(left + side * 0.6f, top), Size(side * 0.4f, side))
    drawRect(ribbon, Offset(bottomCenter.x - side * 0.08f, top), Size(side * 0.16f, side))
    drawRect(ribbon, Offset(left, top + side * 0.3f), Size(side, side * 0.14f))
    drawCircle(ribbon, side * 0.13f, Offset(bottomCenter.x - side * 0.14f, top - side * 0.05f))
    drawCircle(ribbon, side * 0.13f, Offset(bottomCenter.x + side * 0.14f, top - side * 0.05f))
}

/** Tier geometry as fractions of the tree height: lift of the tier bottom, half-width, tier height. */
private val Tiers = listOf(Triple(0.1f, 0.4f, 0.36f), Triple(0.32f, 0.31f, 0.33f), Triple(0.53f, 0.22f, 0.33f))

/**
 * Decorated fir with gifts at its foot: the tree, baubles and tinsel are one cached sprite;
 * only the star and the small lights on top of it twinkle.
 */
fun DrawScope.drawNewYearTree(baseCenter: Offset, height: Float, t: Float, light: Boolean) {
    val h = height.toInt().coerceAtLeast(8)
    val w = h
    val sprite = SpriteCache.get(this, SpriteKey("nytree", h, 0, false), w, h) {
        val cx = w / 2f
        val base = h.toFloat()
        drawRect(Color(0xFF4A3426), Offset(cx - h * 0.04f, base - h * 0.13f), Size(h * 0.08f, h * 0.1f))
        Tiers.forEachIndexed { i, (lift, half, tall) ->
            val bottom = base - h * 0.1f - h * lift
            val top = bottom - h * tall
            val tier = Path().apply {
                moveTo(cx, top)
                lineTo(cx - h * half, bottom)
                quadraticTo(cx, bottom + h * 0.05f, cx + h * half, bottom)
                close()
            }
            drawPath(tier, if (i % 2 == 0) TreeDark else TreeMid)
            // Tinsel swag across the tier.
            val swag = Path().apply {
                moveTo(cx - h * half * 0.7f, bottom - h * tall * 0.32f)
                quadraticTo(cx, bottom - h * tall * 0.05f, cx + h * half * 0.62f, bottom - h * tall * 0.42f)
            }
            drawPath(swag, Gold.copy(alpha = 0.85f), style = Stroke(width = h * 0.012f, cap = StrokeCap.Round))
            // Baubles along the bottom edge.
            listOf(-0.72f, -0.15f, 0.48f).forEachIndexed { k, fx ->
                val c = BulbColors[(i * 3 + k) % BulbColors.size]
                val p = Offset(cx + h * half * fx, bottom - h * 0.012f)
                drawCircle(c, h * 0.028f, p)
                drawCircle(Color.White.copy(alpha = 0.6f), h * 0.008f, Offset(p.x - h * 0.009f, p.y - h * 0.01f))
            }
        }
        drawGift(Offset(cx - h * 0.3f, base), h * 0.14f, Color(0xFFD9434F), Gold)
        drawGift(Offset(cx + h * 0.27f, base), h * 0.12f, Color(0xFF3F7FD9), Color.White)
        drawGift(Offset(cx - h * 0.14f, base), h * 0.085f, Color(0xFF8E55D9), Gold)
    }
    val left = baseCenter.x - w / 2f
    val top = baseCenter.y - h
    drawImage(sprite, Offset(left, top))

    // Twinkling lights scattered over the tiers.
    Tiers.forEachIndexed { i, (lift, half, tall) ->
        val bottom = top + h - h * 0.1f - h * lift
        listOf(-0.45f to 0.55f, 0.2f to 0.3f, 0.5f to 0.7f, -0.1f to 0.82f).forEachIndexed { k, (fx, fy) ->
            val y = bottom - h * tall * (1f - fy) * 0.9f
            val spread = h * half * (fy * 0.9f)
            val p = Offset(baseCenter.x + spread * fx * 1.6f, y)
            val color = BulbColors[(i + k * 2) % BulbColors.size]
            val on = if (light) 1f else 0.5f + 0.5f * sin(t * 3f + i * 2.1f + k * 1.3f)
            if (!light) drawGlowCached(p, h * 0.04f, color, on * 0.8f)
            drawCircle(color.copy(alpha = 0.6f + 0.4f * on), h * 0.011f, p)
        }
    }
    // Star on the top.
    val starC = Offset(baseCenter.x, top + h * 0.035f)
    val pulse = if (light) 1f else 0.75f + 0.25f * sin(t * 2f)
    if (!light) drawGlowCached(starC, h * 0.1f, Gold, pulse)
    val star = Path()
    val outer = h * 0.055f
    repeat(10) { i ->
        val a = -PI / 2 + i * PI / 5
        val rr = if (i % 2 == 0) outer else outer * 0.45f
        val p = Offset(starC.x + cos(a).toFloat() * rr, starC.y + sin(a).toFloat() * rr)
        if (i == 0) star.moveTo(p.x, p.y) else star.lineTo(p.x, p.y)
    }
    star.close()
    drawPath(star, Gold)
}

/** Two alternating firework bursts in the band [0, bandHeight]; [r] is the burst radius. */
fun DrawScope.drawFireworks(t: Float, bandHeight: Float, r: Float) {
    val period = 3.4f
    repeat(2) { k ->
        val local = t + k * period / 2
        val n = floor(local / period).toInt()
        val p = local / period - n
        if (p > 0.72f) return@repeat
        val q = p / 0.72f
        val seed = (((n * 7919 + k * 104729) % 1000) + 1000) % 1000 / 1000f
        val cx = size.width * (0.18f + 0.64f * seed)
        val cy = bandHeight * (0.3f + 0.4f * ((seed * 7f) % 1f))
        val color = BulbColors[((n + k) % BulbColors.size + BulbColors.size) % BulbColors.size]
        val radius = r * (1f - (1f - q) * (1f - q))
        val alpha = 1f - q
        val drop = q * q * r * 0.35f
        if (q < 0.18f) drawGlowCached(Offset(cx, cy), r * 0.6f, Color.White, (0.18f - q) * 4f)
        repeat(18) { i ->
            val a = i * 2 * PI.toFloat() / 18 + seed
            val c = cos(a); val s = sin(a)
            val tip = Offset(cx + c * radius, cy + s * radius + drop)
            val tail = Offset(cx + c * radius * 0.72f, cy + s * radius * 0.72f + drop * 0.7f)
            drawLine(color.copy(alpha = alpha * 0.45f), tail, tip, r * 0.03f, StrokeCap.Round)
            drawCircle(color.copy(alpha = alpha), r * 0.035f, tip)
        }
    }
}
