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
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Carved faces for jack-o'-lanterns. */
enum class PumpkinFace { Classic, Happy, Spooky, Cute, Sleepy }

/** Costumes; [None] is a plain pumpkin. */
enum class PumpkinCostume { None, WitchHat, TopHat, Vampire, Bow, Ghost }

private val PumpkinLight = Color(0xFFF59A2E)
private val PumpkinMid = Color(0xFFE07B1A)
private val PumpkinDark = Color(0xFFA64A0C)
private val PumpkinGroove = Color(0xFF7A3207)
private val StemColor = Color(0xFF5B6B2C)
private val StemDark = Color(0xFF3B4719)
private val CarveDark = Color(0xFF2A1203)
private val Glow = Color(0xFFFFD36B)

/**
 * A detailed jack-o'-lantern centred at [center] with body radius [r] (px).
 * [glow] 0..1 flickers the candle light behind the carved face; [blink] 0..1 closes the eyes.
 */
fun DrawScope.drawPumpkin(
    center: Offset, r: Float, face: PumpkinFace, costume: PumpkinCostume,
    glow: Float = 1f, blink: Float = 0f, rotationDeg: Float = 0f, alpha: Float = 1f,
    /** Light mode: solid colours instead of gradients, no inner glow halo. */
    flat: Boolean = false
) {
    translate(center.x, center.y) {
        rotate(rotationDeg, pivot = Offset.Zero) {
            if (costume == PumpkinCostume.Vampire) drawVampireCape(r, alpha)
            if (costume == PumpkinCostume.Ghost) drawGhostSheet(r, alpha, glow) else {
                drawStem(r, alpha)
                drawBody(r, alpha, flat)
                if (costume == PumpkinCostume.Vampire) drawVampireHair(r, alpha)
                drawFace(r, face, glow, blink, alpha, fangs = costume == PumpkinCostume.Vampire, flat = flat)
            }
            when (costume) {
                PumpkinCostume.WitchHat -> drawWitchHat(r, alpha)
                PumpkinCostume.TopHat -> drawTopHat(r, alpha)
                PumpkinCostume.Bow -> drawBow(r, alpha)
                else -> Unit
            }
        }
    }
}

private fun DrawScope.drawBody(r: Float, alpha: Float, flat: Boolean) {
    val w = r * 1.25f
    val h = r
    // Ribs: outer pair, inner pair, centre — each a gradient oval, outer ones darker.
    val ribs = listOf(-0.62f to 0.55f, 0.62f to 0.55f, -0.3f to 0.72f, 0.3f to 0.72f, 0f to 0.62f)
    ribs.forEachIndexed { index, (dx, widthFactor) ->
        val rw = w * widthFactor
        val left = dx * w - rw
        val brush = Brush.radialGradient(
            colors = listOf(PumpkinLight.copy(alpha = alpha), PumpkinMid.copy(alpha = alpha), PumpkinDark.copy(alpha = alpha)),
            center = Offset(dx * w - rw * 0.25f, -h * 0.35f), radius = rw * 1.6f
        )
        if (flat) drawOval((if (index < 2) PumpkinDark else PumpkinMid).copy(alpha = alpha), topLeft = Offset(left, -h), size = Size(rw * 2, h * 2))
        else drawOval(brush, topLeft = Offset(left, -h), size = Size(rw * 2, h * 2))
        if (index < 4) drawOval(PumpkinGroove.copy(alpha = alpha * 0.55f), topLeft = Offset(left, -h), size = Size(rw * 2, h * 2), style = Stroke(r * 0.04f))
    }
    // Soft top highlight.
    drawOval(Color.White.copy(alpha = alpha * 0.18f), topLeft = Offset(-w * 0.55f, -h * 0.82f), size = Size(w * 0.5f, h * 0.28f))
}

private fun DrawScope.drawStem(r: Float, alpha: Float) {
    val stem = Path().apply {
        moveTo(-r * 0.12f, -r * 0.9f)
        cubicTo(-r * 0.14f, -r * 1.2f, r * 0.02f, -r * 1.42f, r * 0.24f, -r * 1.45f)
        lineTo(r * 0.28f, -r * 1.3f)
        cubicTo(r * 0.12f, -r * 1.25f, r * 0.12f, -r * 1.1f, r * 0.14f, -r * 0.9f)
        close()
    }
    drawPath(stem, StemColor.copy(alpha = alpha))
    drawPath(stem, StemDark.copy(alpha = alpha), style = Stroke(r * 0.04f))
    // Curly vine.
    val vine = Path().apply {
        moveTo(r * 0.1f, -r * 1.05f)
        cubicTo(r * 0.45f, -r * 1.25f, r * 0.62f, -r * 0.95f, r * 0.45f, -r * 0.88f)
        cubicTo(r * 0.32f, -r * 0.84f, r * 0.34f, -r * 1.02f, r * 0.46f, -r * 1.0f)
    }
    drawPath(vine, StemColor.copy(alpha = alpha), style = Stroke(r * 0.05f, cap = StrokeCap.Round))
}

