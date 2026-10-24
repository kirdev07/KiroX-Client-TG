package com.kirox.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kirox.app.phone.PhoneEntryState
import com.kirox.app.demo.DemoAuth
import com.kirox.app.ui.country.CountryPickerScreen
import com.kirox.app.ui.demo.DemoCodeScreen
import com.kirox.app.ui.home.DemoMainScreen
import com.kirox.app.ui.splash.AnimatedSplashScreen2
import com.kirox.app.ui.start.PhoneLoginScreen
import com.kirox.app.telegram.TelegramAuthState
import com.kirox.app.telegram.TelegramViewModel
import com.kirox.app.ui.auth.TelegramAuthStepScreen
import com.kirox.app.ui.home.TelegramMainScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private enum class Destination { Phone, Countries, TelegramAuth, TelegramHome, DemoCode, DemoHome }

@Composable
fun KiroXApp() {
    val context = LocalContext.current
    val telegramViewModel: TelegramViewModel = viewModel()
    val telegramAuth by telegramViewModel.auth.state.collectAsStateWithLifecycle()
    val telegramBusy by telegramViewModel.auth.busy.collectAsStateWithLifecycle()
    val telegramError by telegramViewModel.auth.error.collectAsStateWithLifecycle()
    val mainChats by telegramViewModel.chats.mainChats.collectAsStateWithLifecycle()
    val archiveChats by telegramViewModel.chats.archiveChats.collectAsStateWithLifecycle()
    var phoneState by remember { mutableStateOf<PhoneEntryState?>(null) }
    var splashVisible by remember { mutableStateOf(true) }
    var destination by remember { mutableStateOf(Destination.Phone) }

    LaunchedEffect(Unit) {
        phoneState = withContext(Dispatchers.Default) { PhoneEntryState.create(context) }
    }

    LaunchedEffect(telegramAuth) {
        if (destination == Destination.DemoCode || destination == Destination.DemoHome || destination == Destination.Countries) return@LaunchedEffect
        destination = when (telegramAuth) {
            TelegramAuthState.Ready -> Destination.TelegramHome
            TelegramAuthState.WaitPhoneNumber, is TelegramAuthState.Blocked, TelegramAuthState.Initializing -> Destination.Phone
            TelegramAuthState.Closing -> Destination.Phone
            else -> Destination.TelegramAuth
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Crossfade(
            targetState = splashVisible || phoneState == null,
            animationSpec = tween(0),
            label = "splash_to_phone"
        ) { showSplash ->
            if (showSplash) {
                AnimatedSplashScreen2(
                    contentReady = phoneState != null,
                    playFullAnimation = true,
                    onFinished = { splashVisible = false }
                )
            } else {
                val state = requireNotNull(phoneState)
                AnimatedContent(
                    targetState = destination,
                    transitionSpec = {
                        if (targetState != Destination.Phone) {
                            slideInHorizontally(tween(280)) { it } togetherWith slideOutHorizontally(tween(280)) { -it / 4 }
                        } else {
                            slideInHorizontally(tween(280)) { -it / 4 } togetherWith slideOutHorizontally(tween(280)) { it }
                        }
                    },
                    label = "phone_navigation"
                ) { screen ->
                    when (screen) {
                        Destination.Phone -> PhoneLoginScreen(
                            state,
                            onChooseCountry = { destination = Destination.Countries },
                            onDemoMode = { destination = Destination.DemoCode },
                            onContinue = telegramViewModel.auth::phone,
                            externalStatus = (telegramAuth as? TelegramAuthState.Blocked)?.message ?: telegramError,
                            busy = telegramBusy
                        )
                        Destination.Countries -> CountryPickerScreen(state, onBack = { destination = Destination.Phone })
                        Destination.DemoCode -> DemoCodeScreen(
                            codeLength = DemoAuth.codeLength,
                            onVerify = DemoAuth::verify,
                            onSuccess = { destination = Destination.DemoHome },
                            onExit = { destination = Destination.Phone }
                        )
                        Destination.DemoHome -> DemoMainScreen(onExit = { destination = Destination.Phone })
                        Destination.TelegramAuth -> TelegramAuthStepScreen(telegramAuth, telegramViewModel.auth, telegramBusy, telegramError)
                        Destination.TelegramHome -> TelegramMainScreen(mainChats, archiveChats)
                    }
                }
            }
        }
    }
}
