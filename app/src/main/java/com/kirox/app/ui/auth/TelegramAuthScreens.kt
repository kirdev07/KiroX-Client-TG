package com.kirox.app.ui.auth

import com.kirox.app.ui.theme.Fusion
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
    val recovering = state is TelegramAuthState.WaitPassword && recoveryCodeSent
    // TDLib cannot go back from QR login to phone entry; abandoning the login restarts it cleanly.
    BackHandler(enabled = state is TelegramAuthState.WaitOtherDevice && !busy) { repository.logOut() }
    BackHandler(enabled = recovering) { repository.cancelPasswordRecovery() }

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
        is TelegramAuthState.WaitCode -> "Введите код, отправленный Telegram на ${state.phoneNumber}."
        is TelegramAuthState.WaitPassword -> when {
            recovering -> "Введите код, отправленный на ${state.recoveryEmailPattern.ifBlank { "резервную почту" }}, и задайте новый пароль."
            state.hint.isBlank() -> "Введите пароль двухэтапной аутентификации."
            else -> "Подсказка: ${state.hint}"
        }
        TelegramAuthState.WaitEmailAddress -> "Telegram запрашивает email для продолжения входа."
        is TelegramAuthState.WaitEmailCode -> "Введите код подтверждения из письма."
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
    Column(
        Modifier.fillMaxSize().background(Fusion.colors.background).statusBarsPadding().navigationBarsPadding()
            .imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 28.dp)
    ) {
        Box(Modifier.fillMaxWidth().height(56.dp)) {
            com.kirox.app.ui.components.SeasonalLayer(Modifier.matchParentSize(), freeZoneFraction = 0.4f..0.98f)
            Text("kiroX", color = Fusion.colors.textPrimary, fontSize = 30.sp, fontWeight = FontWeight.Light, modifier = Modifier.align(Alignment.CenterStart))
        }
        Spacer(Modifier.height(56.dp))
        Text(title, color = Fusion.colors.textPrimary, fontSize = 32.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        Text(description, color = Fusion.colors.textSecondary, fontSize = 15.sp, lineHeight = 21.sp)
        Spacer(Modifier.height(32.dp))
        if (state is TelegramAuthState.WaitOtherDevice) {
            QrCode(state.link, Modifier.align(Alignment.CenterHorizontally).size(240.dp))
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
        Spacer(Modifier.height(24.dp))
        if (state is TelegramAuthState.WaitOtherDevice) {
            TextAction("Войти по номеру телефона", enabled = !busy) { repository.logOut() }
        } else {
            AuthButton(if (busy) "Проверка…" else if (recovering) "Сохранить пароль" else "Продолжить", !canSubmit) {
                when {
                    recovering -> repository.recoverPassword(first, second, third)
                    state is TelegramAuthState.WaitCode -> repository.code(first)
                    state is TelegramAuthState.WaitPassword -> repository.password(first)
                    state == TelegramAuthState.WaitEmailAddress -> repository.email(first)
                    state is TelegramAuthState.WaitEmailCode -> repository.emailCode(first)
                    state is TelegramAuthState.WaitRegistration -> repository.register(first, second)
                }
            }
        }
        if (state is TelegramAuthState.WaitCode && state.canResend) {
            Spacer(Modifier.height(10.dp))
            TextAction(
                if (state.timeout > 0) "Повторная отправка будет доступна через ${state.timeout} с" else "Отправить код повторно",
                enabled = state.timeout <= 0 && !busy
            ) { repository.resend() }
        }
        if (state is TelegramAuthState.WaitPassword) {
            Spacer(Modifier.height(10.dp))
            if (recovering) {
                TextAction("Отмена", enabled = !busy) { repository.cancelPasswordRecovery() }
            } else {
                TextAction("Забыли пароль?", enabled = !busy) {
                    if (state.hasRecoveryEmail) repository.requestPasswordRecovery() else recoveryUnavailable = true
                }
            }
        }
        if (busy) {
            Spacer(Modifier.height(20.dp))
            CircularProgressIndicator(Modifier.size(22.dp), color = Fusion.colors.textPrimary, strokeWidth = 2.dp)
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
private fun AuthButton(text: String, disabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(RoundedCornerShape(18.dp))
            .background(if (disabled) Fusion.colors.textSecondary else Fusion.colors.accent).clickable(enabled = !disabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Text(text, color = Fusion.colors.onAccent, fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
}
