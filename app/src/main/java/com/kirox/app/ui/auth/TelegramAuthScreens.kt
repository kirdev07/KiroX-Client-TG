package com.kirox.app.ui.auth

import com.kirox.app.ui.theme.Fusion
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import com.kirox.app.telegram.TelegramAuthRepository
import com.kirox.app.telegram.TelegramAuthState

@Composable
fun TelegramAuthStepScreen(state: TelegramAuthState, repository: TelegramAuthRepository, busy: Boolean, error: String?, recoveryCodeSent: Boolean) {
    var first by remember(state, recoveryCodeSent) { mutableStateOf("") }
    var second by remember(state, recoveryCodeSent) { mutableStateOf("") }
    var third by remember(state, recoveryCodeSent) { mutableStateOf("") }
    var termsAccepted by remember(state) { mutableStateOf(false) }
    var recoveryUnavailable by remember(state) { mutableStateOf(false) }
    var resendSeconds by remember(state) { mutableIntStateOf((state as? TelegramAuthState.WaitCode)?.timeout ?: 0) }
    LaunchedEffect(state, resendSeconds) {
        if (resendSeconds > 0) {
            kotlinx.coroutines.delay(1_000)
            resendSeconds--
        }
    }
    val recovering = state is TelegramAuthState.WaitPassword && recoveryCodeSent

    val isPassword = state is TelegramAuthState.WaitPassword && !recovering
    val numeric = state is TelegramAuthState.WaitCode || state is TelegramAuthState.WaitEmailCode
    val title = when (state) {
        is TelegramAuthState.WaitCode -> "Код подтверждения"
        is TelegramAuthState.WaitPassword -> if (recovering) "Восстановление пароля" else "Облачный пароль"
        TelegramAuthState.WaitEmailAddress -> "Электронная почта"
        is TelegramAuthState.WaitEmailCode -> "Код из письма"
        is TelegramAuthState.WaitRegistration -> "Регистрация"
        is TelegramAuthState.WaitOtherDevice -> "Вход по QR-коду"
        else -> "Telegram"
    }
    val description = when (state) {
        is TelegramAuthState.WaitCode -> when (state.sentTo) {
            com.kirox.app.telegram.CodeDestination.App -> "Код отправлен в Telegram на другом вашем устройстве — откройте там чат «Telegram»."
            com.kirox.app.telegram.CodeDestination.Sms -> "Введите код из SMS."
            com.kirox.app.telegram.CodeDestination.Call -> "Telegram позвонит и продиктует код."
            com.kirox.app.telegram.CodeDestination.Fragment -> "Код отправлен в Fragment."
            com.kirox.app.telegram.CodeDestination.Other -> "Введите код, отправленный Telegram."
        }
        is TelegramAuthState.WaitPassword -> when {
            recovering -> "Введите код, отправленный на ${state.recoveryEmailPattern.ifBlank { "резервную почту" }}, и задайте новый пароль."
            state.hint.isBlank() -> "Введите пароль двухэтапной аутентификации."
            else -> "Подсказка: ${state.hint}"
        }
        TelegramAuthState.WaitEmailAddress -> "Telegram запрашивает email для продолжения входа."
        is TelegramAuthState.WaitEmailCode -> if (state.emailPattern.isNotBlank()) "Telegram отправил код на почту ${state.emailPattern}. Проверьте также папку «Спам»." else "Введите код подтверждения из письма."
        is TelegramAuthState.WaitRegistration -> "Введите имя для создания аккаунта Telegram."
        is TelegramAuthState.WaitOtherDevice -> "Откройте Telegram на телефоне: Настройки → Устройства → Подключить устройство, и отсканируйте код."
        else -> "Подождите…"
    }
    val canSubmit = when {
        busy -> false
        recovering -> first.isNotBlank() && second.isNotBlank()
        state is TelegramAuthState.WaitRegistration -> first.isNotBlank() && (state.termsOfService.isBlank() || termsAccepted)
        else -> first.isNotBlank()
    }
    // Every step can go back: recovery returns to the password, anything else abandons the login.
    val onBack: () -> Unit = { if (recovering) repository.cancelPasswordRecovery() else repository.logOut() }
    BackHandler(enabled = !busy && state != TelegramAuthState.Initializing && state != TelegramAuthState.Closing, onBack = onBack)
    val codeLength = when (state) {
        is TelegramAuthState.WaitCode -> state.length.takeIf { it in 3..8 }
        is TelegramAuthState.WaitEmailCode -> state.length.takeIf { it in 3..8 }
        else -> null
    }
    val submit: () -> Unit = {
        when {
            recovering -> repository.recoverPassword(first, second, third)
            state is TelegramAuthState.WaitCode -> repository.code(first)
            state is TelegramAuthState.WaitPassword -> repository.password(first)
            state == TelegramAuthState.WaitEmailAddress -> repository.email(first)
            state is TelegramAuthState.WaitEmailCode -> repository.emailCode(first)
            state is TelegramAuthState.WaitRegistration -> repository.register(first, second)
        }
    }
    // A wrong code clears the cells so it can be typed again; the cells shake once.
    LaunchedEffect(error) { if (error != null && codeLength != null) first = "" }

    Column(
        Modifier.fillMaxSize().background(Fusion.colors.background).statusBarsPadding().navigationBarsPadding().imePadding()
    ) {
        Row(Modifier.fillMaxWidth().height(56.dp).padding(start = 4.dp, end = 24.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).clip(CircleShape).clickable(enabled = !busy, onClick = onBack), contentAlignment = Alignment.Center) {
                com.kirox.app.ui.icons.KiroXIcon(com.kirox.app.ui.icons.KiroXIcon.Back, "Назад", Modifier.size(24.dp), Fusion.colors.textPrimary)
            }
            Box(Modifier.weight(1f).fillMaxHeight()) {
                com.kirox.app.ui.components.SeasonalLayer(Modifier.matchParentSize(), freeZoneFraction = 0.35f..0.98f)
                Text("kiroX", color = Fusion.colors.textPrimary, fontSize = 26.sp, fontWeight = FontWeight.Light, modifier = Modifier.align(Alignment.CenterStart))
            }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp)) {
            Spacer(Modifier.height(36.dp))
            Text(title, color = Fusion.colors.textPrimary, fontSize = 32.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            Text(description, color = Fusion.colors.textSecondary, fontSize = 15.sp, lineHeight = 21.sp)
            if (state is TelegramAuthState.WaitCode) {
                // The number the code went to, with a quick way to fix it.
                Row(
                    Modifier.padding(top = 10.dp).clip(RoundedCornerShape(12.dp)).clickable(enabled = !busy) { repository.logOut() }.padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(prettyPhone(state.phoneNumber), color = Fusion.colors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.width(8.dp))
                    com.kirox.app.ui.icons.KiroXIcon(com.kirox.app.ui.icons.KiroXIcon.Edit, "Изменить номер", Modifier.size(18.dp), Fusion.colors.textSecondary)
                }
            }
            Spacer(Modifier.height(28.dp))
            if (state is TelegramAuthState.WaitOtherDevice) {
                QrCode(state.link, Modifier.align(Alignment.CenterHorizontally).size(240.dp))
            } else if (codeLength != null && !recovering) {
                CodeCells(first, codeLength, error != null, enabled = !busy) { value ->
                    first = value
                    repository.clearError()
                    if (value.length == codeLength) submit()
                }
            } else {
                AuthField(
                    value = first,
                    onValueChange = { raw ->
                        val value = if (numeric || recovering) raw.filter(Char::isDigit) else raw
                        val max = when (state) {
                            is TelegramAuthState.WaitCode -> state.length.takeIf { it > 0 } ?: 12
                            is TelegramAuthState.WaitEmailCode -> state.length.takeIf { it > 0 } ?: 12
                            else -> 128
                        }
                        first = value.take(max)
                        repository.clearError()
                    },
                    hint = when {
                        recovering -> "Код восстановления"
                        state is TelegramAuthState.WaitRegistration -> "Имя"
                        state == TelegramAuthState.WaitEmailAddress -> "Email"
                        else -> if (numeric) "Код" else "Пароль"
                    },
                    numeric = numeric || recovering,
                    password = isPassword
                )
                if (state is TelegramAuthState.WaitRegistration) {
                    Spacer(Modifier.height(12.dp))
                    AuthField(second, { second = it.take(128) }, "Фамилия (необязательно)")
                    if (state.termsOfService.isNotBlank()) {
                        Spacer(Modifier.height(20.dp))
                        TermsOfService(state.termsOfService, state.minUserAge, termsAccepted) { termsAccepted = it }
                    }
                }
                if (recovering) {
                    Spacer(Modifier.height(12.dp))
                    AuthField(second, { second = it.take(128); repository.clearError() }, "Новый пароль", password = true)
                    Spacer(Modifier.height(12.dp))
                    AuthField(third, { third = it.take(128) }, "Подсказка (необязательно)")
                }
            }
            if (error != null) {
                Spacer(Modifier.height(12.dp))
                Text(error, color = Fusion.colors.danger, fontSize = 13.sp)
            }
            if (recoveryUnavailable) {
                Spacer(Modifier.height(12.dp))
                Text("К аккаунту не привязана резервная почта, восстановить пароль в KiroX нельзя.", color = Fusion.colors.textSecondary, fontSize = 13.sp)
            }
            Spacer(Modifier.height(12.dp))
            if (state is TelegramAuthState.WaitCode && state.canResend) {
                TextAction(
                    if (resendSeconds > 0) "Отправить код повторно через $resendSeconds с" else "Отправить код повторно",
                    enabled = resendSeconds <= 0 && !busy
                ) { repository.resend() }
            }
            if (state is TelegramAuthState.WaitCode) {
                // A way in when the code is unreachable, e.g. it went to a device you no longer have.
                TextAction("Войти по QR-коду", enabled = !busy) { repository.requestQr() }
                if (state.sentTo == com.kirox.app.telegram.CodeDestination.App && !state.canResend) Text(
                    "Нет другого устройства с Telegram? Войдите в официальное приложение Telegram — там код может прийти на почту — и подключите kiroX по QR-коду: «Настройки → Устройства → Подключить устройство».",
                    color = Fusion.colors.textTertiary, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 4.dp)
                )
            }
            if (state is TelegramAuthState.WaitPassword && !recovering) {
                TextAction("Забыли пароль?", enabled = !busy) {
                    if (state.hasRecoveryEmail) repository.requestPasswordRecovery() else recoveryUnavailable = true
                }
            }
            Spacer(Modifier.height(16.dp))
        }
        Box(Modifier.padding(horizontal = 24.dp, vertical = 16.dp)) {
            if (state is TelegramAuthState.WaitOtherDevice) {
                AuthButton("Войти по номеру телефона", disabled = busy, busy = false, onClick = { repository.logOut() })
            } else {
                AuthButton(if (recovering) "Сохранить пароль" else "Продолжить", disabled = !canSubmit, busy = busy, onClick = submit)
            }
        }
    }
}