/** Carved holes: dark cut with candle glow inside. */
private fun DrawScope.carve(path: Path, glow: Float, alpha: Float) {
    drawPath(path, CarveDark.copy(alpha = alpha))
    drawPath(path, Glow.copy(alpha = alpha * (0.55f + 0.45f * glow)))
    drawPath(path, Color(0xFFFF8A1F).copy(alpha = alpha * 0.35f), style = Stroke(1.2f))
}

private fun DrawScope.drawFace(r: Float, face: PumpkinFace, glow: Float, blink: Float, alpha: Float, fangs: Boolean, flat: Boolean = false) {
    // Warm inner light spilling over the face.
    if (!flat) drawCircle(Brush.radialGradient(listOf(Glow.copy(alpha = alpha * 0.22f * glow), Color.Transparent), center = Offset(0f, r * 0.05f), radius = r * 0.9f), r * 0.9f, Offset(0f, r * 0.05f))
    val eyeOpen = (1f - blink).coerceIn(0.08f, 1f)
    when (face) {
        PumpkinFace.Classic -> {
            eye(Offset(-r * 0.38f, -r * 0.18f), r * 0.2f, eyeOpen, triangle = true, glow, alpha)
            eye(Offset(r * 0.38f, -r * 0.18f), r * 0.2f, eyeOpen, triangle = true, glow, alpha)
            carve(Path().apply { moveTo(0f, -r * 0.02f); lineTo(-r * 0.09f, r * 0.14f); lineTo(r * 0.09f, r * 0.14f); close() }, glow, alpha)
            carve(zigzagMouth(r, teeth = 3, wide = 0.62f, depth = 0.22f), glow, alpha)
        }
        PumpkinFace.Happy -> {
            arcEye(Offset(-r * 0.36f, -r * 0.16f), r * 0.17f, glow, alpha)
            arcEye(Offset(r * 0.36f, -r * 0.16f), r * 0.17f, glow, alpha)
            carve(Path().apply {
                moveTo(-r * 0.55f, r * 0.18f)
                quadraticTo(0f, r * 0.75f, r * 0.55f, r * 0.18f)
                quadraticTo(0f, r * 0.42f, -r * 0.55f, r * 0.18f); close()
            }, glow, alpha)
        }
        PumpkinFace.Spooky -> {
            carve(Path().apply { moveTo(-r * 0.58f, -r * 0.36f); lineTo(-r * 0.16f, -r * 0.16f); lineTo(-r * 0.5f, -r * (0.36f - 0.32f * eyeOpen)); close() }, glow, alpha)
            carve(Path().apply { moveTo(r * 0.58f, -r * 0.36f); lineTo(r * 0.16f, -r * 0.16f); lineTo(r * 0.5f, -r * (0.36f - 0.32f * eyeOpen)); close() }, glow, alpha)
            carve(zigzagMouth(r, teeth = 5, wide = 0.72f, depth = 0.32f), glow, alpha)
        }
        PumpkinFace.Cute -> {
            eye(Offset(-r * 0.32f, -r * 0.12f), r * 0.14f, eyeOpen, triangle = false, glow, alpha)
            eye(Offset(r * 0.32f, -r * 0.12f), r * 0.14f, eyeOpen, triangle = false, glow, alpha)
            drawCircle(Color(0xFFFF6F91).copy(alpha = alpha * 0.45f), r * 0.1f, Offset(-r * 0.55f, r * 0.12f))
            drawCircle(Color(0xFFFF6F91).copy(alpha = alpha * 0.45f), r * 0.1f, Offset(r * 0.55f, r * 0.12f))
            carve(Path().apply {
                moveTo(-r * 0.18f, r * 0.2f); quadraticTo(-r * 0.09f, r * 0.34f, 0f, r * 0.22f)
                quadraticTo(r * 0.09f, r * 0.34f, r * 0.18f, r * 0.2f); quadraticTo(0f, r * 0.42f, -r * 0.18f, r * 0.2f); close()
            }, glow, alpha)
        }
        PumpkinFace.Sleepy -> {
            listOf(-1f, 1f).forEach { side ->
                drawLine(CarveDark.copy(alpha = alpha), Offset(side * r * 0.5f, -r * 0.14f), Offset(side * r * 0.2f, -r * 0.12f), r * 0.08f, StrokeCap.Round)
                drawLine(Glow.copy(alpha = alpha * 0.7f * glow), Offset(side * r * 0.5f, -r * 0.14f), Offset(side * r * 0.2f, -r * 0.12f), r * 0.04f, StrokeCap.Round)
            }
            carve(Path().apply { addOval(androidx.compose.ui.geometry.Rect(-r * 0.12f, r * 0.18f, r * 0.12f, r * 0.42f)) }, glow, alpha)
        }
    }
    if (fangs) {
        listOf(-1f, 1f).forEach { side ->
            drawPath(Path().apply {
                moveTo(side * r * 0.22f, r * 0.24f); lineTo(side * r * 0.12f, r * 0.24f); lineTo(side * r * 0.17f, r * 0.44f); close()
            }, Color.White.copy(alpha = alpha * 0.95f))
        }
    }
}

