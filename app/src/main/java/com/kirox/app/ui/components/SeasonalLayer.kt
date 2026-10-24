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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.kirox.app.ui.theme.LocalMotionFactor
import kotlin.math.abs
import kotlin.math.sin

/** Whether decorative animations are on (KiroX switch in «Анимации»). */
val LocalSeasonalEffects = staticCompositionLocalOf { true }

/** Which decorative animation is chosen. */
val LocalEffectScene = staticCompositionLocalOf { com.kirox.app.settings.EffectScene.Main }

/** [Header] is a light strip behind screen titles; [Showcase] is the full scene for previews and splash. */
enum class SeasonalMode { Header, Showcase, Splash, Banner }

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

/** Launch screen: a small parade under the logo, leaving the centre and the bottom copy clear. */
private val SplashLanterns = listOf(
    Lantern(0.22f, 0.66f, 0.85f, PumpkinFace.Happy, PumpkinCostume.WitchHat, 0.2f),
    Lantern(0.50f, 0.68f, 1.0f, PumpkinFace.Classic, PumpkinCostume.None, 0.55f),
    Lantern(0.78f, 0.66f, 0.85f, PumpkinFace.Cute, PumpkinCostume.Vampire, 0.85f),
    Lantern(0.30f, 0.31f, 0.5f, PumpkinFace.Sleepy, PumpkinCostume.None, 0.4f),
    Lantern(0.72f, 0.33f, 0.5f, PumpkinFace.Spooky, PumpkinCostume.TopHat, 0.7f)
)

/** Customization banner: lanterns at the sides, the centre stays free for the logo. */
private val BannerLanterns = listOf(
    Lantern(0.11f, 0.66f, 1f, PumpkinFace.Classic, PumpkinCostume.WitchHat, 0.15f),
    Lantern(0.89f, 0.66f, 1f, PumpkinFace.Cute, PumpkinCostume.Vampire, 0.65f),
    Lantern(0.25f, 0.30f, 0.6f, PumpkinFace.Happy, PumpkinCostume.None, 0.4f),
    Lantern(0.75f, 0.28f, 0.6f, PumpkinFace.Spooky, PumpkinCostume.TopHat, 0.85f)
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
    /** Draw this scene instead of the chosen one (previews in the list). */
    scene: com.kirox.app.settings.EffectScene? = null,
    /** Free horizontal gap (px) for header pumpkins, so they never cover the title or buttons. */
    freeZone: ClosedFloatingPointRange<Float>? = null,
    /** The same gap as fractions of the width, for headers with fixed layouts. */
    freeZoneFraction: ClosedFloatingPointRange<Float>? = null
) {
    if (!force && !LocalSeasonalEffects.current) return
    val context = LocalContext.current
    val systemAnimations = remember { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f }
    val speed = LocalMotionFactor.current
    val animate = systemAnimations && speed > 0f
    val light = com.kirox.app.ui.theme.LocalLightEffects.current
    val picked = scene ?: LocalEffectScene.current
    val chosen = remember(picked) { picked.resolved() }
    val clock = LocalSceneClock.current
    SubscribeToSceneClock(clock, animate, large = mode != SeasonalMode.Header)
    // Own graphics layer: a scene redraw re-records only this layer, not the screen behind it.
    Canvas(modifier.graphicsLayer()) {
        // Read the shared clock here, in the draw phase: the scene redraws without recomposing.
        val time = if (animate) clock.time else 2.3f
        when (chosen) {
            com.kirox.app.settings.EffectScene.Halloween -> halloween(time, mode, freeZone ?: freeZoneFraction?.let { (size.width * it.start)..(size.width * it.endInclusive) }, light)
            com.kirox.app.settings.EffectScene.Main, com.kirox.app.settings.EffectScene.Auto -> sparkles(time, mode, light)
            com.kirox.app.settings.EffectScene.NewYear -> newYear(time, mode, light, freeZone ?: freeZoneFraction?.let { (size.width * it.start)..(size.width * it.endInclusive) })
            com.kirox.app.settings.EffectScene.Winter -> winter(time, mode, light, freeZone ?: freeZoneFraction?.let { (size.width * it.start)..(size.width * it.endInclusive) })
        }
    }
}