@Composable
private fun TermsOfService(text: String, minUserAge: Int, accepted: Boolean, onAcceptedChange: (Boolean) -> Unit) {
    Text("Условия использования Telegram", color = Fusion.colors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(8.dp))
    Box(
        Modifier.fillMaxWidth().heightIn(max = 220.dp).clip(RoundedCornerShape(14.dp)).background(Fusion.colors.surface1)
            .border(1.dp, Fusion.colors.outline, RoundedCornerShape(14.dp)).verticalScroll(rememberScrollState()).padding(14.dp)
    ) { Text(text, color = Fusion.colors.textSecondary, fontSize = 13.sp, lineHeight = 18.sp) }
    if (minUserAge > 0) {
        Spacer(Modifier.height(8.dp))
        Text("Минимальный возраст: $minUserAge лет", color = Fusion.colors.textSecondary, fontSize = 12.sp)
    }
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { onAcceptedChange(!accepted) }, verticalAlignment = Alignment.CenterVertically) {
        Checkbox(
            accepted, onAcceptedChange,
            colors = CheckboxDefaults.colors(checkedColor = Fusion.colors.accent, checkmarkColor = Fusion.colors.onAccent, uncheckedColor = Fusion.colors.outlineStrong)
        )
        Text("Я принимаю условия использования", color = Fusion.colors.textPrimary, fontSize = 14.sp)
    }
}

