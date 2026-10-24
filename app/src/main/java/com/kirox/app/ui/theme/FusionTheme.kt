package com.kirox.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

enum class FusionPalette { AmoledBlack, GraphiteDark }

@Immutable
data class FusionColors(
    val background: Color,
    val surface1: Color,
    val surface2: Color,
    val surface3: Color,
    val outline: Color,
    val outlineStrong: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val accent: Color,
    val onAccent: Color,
    val danger: Color,
    val avatar: Color,
    /** Rounded square behind settings icons. */
    val iconContainer: Color
) {
    companion object {
        val Amoled = FusionColors(
            background = Color(0xFF000000), surface1 = Color(0xFF101010), surface2 = Color(0xFF191919), surface3 = Color(0xFF292929),
            outline = Color(0xFF242424), outlineStrong = Color(0xFF454545), textPrimary = Color(0xFFFFFFFF), textSecondary = Color(0xFFAAAAAA),
            textTertiary = Color(0xFF888888), accent = Color(0xFFFFFFFF), onAccent = Color(0xFF000000),
            danger = Color(0xFFFF6961), avatar = Color(0xFF3A4652), iconContainer = Color(0xFF181818)
        )
        val Graphite = FusionColors(
            background = Color(0xFF080808), surface1 = Color(0xFF181818), surface2 = Color(0xFF242424), surface3 = Color(0xFF343434),
            outline = Color(0xFF292929), outlineStrong = Color(0xFF454545), textPrimary = Color(0xFFFFFFFF), textSecondary = Color(0xFFAAAAAA),
            textTertiary = Color(0xFF858585), accent = Color(0xFFFFFFFF), onAccent = Color(0xFF000000),
            danger = Color(0xFFFF6961), avatar = Color(0xFF465562), iconContainer = Color(0xFF202020)
        )
    }
}

object FusionSpacing { val xs = 4.dp; val s = 8.dp; val m = 12.dp; val l = 16.dp; val xl = 20.dp; val xxl = 24.dp; val xxxl = 32.dp }
object FusionRadius { val sm = 8.dp; val md = 12.dp; val lg = 16.dp; val xl = 24.dp }
object FusionSize { val touch = 48.dp; val icon = 24.dp; val iconSmall = 20.dp; val button = 52.dp; val listRow = 72.dp; val hairline = 1.dp }
object FusionMotion { const val FAST = 120; const val NORMAL = 200; const val SLOW = 280 }

private val LocalFusionColors = staticCompositionLocalOf { FusionColors.Amoled }

/** User animation level: 1 = normal, 0.5 = reduced, 0 = off. Multiplies every Fusion duration. */
val LocalMotionFactor = staticCompositionLocalOf { 1f }

/** Corner radius of chat bubbles in dp (KiroX «Форма пузырей»). */
val LocalBubbleRadius = staticCompositionLocalOf { 16f }

/** A Fusion duration scaled by the user's animation level. */
@Composable
@ReadOnlyComposable
fun motionMs(base: Int): Int = (base * LocalMotionFactor.current).toInt()

object Fusion {
    val colors: FusionColors @Composable @ReadOnlyComposable get() = LocalFusionColors.current
}

/** Provides Fusion tokens and a monochrome Material scheme so stock M3 widgets stop using the default purple palette. */
@Composable
fun FusionTheme(palette: FusionPalette = FusionPalette.AmoledBlack, content: @Composable () -> Unit) {
    val colors = if (palette == FusionPalette.GraphiteDark) FusionColors.Graphite else FusionColors.Amoled
    val scheme = darkColorScheme(
        primary = colors.accent, onPrimary = colors.onAccent,
        secondary = colors.textSecondary, onSecondary = colors.onAccent,
        background = colors.background, onBackground = colors.textPrimary,
        surface = colors.surface1, onSurface = colors.textPrimary,
        surfaceVariant = colors.surface2, onSurfaceVariant = colors.textSecondary,
        surfaceContainer = colors.surface1, surfaceContainerHigh = colors.surface2,
        outline = colors.outline, outlineVariant = colors.outline,
        error = colors.danger, onError = colors.onAccent
    )
    CompositionLocalProvider(LocalFusionColors provides colors) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
