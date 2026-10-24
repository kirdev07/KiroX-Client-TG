package com.kirox.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

/**
 * One animation clock for every decorative scene on screen.
 * It ticks on a timer only while scenes are visible: 60 fps while the splash scene is shown,
 * 30 fps for previews, banners and header strips, 30 fps in light mode.
 * [time] is read only in the draw phase, so scenes redraw without recomposing.
 */
@Stable
class SceneClock {
    private val seconds = mutableFloatStateOf(2.3f)
    private val headerScenes = mutableIntStateOf(0)
    private val largeScenes = mutableIntStateOf(0)

    /** Scene time in seconds; read it inside draw lambdas only. */
    val time: Float get() = seconds.floatValue

    internal fun subscribe(large: Boolean) { if (large) largeScenes.intValue++ else headerScenes.intValue++ }
    internal fun unsubscribe(large: Boolean) { if (large) largeScenes.intValue-- else headerScenes.intValue-- }

    /** Runs the tick loop while scenes are subscribed. [speed] 0 freezes it. */
    @Composable
    fun Drive(speed: Float, light: Boolean) {
        LaunchedEffect(this, speed, light) {
            if (speed <= 0f) return@LaunchedEffect
            snapshotFlow {
                when {
                    largeScenes.intValue + headerScenes.intValue == 0 -> 0
                    light || largeScenes.intValue == 0 -> 30
                    else -> 60
                }
            }.collectLatest { fps ->
                if (fps == 0) return@collectLatest
                // Tick on a timer, not on every display frame: a 120 Hz screen would otherwise
                // run a full UI frame 120 times a second.
                val tickMs = 1000L / fps
                var last = System.nanoTime()
                while (true) {
                    delay(tickMs)
                    val now = System.nanoTime()
                    seconds.floatValue += (now - last) / 1_000_000_000f * speed
                    last = now
                }
            }
        }
    }
}

val LocalSceneClock = staticCompositionLocalOf { SceneClock() }

/** Keeps the shared clock running while the calling scene is on screen. */
@Composable
fun SubscribeToSceneClock(clock: SceneClock, active: Boolean, large: Boolean = false) {
    DisposableEffect(clock, active, large) {
        if (active) clock.subscribe(large)
        onDispose { if (active) clock.unsubscribe(large) }
    }
}