@Composable
private fun QrCode(link: String, modifier: Modifier) {
    val matrix = remember(link) { encodeQr(link) }
    Box(modifier.clip(RoundedCornerShape(18.dp)).background(Color.White).padding(14.dp).semantics { contentDescription = "QR-код для входа" }) {
        if (matrix == null) {
            Text("Не удалось построить QR-код", color = Color.Black, modifier = Modifier.align(Alignment.Center))
        } else Canvas(Modifier.fillMaxSize()) {
            val cell = size.minDimension / matrix.width
            for (y in 0 until matrix.height) for (x in 0 until matrix.width) {
                if (matrix[x, y]) drawRect(Color.Black, Offset(x * cell, y * cell), Size(cell + 0.5f, cell + 0.5f))
            }
        }
    }
}

// QR codes must stay black on white regardless of theme so phone cameras can read them.
private fun encodeQr(link: String): BitMatrix? = runCatching {
    QRCodeWriter().encode(link, BarcodeFormat.QR_CODE, 0, 0, mapOf(EncodeHintType.MARGIN to 0))
}.getOrNull()

@Composable
private fun TextAction(text: String, enabled: Boolean, onClick: () -> Unit) {
    Text(
        text,
        color = if (enabled) Fusion.colors.textPrimary else Fusion.colors.textTertiary,
        modifier = Modifier.heightIn(min = 48.dp).wrapContentHeight().clickable(enabled = enabled, onClick = onClick)
    )
}

