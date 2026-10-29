package com.kirox.app.ui.start

import com.kirox.app.ui.components.fusionBackground
import androidx.compose.runtime.ReadOnlyComposable
import com.kirox.app.ui.theme.Fusion
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kirox.app.R
import com.kirox.app.phone.PhoneEntryState
import com.kirox.app.phone.PhoneStatus
import com.kirox.app.ui.icons.KiroXIcon
import com.kirox.app.ui.icons.KiroXIcon as IconGlyph

internal val KiroXWhite: Color @Composable @ReadOnlyComposable get() = Fusion.colors.textPrimary
internal val KiroXSecondary: Color @Composable @ReadOnlyComposable get() = Fusion.colors.textSecondary
internal val KiroXSurface: Color @Composable @ReadOnlyComposable get() = Fusion.colors.surface1
internal val KiroXOutline: Color @Composable @ReadOnlyComposable get() = Fusion.colors.outline

@Composable
fun PhoneLoginScreen(
    state: PhoneEntryState,
    onChooseCountry: () -> Unit,
    onContinue: (String) -> Unit = {},
    onQrLogin: () -> Unit = {},
    externalStatus: String? = null,
    busy: Boolean = false,
    /** Set while adding a second account: goes back to the signed-in one. */
    onCancel: (() -> Unit)? = null
) {
    if (onCancel != null) androidx.activity.compose.BackHandler(onBack = onCancel)
    val focusManager = LocalFocusManager.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .fusionBackground(Fusion.colors)
            .statusBarsPadding()
            .imePadding()
    ) {
        // Seasonal strip to the right of the logo; the form below stays clean.
        com.kirox.app.ui.components.SeasonalLayer(Modifier.fillMaxWidth().height(96.dp), freeZoneFraction = 0.42f..0.96f)
        // The form scrolls above the buttons, so the keyboard never pushes buttons over it.
        Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
            if (Fusion.colors.backdrop != null) {
                // Fusion UI v2 (approved auth board): centred mark, title and hint.
                Spacer(Modifier.height(40.dp))
                Box(
                    Modifier.align(Alignment.CenterHorizontally).size(76.dp).clip(androidx.compose.foundation.shape.CircleShape)
                        .background(androidx.compose.ui.graphics.Brush.radialGradient(listOf(Fusion.colors.accent.copy(alpha = 0.45f), androidx.compose.ui.graphics.Color.Transparent))),
                    contentAlignment = Alignment.Center
                ) { com.kirox.app.ui.icons.KiroXIcon(com.kirox.app.ui.icons.KiroXIcon.Send, null, Modifier.size(38.dp), Fusion.colors.accent) }
                Spacer(Modifier.height(18.dp))
                Text("Введите номер", color = KiroXWhite, fontSize = 26.sp, fontWeight = FontWeight.Bold, textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                Text("Ваш номер телефона\nдля входа в Telegram", color = KiroXSecondary, fontSize = 15.sp, lineHeight = 21.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(32.dp))
            } else {
            Spacer(Modifier.height(18.dp))
            BrandLogo()
            Spacer(Modifier.height(58.dp))
            Text(
                buildAnnotatedString {
                    append("Ваш номер\n")
                    withStyle(SpanStyle(color = Fusion.colors.textTertiary)) { append("телефона") }
                },
                color = KiroXWhite, fontSize = 34.sp, lineHeight = 38.sp, fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(16.dp))
            Text("Введите номер, чтобы продолжить\nвход в Telegram через kiroX.", color = KiroXSecondary, fontSize = 15.sp, lineHeight = 21.sp)
            Spacer(Modifier.height(32.dp))
            }
            FieldLabel("СТРАНА")
            Spacer(Modifier.height(8.dp))
            CountrySelector(state, onChooseCountry)
            Spacer(Modifier.height(20.dp))
            FieldLabel("НОМЕР ТЕЛЕФОНА")
            Spacer(Modifier.height(8.dp))
            PhoneNumberField(state)
            Spacer(Modifier.height(10.dp))
            Text("Номер будет автоматически отформатирован\nпод выбранную страну.", color = KiroXSecondary, fontSize = 13.sp, lineHeight = 18.sp)
            Spacer(Modifier.height(8.dp))
            StatusMessage(state.status)
            if (externalStatus != null) Text(externalStatus, color = Fusion.colors.danger, fontSize = 13.sp, lineHeight = 18.sp)
        }
        Column(
            modifier = Modifier.navigationBarsPadding().padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            PrimaryButton(text = if (busy) "Подключение…" else "Продолжить", onClick = {
                focusManager.clearFocus()
                if (!busy && state.validate()) onContinue(state.internationalNumber)
            })
            if (onCancel != null) Text(
                "Вернуться к аккаунту", color = KiroXSecondary, fontSize = 15.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.fillMaxWidth().clickable(onClick = onCancel).padding(vertical = 10.dp)
            )
            // Second way in, styled as a quieter button; hidden while typing so only "Продолжить" sits above the keyboard.
            val keyboardOpen = WindowInsets.ime.getBottom(androidx.compose.ui.platform.LocalDensity.current) > 0
            if (!keyboardOpen) Row(
                Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(RoundedCornerShape(26.dp))
                    .border(1.dp, Fusion.colors.outlineStrong, RoundedCornerShape(26.dp))
                    .clickable(enabled = !busy) { focusManager.clearFocus(); onQrLogin() },
                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically
            ) {
                com.kirox.app.ui.icons.KiroXIcon(com.kirox.app.ui.icons.KiroXIcon.Qr, null, Modifier.size(20.dp), if (busy) KiroXSecondary else KiroXWhite)
                Spacer(Modifier.width(10.dp))
                Text("Войти по QR-коду", color = if (busy) KiroXSecondary else KiroXWhite, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            }
        }
        }
    }
}