private fun DrawScope.halloween(t: Float, mode: SeasonalMode, freeZone: ClosedFloatingPointRange<Float>?, light: Boolean) {
    val showcase = mode != SeasonalMode.Header
    val webRadius = when (mode) {
        SeasonalMode.Showcase -> size.height * 0.55f
        SeasonalMode.Splash -> size.width * 0.42f
        SeasonalMode.Banner -> size.height * 0.5f
        SeasonalMode.Header -> minOf(size.height * 1.1f, 64.dp.toPx())
    }
    val drop = (sin(t * 0.7f) * 0.5f + 0.5f)
    drawCobwebCached(Offset.Zero, webRadius, flip = false)
    drawSpider(Offset.Zero, webRadius, flip = false, spiderDrop = drop)
    drawCobwebCached(Offset(size.width, 0f), webRadius * 0.85f, flip = true)
    drawSpider(Offset(size.width, 0f), webRadius * 0.85f, flip = true, spiderDrop = 1f - drop)

    // Bats cross the scene on slow, slightly wavy paths.
    val bats = when { light && !showcase -> 0; light -> 1; showcase -> 3; else -> 2 }
    repeat(bats) { i ->
        val period = 11f + i * 4f
        val p = ((t + i * 3.7f) % period) / period
        val x = -40f + (size.width + 80f) * p
        val y = size.height * (when (mode) { SeasonalMode.Splash -> 0.12f + 0.1f * i; SeasonalMode.Banner -> 0.1f + 0.05f * i; else -> 0.18f + 0.18f * i }) + sin(t * 1.6f + i) * size.height * 0.03f
        val span = (if (showcase) 34.dp else 20.dp).toPx() * (1f - i * 0.15f)
        drawBatCached(Offset(x, y), span, flap = sin(t * 9f + i * 2f))
    }

    val base = when (mode) {
        SeasonalMode.Showcase -> 26.dp
        SeasonalMode.Splash -> 34.dp
        SeasonalMode.Banner -> 20.dp
        SeasonalMode.Header -> 10.dp
    }.toPx()
    val sceneLanterns = when (mode) { SeasonalMode.Splash -> SplashLanterns; SeasonalMode.Banner -> BannerLanterns; else -> ShowcaseLanterns }
    val placed: List<Pair<Lantern, Float>> = if (showcase) sceneLanterns.map { it to size.width * it.x } else {
        // Fit as many header pumpkins as the free gap allows (none if it is too narrow).
        val zone = freeZone ?: return
        val slot = base * 3.4f
        val count = ((zone.endInclusive - zone.start) / slot).toInt().coerceIn(0, if (light) 1 else HeaderLanterns.size)
        val start = zone.start + ((zone.endInclusive - zone.start) - count * slot) / 2 + slot / 2
        HeaderLanterns.take(count).mapIndexed { i, l -> l to start + i * slot }
    }
    val shown = if (light && showcase) placed.filter { it.first.size >= 0.8f } else placed
    shown.forEach { (l, cx) ->
        val bob = sin(t * 1.3f + l.seed * 6f) * base * 0.12f
        val glow = (0.75f + 0.25f * sin(t * 7f + l.seed * 11f) * sin(t * 2.3f + l.seed * 5f)).coerceIn(0f, 1f)
        // Blink for ~0.2 s every few seconds, each pumpkin at its own moment.
        val blinkPhase = (t + l.seed * 5f) % 4.2f
        val blink = if (blinkPhase < 0.2f) 1f - abs(blinkPhase - 0.1f) * 10f else 0f
        drawPumpkinCached(
            center = Offset(cx, size.height * l.y + bob),
            r = base * l.size,
            face = l.face, costume = l.costume,
            glow = glow, blink = blink,
            rotationDeg = sin(t * 0.9f + l.seed * 4f) * 6f,
            flat = light
        )
    }
    // A few embers drifting up.
    repeat(if (light) 0 else if (showcase) 10 else 5) { i ->
        val seed = (i * 7919 % 1000) / 1000f
        val y = size.height * (1f - ((t * 0.08f + seed) % 1f))
        val x = size.width * ((i * 37 % 101) / 100f) + sin(t + i) * 6f
        drawCircle(Color(0xFFFFB347).copy(alpha = 0.35f + 0.25f * seed), 1.6f + seed * 1.6f, Offset(x, y))
    }
}

/**
 * Winter: frost in the top corners, snowflakes of four shapes in three depth layers
 * (far ones small, faint and slow), and snowdrifts with snowy firs along the bottom of large scenes.
 */
private fun DrawScope.winter(t: Float, mode: SeasonalMode, light: Boolean, freeZone: ClosedFloatingPointRange<Float>?) {
    val header = mode == SeasonalMode.Header
    val frost = if (header) minOf(size.height * 1.1f, 56.dp.toPx()) else minOf(size.width, size.height) * 0.32f
    drawFrostCached(Offset.Zero, frost, flip = false)
    drawFrostCached(Offset(size.width, 0f), frost * 0.85f, flip = true)

    val driftHeight = when (mode) {
        SeasonalMode.Header -> 0f
        SeasonalMode.Splash -> size.height * 0.2f
        else -> size.height * 0.34f
    }
    val count = when (mode) { SeasonalMode.Header -> 10; SeasonalMode.Splash -> 42; else -> 26 } / (if (light) 2 else 1)
    val baseR = (if (header) 3.dp else 5.dp).toPx()
    repeat(count) { i ->
        val seed = (i * 7919 % 1000) / 1000f
        val layer = i % 3 // 0 far, 1 middle, 2 near
        val r = baseR * (0.7f + layer * 0.55f) * (0.85f + seed * 0.3f)
        val speed = 0.05f + layer * 0.035f + seed * 0.02f
        val fall = (t * speed + seed) % 1f
        val y = -r + fall * (size.height + 2 * r)
        // In headers flakes fall only through the free gap, never over the title or buttons.
        val left = if (header && freeZone != null) freeZone.start else 0f
        val span = if (header && freeZone != null) (freeZone.endInclusive - freeZone.start).coerceAtLeast(0f) else size.width
        if (span <= 0f) return@repeat
        val x = left + span * ((i * 37 % 101) / 100f) + sin(t * 0.8f + seed * 6.283f) * (if (header) 3f else 6f + layer * 5f)
        val shape = FlakeShape.entries[i % FlakeShape.entries.size]
        val rotation = if (light) 0f else (t * (20f + seed * 40f) * (if (i % 2 == 0) 1f else -1f)) % 360f
        drawFlakeCached(Offset(x, y), r, shape, rotation, alpha = 0.45f + layer * 0.22f)
    }
    if (driftHeight > 0f) drawDriftsCached(size.width, driftHeight, withTrees = !light)
}

