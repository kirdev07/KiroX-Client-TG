package com.kirox.app.ui.components

import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.kirox.app.ui.theme.LocalMotionFactor
import java.util.Calendar
import kotlin.math.abs
import kotlin.math.sin

/** Whether screen headers draw seasonal particles (KiroX setting «Сезонные эффекты»). */
val LocalSeasonalEffects = staticCompositionLocalOf { true }

enum class Season { Halloween, Winter, Plain }

/** [Header] is a light strip behind screen titles; [Showcase] is the full scene for previews and splash. */
enum class SeasonalMode { Header, Showcase }

fun currentSeason(calendar: Calendar = Calendar.getInstance()): Season {
    val month = calendar.get(Calendar.MONTH)
    val day = calendar.get(Calendar.DAY_OF_MONTH)
    return when {
        (month == Calendar.OCTOBER && day >= 10) || (month == Calendar.NOVEMBER && day <= 3) -> Season.Halloween
        month == Calendar.DECEMBER || month == Calendar.JANUARY || month == Calendar.FEBRUARY -> Season.Winter
        else -> Season.Plain
    }
}

private data class Lantern(val x: Float, val y: Float, val size: Float, val face: PumpkinFace, val costume: PumpkinCostume, val seed: Float)

/** Header pumpkins, laid out left to right inside the free gap between title and actions. */
private val HeaderLanterns = listOf(
    Lantern(0f, 0.62f, 1f, PumpkinFace.Classic, PumpkinCostume.WitchHat, 0.1f),
    Lantern(0f, 0.55f, 0.85f, PumpkinFace.Cute, PumpkinCostume.None, 0.6f),
    Lantern(0f, 0.62f, 0.95f, PumpkinFace.Spooky, PumpkinCostume.Vampire, 0.35f)
)

private val ShowcaseLanterns = listOf(
    Lantern(0.14f, 0.66f, 0.85f, PumpkinFace.Classic, PumpkinCostume.WitchHat, 0.1f),
    Lantern(0.32f, 0.70f, 0.8f, PumpkinFace.Happy, PumpkinCostume.TopHat, 0.45f),
    Lantern(0.50f, 0.64f, 0.95f, PumpkinFace.Spooky, PumpkinCostume.Vampire, 0.8f),
    Lantern(0.68f, 0.70f, 0.8f, PumpkinFace.Cute, PumpkinCostume.Bow, 0.25f),
    Lantern(0.86f, 0.62f, 0.85f, PumpkinFace.Sleepy, PumpkinCostume.Ghost, 0.6f),
    Lantern(0.40f, 0.30f, 0.55f, PumpkinFace.Classic, PumpkinCostume.None, 0.9f),
    Lantern(0.62f, 0.26f, 0.5f, PumpkinFace.Happy, PumpkinCostume.None, 0.15f)
)

/**
 * Seasonal decoration: around Halloween, jack-o'-lanterns with carved faces (some in costumes),
 * bats and a cobweb with a spider; snow in winter; a few drifting dots otherwise.
 * Static when system or KiroX animations are off; the frame clock pauses in the background.
 */
@Composable
fun SeasonalLayer(
    modifier: Modifier = Modifier,
    mode: SeasonalMode = SeasonalMode.Header,
    force: Boolean = false,
    /** Free horizontal gap (px) for header pumpkins, so they never cover the title or buttons. */
    freeZone: ClosedFloatingPointRange<Float>? = null
) {
    if (!force && !LocalSeasonalEffects.current) return
    val context = LocalContext.current
    val systemAnimations = remember { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f }
    val speed = LocalMotionFactor.current
    val animate = systemAnimations && speed > 0f
    val season = remember { currentSeason() }
    val time by produceState(2.3f, animate, speed) {
        if (!animate) return@produceState
        var last = withFrameNanos { it }
        while (true) {
            withFrameNanos { now ->
                value += (now - last) / 1_000_000_000f * speed
                last = now
            }
        }
    }
    Canvas(modifier) {
        when (season) {
            Season.Halloween -> halloween(time, mode, freeZone)
            Season.Winter -> snow(time, mode)
            Season.Plain -> dots(time)
        }
    }
}

