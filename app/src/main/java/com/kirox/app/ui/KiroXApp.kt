package com.kirox.app.ui

import androidx.activity.compose.BackHandler
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
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
import com.kirox.app.ui.components.LocalTelegramFiles
import com.kirox.app.ui.navigation.BackStack
import com.kirox.app.ui.navigation.NavGraph
import com.kirox.app.ui.navigation.Screen
import com.kirox.app.ui.theme.Fusion
import com.kirox.app.ui.theme.FusionMotion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun KiroXApp() {
    val context = LocalContext.current
    val telegramViewModel: TelegramViewModel = viewModel()
    val telegramAuth by telegramViewModel.auth.state.collectAsStateWithLifecycle()
    val authRevision by telegramViewModel.auth.revision.collectAsStateWithLifecycle()
    val telegramBlocked by telegramViewModel.auth.blocked.collectAsStateWithLifecycle()
    val telegramBusy by telegramViewModel.auth.busy.collectAsStateWithLifecycle()
    val telegramError by telegramViewModel.auth.error.collectAsStateWithLifecycle()
    val recoveryCodeSent by telegramViewModel.auth.recoveryCodeSent.collectAsStateWithLifecycle()
    val connection by telegramViewModel.connection.state.collectAsStateWithLifecycle()
    val profile by telegramViewModel.users.profile.collectAsStateWithLifecycle()
    val chatLists by telegramViewModel.chats.lists.collectAsStateWithLifecycle()
    val contacts by telegramViewModel.users.contacts.collectAsStateWithLifecycle()
    val contactsLoading by telegramViewModel.users.contactsLoading.collectAsStateWithLifecycle()
    var phoneState by remember { mutableStateOf<PhoneEntryState?>(null) }
    var splashVisible by rememberSaveable { mutableStateOf(true) }
    val stack = rememberSaveable(saver = BackStack.Saver) { BackStack(listOf(Screen.Phone)) }

    LaunchedEffect(Unit) {
        phoneState = withContext(Dispatchers.Default) { PhoneEntryState.create(context) }
    }

    // TDLib drives the graph: Ready opens main, auth steps open the auth step screen.
    LaunchedEffect(authRevision) {
        val inDemo = Screen.DemoCode in stack || Screen.DemoHome in stack
        when (telegramAuth) {
            TelegramAuthState.Ready -> stack.resetTo(Screen.Chats)
            TelegramAuthState.WaitPhoneNumber, TelegramAuthState.Initializing, TelegramAuthState.Closing, is TelegramAuthState.Blocked ->
                if (stack.current.graph == NavGraph.Main || stack.current == Screen.AuthStep) stack.resetTo(Screen.Phone)
            else -> if (!inDemo) stack.resetTo(Screen.Phone, Screen.AuthStep)
        }
    }

    BackHandler(enabled = stack.canPop) { stack.pop() }

    Box(Modifier.fillMaxSize().background(Fusion.colors.background)) {
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
                    targetState = stack.current,
                    transitionSpec = {
                        if (stack.forward) {
                            slideInHorizontally(tween(FusionMotion.SLOW)) { it } togetherWith slideOutHorizontally(tween(FusionMotion.SLOW)) { -it / 4 }
                        } else {
                            slideInHorizontally(tween(FusionMotion.SLOW)) { -it / 4 } togetherWith slideOutHorizontally(tween(FusionMotion.SLOW)) { it }
                        }
                    },
                    label = "navigation"
                ) { screen ->
                    when (screen) {
                        Screen.Phone -> PhoneLoginScreen(
                            state,
                            onChooseCountry = { stack.push(Screen.Countries) },
                            onDemoMode = { stack.push(Screen.DemoCode) },
                            onContinue = telegramViewModel.auth::phone,
                            onQrLogin = telegramViewModel.auth::requestQr,
                            externalStatus = telegramBlocked ?: (telegramAuth as? TelegramAuthState.Blocked)?.message ?: telegramError,
                            busy = telegramBusy
                        )
                        Screen.Countries -> CountryPickerScreen(state, onBack = { stack.pop() })
                        Screen.DemoCode -> DemoCodeScreen(
                            codeLength = DemoAuth.codeLength,
                            onVerify = DemoAuth::verify,
                            onSuccess = { stack.replaceTop(Screen.DemoHome) },
                            onExit = { stack.pop() }
                        )
                        Screen.DemoHome -> DemoMainScreen(onExit = { stack.resetTo(Screen.Phone) })
                        Screen.AuthStep -> TelegramAuthStepScreen(telegramAuth, telegramViewModel.auth, telegramBusy, telegramError, recoveryCodeSent)
                        Screen.Chats -> CompositionLocalProvider(LocalTelegramFiles provides telegramViewModel.files) {
                            TelegramMainScreen(
                                chatLists, telegramViewModel.chats::loadMore, contacts, contactsLoading, profile, connection,
                                onLogOut = telegramViewModel.auth::logOut
                            )
                        }
                    }
                }
            }
        }
    }
}
