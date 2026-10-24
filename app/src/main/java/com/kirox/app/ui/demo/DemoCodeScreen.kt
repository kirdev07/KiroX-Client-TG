package com.kirox.app.ui.demo

import com.kirox.app.ui.theme.Fusion
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kirox.app.ui.start.BrandLogo
import com.kirox.app.ui.start.KiroXOutline
import com.kirox.app.ui.start.KiroXSecondary
import com.kirox.app.ui.start.KiroXSurface
import com.kirox.app.ui.start.KiroXWhite
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

@Composable
fun DemoCodeScreen(
    codeLength: Int,
    onVerify: (String) -> Boolean,
    onSuccess: () -> Unit,
    onExit: () -> Unit
) {
    var code by remember(codeLength) { mutableStateOf("") }
    var hasError by remember { mutableStateOf(false) }
    var checking by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val shake = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    fun submit(candidate: String) {
        if (checking || candidate.length != codeLength) return
        checking = true
        if (onVerify(candidate)) {
            keyboard?.hide()
            onSuccess()
        } else {
            hasError = true
            scope.launch {
                listOf(10f, -9f, 7f, -5f, 2f, 0f).forEach { shake.animateTo(it) }
                checking = false
            }
        }
    }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Fusion.colors.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Spacer(Modifier.height(18.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            BrandLogo(Modifier.weight(1f))
            DemoBadge()
        }
        Spacer(Modifier.height(62.dp))
        Text("Код подтверждения", color = KiroXWhite, fontSize = 34.sp, lineHeight = 39.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(14.dp))
        Text("Введите тестовый код, чтобы открыть демонстрационный режим KiroX.", color = KiroXSecondary, fontSize = 15.sp, lineHeight = 21.sp)
        Spacer(Modifier.height(38.dp))

        BasicTextField(
            value = code,
            onValueChange = { entered ->
                val next = entered.filter(Char::isDigit).take(codeLength)
                code = next
                hasError = false
                checking = false
                submit(next)
            },
            modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
            textStyle = TextStyle(color = Color.Transparent),
            cursorBrush = SolidColor(Color.Transparent),
            singleLine = true,
            decorationBox = {
                Row(
                    modifier = Modifier.fillMaxWidth().offset { IntOffset(shake.value.roundToInt(), 0) },
                    horizontalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    repeat(codeLength) { index ->
                        val active = index == code.length && code.length < codeLength
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(0.86f)
                                .clip(RoundedCornerShape(16.dp))
                                .background(KiroXSurface)
                                .border(
                                    1.dp,
                                    when {
                                        hasError -> Fusion.colors.danger
                                        active -> KiroXWhite
                                        else -> KiroXOutline
                                    },
                                    RoundedCornerShape(16.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(code.getOrNull(index)?.toString().orEmpty(), color = KiroXWhite, fontSize = 25.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        )
        Spacer(Modifier.height(14.dp))
        AnimatedVisibility(hasError, enter = fadeIn(), exit = fadeOut()) {
            Text("Неверный код. Проверьте цифры и попробуйте снова.", color = Fusion.colors.danger, fontSize = 13.sp)
        }
        Spacer(Modifier.height(18.dp))
        Text("Тестовый код: 12345", color = KiroXSecondary, fontSize = 13.sp)
        Spacer(Modifier.weight(1f))
        ExitDemoButton(onExit)
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
internal fun DemoBadge() {
    Box(
        Modifier.clip(RoundedCornerShape(50)).background(Fusion.colors.accent).padding(horizontal = 12.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Text("ДЕМО-РЕЖИМ", color = Fusion.colors.onAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = .8.sp)
    }
}

@Composable
internal fun ExitDemoButton(onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        Modifier.fillMaxWidth().height(54.dp).clip(RoundedCornerShape(18.dp))
            .border(1.dp, KiroXOutline, RoundedCornerShape(18.dp))
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text("Выйти к авторизации", color = KiroXWhite, fontSize = 15.sp, fontWeight = FontWeight.Medium)
    }
}