private fun DrawScope.eye(c: Offset, size: Float, open: Float, triangle: Boolean, glow: Float, alpha: Float) {
    if (triangle) carve(Path().apply {
        moveTo(c.x, c.y - size * open); lineTo(c.x - size, c.y + size * 0.7f * open); lineTo(c.x + size, c.y + size * 0.7f * open); close()
    }, glow, alpha)
    else {
        carve(Path().apply { addOval(androidx.compose.ui.geometry.Rect(c.x - size, c.y - size * open, c.x + size, c.y + size * open)) }, glow, alpha)
        if (open > 0.5f) drawCircle(Color.White.copy(alpha = alpha * 0.85f), size * 0.32f, Offset(c.x - size * 0.3f, c.y - size * 0.3f))
    }
}

private fun DrawScope.arcEye(c: Offset, size: Float, glow: Float, alpha: Float) {
    carve(Path().apply {
        moveTo(c.x - size, c.y + size * 0.3f)
        quadraticTo(c.x, c.y - size * 1.1f, c.x + size, c.y + size * 0.3f)
        quadraticTo(c.x, c.y - size * 0.35f, c.x - size, c.y + size * 0.3f); close()
    }, glow, alpha)
}

private fun zigzagMouth(r: Float, teeth: Int, wide: Float, depth: Float): Path = Path().apply {
    val left = -r * wide; val right = r * wide; val top = r * 0.18f
    moveTo(left, top)
    quadraticTo(0f, top + r * (depth + 0.45f), right, top)
    // Upper lip with teeth notches.
    val step = (right - left) / (teeth * 2)
    var x = right
    for (i in 0 until teeth * 2) {
        x -= step
        lineTo(x, if (i % 2 == 0) top + r * depth * 0.55f else top + r * 0.06f)
    }
    close()
}

private fun DrawScope.drawWitchHat(r: Float, alpha: Float) {
    val hat = Color(0xFF2B1B3D).copy(alpha = alpha)
    val brimTop = -r * 0.9f
    drawOval(hat, topLeft = Offset(-r * 1.15f, brimTop - r * 0.16f), size = Size(r * 2.3f, r * 0.34f))
    val cone = Path().apply {
        moveTo(-r * 0.62f, brimTop); cubicTo(-r * 0.4f, brimTop - r * 0.8f, r * 0.1f, brimTop - r * 1.2f, r * 0.62f, brimTop - r * 1.45f)
        cubicTo(r * 0.3f, brimTop - r * 1.0f, r * 0.55f, brimTop - r * 0.5f, r * 0.62f, brimTop); close()
    }
    drawPath(cone, hat)
    drawRect(Color(0xFF7A4BB5).copy(alpha = alpha), topLeft = Offset(-r * 0.6f, brimTop - r * 0.28f), size = Size(r * 1.2f, r * 0.2f))
    drawRect(Color(0xFFFFC84A).copy(alpha = alpha), topLeft = Offset(-r * 0.1f, brimTop - r * 0.3f), size = Size(r * 0.2f, r * 0.24f), style = Stroke(r * 0.05f))
}

private fun DrawScope.drawTopHat(r: Float, alpha: Float) {
    val hat = Color(0xFF151515).copy(alpha = alpha)
    val brim = -r * 0.88f
    drawOval(hat, topLeft = Offset(-r * 0.9f, brim - r * 0.12f), size = Size(r * 1.8f, r * 0.26f))
    drawRect(hat, topLeft = Offset(-r * 0.55f, brim - r * 0.95f), size = Size(r * 1.1f, r * 0.9f))
    drawRect(Color(0xFFB0322E).copy(alpha = alpha), topLeft = Offset(-r * 0.55f, brim - r * 0.32f), size = Size(r * 1.1f, r * 0.16f))
    drawRect(Color.White.copy(alpha = alpha * 0.12f), topLeft = Offset(-r * 0.45f, brim - r * 0.9f), size = Size(r * 0.12f, r * 0.55f))
}

