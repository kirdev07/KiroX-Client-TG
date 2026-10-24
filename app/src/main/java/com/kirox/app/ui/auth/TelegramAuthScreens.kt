package com.kirox.app.ui.auth

import com.kirox.app.ui.theme.Fusion
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kirox.app.telegram.TelegramAuthRepository
import com.kirox.app.telegram.TelegramAuthState

@Composable
fun TelegramAuthStepScreen(state: TelegramAuthState, repository: TelegramAuthRepository, busy: Boolean, error: String?) {
    var first by remember(state) { mutableStateOf("") }
    var second by remember(state) { mutableStateOf("") }
    val isPassword = state is TelegramAuthState.WaitPassword
    val numeric = state is TelegramAuthState.WaitCode || state is TelegramAuthState.WaitEmailCode
    val title = when (state) {
        is TelegramAuthState.WaitCode -> "Код подтверждения"
        is TelegramAuthState.WaitPassword -> "Облачный пароль"
        TelegramAuthState.WaitEmailAddress -> "Электронная почта"
        is TelegramAuthState.WaitEmailCode -> "Код из письма"
        TelegramAuthState.WaitRegistration -> "Регистрация"
        is TelegramAuthState.WaitOtherDevice -> "Подтвердите вход"
        else -> "Telegram"
    }
    val description = when (state) {
        is TelegramAuthState.WaitCode -> "Введите код, отправленный Telegram на ${state.phoneNumber}."
        is TelegramAuthState.WaitPassword -> if (state.hint.isBlank()) "Введите пароль двухэтапной аутентификации." else "Подсказка: ${state.hint}"
        TelegramAuthState.WaitEmailAddress -> "Telegram запрашивает email для продолжения входа."
        is TelegramAuthState.WaitEmailCode -> "Введите код подтверждения из письма."
        TelegramAuthState.WaitRegistration -> "Введите имя для создания аккаунта Telegram и принятия условий сервиса."
        is TelegramAuthState.WaitOtherDevice -> "Откройте ссылку на уже авторизованном устройстве:\n${state.link}"
        else -> "Подождите…"
    }
    Column(
        Modifier.fillMaxSize().background(Fusion.colors.background).statusBarsPadding().navigationBarsPadding()
            .imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 28.dp)
    ) {
        Text("kiroX", color = Fusion.colors.textPrimary, fontSize = 30.sp, fontWeight = FontWeight.Light)
        Spacer(Modifier.height(56.dp))
        Text(title, color = Fusion.colors.textPrimary, fontSize = 32.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        Text(description, color = Fusion.colors.textSecondary, fontSize = 15.sp, lineHeight = 21.sp)
        Spacer(Modifier.height(32.dp))
        if (state !is TelegramAuthState.WaitOtherDevice) {
            AuthField(
                value = first,
                onValueChange = { raw ->
                    val value = if (numeric) raw.filter(Char::isDigit) else raw
                    val max = when (state) {
                        is TelegramAuthState.WaitCode -> state.length.takeIf { it > 0 } ?: 12
                        is TelegramAuthState.WaitEmailCode -> state.length.takeIf { it > 0 } ?: 12
                        else -> 128
                    }
                    first = value.take(max)
                    repository.clearError()
                },
                hint = when (state) {
                    TelegramAuthState.WaitRegistration -> "Имя"
                    TelegramAuthState.WaitEmailAddress -> "Email"
                    else -> if (numeric) "Код" else "Пароль"
                },
                numeric = numeric,
                password = isPassword
            )
            if (state is TelegramAuthState.WaitRegistration) {
                Spacer(Modifier.height(12.dp))
                AuthField(second, { second = it.take(128) }, "Фамилия (необязательно)")
            }
        }
        if (error != null) {
            Spacer(Modifier.height(12.dp))
            Text(error, color = Fusion.colors.danger, fontSize = 13.sp)
        }
        Spacer(Modifier.height(24.dp))
        if (state !is TelegramAuthState.WaitOtherDevice) {
            AuthButton(if (busy) "Проверка…" else "Продолжить", busy || first.isBlank()) {
                when (state) {
                    is TelegramAuthState.WaitCode -> repository.code(first)
                    is TelegramAuthState.WaitPassword -> repository.password(first)
                    TelegramAuthState.WaitEmailAddress -> repository.email(first)
                    is TelegramAuthState.WaitEmailCode -> repository.emailCode(first)
                    TelegramAuthState.WaitRegistration -> repository.register(first, second)
                    else -> Unit
                }
            }
        }
        if (state is TelegramAuthState.WaitCode && state.canResend) {
            Spacer(Modifier.height(10.dp))
            Text(
                if (state.timeout > 0) "Повторная отправка будет доступна через ${state.timeout} с" else "Отправить код повторно",
                color = if (state.timeout > 0) Fusion.colors.textTertiary else Fusion.colors.textPrimary,
                modifier = Modifier.heightIn(min = 48.dp).wrapContentHeight().clickable(enabled = state.timeout <= 0 && !busy) { repository.resend() }
            )
        }
        if (busy) {
            Spacer(Modifier.height(20.dp))
            CircularProgressIndicator(Modifier.size(22.dp), color = Fusion.colors.textPrimary, strokeWidth = 2.dp)
        }
    }
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
