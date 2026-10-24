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
import com.kirox.app.ui.country.CountryPickerScreen
import com.kirox.app.ui.splash.AnimatedSplashScreen2
import com.kirox.app.ui.start.PhoneLoginScreen
import com.kirox.app.telegram.TelegramAuthState
import com.kirox.app.telegram.TelegramViewModel
import com.kirox.app.ui.auth.TelegramAuthStepScreen
import com.kirox.app.ui.home.TelegramMainScreen
import com.kirox.app.ui.components.LocalTelegramFiles
import com.kirox.app.ui.chat.TelegramChatActions
import com.kirox.app.ui.chat.TelegramChatScreen
import com.kirox.app.telegram.TelegramChatHistory
import com.kirox.app.telegram.TelegramMessageRepository
import kotlinx.coroutines.launch
import com.kirox.app.telegram.TelegramStory
import com.kirox.app.telegram.TelegramStoryRepository
import com.kirox.app.ui.home.TelegramStoryActions
import com.kirox.app.ui.home.TelegramChatListActions
import com.kirox.app.telegram.TelegramChatRepository
import com.kirox.app.telegram.TelegramChatItem
import com.kirox.app.telegram.TelegramChatListKey
import com.kirox.app.ui.navigation.BackStack
import com.kirox.app.ui.navigation.NavGraph
import com.kirox.app.ui.navigation.Screen
import com.kirox.app.ui.theme.Fusion
import com.kirox.app.ui.theme.FusionMotion
import com.kirox.app.ui.theme.motionMs
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
    var openChatId by rememberSaveable { mutableLongStateOf(0L) }
    val history by telegramViewModel.messages.history.collectAsStateWithLifecycle()
    val messageError by telegramViewModel.messages.error.collectAsStateWithLifecycle()
    val callsState by telegramViewModel.calls.state.collectAsStateWithLifecycle()
    val storyChats by telegramViewModel.stories.storyChats.collectAsStateWithLifecycle()
    val storyActions = remember { TelegramStoryActionsImpl(telegramViewModel.stories) }
    val chatActions = remember { TelegramChatListActionsImpl(telegramViewModel.chats) }
    val chatError by telegramViewModel.chats.error.collectAsStateWithLifecycle()
    val settings = remember { (context.applicationContext as com.kirox.app.KiroXApplication).settings }
    val palette by settings.palette.collectAsStateWithLifecycle()
    val seasonal by settings.seasonalEffects.collectAsStateWithLifecycle()
    val bubbleShape by settings.bubbleShape.collectAsStateWithLifecycle()
    val motionLevel by settings.motion.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    fun openChat(id: Long) { openChatId = id; telegramViewModel.messages.open(id); stack.push(Screen.Conversation) }

    LaunchedEffect(Unit) {
        phoneState = withContext(Dispatchers.Default) { PhoneEntryState.create(context) }
    }

    // TDLib drives the graph: Ready opens main, auth steps open the auth step screen.
    LaunchedEffect(authRevision) {
        when (telegramAuth) {
            TelegramAuthState.Ready -> if (stack.current.graph != NavGraph.Main) stack.resetTo(Screen.Chats)
            TelegramAuthState.WaitPhoneNumber, TelegramAuthState.Initializing, TelegramAuthState.Closing, is TelegramAuthState.Blocked ->
                if (stack.current.graph == NavGraph.Main || stack.current == Screen.AuthStep) stack.resetTo(Screen.Phone)
            else -> stack.resetTo(Screen.Phone, Screen.AuthStep)
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
                val slide = motionMs(FusionMotion.SLOW)
                AnimatedContent(
                    targetState = stack.current,
                    transitionSpec = {
                        if (stack.forward) {
                            slideInHorizontally(tween(slide)) { it } togetherWith slideOutHorizontally(tween(slide)) { -it / 4 }
                        } else {
                            slideInHorizontally(tween(slide)) { -it / 4 } togetherWith slideOutHorizontally(tween(slide)) { it }
                        }
                    },
                    label = "navigation"
                ) { screen ->
                    when (screen) {
                        Screen.Phone -> PhoneLoginScreen(
                            state,
                            onChooseCountry = { stack.push(Screen.Countries) },
                            onContinue = telegramViewModel.auth::phone,
                            onQrLogin = telegramViewModel.auth::requestQr,
                            externalStatus = telegramBlocked ?: (telegramAuth as? TelegramAuthState.Blocked)?.message ?: telegramError,
                            busy = telegramBusy
                        )
                        Screen.Countries -> CountryPickerScreen(state, onBack = { stack.pop() })
                        Screen.AuthStep -> TelegramAuthStepScreen(telegramAuth, telegramViewModel.auth, telegramBusy, telegramError, recoveryCodeSent)
                        Screen.Chats -> CompositionLocalProvider(LocalTelegramFiles provides telegramViewModel.files) {
                            TelegramMainScreen(
                                chatLists, chatActions, chatError, telegramViewModel.chats::clearError, contacts, contactsLoading, profile, connection,
                                onOpenChat = ::openChat,
                                onOpenContact = { userId -> scope.launch { runCatching { telegramViewModel.chats.privateChatId(userId) }.onSuccess(::openChat) } },
                                calls = callsState,
                                onLoadCalls = telegramViewModel.calls::loadMore,
                                storyChats = storyChats,
                                storyActions = storyActions,
                                palette = palette,
                                onPalette = settings::setPalette,
                                seasonal = seasonal,
                                onSeasonal = settings::setSeasonalEffects,
                                bubbleShape = bubbleShape,
                                onBubbleShape = settings::setBubbleShape,
                                motion = motionLevel,
                                onMotion = settings::setMotion,
                                onOpenSavedMessages = { scope.launch { runCatching { telegramViewModel.users.savedMessagesChatId() }.onSuccess(::openChat) } },
                                onLogOut = telegramViewModel.auth::logOut
                            )
                        }
                        Screen.Conversation -> CompositionLocalProvider(LocalTelegramFiles provides telegramViewModel.files) {
                            val chatId = openChatId
                            LaunchedEffect(chatId) { telegramViewModel.messages.open(chatId) }
                            DisposableEffect(chatId) { onDispose { telegramViewModel.messages.close(chatId) } }
                            val actions = remember { TelegramMessageActions(telegramViewModel.messages) }
                            TelegramChatScreen(
                                history = if (history.chatId == chatId) history else TelegramChatHistory(chatId = chatId),
                                error = messageError,
                                onErrorShown = telegramViewModel.messages::clearError,
                                forwardTargets = chatLists.main.chats,
                                actions = actions,
                                onBack = { stack.pop() }
                            )
                        }
                    }
                }
            }
        }
    }
}

