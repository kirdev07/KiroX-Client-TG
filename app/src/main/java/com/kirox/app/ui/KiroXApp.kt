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
    val tdClient = remember { (context.applicationContext as com.kirox.app.KiroXApplication).telegram.client }
    val accounts by tdClient.accounts.accounts.collectAsStateWithLifecycle()
    LaunchedEffect(profile) { profile?.let { tdClient.accounts.describe(tdClient.activeSession, it.name, it.phoneNumber) } }
    val chatLists by telegramViewModel.chats.lists.collectAsStateWithLifecycle()
    val contacts by telegramViewModel.users.contacts.collectAsStateWithLifecycle()
    val contactsLoading by telegramViewModel.users.contactsLoading.collectAsStateWithLifecycle()
    var phoneState by remember { mutableStateOf<PhoneEntryState?>(null) }
    var splashVisible by rememberSaveable { mutableStateOf(true) }
    val stack = rememberSaveable(saver = BackStack.Saver) { BackStack(listOf(Screen.Phone)) }
    var openChatId by rememberSaveable { mutableLongStateOf(0L) }
    /** Comment thread or forum topic shown on Screen.Thread; lost with the process, then Thread pops. */
    var openThread by remember { mutableStateOf<ThreadSpec?>(null) }
    val history by telegramViewModel.messages.history.collectAsStateWithLifecycle()
    val autoDownloadRule by telegramViewModel.autoDownload.rule.collectAsStateWithLifecycle()
    val messageError by telegramViewModel.messages.error.collectAsStateWithLifecycle()
    val callsState by telegramViewModel.calls.state.collectAsStateWithLifecycle()
    val activeCall by telegramViewModel.voip.call.collectAsStateWithLifecycle()
    val groupCall by telegramViewModel.groupCalls.call.collectAsStateWithLifecycle()
    var groupCallNotice by remember { mutableStateOf<String?>(null) }
    val groupCallActions = remember {
        object : com.kirox.app.ui.call.GroupCallActions {
            override fun setMuted(muted: Boolean) = telegramViewModel.groupCalls.setMuted(muted)
            override fun leave() = telegramViewModel.groupCalls.leave()
            override fun dismiss() = telegramViewModel.groupCalls.dismiss()
        }
    }
    val callActions = remember {
        object : com.kirox.app.ui.call.CallActions {
            override fun accept() = telegramViewModel.voip.accept()
            override fun hangUp() = telegramViewModel.voip.hangUp()
            override fun dismiss() = telegramViewModel.voip.dismiss()
            override fun setMuted(muted: Boolean) = telegramViewModel.voip.setMuted(muted)
            override fun setCamera(on: Boolean) = telegramViewModel.voip.setCamera(on)
            override fun switchCamera() = telegramViewModel.voip.switchCamera()
            override fun setVideoSinks(remote: org.webrtc.VideoSink?, local: org.webrtc.VideoSink?) = telegramViewModel.voip.setVideoSinks(remote, local)
        }
    }
    // Microphone (and camera for video) are asked right before a call starts or is answered.
    var pendingCall by remember { mutableStateOf<(() -> Unit)?>(null) }
    val callPermissions = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        val action = pendingCall
        pendingCall = null
        if (granted[android.Manifest.permission.RECORD_AUDIO] == true) action?.invoke()
        else android.widget.Toast.makeText(context, "Без доступа к микрофону звонок невозможен", android.widget.Toast.LENGTH_LONG).show()
    }
    // Android 13+: ask once after sign-in, so new-message notifications can be shown.
    val notificationPermission = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { }
    LaunchedEffect(telegramAuth) {
        if (telegramAuth == TelegramAuthState.Ready && android.os.Build.VERSION.SDK_INT >= 33 &&
            androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
    }
    fun withCallPermissions(video: Boolean, action: () -> Unit) {
        val needed = listOfNotNull(
            android.Manifest.permission.RECORD_AUDIO,
            if (video) android.Manifest.permission.CAMERA else null,
            // The "call in progress" notification; the call works without it.
            if (android.os.Build.VERSION.SDK_INT >= 33) android.Manifest.permission.POST_NOTIFICATIONS else null
        )
            .filter { androidx.core.content.ContextCompat.checkSelfPermission(context, it) != android.content.pm.PackageManager.PERMISSION_GRANTED }
        if (needed.isEmpty()) action() else { pendingCall = action; callPermissions.launch(needed.toTypedArray()) }
    }
    val permittedCallActions = remember(callActions) {
        object : com.kirox.app.ui.call.CallActions by callActions {
            override fun accept() {
                val video = telegramViewModel.voip.call.value?.isVideo == true
                withCallPermissions(video) { callActions.accept() }
            }
            override fun setCamera(on: Boolean) {
                if (on) withCallPermissions(video = true) { callActions.setCamera(true) } else callActions.setCamera(false)
            }
        }
    }
    val storyChats by telegramViewModel.stories.storyChats.collectAsStateWithLifecycle()
    val storyActions = remember { TelegramStoryActionsImpl(telegramViewModel.stories) }
    val chatActions = remember { TelegramChatListActionsImpl(telegramViewModel.chats, telegramViewModel.messages) }
    val chatMenu = remember {
        object : com.kirox.app.ui.chat.TelegramChatMenuActions {
            override suspend fun isMuted(chatId: Long) = telegramViewModel.chats.isMuted(chatId)
            override suspend fun setMuted(chatId: Long, muted: Boolean) = telegramViewModel.chats.setMuted(chatId, muted)
            override suspend fun clearHistory(chatId: Long) = telegramViewModel.chats.clearHistory(chatId)
            override suspend fun setAutoDelete(chatId: Long, seconds: Int) = telegramViewModel.chats.setAutoDelete(chatId, seconds)
            override suspend fun deleteChat(chatId: Long) = telegramViewModel.chats.deleteChat(chatId)
        }
    }
    val chatError by telegramViewModel.chats.error.collectAsStateWithLifecycle()
    val settings = remember { (context.applicationContext as com.kirox.app.KiroXApplication).settings }
    val palette by settings.palette.collectAsStateWithLifecycle()
    val seasonal by settings.seasonalEffects.collectAsStateWithLifecycle()
    val bubbleShape by settings.bubbleShape.collectAsStateWithLifecycle()
    val motionLevel by settings.motion.collectAsStateWithLifecycle()
    val effectsQuality by settings.effectsQuality.collectAsStateWithLifecycle()
    val effectScene by settings.effectScene.collectAsStateWithLifecycle()
    val chatTextSize by settings.chatTextSize.collectAsStateWithLifecycle()
    val chatWallpaper by settings.chatWallpaper.collectAsStateWithLifecycle()
    val chatDensity by settings.chatDensity.collectAsStateWithLifecycle()
    val weakDevice = remember { com.kirox.app.settings.prefersLightEffects(context) }
    val scope = rememberCoroutineScope()
    fun openChat(id: Long) { openChatId = id; telegramViewModel.messages.open(id); stack.push(Screen.Conversation) }
    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
    // t.me and tg: links open the chat inside KiroX; anything else (and unknown names) in the browser.
    val linkOpener: (String) -> Unit = remember(uriHandler) {
        { url ->
            val link = com.kirox.app.telegram.telegramLinkOf(url)
            if (link == null) runCatching { uriHandler.openUri(url) }
            else scope.launch {
                val chatId = telegramViewModel.resolveLink(link)
                if (chatId != null) openChat(chatId)
                else if (link is com.kirox.app.telegram.TelegramLink.Username) runCatching { uriHandler.openUri(url) }
            }
        }
    }

    LaunchedEffect(Unit) {
        phoneState = withContext(Dispatchers.Default) { PhoneEntryState.create(context) }
    }

    // TDLib drives the graph: Ready opens main, auth steps open the auth step screen.
    LaunchedEffect(authRevision) {
        when (telegramAuth) {
            TelegramAuthState.Ready -> if (stack.current.graph != NavGraph.Main) stack.resetTo(Screen.Chats)
            // Initializing/Closing also happen when TDLib reopens after a background pause: keep the screen.
            TelegramAuthState.Initializing, TelegramAuthState.Closing -> Unit
            TelegramAuthState.WaitPhoneNumber, is TelegramAuthState.Blocked ->
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
                            busy = telegramBusy,
                            onCancel = if (accounts.size > 1) tdClient::cancelNewAccount else null
                        )
                        Screen.Countries -> CountryPickerScreen(state, onBack = { stack.pop() })
                        Screen.AuthStep -> TelegramAuthStepScreen(telegramAuth, telegramViewModel.auth, telegramBusy, telegramError, recoveryCodeSent)
                        Screen.Chats -> CompositionLocalProvider(
                            LocalTelegramFiles provides telegramViewModel.files,
                            com.kirox.app.ui.home.LocalChatActivityLines provides telegramViewModel.activity.lines.collectAsStateWithLifecycle().value
                        ) {
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
                                chatTextSize = chatTextSize,
                                onChatTextSize = settings::setChatTextSize,
                                chatWallpaper = chatWallpaper,
                                onChatWallpaper = settings::setChatWallpaper,
                                chatDensity = chatDensity,
                                onChatDensity = settings::setChatDensity,
                                effectScene = effectScene,
                                onEffectScene = settings::setEffectScene,
                                effectsQuality = effectsQuality,
                                onEffectsQuality = settings::setEffectsQuality,
                                weakDevice = weakDevice,
                                onOpenSavedMessages = { scope.launch { runCatching { telegramViewModel.users.savedMessagesChatId() }.onSuccess(::openChat) } },
                                onLogOut = telegramViewModel.auth::logOut,
                                onStartCall = { userId, video -> withCallPermissions(video) { telegramViewModel.voip.start(userId, video) } },
                                onAddContact = telegramViewModel.users::addContact
                            )
                        }
                        Screen.Conversation -> CompositionLocalProvider(
                            LocalTelegramFiles provides telegramViewModel.files,
                            com.kirox.app.ui.chat.LocalLinkOpener provides linkOpener,
                            com.kirox.app.ui.chat.LocalAutoDownload provides autoDownloadRule
                        ) {
                            val chatId = openChatId
                            LaunchedEffect(chatId) { telegramViewModel.messages.open(chatId) }
                            DisposableEffect(chatId) { onDispose { telegramViewModel.messages.close(chatId) } }
                            val actions = remember { TelegramMessageActions(telegramViewModel.messages, telegramViewModel.activity) }
                            val activityLines by telegramViewModel.activity.lines.collectAsStateWithLifecycle()
                            val stickerSource = remember { TelegramStickerSource(telegramViewModel.stickers) }
                            TelegramChatScreen(
                                // A topic of this forum has the same chat id but belongs to the Thread screen.
                                history = if (history.chatId == chatId && history.threadTitle == null) history else TelegramChatHistory(chatId = chatId),
                                error = messageError,
                                onErrorShown = telegramViewModel.messages::clearError,
                                forwardTargets = chatLists.main.chats,
                                actions = actions,
                                onBack = { stack.pop() },
                                menu = chatMenu,
                                activityLine = activityLines[chatId],
                                stickers = stickerSource,
                                onOpenComments = { postId -> openThread = ThreadSpec.Comments(chatId, postId); stack.push(Screen.Thread) },
                                onOpenTopic = { topic -> openThread = ThreadSpec.Topic(chatId, topic.id, topic.title); stack.push(Screen.Thread) },
                                onCall = if (com.kirox.app.ui.call.callsEnabled) { video ->
                                    withCallPermissions(video) { telegramViewModel.voip.start(chatId, video) }
                                } else null,
                                onGroupCall = if (com.kirox.app.ui.call.callsEnabled) ({
                                    withCallPermissions(false) {
                                        scope.launch {
                                            runCatching { telegramViewModel.groupCalls.join(chatId) }
                                                .onSuccess { com.kirox.app.voip.GroupCallService.start(context) }
                                                .onFailure { groupCallNotice = it.message ?: "Не удалось войти в видеочат" }
                                        }
                                    }
                                }) else null
                            )
                        }
                        Screen.Thread -> CompositionLocalProvider(
                            LocalTelegramFiles provides telegramViewModel.files,
                            com.kirox.app.ui.chat.LocalLinkOpener provides linkOpener,
                            com.kirox.app.ui.chat.LocalAutoDownload provides autoDownloadRule
                        ) {
                            val spec = openThread
                            if (spec == null) {
                                LaunchedEffect(Unit) { stack.pop() }
                            } else {
                                LaunchedEffect(spec) {
                                    when (spec) {
                                        is ThreadSpec.Comments -> telegramViewModel.messages.openComments(spec.chatId, spec.postId)
                                        is ThreadSpec.Topic -> telegramViewModel.messages.openTopic(spec.chatId, spec.topicId, spec.title)
                                    }
                                }
                                DisposableEffect(spec) { onDispose { telegramViewModel.messages.closeThread() } }
                                val actions = remember { TelegramMessageActions(telegramViewModel.messages, telegramViewModel.activity) }
                                val stickerSource = remember { TelegramStickerSource(telegramViewModel.stickers) }
                                val title = when (spec) { is ThreadSpec.Comments -> "Комментарии"; is ThreadSpec.Topic -> spec.title }
                                TelegramChatScreen(
                                    history = if (history.threadTitle != null) history else TelegramChatHistory(threadTitle = title),
                                    error = messageError,
                                    onErrorShown = telegramViewModel.messages::clearError,
                                    forwardTargets = chatLists.main.chats,
                                    actions = actions,
                                    onBack = { stack.pop() },
                                    stickers = stickerSource
                                )
                            }
                        }
                    }
                }
            }
        }
        groupCall?.let { call ->
            CompositionLocalProvider(LocalTelegramFiles provides telegramViewModel.files) { com.kirox.app.ui.call.GroupCallScreen(call, groupCallActions) }
        }
        groupCallNotice?.let { text ->
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { groupCallNotice = null },
                text = { androidx.compose.material3.Text(text) },
                confirmButton = { androidx.compose.material3.TextButton(onClick = { groupCallNotice = null }) { androidx.compose.material3.Text("Понятно") } }
            )
        }
        activeCall?.let { call ->
            CompositionLocalProvider(LocalTelegramFiles provides telegramViewModel.files) { com.kirox.app.ui.call.CallScreen(call, permittedCallActions) }
        }
    }
}

