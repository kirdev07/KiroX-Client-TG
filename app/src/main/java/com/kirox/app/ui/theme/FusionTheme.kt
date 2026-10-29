package com.kirox.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** KiroX themes. All are dark by design: there is deliberately no light theme. */
enum class FusionPalette(val title: String, val subtitle: String) {
    AmoledBlack("AMOLED Black", "Чистый чёрный, бережёт заряд"),
    GraphiteDark("Graphite Dark", "Мягкий тёмно-серый"),
    Midnight("Полночь", "Глубокий холодный синий"),
    Espresso("Эспрессо", "Тёплый тёмный")
}

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
        val Midnight = FusionColors(
            background = Color(0xFF070A10), surface1 = Color(0xFF111723), surface2 = Color(0xFF19202E), surface3 = Color(0xFF263042),
            outline = Color(0xFF1E2636), outlineStrong = Color(0xFF3F4A60), textPrimary = Color(0xFFFFFFFF), textSecondary = Color(0xFFA7B0C0),
            textTertiary = Color(0xFF7B8598), accent = Color(0xFFFFFFFF), onAccent = Color(0xFF000000),
            danger = Color(0xFFFF6961), avatar = Color(0xFF34435C), iconContainer = Color(0xFF151C2A)
        )
        val Espresso = FusionColors(
            background = Color(0xFF0D0A08), surface1 = Color(0xFF1A1512), surface2 = Color(0xFF241D19), surface3 = Color(0xFF342A24),
            outline = Color(0xFF2A221D), outlineStrong = Color(0xFF54463C), textPrimary = Color(0xFFFFFFFF), textSecondary = Color(0xFFB7AAA0),
            textTertiary = Color(0xFF8C7F75), accent = Color(0xFFFFFFFF), onAccent = Color(0xFF000000),
            danger = Color(0xFFFF6961), avatar = Color(0xFF55443A), iconContainer = Color(0xFF1F1915)
        )

        fun of(palette: FusionPalette) = when (palette) {
            FusionPalette.AmoledBlack -> Amoled
            FusionPalette.GraphiteDark -> Graphite
            FusionPalette.Midnight -> Midnight
            FusionPalette.Espresso -> Espresso
        }
    }
}

object FusionSpacing { val xs = 4.dp; val s = 8.dp; val m = 12.dp; val l = 16.dp; val xl = 20.dp; val xxl = 24.dp; val xxxl = 32.dp }
object FusionRadius { val sm = 8.dp; val md = 12.dp; val lg = 16.dp; val xl = 24.dp }
object FusionSize { val touch = 48.dp; val icon = 24.dp; val iconSmall = 20.dp; val button = 52.dp; val listRow = 72.dp; val hairline = 1.dp }
object FusionMotion { const val FAST = 120; const val NORMAL = 200; const val SLOW = 280 }

private val LocalFusionColors = staticCompositionLocalOf { FusionColors.Amoled }

/** User animation level: 1 = normal, 0.5 = reduced, 0 = off. Multiplies every Fusion duration. */
val LocalMotionFactor = staticCompositionLocalOf { 1f }

/** True when decorative effects must be light (fewer particles, flat colours, ~30 fps). */
val LocalLightEffects = staticCompositionLocalOf { false }

/** Corner radius of chat bubbles in dp (KiroX «Форма пузырей»). */
val LocalBubbleRadius = staticCompositionLocalOf { 16f }

/** Chat look from KiroX settings: bubbles, text size, wallpaper, density. */
val LocalChatStyle = staticCompositionLocalOf { com.kirox.app.settings.ChatStyle() }

/** A Fusion duration scaled by the user's animation level. */
@Composable
@ReadOnlyComposable
fun motionMs(base: Int): Int = (base * LocalMotionFactor.current).toInt()

object Fusion {
    val colors: FusionColors @Composable @ReadOnlyComposable get() = LocalFusionColors.current
}

/**
 * Material 3 colour roles from a Fusion palette: stock M3 components (top bars, lists, buttons,
 * fields, navigation with its pill indicator, dialogs, sheets) render in the monochrome KiroX look.
 */
internal fun fusionColorScheme(colors: FusionColors) = darkColorScheme(
    primary = colors.accent, onPrimary = colors.onAccent,
    primaryContainer = colors.surface3, onPrimaryContainer = colors.textPrimary,
    inversePrimary = colors.onAccent,
    secondary = colors.textSecondary, onSecondary = colors.onAccent,
    // Navigation pill, selected chips, tonal buttons.
    secondaryContainer = colors.surface3, onSecondaryContainer = colors.textPrimary,
    tertiary = colors.textSecondary, onTertiary = colors.onAccent,
    tertiaryContainer = colors.surface2, onTertiaryContainer = colors.textPrimary,
    background = colors.background, onBackground = colors.textPrimary,
    surface = colors.background, onSurface = colors.textPrimary,
    surfaceVariant = colors.surface2, onSurfaceVariant = colors.textSecondary,
    surfaceTint = Color.Transparent,
    inverseSurface = colors.textPrimary, inverseOnSurface = colors.background,
    error = colors.danger, onError = colors.onAccent,
    errorContainer = colors.danger.copy(alpha = 0.18f), onErrorContainer = colors.danger,
    outline = colors.outlineStrong, outlineVariant = colors.outline,
    scrim = Color.Black,
    surfaceBright = colors.surface3, surfaceDim = colors.background,
    surfaceContainerLowest = colors.background, surfaceContainerLow = colors.surface1,
    surfaceContainer = colors.surface1, surfaceContainerHigh = colors.surface2, surfaceContainerHighest = colors.surface3
)

/** M3 type scale, sized like the old Fusion screens (bold titles, 16 sp body). */
internal val FusionTypography = Typography().let { base ->
    base.copy(
        headlineMedium = base.headlineMedium.copy(fontSize = 26.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold),
        headlineSmall = base.headlineSmall.copy(fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.Bold),
        titleLarge = base.titleLarge.copy(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold),
        titleMedium = base.titleMedium.copy(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold),
        bodyLarge = base.bodyLarge.copy(fontSize = 16.sp, lineHeight = 22.sp),
        bodyMedium = base.bodyMedium.copy(fontSize = 14.sp, lineHeight = 20.sp),
        labelLarge = base.labelLarge.copy(fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    )
}

/** Fusion radii as M3 shapes: dialogs and sheets use extraLarge, cards large, fields medium. */
internal val FusionShapes = Shapes(
    extraSmall = RoundedCornerShape(FusionRadius.sm),
    small = RoundedCornerShape(FusionRadius.md),
    medium = RoundedCornerShape(FusionRadius.lg),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

/** Provides Fusion tokens and the matching Material 3 theme. */
@Composable
fun FusionTheme(palette: FusionPalette = FusionPalette.AmoledBlack, content: @Composable () -> Unit) {
    val colors = FusionColors.of(palette)
    CompositionLocalProvider(LocalFusionColors provides colors) {
        MaterialTheme(colorScheme = fusionColorScheme(colors), typography = FusionTypography, shapes = FusionShapes, content = content)
    }
}