private fun DrawScope.drawBow(r: Float, alpha: Float) {
    val bow = Color(0xFF1C1C1C).copy(alpha = alpha)
    val y = r * 0.98f
    drawPath(Path().apply { moveTo(0f, y); lineTo(-r * 0.4f, y - r * 0.2f); lineTo(-r * 0.4f, y + r * 0.2f); close() }, bow)
    drawPath(Path().apply { moveTo(0f, y); lineTo(r * 0.4f, y - r * 0.2f); lineTo(r * 0.4f, y + r * 0.2f); close() }, bow)
    drawCircle(Color(0xFF7A4BB5).copy(alpha = alpha), r * 0.09f, Offset(0f, y))
}

private fun DrawScope.drawVampireCape(r: Float, alpha: Float) {
    val cape = Path().apply {
        moveTo(-r * 1.25f, r * 0.2f); lineTo(-r * 0.7f, -r * 1.05f); lineTo(0f, -r * 0.55f); lineTo(r * 0.7f, -r * 1.05f); lineTo(r * 1.25f, r * 0.2f)
        lineTo(r * 0.9f, r * 0.7f); lineTo(-r * 0.9f, r * 0.7f); close()
    }
    drawPath(cape, Color(0xFF111111).copy(alpha = alpha))
    drawPath(Path().apply { moveTo(-r * 1.05f, r * 0.15f); lineTo(-r * 0.65f, -r * 0.88f); lineTo(-r * 0.25f, -r * 0.5f); close() }, Color(0xFF9E1B23).copy(alpha = alpha))
    drawPath(Path().apply { moveTo(r * 1.05f, r * 0.15f); lineTo(r * 0.65f, -r * 0.88f); lineTo(r * 0.25f, -r * 0.5f); close() }, Color(0xFF9E1B23).copy(alpha = alpha))
}

private fun DrawScope.drawVampireHair(r: Float, alpha: Float) {
    drawPath(Path().apply {
        moveTo(-r * 0.78f, -r * 0.66f); quadraticTo(-r * 0.4f, -r * 1.02f, 0f, -r * 0.94f)
        quadraticTo(r * 0.4f, -r * 1.02f, r * 0.78f, -r * 0.66f); quadraticTo(r * 0.35f, -r * 0.72f, 0f, -r * 0.55f)
        quadraticTo(-r * 0.35f, -r * 0.72f, -r * 0.78f, -r * 0.66f); close()
    }, Color(0xFF141414).copy(alpha = alpha))
}

private fun DrawScope.drawGhostSheet(r: Float, alpha: Float, glow: Float) {
    val sheet = Path().apply {
        moveTo(-r * 1.0f, r * 1.0f)
        lineTo(-r * 1.0f, -r * 0.1f)
        cubicTo(-r * 1.0f, -r * 1.4f, r * 1.0f, -r * 1.4f, r * 1.0f, -r * 0.1f)
        lineTo(r * 1.0f, r * 1.0f)
        // Wavy hem.
        for (i in 0 until 4) {
            val x0 = r * 1.0f - i * r * 0.5f
            quadraticTo(x0 - r * 0.125f, r * 0.78f, x0 - r * 0.25f, r * 1.0f)
            quadraticTo(x0 - r * 0.375f, r * 1.2f, x0 - r * 0.5f, r * 1.0f)
        }
        close()
    }
    drawPath(sheet, Color(0xFFF2F2F2).copy(alpha = alpha * 0.92f))
    drawPath(sheet, Color(0xFFBDBDBD).copy(alpha = alpha * 0.6f), style = Stroke(r * 0.04f))
    // A pumpkin peeking from under the sheet.
    drawOval(PumpkinMid.copy(alpha = alpha), topLeft = Offset(-r * 0.35f, r * 0.82f), size = Size(r * 0.7f, r * 0.32f))
    listOf(-1f, 1f).forEach { side -> drawOval(CarveDark.copy(alpha = alpha), topLeft = Offset(side * r * 0.32f - r * 0.12f, -r * 0.42f), size = Size(r * 0.24f, r * 0.32f)) }
    drawOval(CarveDark.copy(alpha = alpha), topLeft = Offset(-r * 0.12f, -r * 0.02f), size = Size(r * 0.24f, r * 0.26f))
}

