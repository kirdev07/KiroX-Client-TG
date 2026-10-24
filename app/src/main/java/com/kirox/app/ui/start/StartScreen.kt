package com.kirox.app.ui.start

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text

private val White = Color.White
private val Secondary = Color(0xFF8E8E93)
private val Outline = Color(0xFF3A3A3C)

@Composable
fun StartScreen() {
    var countryCode by rememberSaveable { mutableStateOf("") }
    var phoneNumber by rememberSaveable { mutableStateOf("") }
    var syncContacts by rememberSaveable { mutableStateOf(true) }
    val focusManager = LocalFocusManager.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .imePadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.Center)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 32.dp, vertical = 72.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Номер телефона",
                color = White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Проверьте код страны и введите свой номер телефона.",
                color = Secondary,
                fontSize = 14.sp,
                lineHeight = 18.sp,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(24.dp))

            CountryField()
            Spacer(Modifier.height(8.dp))
            PhoneField(
                countryCode = countryCode,
                onCountryCodeChange = { countryCode = it.filter(Char::isDigit).take(4) },
                phoneNumber = phoneNumber,
                onPhoneNumberChange = { phoneNumber = it.filter(Char::isDigit).take(15) }
            )
            Spacer(Modifier.height(12.dp))
            SyncContactsRow(
                checked = syncContacts,
                onCheckedChange = { syncContacts = !syncContacts }
            )
        }

        ContinueButton(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(end = 24.dp, bottom = 24.dp),
            onClick = { focusManager.clearFocus() }
        )
    }
}

@Composable
private fun CountryField() {
    OutlinedContainer(label = "Страна", focused = false) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Выберите страну", color = Secondary, fontSize = 16.sp, modifier = Modifier.weight(1f))
            Canvas(Modifier.size(24.dp)) {
                val x = size.width * 0.42f
                drawLine(Secondary, Offset(x, size.height * 0.33f), Offset(size.width * 0.62f, size.height * 0.5f), 2.dp.toPx())
                drawLine(Secondary, Offset(size.width * 0.62f, size.height * 0.5f), Offset(x, size.height * 0.67f), 2.dp.toPx())
            }
        }
    }
}

@Composable
private fun PhoneField(
    countryCode: String,
    onCountryCodeChange: (String) -> Unit,
    phoneNumber: String,
    onPhoneNumberChange: (String) -> Unit
) {
    var focused by remember { mutableStateOf(false) }
    OutlinedContainer(label = "Номер телефона", focused = focused) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("+", color = White, fontSize = 16.sp)
            BasicTextField(
                value = countryCode,
                onValueChange = onCountryCodeChange,
                modifier = Modifier.width(52.dp).padding(start = 9.dp).onFocusChanged { focused = it.isFocused },
                textStyle = TextStyle(color = White, fontSize = 16.sp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                singleLine = true,
                cursorBrush = SolidColor(White)
            )
            Box(Modifier.padding(horizontal = 12.dp).width(1.dp).height(32.dp).background(Outline))
            BasicTextField(
                value = phoneNumber,
                onValueChange = onPhoneNumberChange,
                modifier = Modifier.weight(1f).onFocusChanged { focused = it.isFocused },
                textStyle = TextStyle(color = White, fontSize = 16.sp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                singleLine = true,
                cursorBrush = SolidColor(White),
                decorationBox = { inner ->
                    Box {
                        if (phoneNumber.isEmpty()) Text("--- --- -- --", color = Color(0xFF5A5A5E), fontSize = 16.sp)
                        inner()
                    }
                }
            )
        }
    }
}

@Composable
private fun OutlinedContainer(label: String, focused: Boolean, content: @Composable BoxScope.() -> Unit) {
    val color = if (focused) White else Outline
    Box(
        modifier = Modifier.fillMaxWidth().height(58.dp).border(1.dp, color, RoundedCornerShape(10.dp))
    ) {
        Box(Modifier.fillMaxSize(), content = content)
        Text(
            text = label,
            color = if (focused) White else Secondary,
            fontSize = 12.sp,
            modifier = Modifier.align(Alignment.TopStart).offset(x = 12.dp, y = (-8).dp).background(Color.Black).padding(horizontal = 4.dp)
        )
    }
}

@Composable
private fun SyncContactsRow(checked: Boolean, onCheckedChange: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().height(48.dp).clickable(onClick = onCheckedChange),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Canvas(Modifier.size(22.dp)) {
            drawRoundRect(if (checked) White else Color.Transparent, cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx()))
            drawRoundRect(if (checked) White else Outline, cornerRadius = androidx.compose.ui.geometry.CornerRadius(4.dp.toPx()), style = Stroke(1.5.dp.toPx()))
            if (checked) {
                drawLine(Color.Black, Offset(size.width * .25f, size.height * .52f), Offset(size.width * .44f, size.height * .7f), 2.dp.toPx())
                drawLine(Color.Black, Offset(size.width * .44f, size.height * .7f), Offset(size.width * .76f, size.height * .32f), 2.dp.toPx())
            }
        }
        Spacer(Modifier.width(12.dp))
        Text("Синхронизировать контакты", color = White, fontSize = 15.sp)
    }
}

@Composable
private fun ContinueButton(modifier: Modifier = Modifier, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = modifier.size(56.dp).clip(CircleShape).background(White).clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.size(24.dp)) {
            drawLine(Color.Black, Offset(size.width * .2f, size.height * .5f), Offset(size.width * .78f, size.height * .5f), 2.dp.toPx())
            drawLine(Color.Black, Offset(size.width * .58f, size.height * .28f), Offset(size.width * .8f, size.height * .5f), 2.dp.toPx())
            drawLine(Color.Black, Offset(size.width * .8f, size.height * .5f), Offset(size.width * .58f, size.height * .72f), 2.dp.toPx())
        }
    }
}
