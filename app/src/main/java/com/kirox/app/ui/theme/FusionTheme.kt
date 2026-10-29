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

/**
 * KiroX themes. All are dark by design: there is deliberately no light theme. [Expressive] and
 * [Glass] are the Fusion UI v2 styles; the four monochrome palettes stay for those who chose them.
 * Stored by enum name, so new values never break a saved choice.
 */
enum class FusionPalette(val title: String, val subtitle: String) {
    Expressive("Fusion Expressive", "Фиолетовый Material 3 Expressive"),
    Glass("Fusion Glass", "Сине-стеклянные панели"),
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
    val iconContainer: Color,
    val incomingBubble: Color = Color(0xFF1D1D1F),
    val outgoingBubble: Color = Color(0xFF37383B),
    val onOutgoing: Color = Color(0xFFFFFFFF),
    /** Message composer and other bottom bars. */
    val inputBar: Color = Color(0xFF171719),
    /** Selected pill: navigation indicator, active chips and segments. */
    val selectedPill: Color = surface3,
    val onSelectedPill: Color = textPrimary,
    /** Screen backdrop: vertical gradient behind the main screens; null paints [background]. */
    val backdrop: List<Color>? = null,
    /** Soft colour glows on the backdrop (Expressive bloom, Glass sky). */
    val glow: List<Color> = emptyList(),
    /** Glass style: bars and cards are translucent over the backdrop, with a light stroke. */
    val glass: Boolean = false,
    val chatWallpaper: List<Color>? = null
) {
    /** Background of a screen inside the main shell: see-through over the v2 backdrop, solid otherwise. */
    val screen: Color get() = if (backdrop != null) Color.Transparent else background

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

        /** Fusion Expressive: purple Material 3 Expressive on an AMOLED violet base (theme-tokens.json). */
        val Expressive = FusionColors(
            background = Color(0xFF080710), surface1 = Color(0xFF141121), surface2 = Color(0xFF211B31), surface3 = Color(0xFF2D2641),
            outline = Color(0xFF241D34), outlineStrong = Color(0xFF4A3A6D), textPrimary = Color(0xFFF8F5FF), textSecondary = Color(0xFFB5AACD),
            textTertiary = Color(0xFF8C82A6), accent = Color(0xFFA276FF), onAccent = Color(0xFFFFFFFF),
            danger = Color(0xFFFF5D75), avatar = Color(0xFF3A2F55), iconContainer = Color(0xFF241D38),
            incomingBubble = Color(0xFF211B32), outgoingBubble = Color(0xFF7354D8), inputBar = Color(0xFF141121),
            selectedPill = Color(0xFF6749CE), onSelectedPill = Color(0xFFFFFFFF),
            backdrop = listOf(Color(0xFF10091D), Color(0xFF160C2B), Color(0xFF080710)),
            glow = listOf(Color(0x557354D8), Color(0x333B1F7A)),
            chatWallpaper = listOf(Color(0xFF140B26), Color(0xFF1E1138), Color(0xFF0A0814))
        )

        /** Fusion Glass: deep navy with translucent blue panels and a soft sky glow. */
        val Glass = FusionColors(
            background = Color(0xFF071424), surface1 = Color(0xFF122640), surface2 = Color(0xFF1B3554), surface3 = Color(0xFF284A72),
            outline = Color(0x4D6382A9), outlineStrong = Color(0xFF6382A9), textPrimary = Color(0xFFF4F8FF), textSecondary = Color(0xFFAEC1D8),
            textTertiary = Color(0xFF8299B5), accent = Color(0xFF6B91FF), onAccent = Color(0xFFFFFFFF),
            danger = Color(0xFFFF5D75), avatar = Color(0xFF2A4B85), iconContainer = Color(0x66223B5C),
            incomingBubble = Color(0xCC223A5E), outgoingBubble = Color(0xFF426CF2), inputBar = Color(0xB3193858),
            selectedPill = Color(0xFF396FDB), onSelectedPill = Color(0xFFFFFFFF),
            backdrop = listOf(Color(0xFF091B34), Color(0xFF12305A), Color(0xFF071424)),
            glow = listOf(Color(0x556B91FF), Color(0x449A83FF)),
            glass = true,
            chatWallpaper = listOf(Color(0xFF0B2242), Color(0xFF173863), Color(0xFF081628))
        )

        fun of(palette: FusionPalette) = when (palette) {
            FusionPalette.Expressive -> Expressive
            FusionPalette.Glass -> Glass
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

private val LocalFusionColors = staticCompositionLocalOf { FusionColors.Expressive }

/** Glass blur of backdrop glows (Android 12+); off draws the same soft glows without blur. */
val LocalGlassBlur = staticCompositionLocalOf { true }

/** App-wide switches the owner turns on and off between versions. */
object KiroXFeatures {
    /**
     * Decorative and UI animations. Off for now at the owner's request: motion factor 0, no seasonal
     * particles, no list/swipe/button animations. One line turns them back on.
     */
    const val ANIMATIONS = false
}

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
    primaryContainer = if (colors.backdrop != null) colors.selectedPill else colors.surface3, onPrimaryContainer = if (colors.backdrop != null) colors.onSelectedPill else colors.textPrimary,
    inversePrimary = colors.onAccent,
    secondary = colors.textSecondary, onSecondary = colors.onAccent,
    // Navigation pill, selected chips, tonal buttons.
    secondaryContainer = colors.selectedPill, onSecondaryContainer = colors.onSelectedPill,
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
).let { scheme ->
    // Glass dialogs, menus and sheets stay opaque: text over a translucent dialog is hard to read.
    if (colors.glass) scheme.copy(surfaceContainerHigh = Color(0xFF1B3554), surfaceContainer = Color(0xFF122640)) else scheme
}

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