@Composable
private fun AuthField(value: String, onValueChange: (String) -> Unit, hint: String, numeric: Boolean = false, password: Boolean = false) {
    BasicTextField(
        value, onValueChange,
        Modifier.fillMaxWidth().heightIn(min = 58.dp).clip(RoundedCornerShape(17.dp)).background(Fusion.colors.surface1)
            .border(1.dp, Fusion.colors.outline, RoundedCornerShape(17.dp)).padding(horizontal = 16.dp, vertical = 18.dp),
        textStyle = TextStyle(Fusion.colors.textPrimary, 17.sp), cursorBrush = SolidColor(Fusion.colors.textPrimary), singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = if (numeric) KeyboardType.NumberPassword else if (password) KeyboardType.Password else KeyboardType.Text),
        visualTransformation = if (password) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        decorationBox = { inner -> Box { if (value.isEmpty()) Text(hint, color = Fusion.colors.textTertiary, fontSize = 16.sp); inner() } }
    )
}

@Composable
private fun AuthButton(text: String, disabled: Boolean, busy: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(RoundedCornerShape(28.dp))
            .background(if (disabled && !busy) Fusion.colors.surface2 else Fusion.colors.accent)
            .clickable(enabled = !disabled && !busy, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (busy) CircularProgressIndicator(Modifier.size(22.dp), color = Fusion.colors.onAccent, strokeWidth = 2.dp)
        else Text(text, color = if (disabled) Fusion.colors.textTertiary else Fusion.colors.onAccent, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** One cell per digit; the hidden field keeps the keyboard, paste and SMS suggestions working. */
@Composable
private fun CodeCells(value: String, length: Int, error: Boolean, enabled: Boolean, onValueChange: (String) -> Unit) {
    val focus = remember { androidx.compose.ui.focus.FocusRequester() }
    val shake = remember { androidx.compose.animation.core.Animatable(0f) }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    LaunchedEffect(error) {
        if (error) for (x in listOf(14f, -12f, 8f, -6f, 3f, 0f)) shake.animateTo(x, androidx.compose.animation.core.tween(45))
    }
    BasicTextField(
        value = value,
        onValueChange = { raw -> onValueChange(raw.filter(Char::isDigit).take(length)) },
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().focusRequester(focus).offset { androidx.compose.ui.unit.IntOffset(shake.value.dp.roundToPx(), 0) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        singleLine = true,
        cursorBrush = SolidColor(Color.Transparent),
        textStyle = TextStyle(Color.Transparent),
        decorationBox = { _ ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                repeat(length) { i ->
                    val current = i == value.length && enabled
                    val border = when {
                        error -> Fusion.colors.danger
                        current -> Fusion.colors.textPrimary
                        i < value.length -> Fusion.colors.outlineStrong
                        else -> Fusion.colors.outline
                    }
                    Box(
                        Modifier.weight(1f).aspectRatio(0.82f).clip(RoundedCornerShape(14.dp)).background(Fusion.colors.surface1)
                            .border(if (current || error) 1.5.dp else 1.dp, border, RoundedCornerShape(14.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(value.getOrNull(i)?.toString().orEmpty(), color = Fusion.colors.textPrimary, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    )
}

/** "+77082950192" → "+7 708 295 01 92"; falls back to the raw number. */
private fun prettyPhone(raw: String): String {
    val digits = "+" + raw.filter(Char::isDigit)
    return runCatching {
        val util = com.google.i18n.phonenumbers.PhoneNumberUtil.getInstance()
        util.format(util.parse(digits, null), com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberFormat.INTERNATIONAL)
    }.getOrDefault(digits)
}
