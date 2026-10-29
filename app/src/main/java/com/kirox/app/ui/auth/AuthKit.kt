package com.kirox.app.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kirox.app.ui.icons.KiroXIcon
import com.kirox.app.ui.theme.Fusion

/*
 * Building blocks of the approved "Авторизация и аккаунты" board (Fusion v2): near-black AMOLED
 * base with a violet glow, a glowing mark, rounded pills, a gradient button and an on-screen keypad.
 */

/** AMOLED base with a soft accent glow at the top and a fainter one at the bottom. */
@Composable
internal fun Modifier.authBackdrop(): Modifier {
    val accent = Fusion.colors.accent
    return background(Color(0xFF06040B)).drawBehind {
        drawRect(Brush.radialGradient(listOf(accent.copy(alpha = 0.32f), Color.Transparent), Offset(size.width * 0.5f, size.height * 0.08f), size.width * 0.95f))
        drawRect(Brush.radialGradient(listOf(accent.copy(alpha = 0.12f), Color.Transparent), Offset(size.width * 0.15f, size.height * 0.95f), size.width * 0.8f))
    }
}

/** Mark in the centre of a step: icon in the accent colour over a glow. */
@Composable
internal fun AuthMark(icon: KiroXIcon, modifier: Modifier = Modifier) {
    Box(
        modifier.size(72.dp).clip(CircleShape).background(Brush.radialGradient(listOf(Fusion.colors.accent.copy(alpha = 0.42f), Color.Transparent))),
        contentAlignment = Alignment.Center
    ) { KiroXIcon(icon, null, Modifier.size(34.dp), Fusion.colors.accent) }
}

@Composable
internal fun AuthHeading(title: String, subtitle: String) {
    Text(title, color = Fusion.colors.textPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    Spacer(Modifier.height(8.dp))
    Text(subtitle, color = Fusion.colors.textSecondary, fontSize = 15.sp, lineHeight = 21.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
}

/** Rounded translucent pill used for the country, the number and the password. */
@Composable
internal fun AuthPill(modifier: Modifier = Modifier, focused: Boolean = false, onClick: (() -> Unit)? = null, content: @Composable RowScope.() -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier.fillMaxWidth().heightIn(min = 54.dp).clip(shape).background(Color.White.copy(alpha = 0.06f))
            .border(1.dp, if (focused) Fusion.colors.accent.copy(alpha = 0.8f) else Color.White.copy(alpha = 0.10f), shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically, content = content
    )
}

/** Wide button with the board's violet gradient; grey while it cannot be pressed. */
@Composable
internal fun AuthGradientButton(text: String, enabled: Boolean, busy: Boolean = false, onClick: () -> Unit) {
    val accent = Fusion.colors.accent
    val brush = if (enabled || busy) Brush.horizontalGradient(listOf(accent.copy(alpha = 0.85f), accent)) else Brush.horizontalGradient(listOf(Fusion.colors.surface2, Fusion.colors.surface2))
    Box(
        Modifier.fillMaxWidth().heightIn(min = 54.dp).clip(RoundedCornerShape(27.dp)).background(brush)
            .clickable(enabled = enabled && !busy, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (busy) CircularProgressIndicator(Modifier.size(22.dp), color = Fusion.colors.onAccent, strokeWidth = 2.dp)
        else Text(text, color = if (enabled) Fusion.colors.onAccent else Fusion.colors.textTertiary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** Quiet text link under the form (QR login, "Забыли пароль?", back to the account). */
@Composable
internal fun AuthLink(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    Text(
        text, color = if (enabled) Fusion.colors.accent else Fusion.colors.textTertiary, fontSize = 15.sp, textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(enabled = enabled, onClick = onClick).padding(vertical = 10.dp)
    )
}

private val keyLetters = mapOf('2' to "ABC", '3' to "DEF", '4' to "GHI", '5' to "JKL", '6' to "MNO", '7' to "PQRS", '8' to "TUV", '9' to "WXYZ")

/** Phone-style keypad in place of the system keyboard, as on the board. */
@Composable
internal fun AuthKeypad(enabled: Boolean = true, onDigit: (Char) -> Unit, onDelete: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    val rows = listOf("123", "456", "789", " 0<")
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { key ->
                    val blank = key == ' '
                    Box(
                        Modifier.weight(1f).height(52.dp).clip(RoundedCornerShape(14.dp))
                            .background(if (blank || key == '<') Color.Transparent else Color.White.copy(alpha = 0.07f))
                            .clickable(enabled = enabled && !blank) {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                if (key == '<') onDelete() else onDigit(key)
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        when {
                            blank -> Unit
                            key == '<' -> KiroXIcon(KiroXIcon.Backspace, "Стереть", Modifier.size(24.dp), Fusion.colors.textPrimary)
                            else -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(key.toString(), color = Fusion.colors.textPrimary, fontSize = 22.sp, fontWeight = FontWeight.Medium)
                                keyLetters[key]?.let { Text(it, color = Fusion.colors.textTertiary, fontSize = 9.sp, letterSpacing = 1.sp) }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Display-only digit boxes for the code step; input comes from [AuthKeypad]. */
@Composable
internal fun AuthCodeBoxes(value: String, length: Int, error: Boolean) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally)) {
        repeat(length) { i ->
            val current = i == value.length
            val shape = RoundedCornerShape(14.dp)
            Box(
                Modifier.weight(1f, fill = false).widthIn(max = 54.dp).aspectRatio(0.84f).clip(shape).background(Color.White.copy(alpha = 0.06f))
                    .border(if (current || error) 1.5.dp else 1.dp, when { error -> Fusion.colors.danger; current -> Fusion.colors.accent; else -> Color.White.copy(alpha = 0.10f) }, shape),
                contentAlignment = Alignment.Center
            ) { Text(value.getOrNull(i)?.toString().orEmpty(), color = Fusion.colors.textPrimary, fontSize = 24.sp, fontWeight = FontWeight.SemiBold) }
        }
    }
}