private class TelegramMessageActions(private val repository: TelegramMessageRepository) : TelegramChatActions {
    override fun loadOlder() = repository.loadOlder()
    override fun sendText(text: String, replyToMessageId: Long?) = repository.sendText(text, replyToMessageId)
    override fun sendPhoto(path: String, caption: String) = repository.sendPhoto(path, caption)
    override fun sendDocument(path: String, caption: String) = repository.sendDocument(path, caption)
    override fun edit(messageId: Long, text: String) = repository.edit(messageId, text)
    override fun delete(messageIds: List<Long>, forEveryone: Boolean) = repository.delete(messageIds, forEveryone)
    override fun forward(toChatId: Long, messageIds: List<Long>) = repository.forward(toChatId, messageIds)
}

private class TelegramStoryActionsImpl(private val repository: TelegramStoryRepository) : TelegramStoryActions {
    override suspend fun story(chatId: Long, storyId: Int): TelegramStory? = repository.story(chatId, storyId)
    override fun open(chatId: Long, storyId: Int) = repository.open(chatId, storyId)
    override fun close(chatId: Long, storyId: Int) = repository.close(chatId, storyId)
}

private class TelegramChatListActionsImpl(private val repository: TelegramChatRepository) : TelegramChatListActions {
    override fun loadMore(key: TelegramChatListKey) = repository.loadMore(key)
    override fun setArchived(chatId: Long, archived: Boolean) = repository.setArchived(chatId, archived)
    override fun setPinned(key: TelegramChatListKey, chatId: Long, pinned: Boolean) = repository.setPinned(key, chatId, pinned)
    override fun markRead(chatId: Long) = repository.markRead(chatId)
    override fun markUnread(chatId: Long) = repository.markUnread(chatId)
    override suspend fun search(query: String): List<TelegramChatItem> = repository.search(query)
}