/**
 * New Year: a twinkling garland along the top, a decorated fir with gifts on a snowdrift,
 * firework bursts in the sky and a light snowfall. Headers keep only the garland and a few flakes.
 */
private fun DrawScope.newYear(t: Float, mode: SeasonalMode, light: Boolean, freeZone: ClosedFloatingPointRange<Float>?) {
    val header = mode == SeasonalMode.Header
    val x0 = if (header && freeZone != null) freeZone.start else 0f
    val x1 = if (header && freeZone != null) freeZone.endInclusive else size.width
    val bulbR = (if (header) 2.5.dp else if (mode == SeasonalMode.Splash) 4.5.dp else 3.5.dp).toPx()
    if (!header && !light) drawFireworks(t, size.height * 0.5f, minOf(size.width, size.height) * (if (mode == SeasonalMode.Splash) 0.16f else 0.2f))

    val count = when (mode) { SeasonalMode.Header -> 6; SeasonalMode.Splash -> 22; else -> 14 } / (if (light) 2 else 1)
    val baseR = (if (header) 2.5.dp else 3.5.dp).toPx()
    repeat(count) { i ->
        val seed = (i * 7919 % 1000) / 1000f
        val r = baseR * (0.7f + (i % 3) * 0.4f)
        val fall = (t * (0.05f + seed * 0.04f) + seed) % 1f
        val span = (x1 - x0).coerceAtLeast(0f)
        if (span <= 0f) return@repeat
        val x = x0 + span * ((i * 37 % 101) / 100f) + sin(t * 0.8f + seed * 6.283f) * 4f
        drawFlakeCached(Offset(x, -r + fall * (size.height + 2 * r)), r, if (i % 2 == 0) FlakeShape.Simple else FlakeShape.Star, if (light) 0f else t * 30f % 360f, alpha = 0.5f)
    }
    if (!header) {
        val drift = size.height * (if (mode == SeasonalMode.Splash) 0.14f else 0.22f)
        drawDriftsCached(size.width, drift, withTrees = false)
        val treeH = if (mode == SeasonalMode.Splash) size.height * 0.2f else size.height * 0.72f
        val treeX = if (mode == SeasonalMode.Splash) size.width * 0.78f else size.width * 0.8f
        drawNewYearTree(Offset(treeX, size.height - drift * 0.35f), treeH, t, light)
    }
    // On the splash the garland hangs below the status bar.
    drawGarland(x0, x1, when (mode) { SeasonalMode.Header -> 2.dp.toPx(); SeasonalMode.Splash -> 30.dp.toPx(); else -> -bulbR }, if (header) size.height * 0.16f else bulbR * 2.4f, t, bulbR, light)
}

/** The main kiroX animation: slow drifting, twinkling white particles (as on the splash screen). */
private fun DrawScope.sparkles(t: Float, mode: SeasonalMode, light: Boolean) {
    val count = when (mode) { SeasonalMode.Header -> 14; SeasonalMode.Showcase, SeasonalMode.Banner -> 34; SeasonalMode.Splash -> 46 } / (if (light) 2 else 1)
    repeat(count) { i ->
        val seed = (i * 7919 % 1000) / 1000f
        val speed = 0.45f + (i * 37 % 100) / 80f
        val x = ((((i * 37 % 101) / 100f) + t * speed * 0.012f) % 1f) * size.width
        val y = size.height * ((i * 53 % 97) / 96f) + sin((t * speed + seed) * 6.283f) * 8f
        val twinkle = (0.72f + 0.28f * sin((t * speed + seed) * 9.42f)).coerceIn(0.3f, 1f)
        val radius = (0.6f + seed * 1.2f) * density
        drawCircle(Color.White.copy(alpha = (0.2f + seed * 0.6f) * twinkle), radius, Offset(x, y))
        if (!light && i % 9 == 0) drawCircle(Color.White.copy(alpha = 0.08f * twinkle), radius * 4f, Offset(x, y))
    }
}