/** Bat silhouette; [flap] -1..1 lifts and lowers the wings. */
fun DrawScope.drawBat(center: Offset, span: Float, flap: Float, alpha: Float = 1f) {
    val color = Color(0xFF4B3A5C).copy(alpha = alpha)
    val lift = flap * span * 0.25f
    translate(center.x, center.y) {
        listOf(-1f, 1f).forEach { side ->
            val wing = Path().apply {
                moveTo(side * span * 0.08f, 0f)
                quadraticTo(side * span * 0.3f, -span * 0.12f - lift, side * span * 0.5f, -span * 0.02f - lift)
                quadraticTo(side * span * 0.42f, span * 0.02f, side * span * 0.38f, span * 0.12f - lift * 0.3f)
                quadraticTo(side * span * 0.3f, span * 0.04f, side * span * 0.24f, span * 0.12f - lift * 0.2f)
                quadraticTo(side * span * 0.17f, span * 0.04f, side * span * 0.08f, span * 0.08f)
                close()
            }
            drawPath(wing, color)
            drawPath(wing, Color(0xFF8C73A6).copy(alpha = alpha * 0.6f), style = Stroke(1.2f))
        }
        drawOval(color, topLeft = Offset(-span * 0.09f, -span * 0.06f), size = Size(span * 0.18f, span * 0.2f))
        drawPath(Path().apply { moveTo(-span * 0.07f, -span * 0.04f); lineTo(-span * 0.04f, -span * 0.12f); lineTo(-span * 0.01f, -span * 0.04f); close() }, color)
        drawPath(Path().apply { moveTo(span * 0.07f, -span * 0.04f); lineTo(span * 0.04f, -span * 0.12f); lineTo(span * 0.01f, -span * 0.04f); close() }, color)
        drawCircle(Color(0xFFFFB347).copy(alpha = alpha), span * 0.018f, Offset(-span * 0.035f, -span * 0.005f))
        drawCircle(Color(0xFFFFB347).copy(alpha = alpha), span * 0.018f, Offset(span * 0.035f, -span * 0.005f))
    }
}

/** Corner cobweb with sagging silk between threads and a spider hanging at [spiderDrop] (0..1). */
fun DrawScope.drawCobweb(corner: Offset, radius: Float, flip: Boolean, spiderDrop: Float, alpha: Float = 1f) {
    val silk = Color.White.copy(alpha = 0.2f * alpha)
    val dir = if (flip) -1f else 1f
    val angles = (0..5).map { it * (PI / 2) / 5 }
    fun point(a: Double, d: Float) = corner + Offset(dir * cos(a).toFloat() * d, sin(a).toFloat() * d)
    angles.forEach { a -> drawLine(silk, corner, point(a, radius), 1.4f) }
    listOf(0.28f, 0.5f, 0.72f, 0.93f).forEach { ring ->
        val path = Path()
        angles.forEachIndexed { index, a ->
            val p = point(a, radius * ring)
            if (index == 0) path.moveTo(p.x, p.y) else {
                val prev = angles[index - 1]
                val mid = point((prev + a) / 2, radius * ring * 0.86f) // silk sags toward the corner
                path.quadraticTo(mid.x, mid.y, p.x, p.y)
            }
        }
        drawPath(path, silk, style = Stroke(1.1f))
    }
    // Spider on a thread.
    val anchor = point(PI / 4, radius * 0.55f)
    val spider = anchor + Offset(0f, radius * (0.25f + 0.45f * spiderDrop))
    drawLine(Color.White.copy(alpha = 0.28f * alpha), anchor, spider, 1f)
    val s = radius * 0.06f
    val body = Color(0xFF151015).copy(alpha = alpha)
    repeat(4) { i ->
        val spread = (i - 1.5f) * 0.45f
        listOf(-1f, 1f).forEach { side ->
            drawLine(body, spider, spider + Offset(side * s * 2.2f, s * spread * 1.6f - s * 0.6f), s * 0.28f, StrokeCap.Round)
        }
    }
    drawCircle(body, s, spider)
    drawCircle(body, s * 0.65f, spider + Offset(0f, -s * 1.2f))
    drawCircle(Color(0xFFFF5A3C).copy(alpha = alpha), s * 0.18f, spider + Offset(-s * 0.22f, -s * 1.25f))
    drawCircle(Color(0xFFFF5A3C).copy(alpha = alpha), s * 0.18f, spider + Offset(s * 0.22f, -s * 1.25f))
}