private fun DrawScope.halloween(t: Float, mode: SeasonalMode, freeZone: ClosedFloatingPointRange<Float>?) {
    val showcase = mode == SeasonalMode.Showcase
    val webRadius = if (showcase) size.height * 0.55f else minOf(size.height * 1.1f, 64.dp.toPx())
    val drop = (sin(t * 0.7f) * 0.5f + 0.5f)
    drawCobweb(Offset.Zero, webRadius, flip = false, spiderDrop = drop)
    drawCobweb(Offset(size.width, 0f), webRadius * 0.85f, flip = true, spiderDrop = 1f - drop)

    // Bats cross the scene on slow, slightly wavy paths.
    val bats = if (showcase) 3 else 2
    repeat(bats) { i ->
        val period = 11f + i * 4f
        val p = ((t + i * 3.7f) % period) / period
        val x = -40f + (size.width + 80f) * p
        val y = size.height * (0.18f + 0.18f * i) + sin(t * 1.6f + i) * size.height * 0.06f
        val span = (if (showcase) 34.dp else 20.dp).toPx() * (1f - i * 0.15f)
        drawBat(Offset(x, y), span, flap = sin(t * 9f + i * 2f))
    }

    val base = (if (showcase) 26.dp else 10.dp).toPx()
    val placed: List<Pair<Lantern, Float>> = if (showcase) ShowcaseLanterns.map { it to size.width * it.x } else {
        // Fit as many header pumpkins as the free gap allows (none if it is too narrow).
        val zone = freeZone ?: return
        val slot = base * 3.4f
        val count = ((zone.endInclusive - zone.start) / slot).toInt().coerceIn(0, HeaderLanterns.size)
        val start = zone.start + ((zone.endInclusive - zone.start) - count * slot) / 2 + slot / 2
        HeaderLanterns.take(count).mapIndexed { i, l -> l to start + i * slot }
    }
    placed.forEach { (l, cx) ->
        val bob = sin(t * 1.3f + l.seed * 6f) * base * 0.12f
        val glow = (0.75f + 0.25f * sin(t * 7f + l.seed * 11f) * sin(t * 2.3f + l.seed * 5f)).coerceIn(0f, 1f)
        // Blink for ~0.2 s every few seconds, each pumpkin at its own moment.
        val blinkPhase = (t + l.seed * 5f) % 4.2f
        val blink = if (blinkPhase < 0.2f) 1f - abs(blinkPhase - 0.1f) * 10f else 0f
        drawPumpkin(
            center = Offset(cx, size.height * l.y + bob),
            r = base * l.size,
            face = l.face, costume = l.costume,
            glow = glow, blink = blink,
            rotationDeg = sin(t * 0.9f + l.seed * 4f) * 6f
        )
    }
    // A few embers drifting up.
    repeat(if (showcase) 10 else 5) { i ->
        val seed = (i * 7919 % 1000) / 1000f
        val y = size.height * (1f - ((t * 0.08f + seed) % 1f))
        val x = size.width * ((i * 37 % 101) / 100f) + sin(t + i) * 6f
        drawCircle(Color(0xFFFFB347).copy(alpha = 0.35f + 0.25f * seed), 1.6f + seed * 1.6f, Offset(x, y))
    }
}

private fun DrawScope.snow(t: Float, mode: SeasonalMode) {
    repeat(if (mode == SeasonalMode.Showcase) 40 else 16) { i ->
        val seed = (i * 7919 % 1000) / 1000f
        val x = size.width * ((i * 37 % 101) / 100f) + sin(t * 0.8f + i) * 10f
        val y = size.height * ((t * (0.05f + seed * 0.05f) + seed) % 1f)
        drawCircle(Color.White.copy(alpha = 0.35f + seed * 0.4f), 1.6f + seed * 2.6f, Offset(x, y))
    }
}

private fun DrawScope.dots(t: Float) {
    repeat(10) { i ->
        val seed = (i * 7919 % 1000) / 1000f
        val x = size.width * ((i * 37 % 101) / 100f) + sin(t + i) * 6f
        val y = size.height * ((t * 0.04f + seed) % 1f)
        drawCircle(Color.White.copy(alpha = 0.2f + (i % 3) * 0.1f), 1.5f + (i % 2), Offset(x, y))
    }
}