@Composable
internal fun BrandLogo(modifier: Modifier = Modifier) {
    Text(
        text = buildAnnotatedString {
            append("kiro")
            withStyle(SpanStyle(color = Fusion.colors.textTertiary, fontWeight = FontWeight.Light)) { append("X") }
        },
        color = KiroXWhite,
        fontSize = 31.sp,
        fontWeight = FontWeight.Light,
        modifier = modifier
    )
}

@Composable
private fun FieldLabel(text: String) {
    Text(text, color = KiroXSecondary, fontSize = 12.sp, letterSpacing = .6.sp)
}

@Composable
private fun CountrySelector(state: PhoneEntryState, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(KiroXSurface)
            .border(1.dp, KiroXOutline, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(state.country.flag, fontSize = 24.sp)
        Spacer(Modifier.width(12.dp))
        Text(state.country.name, color = KiroXWhite, fontSize = 16.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f), maxLines = 1)
        Text(state.country.dialCode, color = KiroXSecondary, fontSize = 15.sp)
        Spacer(Modifier.width(8.dp))
        ChevronRight()
    }
}

@Composable
private fun PhoneNumberField(state: PhoneEntryState) {
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(KiroXSurface)
            .border(1.dp, if (focused) KiroXWhite else KiroXOutline, RoundedCornerShape(18.dp))
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(state.country.dialCode, color = KiroXWhite, fontSize = 17.sp, fontWeight = FontWeight.Medium)
        Box(Modifier.padding(horizontal = 14.dp).width(1.dp).height(28.dp).background(KiroXOutline))
        BasicTextField(
            value = state.digits,
            onValueChange = state::updateNumber,
            modifier = Modifier.weight(1f).onFocusChanged { focused = it.isFocused },
            textStyle = TextStyle(color = KiroXWhite, fontSize = 17.sp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { state.validate() }),
            singleLine = true,
            cursorBrush = SolidColor(KiroXWhite),
            visualTransformation = PhoneNumberVisualTransformation { state.formattedNumber },
            decorationBox = { inner ->
                Box {
                    if (state.digits.isEmpty()) Text("700 000 00 00", color = Fusion.colors.textTertiary, fontSize = 17.sp)
                    inner()
                }
            }
        )
    }
}

private class PhoneNumberVisualTransformation(
    private val formattedValue: () -> String
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val formatted = formattedValue()
        val originalLength = text.text.length
        val originalToTransformed = IntArray(originalLength + 1)
        var digitIndex = 0
        originalToTransformed[0] = 0
        formatted.forEachIndexed { index, character ->
            if (character.isDigit() && digitIndex < originalLength) {
                digitIndex++
                originalToTransformed[digitIndex] = index + 1
            }
        }
        while (digitIndex < originalLength) {
            digitIndex++
            originalToTransformed[digitIndex] = formatted.length
        }
        val mapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int =
                originalToTransformed[offset.coerceIn(0, originalLength)]

            override fun transformedToOriginal(offset: Int): Int =
                formatted.take(offset.coerceIn(0, formatted.length)).count(Char::isDigit).coerceAtMost(originalLength)
        }
        return TransformedText(AnnotatedString(formatted), mapping)
    }
}

@Composable
private fun StatusMessage(status: PhoneStatus) {
    when (status) {
        PhoneStatus.Idle -> Spacer(Modifier.height(20.dp))
        is PhoneStatus.Error -> Text(status.message, color = Fusion.colors.danger, fontSize = 13.sp, lineHeight = 18.sp)
        is PhoneStatus.Ready -> Text(status.message, color = Fusion.colors.textSecondary, fontSize = 13.sp, lineHeight = 18.sp)
    }
}

@Composable
internal fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(RoundedCornerShape(if (Fusion.colors.backdrop != null) 28.dp else 18.dp))
            .background(if (Fusion.colors.backdrop != null) androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(androidx.compose.ui.graphics.lerp(Fusion.colors.accent, androidx.compose.ui.graphics.Color.White, 0.15f), Fusion.colors.accent)) else androidx.compose.ui.graphics.SolidColor(Fusion.colors.accent))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = Fusion.colors.onAccent, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        IconGlyph(KiroXIcon.Chevron, null, Modifier.align(Alignment.CenterEnd).padding(end = 28.dp).size(22.dp), Fusion.colors.onAccent)
    }
}

@Composable
internal fun ChevronRight(color: Color = KiroXSecondary) {
    Canvas(Modifier.size(18.dp)) {
        drawLine(color, Offset(size.width * .34f, size.height * .24f), Offset(size.width * .64f, size.height * .5f), 1.7.dp.toPx())
        drawLine(color, Offset(size.width * .64f, size.height * .5f), Offset(size.width * .34f, size.height * .76f), 1.7.dp.toPx())
    }
}