private class TelegramMessageActions(
    private val repository: TelegramMessageRepository,
    private val activityRepository: com.kirox.app.telegram.TelegramChatActionRepository
) : TelegramChatActions {
    override fun loadOlder() = repository.loadOlder()
    override fun sendText(text: String, replyToMessageId: Long?) = repository.sendText(text, replyToMessageId)
    override fun sendPhoto(path: String, caption: String) = repository.sendPhoto(path, caption)
    override fun sendDocument(path: String, caption: String) = repository.sendDocument(path, caption)
    override fun sendVoice(path: String, durationSeconds: Int, waveform: ByteArray) = repository.sendVoice(path, durationSeconds, waveform)
    override fun toggleReaction(messageId: Long, emoji: String) = repository.toggleReaction(messageId, emoji)
    override fun sendVideo(path: String, caption: String, durationSeconds: Int, width: Int, height: Int) = repository.sendVideo(path, caption, durationSeconds, width, height)
    override fun edit(messageId: Long, text: String) = repository.edit(messageId, text)
    override fun delete(messageIds: List<Long>, forEveryone: Boolean) = repository.delete(messageIds, forEveryone)
    override fun forward(toChatId: Long, messageIds: List<Long>) = repository.forward(toChatId, messageIds)
    override fun saveDraft(chatId: Long, text: String, threadId: Long, forumTopicId: Int) = repository.saveDraft(chatId, text, threadId, forumTopicId)
    override fun setActivity(chatId: Long, activity: com.kirox.app.telegram.ChatActivity?) = activityRepository.send(chatId, activity)
    override fun sendAlbum(items: List<Pair<String, com.kirox.app.telegram.VideoMeta?>>, caption: String) = repository.sendAlbum(items, caption)
    override fun sendPanelItem(item: com.kirox.app.telegram.TelegramPanelItem, replyToMessageId: Long?) = repository.sendPanelItem(item, replyToMessageId)
    override fun sendVideoNote(path: String, durationSeconds: Int, length: Int) = repository.sendVideoNote(path, durationSeconds, length)
    override fun pin(messageId: Long) = repository.pin(messageId)
    override fun unpin(messageId: Long) = repository.unpin(messageId)
    override fun jumpTo(messageId: Long) = repository.jumpTo(messageId)
    override fun jumpDone() = repository.jumpDone()
    override suspend fun search(query: String, fromMessageId: Long) = repository.search(query, fromMessageId)
    override suspend fun availableReactions(messageId: Long) = repository.availableReactions(messageId)
    override suspend fun forumTopics(chatId: Long) = repository.forumTopics(chatId)
    override fun vote(messageId: Long, indices: List<Int>) = repository.vote(messageId, indices)
    override fun sendPoll(question: String, options: List<String>, anonymous: Boolean, multipleAnswers: Boolean, correctOption: Int?) = repository.sendPoll(question, options, anonymous, multipleAnswers, correctOption)
    override suspend fun pressButton(messageId: Long, button: com.kirox.app.telegram.TelegramButton) = repository.pressButton(messageId, button)
    override suspend fun translate(messageId: Long, language: String) = repository.translate(messageId, language)
}

