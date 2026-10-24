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
    val avatar: Color
) {
    companion object {
        val Amoled = FusionColors(
            background = Color(0xFF000000), surface1 = Color(0xFF0F0F10), surface2 = Color(0xFF17171A), surface3 = Color(0xFF2A2A2E),
            outline = Color(0xFF26262A), outlineStrong = Color(0xFF6E6E73), textPrimary = Color(0xFFF4F4F4), textSecondary = Color(0xFF8E8E93),
            textTertiary = Color(0xFF5C5C60), accent = Color(0xFFFFFFFF), onAccent = Color(0xFF000000),
            danger = Color(0xFFFF6961), avatar = Color(0xFF3E4C58)
        )
        val Graphite = FusionColors(
            background = Color(0xFF121214), surface1 = Color(0xFF1A1A1D), surface2 = Color(0xFF232327), surface3 = Color(0xFF33333A),
            outline = Color(0xFF303035), outlineStrong = Color(0xFF77777D), textPrimary = Color(0xFFF2F2F2), textSecondary = Color(0xFF9A9AA0),
            textTertiary = Color(0xFF6A6A70), accent = Color(0xFFFFFFFF), onAccent = Color(0xFF000000),
            danger = Color(0xFFFF6961), avatar = Color(0xFF465562)
        )
    }
}

object FusionSpacing { val xs = 4.dp; val s = 8.dp; val m = 12.dp; val l = 16.dp; val xl = 20.dp; val xxl = 24.dp; val xxxl = 32.dp }
object FusionRadius { val sm = 8.dp; val md = 12.dp; val lg = 16.dp; val xl = 24.dp }
object FusionSize { val touch = 48.dp; val icon = 24.dp; val iconSmall = 20.dp; val button = 52.dp; val listRow = 72.dp; val hairline = 1.dp }
object FusionMotion { const val FAST = 120; const val NORMAL = 200; const val SLOW = 280 }

private val LocalFusionColors = staticCompositionLocalOf { FusionColors.Amoled }

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
