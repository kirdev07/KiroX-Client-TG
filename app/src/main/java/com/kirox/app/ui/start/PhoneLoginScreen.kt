package com.kirox.app.ui.start

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
    onDemoMode: () -> Unit,
    onContinue: (String) -> Unit = {},
    onQrLogin: () -> Unit = {},
    externalStatus: String? = null,
    busy: Boolean = false
) {
    val focusManager = LocalFocusManager.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Fusion.colors.background)
            .statusBarsPadding()
            .imePadding()
    ) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 180.dp)) {
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
            modifier = Modifier.navigationBarsPadding().padding(horizontal = 24.dp, vertical = 20.dp).align(Alignment.BottomCenter),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                "Войти по QR-коду",
                color = if (busy) KiroXSecondary else KiroXWhite, fontSize = 15.sp, fontWeight = FontWeight.Medium,
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).wrapContentHeight().clickable(enabled = !busy) {
                    focusManager.clearFocus()
                    onQrLogin()
                },
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            DemoButton(onClick = {
                focusManager.clearFocus()
                onDemoMode()
            })
            PrimaryButton(text = if (busy) "Подключение…" else "Продолжить", onClick = {
                focusManager.clearFocus()
                if (!busy && state.validate()) onContinue(state.internationalNumber)
            })
        }
    }
}

@Composable
private fun DemoButton(onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, KiroXOutline, RoundedCornerShape(16.dp))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text("Демо-режим", color = KiroXWhite, fontSize = 15.sp, fontWeight = FontWeight.Medium)
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
            value = state.formattedNumber,
            onValueChange = state::updateNumber,
            modifier = Modifier.weight(1f).onFocusChanged { focused = it.isFocused },
            textStyle = TextStyle(color = KiroXWhite, fontSize = 17.sp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { state.validate() }),
            singleLine = true,
            cursorBrush = SolidColor(KiroXWhite),
            decorationBox = { inner ->
                Box {
                    if (state.digits.isEmpty()) Text("700 000 00 00", color = Fusion.colors.textTertiary, fontSize = 17.sp)
                    inner()
                }
            }
        )
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
            .clip(RoundedCornerShape(18.dp))
            .background(KiroXWhite)
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