private class TelegramStoryActionsImpl(private val repository: TelegramStoryRepository) : TelegramStoryActions {
    override suspend fun story(chatId: Long, storyId: Int): TelegramStory? = repository.story(chatId, storyId)
    override fun open(chatId: Long, storyId: Int) = repository.open(chatId, storyId)
    override fun close(chatId: Long, storyId: Int) = repository.close(chatId, storyId)
    override suspend fun react(chatId: Long, storyId: Int, emoji: String?) = repository.react(chatId, storyId, emoji)
    override suspend fun reply(chatId: Long, storyId: Int, text: String) = repository.reply(chatId, storyId, text)
    override suspend fun delete(chatId: Long, storyId: Int) = repository.delete(chatId, storyId)
    override suspend fun viewers(storyId: Int) = repository.viewers(storyId)
}

private class TelegramChatListActionsImpl(private val repository: TelegramChatRepository, private val messages: TelegramMessageRepository) : TelegramChatListActions {
    override suspend fun searchMessages(query: String) = repository.searchMessages(query)
    override fun jumpToMessage(messageId: Long) = messages.jumpTo(messageId)
    override fun loadMore(key: TelegramChatListKey) = repository.loadMore(key)
    override fun setArchived(chatId: Long, archived: Boolean) = repository.setArchived(chatId, archived)
    override fun setPinned(key: TelegramChatListKey, chatId: Long, pinned: Boolean) = repository.setPinned(key, chatId, pinned)
    override fun markRead(chatId: Long) = repository.markRead(chatId)
    override fun markUnread(chatId: Long) = repository.markUnread(chatId)
    override suspend fun search(query: String): List<TelegramChatItem> = repository.search(query)
}

private class TelegramStickerSource(private val repository: com.kirox.app.telegram.TelegramStickerRepository) : com.kirox.app.ui.chat.StickerPanelSource {
    override suspend fun installedSets() = repository.installedSets()
    override suspend fun set(id: Long) = repository.set(id)
    override suspend fun recent() = repository.recent()
    override suspend fun savedGifs() = repository.savedGifs()
}

/** What Screen.Thread shows. */
private sealed interface ThreadSpec {
    data class Comments(val chatId: Long, val postId: Long) : ThreadSpec
    data class Topic(val chatId: Long, val topicId: Int, val title: String) : ThreadSpec
}
