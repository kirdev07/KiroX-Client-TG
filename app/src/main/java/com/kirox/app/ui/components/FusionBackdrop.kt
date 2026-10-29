package com.kirox.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.kirox.app.ui.theme.Fusion
import com.kirox.app.ui.theme.LocalGlassBlur

/**
 * Background of the main screens: the style's vertical gradient with two soft colour glows
 * (Expressive violet bloom, Glass blue sky). Static: drawn once, nothing animates. On Android 12+
 * with blur on, the glows are blurred further; elsewhere radial gradients alone keep them soft.
 */
@Composable
fun FusionBackdrop(modifier: Modifier = Modifier) {
    val colors = Fusion.colors
    val backdrop = colors.backdrop
    if (backdrop == null) { Box(modifier.fillMaxSize().background(colors.background)); return }
    Box(modifier.fillMaxSize().background(Brush.verticalGradient(backdrop))) {
        if (colors.glow.isEmpty()) return@Box
        val blur = LocalGlassBlur.current && android.os.Build.VERSION.SDK_INT >= 31
        Box(
            Modifier.fillMaxSize()
                .then(if (blur) Modifier.blur(48.dp) else Modifier)
                .drawBehind {
                    val first = colors.glow[0]
                    drawCircle(Brush.radialGradient(listOf(first, Color.Transparent), center = Offset(size.width * 0.85f, size.height * 0.08f), radius = size.width * 0.75f), radius = size.width * 0.75f, center = Offset(size.width * 0.85f, size.height * 0.08f))
                    colors.glow.getOrNull(1)?.let { second ->
                        drawCircle(Brush.radialGradient(listOf(second, Color.Transparent), center = Offset(size.width * 0.1f, size.height * 0.7f), radius = size.width * 0.8f), radius = size.width * 0.8f, center = Offset(size.width * 0.1f, size.height * 0.7f))
                    }
                }
        )
    }
}

/**
 * A card or bar surface in the current style: opaque layered graphite in Expressive and the old
 * palettes; translucent tint with a thin light stroke in Glass (the backdrop shows through).
 */
fun Modifier.fusionPanel(colors: com.kirox.app.ui.theme.FusionColors, shape: Shape = RoundedCornerShape(20.dp), opaque: Color = colors.surface1): Modifier =
    if (colors.glass) this.background(Color(0x8C1B3554), shape).border(1.dp, Color(0x2EFFFFFF), shape)
    else this.background(opaque, shape)

/** Full-screen background in the current style: the v2 gradient, or the plain palette black. */
fun Modifier.fusionBackground(colors: com.kirox.app.ui.theme.FusionColors): Modifier =
    colors.backdrop?.let { this.background(Brush.verticalGradient(it)) } ?: this.background(colors.background)
