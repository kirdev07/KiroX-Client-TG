package com.kirox.app.ui.chat

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kirox.app.telegram.TelegramChatHistory
import com.kirox.app.telegram.TelegramChatItem
import com.kirox.app.telegram.TelegramDelivery
import com.kirox.app.telegram.TelegramMessage
import com.kirox.app.telegram.TelegramMessageKind
import com.kirox.app.telegram.TelegramPeerKind
import com.kirox.app.ui.components.TelegramAvatar
import com.kirox.app.ui.format.TelegramFormat
import com.kirox.app.ui.icons.KiroXIcon
import com.kirox.app.ui.theme.Fusion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Actions the real chat screen can perform; implemented by the message repository. */
/** Chat-level actions behind the ⋮ menu. */
interface TelegramChatMenuActions {
    suspend fun isMuted(chatId: Long): Boolean
    suspend fun setMuted(chatId: Long, muted: Boolean)
    suspend fun clearHistory(chatId: Long)
    suspend fun setAutoDelete(chatId: Long, seconds: Int)
    suspend fun deleteChat(chatId: Long)
}

interface TelegramChatActions {
    fun loadOlder()
    fun sendText(text: String, replyToMessageId: Long?)
    fun sendPhoto(path: String, caption: String)
    fun sendDocument(path: String, caption: String)
    fun sendVoice(path: String, durationSeconds: Int, waveform: ByteArray)
    fun toggleReaction(messageId: Long, emoji: String)
    fun sendVideo(path: String, caption: String, durationSeconds: Int, width: Int, height: Int)
    fun edit(messageId: Long, text: String)
    fun delete(messageIds: List<Long>, forEveryone: Boolean)
    fun forward(toChatId: Long, messageIds: List<Long>)
    /** Stores the unsent text as the chat's Telegram draft; blank text clears it. */
    fun saveDraft(chatId: Long, text: String) {}
    /** Our activity in the chat (typing, recording); null cancels it. */
    fun setActivity(chatId: Long, activity: com.kirox.app.telegram.ChatActivity?) {}
    /** 2–10 photos and videos as one album; a null meta marks a photo. */
    fun sendAlbum(items: List<Pair<String, com.kirox.app.telegram.VideoMeta?>>, caption: String) {}
    fun sendPanelItem(item: com.kirox.app.telegram.TelegramPanelItem, replyToMessageId: Long?) {}
    fun sendVideoNote(path: String, durationSeconds: Int, length: Int) {}
    fun pin(messageId: Long) {}
    fun unpin(messageId: Long) {}
    /** Loads history up to [messageId] and scrolls there (TelegramChatHistory.jumpTo). */
    fun jumpTo(messageId: Long) {}
    fun jumpDone() {}
    /** Server search in the chat: results newest first and the id to continue from (0 at the end). */
    suspend fun search(query: String, fromMessageId: Long): Pair<List<TelegramMessage>, Long> = emptyList<TelegramMessage>() to 0L
    suspend fun availableReactions(messageId: Long): List<String>? = null
    suspend fun forumTopics(chatId: Long): List<com.kirox.app.telegram.TelegramForumTopic> = emptyList()
    fun vote(messageId: Long, indices: List<Int>) {}
    fun sendPoll(question: String, options: List<String>, anonymous: Boolean, multipleAnswers: Boolean, correctOption: Int?) {}
    /** Bot's answer to an inline button: text to show and URL to open. */
    suspend fun pressButton(messageId: Long, button: com.kirox.app.telegram.TelegramButton): Pair<String, String?>? = null
    suspend fun translate(messageId: Long, language: String): String? = null
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TelegramChatScreen(
    history: TelegramChatHistory,
    error: String?,
    onErrorShown: () -> Unit,
    forwardTargets: List<TelegramChatItem>,
    actions: TelegramChatActions,
    onBack: () -> Unit,
    /** Starts a call with the peer of a private chat (its chat id is the user id). */
    onCall: ((isVideo: Boolean) -> Unit)? = null,
    menu: TelegramChatMenuActions? = null,
    /** What someone in this chat is doing now ("печатает…"); replaces the status line while set. */
    activityLine: String? = null,
    /** Stickers and GIFs for the panel; emoji only when null. */
    stickers: StickerPanelSource? = null,
    /** Opens the comments of a channel post (by its message id). */
    onOpenComments: ((Long) -> Unit)? = null,
    /** Opens a topic of the forum shown in this screen. */
    onOpenTopic: ((com.kirox.app.telegram.TelegramForumTopic) -> Unit)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val style = com.kirox.app.ui.theme.LocalChatStyle.current
    val appearance = remember(style) {
        ChatAppearance(bubbleRadiusDp = style.bubbleRadiusDp, textScale = style.textScale, messageSpacingDp = style.density.spacingDp, bubblePaddingDp = style.density.paddingDp)
    }
    var input by rememberSaveable(history.chatId, stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue()) }
    var replyTo by remember(history.chatId) { mutableStateOf<MessageUi?>(null) }
    var editing by remember(history.chatId) { mutableStateOf<MessageUi?>(null) }
    var selected by remember { mutableStateOf<MessageUi?>(null) }
    var deleting by remember { mutableStateOf<MessageUi?>(null) }
    var forwarding by remember { mutableStateOf<List<Long>?>(null) }
    /** Multi-select: ids of selected messages; empty when not selecting. */
    var selectedIds by remember(history.chatId) { mutableStateOf(setOf<Long>()) }
    var deletingMany by remember { mutableStateOf<List<Long>?>(null) }
    var highlighted by remember(history.chatId) { mutableStateOf(0L) }
    var reactionChoices by remember { mutableStateOf(DEFAULT_REACTIONS) }
    var attachments by rememberSaveable { mutableStateOf(false) }
    var stickerPanel by rememberSaveable { mutableStateOf(false) }
    var showBotKeyboard by remember(history.chatId) { mutableStateOf(true) }
    var creatingPoll by remember { mutableStateOf(false) }
    var translation by remember { mutableStateOf<Pair<String, String?>?>(null) }
    var notice by remember { mutableStateOf<String?>(null) }
    var videoNote by remember { mutableStateOf(false) }
    val noteCameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
        if (granted.values.all { it }) videoNote = true else notice = "Для видеосообщения нужны камера и микрофон"
    }
    val listState = rememberLazyListState()

    val isGroup = history.peerKind == TelegramPeerKind.Group
    // Newest first for reverseLayout; date separators are inserted as system rows.
    val recorder = remember { VoiceRecorder(context) }
    var recording by remember { mutableStateOf(false) }
    var recordLevel by remember { mutableStateOf(0f) }
    var recordMs by remember { mutableStateOf(0L) }
    var recordPaused by remember { mutableStateOf(false) }
    fun startRecording() {
        if (recorder.start()) { recording = true; recordMs = 0 } else notice = "Не удалось начать запись"
    }
    val micPermission = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) startRecording() else notice = "Без доступа к микрофону голосовое не записать" }
    LaunchedEffect(recording) {
        val chatId = history.chatId
        while (recording) {
            recordLevel = recorder.sampleLevel()
            recordMs = recorder.elapsedMs
            // Throttled by the repository to one request every few seconds.
            if (chatId != 0L) actions.setActivity(chatId, com.kirox.app.telegram.ChatActivity.RecordingVoice)
            kotlinx.coroutines.delay(100)
        }
        if (chatId != 0L) actions.setActivity(chatId, null)
    }
    DisposableEffect(Unit) { onDispose { recorder.stop(discard = true); VoicePlayer.stop() } }
    // Drafts: the chat's Telegram draft fills an empty input once; unsent text is saved back when
    // leaving the chat or the app. Text being edited is an existing message, not a draft.
    var draftLoaded by remember(history.chatId) { mutableStateOf(false) }
    LaunchedEffect(history.chatId, history.draft) {
        if (history.chatId == 0L || draftLoaded) return@LaunchedEffect
        draftLoaded = true
        if (input.text.isEmpty() && history.draft.isNotEmpty()) input = TextFieldValue(history.draft, androidx.compose.ui.text.TextRange(history.draft.length))
    }
    // Last applied state; on a chat switch the old chat's onDispose runs before this is updated,
    // so it still holds that chat's text and not the next chat's input.
    val pendingDraft = remember { DraftHolder() }
    SideEffect {
        pendingDraft.chatId = history.chatId
        pendingDraft.text = if (editing == null && draftLoaded) input.text else null
    }
    // Another chat or the background: a voice note does not keep playing, an unsent recording is dropped.
    DisposableEffect(history.chatId) {
        onDispose { VoicePlayer.stop(); pendingDraft.save(actions) }
    }
    androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_STOP) {
        VoicePlayer.stop()
        if (recording) { recorder.stop(discard = true); recording = false }
        pendingDraft.save(actions)
    }
    var menuOpen by remember { mutableStateOf(false) }
    var searching by remember(history.chatId) { mutableStateOf(false) }
    var searchQuery by remember(history.chatId) { mutableStateOf("") }
    var muted by remember(history.chatId) { mutableStateOf<Boolean?>(null) }
    var confirmClear by remember { mutableStateOf(false) }
    var autoDeleteOpen by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    // Search filters the loaded part of the conversation.
    val shownMessages = remember(history.messages, searchQuery) {
        val q = searchQuery.trim()
        if (q.isEmpty()) history.messages else history.messages.filter { it.text.contains(q, ignoreCase = true) }
    }
    // Server search: finds messages that are not loaded yet; tapping one scrolls the chat to it.
    var searchResults by remember(history.chatId) { mutableStateOf<List<TelegramMessage>?>(null) }
    var searchNext by remember(history.chatId) { mutableStateOf(0L) }
    LaunchedEffect(searchQuery, searching) {
        val q = searchQuery.trim()
        searchResults = null
        searchNext = 0
        if (!searching || q.length < 2) return@LaunchedEffect
        kotlinx.coroutines.delay(400)
        runCatching { actions.search(q, 0) }
            .onSuccess { (found, next) -> searchResults = found; searchNext = next }
            .onFailure { searchResults = emptyList() }
    }
    val rows = remember(shownMessages) { toRows(shownMessages.asReversed()) }
    val byId = remember(history.messages) { history.messages.associateBy { it.id } }

    LaunchedEffect(error) { if (error != null) { notice = error; onErrorShown() } }
    LaunchedEffect(history.jumpTo, rows) {
        val target = history.jumpTo
        if (target == 0L) return@LaunchedEffect
        val index = rows.indexOfFirst { it.key == "m$target" }
        if (index >= 0) {
            listState.animateScrollToItem(index)
            highlighted = target
            actions.jumpDone()
            kotlinx.coroutines.delay(1600)
            if (highlighted == target) highlighted = 0
        }
    }
    LaunchedEffect(listState, history.chatId) {
        snapshotFlow { listState.layoutInfo.let { (it.visibleItemsInfo.lastOrNull()?.index ?: 0) >= it.totalItemsCount - 15 } }
            .distinctUntilChanged().filter { it }.collect { actions.loadOlder() }
    }
    // Keep the newest message in view when it arrives while the user is at the bottom.
    LaunchedEffect(history.messages.lastOrNull()?.id) {
        val newest = history.messages.lastOrNull() ?: return@LaunchedEffect
        if (newest.isOutgoing || listState.firstVisibleItemIndex <= 3) listState.animateScrollToItem(0)
    }

    BackHandler(enabled = editing != null || replyTo != null) { editing = null; replyTo = null; input = TextFieldValue() }
    BackHandler(enabled = selectedIds.isNotEmpty()) { selectedIds = emptySet() }

    fun sendPicked(uri: Uri, asPhoto: Boolean) {
        scope.launch {
            val path = withContext(Dispatchers.IO) { copyToCache(context, uri) }
            if (path == null) { notice = "Не удалось прочитать файл"; return@launch }
            val caption = input.text.trim()
            val isVideo = context.contentResolver.getType(uri)?.startsWith("video/") == true
            when {
                isVideo && asPhoto -> {
                    // Duration and size from the file itself; TDLib makes the preview.
                    val meta = withContext(Dispatchers.IO) { videoMeta(path) }
                    actions.sendVideo(path, caption, meta.first, meta.second, meta.third)
                }
                asPhoto -> actions.sendPhoto(path, caption)
                else -> actions.sendDocument(path, caption)
            }
            input = TextFieldValue()
        }
    }
    val files = com.kirox.app.ui.components.LocalTelegramFiles.current
    val linkOpener = LocalLinkOpener.current
    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
    /** Downloads the message's file if needed, then copies it to Downloads/KiroX. */
    fun saveMedia(message: MessageUi) {
        val file = message.media?.file ?: message.photo ?: return
        val name = message.media?.title?.takeIf { message.kind == MessageKind.Document || message.kind == MessageKind.Audio }.orEmpty()
        val mime = message.media?.mimeType?.ifBlank { null } ?: if (message.kind == MessageKind.Photo) "image/jpeg" else ""
        if (files == null) return
        scope.launch {
            notice = "Сохраняю…"
            files.download(file)
            // GetFile and DownloadFile answers can arrive in either order: wait for the file itself.
            val path = kotlinx.coroutines.withTimeoutOrNull(5 * 60_000L) { files.state(file).first { it.path != null } }?.path
            notice = when {
                path == null -> "Не удалось скачать файл"
                saveToDownloads(context, path, name, mime) -> "Сохранено в «Загрузки/KiroX»"
                else -> "Не удалось сохранить файл"
            }
        }
    }
    fun sendAlbum(uris: List<Uri>) {
        scope.launch {
            val items = withContext(Dispatchers.IO) {
                uris.mapNotNull { uri ->
                    val path = copyToCache(context, uri) ?: return@mapNotNull null
                    val isVideo = context.contentResolver.getType(uri)?.startsWith("video/") == true
                    path to if (isVideo) videoMeta(path).let { (d, w, h) -> com.kirox.app.telegram.VideoMeta(d, w, h) } else null
                }
            }
            when {
                items.isEmpty() -> { notice = "Не удалось прочитать файлы"; return@launch }
                items.size < uris.size -> notice = "Часть файлов не удалось прочитать"
            }
            actions.sendAlbum(items, input.text.trim())
            input = TextFieldValue()
        }
    }
    // Gallery: photos and videos; several are sent as one album, like in Telegram.
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(ALBUM_LIMIT)) { uris ->
        when {
            uris.size == 1 -> sendPicked(uris[0], asPhoto = true)
            uris.size > 1 -> sendAlbum(uris)
        }
    }
    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> uri?.let { sendPicked(it, asPhoto = false) } }

    val peer = ChatPeerUi(
        id = history.chatId,
        name = history.threadTitle ?: if (history.peerKind == TelegramPeerKind.Saved) "Избранное" else history.title,
        status = activityLine ?: if (history.threadTitle != null) history.title else if (history.isForum) "темы" else when (history.peerKind) {
            TelegramPeerKind.User -> (if (history.isSecret) "🔒 секретный чат · " else "") + history.peerStatus?.let { TelegramFormat.status(it) }.orEmpty()
            TelegramPeerKind.Bot -> "бот"
            TelegramPeerKind.Group -> "группа"
            TelegramPeerKind.Channel -> "канал"
            else -> ""
        },
        initial = history.title.take(1).uppercase(),
        photo = history.photo,
        canVoiceCall = onCall != null && history.peerKind == TelegramPeerKind.User && !history.isSecret,
        canVideoCall = onCall != null && history.peerKind == TelegramPeerKind.User && !history.isSecret
    )

    Scaffold(
        containerColor = Fusion.colors.background,
        contentWindowInsets = WindowInsets.statusBars,
        topBar = {
            if (selectedIds.isNotEmpty()) SelectionBar(
                count = selectedIds.size,
                onClose = { selectedIds = emptySet() },
                onCopy = {
                    val text = history.messages.filter { it.id in selectedIds && it.text.isNotBlank() }.joinToString("\n\n") { it.text }
                    if (text.isNotEmpty()) { clipboard.setText(AnnotatedString(text)); notice = "Скопировано" }
                    selectedIds = emptySet()
                },
                onForward = { forwarding = selectedIds.sorted(); },
                onDelete = { deletingMany = selectedIds.sorted() }
            ) else if (searching) Column(Modifier.statusBarsPadding()) {
                ChatSearchField(searchQuery, { searchQuery = it }, onClose = { searching = false; searchQuery = "" })
            } else Column {
                ChatHeader(peer, onBack, onProfile = {}, onOverflow = {
                if (menu == null) notice = "Меню чата недоступно" else {
                    menuOpen = true
                    scope.launch { muted = runCatching { menu.isMuted(history.chatId) }.getOrNull() }
                }
            }, onCall = { video -> onCall?.invoke(video) })
                history.pinned?.let { pinned -> PinnedBar(pinned, count = history.pinnedIds.size) { actions.jumpTo(pinned.id) } }
            }
        },
        bottomBar = {
            if (recording) VoiceRecordingBar(
                elapsedMs = recordMs,
                level = recordLevel,
                paused = recordPaused,
                onPause = { if (recorder.isPaused) recorder.resume() else recorder.pause(); recordPaused = recorder.isPaused },
                onCancel = { recorder.stop(discard = true); recording = false; recordPaused = false },
                onSend = {
                    val note = recorder.stop()
                    recording = false
                    recordPaused = false
                    if (note != null) actions.sendVoice(note.file.absolutePath, note.durationSeconds, note.waveform) else notice = "Слишком короткое голосовое"
                }
            ) else if ((history.peerKind != TelegramPeerKind.Channel || history.threadTitle != null) && !history.isForum) Column {
                if (history.replyKeyboard.isNotEmpty() && showBotKeyboard) BotKeyboard(history.replyKeyboard) { text -> actions.sendText(text, null) }
                if (history.replyKeyboard.isNotEmpty()) Text(
                    if (showBotKeyboard) "Скрыть клавиатуру бота" else "Клавиатура бота",
                    color = Fusion.colors.textSecondary, fontSize = 12.sp,
                    modifier = Modifier.fillMaxWidth().background(Fusion.colors.background).clickable { showBotKeyboard = !showBotKeyboard }.padding(vertical = 6.dp),
                    textAlign = TextAlign.Center
                )
                ChatComposer(
                value = input,
                reply = replyTo,
                editing = editing,
                onValueChange = { value ->
                    // Typing is reported only for new text, not for edits or cursor moves.
                    if (editing == null && value.text != input.text && history.chatId != 0L && history.peerKind != TelegramPeerKind.Saved) {
                        actions.setActivity(history.chatId, if (value.text.isEmpty()) null else com.kirox.app.telegram.ChatActivity.Typing)
                    }
                    input = value
                },
                onCancelContext = { replyTo = null; editing = null; input = TextFieldValue() },
                onAttach = { attachments = true },
                onEmoji = { stickerPanel = true },
                onSend = {
                    val text = input.text.trim()
                    if (text.isNotEmpty()) {
                        val edited = editing
                        if (edited != null) actions.edit(edited.id, text) else actions.sendText(text, replyTo?.id)
                        input = TextFieldValue(); replyTo = null; editing = null
                    }
                },
                onMic = {
                    if (androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED) startRecording()
                    else micPermission.launch(android.Manifest.permission.RECORD_AUDIO)
                },
                appearance = appearance
            )
            }
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            com.kirox.app.ui.components.ChatWallpaperLayer(style.wallpaper, Modifier.matchParentSize())
            val results = searchResults
            if (history.isForum) ForumTopicsList(history.chatId, actions, onOpen = { topic -> onOpenTopic?.invoke(topic) })
            else if (searching && results != null) SearchResults(
                results, canLoadMore = searchNext != 0L,
                onLoadMore = {
                    val q = searchQuery.trim()
                    val from = searchNext
                    scope.launch { runCatching { actions.search(q, from) }.onSuccess { (more, next) -> searchResults = results + more; searchNext = next } }
                },
                onOpen = { message -> searching = false; searchQuery = ""; actions.jumpTo(message.id) }
            ) else if (history.messages.isEmpty()) {
                Box(Modifier.fillMaxSize(), Alignment.Center) {
                    if (history.reachedStart) Text("Сообщений пока нет", color = Fusion.colors.textSecondary)
                    else CircularProgressIndicator(Modifier.size(26.dp), color = Fusion.colors.textSecondary, strokeWidth = 2.dp)
                }
            } else if (shownMessages.isEmpty()) {
                Box(Modifier.fillMaxSize(), Alignment.Center) { Text("Ничего не найдено", color = Fusion.colors.textSecondary) }
            } else LazyColumn(
                state = listState,
                reverseLayout = true,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(appearance.messageSpacingDp.dp)
            ) {
                itemsIndexed(rows, key = { _, row -> row.key }, contentType = { _, row -> row.message.direction }) { index, row ->
                    // reverseLayout: the visually previous row is index + 1.
                    val previous = rows.getOrNull(index + 1)?.message
                    val grouped = previous != null && previous.direction == row.message.direction && previous.author == row.message.author && row.message.direction != MessageDirection.System
                    MessageRow(
                        row.message, grouped, appearance,
                        pinned = row.message.id in history.pinnedIds,
                        showAuthor = isGroup,
                        onLongClick = {
                            if (row.message.direction == MessageDirection.System) Unit
                            else if (selectedIds.isNotEmpty()) selectedIds = selectedIds.toggle(row.message.id)
                            else selected = row.message
                        },
                        onReply = { replyTo = row.message; editing = null },
                        onNotice = { notice = it },
                        selected = row.message.id in selectedIds,
                        onClick = if (selectedIds.isNotEmpty() && row.message.direction != MessageDirection.System) ({ selectedIds = selectedIds.toggle(row.message.id) }) else null,
                        onQuote = { id -> actions.jumpTo(id) },
                        highlighted = row.message.id == highlighted,
                        onVote = { indices -> actions.vote(row.message.id, indices) },
                        onButton = { button ->
                            val url = button.url
                            if (url != null) linkOpener?.invoke(url) ?: runCatching { uriHandler.openUri(url) }
                            else scope.launch {
                                val answer = runCatching { actions.pressButton(row.message.id, button) }.getOrNull()
                                answer?.first?.takeIf { it.isNotBlank() }?.let { notice = it }
                                answer?.second?.let { link -> linkOpener?.invoke(link) ?: runCatching { uriHandler.openUri(link) } }
                            }
                        },
                        onComments = if (history.peerKind == TelegramPeerKind.Channel && history.threadTitle == null && onOpenComments != null) ({ onOpenComments(row.message.id) }) else null
                    )
                }
                if (history.loadingOlder) item(key = "loading") {
                    Box(Modifier.fillMaxWidth().padding(12.dp), Alignment.Center) { CircularProgressIndicator(Modifier.size(20.dp), color = Fusion.colors.textSecondary, strokeWidth = 2.dp) }
                }
            }
            notice?.let { message ->
                Text(
                    message, color = Fusion.colors.textPrimary, fontSize = 13.sp,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp).clip(RoundedCornerShape(12.dp)).background(Fusion.colors.surface3).padding(horizontal = 16.dp, vertical = 11.dp)
                )
                LaunchedEffect(message) { kotlinx.coroutines.delay(2200); notice = null }
            }
        }
    }

    if (attachments) AttachmentSheet(
        onDismiss = { attachments = false },
        onPick = { choice ->
            attachments = false
            when (choice) {
                "Фото и видео" -> pickPhoto.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo))
                "Опрос" -> creatingPoll = true
                "Видеосообщение" -> {
                    val needed = arrayOf(android.Manifest.permission.CAMERA, android.Manifest.permission.RECORD_AUDIO)
                    if (needed.all { context.checkSelfPermission(it) == android.content.pm.PackageManager.PERMISSION_GRANTED }) videoNote = true
                    else noteCameraPermission.launch(needed)
                }
                else -> pickFile.launch("*/*")
            }
        },
        actions = buildList {
            add(KiroXIcon.Media to "Фото и видео")
            if (history.peerKind != TelegramPeerKind.Channel) add(KiroXIcon.Camera to "Видеосообщение")
            add(KiroXIcon.File to "Файл")
            if (!history.isSecret) add(KiroXIcon.Sliders to "Опрос")
        },
        footer = if (input.text.isNotBlank()) "Текст из поля ввода станет подписью" else null
    )
    selected?.let { message ->
        val source = byId[message.id]
        LaunchedEffect(message.id) {
            reactionChoices = DEFAULT_REACTIONS
            actions.availableReactions(message.id)?.takeIf { it.isNotEmpty() }?.let { reactionChoices = it }
        }
        MessageActionsSheet(
            message,
            onDismiss = { selected = null },
            onReply = { replyTo = message; editing = null; selected = null },
            onReaction = { emoji -> actions.toggleReaction(message.id, emoji); selected = null },
            onAction = { action ->
                selected = null
                when (action) {
                    "Копировать" -> { clipboard.setText(AnnotatedString(message.text)); notice = "Сообщение скопировано" }
                    "Редактировать" -> { editing = message; replyTo = null; input = TextFieldValue(message.text) }
                    "Удалить" -> deleting = message
                    "Переслать" -> forwarding = listOf(message.id)
                    "Сохранить" -> saveMedia(message)
                    "Закрепить" -> { actions.pin(message.id); notice = "Сообщение закреплено" }
                    "Открепить" -> actions.unpin(message.id)
                    "Выбрать" -> selectedIds = setOf(message.id)
                    "Перевести" -> {
                        translation = message.text to null
                        scope.launch {
                            val language = Locale.getDefault().language.ifBlank { "ru" }
                            translation = message.text to (actions.translate(message.id, language) ?: "Перевод недоступен")
                        }
                    }
                }
            },
            reactions = history.peerKind != TelegramPeerKind.Saved,
            isPinned = message.id in history.pinnedIds,
            reactionChoices = reactionChoices,
            hidden = buildSet {
                if (history.isSecret) add("Закрепить")
                if (source?.canEdit != true) add("Редактировать")
                if (source?.delivery == TelegramDelivery.Sending) { add("Переслать"); add("Ответить") }
            }
        )
    }
    if (creatingPoll) PollCreateDialog(onDismiss = { creatingPoll = false }) { question, options, anonymous, multiple, correct ->
        creatingPoll = false
        actions.sendPoll(question, options, anonymous, multiple, correct)
    }
    translation?.let { (original, translated) ->
        AlertDialog(
            onDismissRequest = { translation = null },
            containerColor = Fusion.colors.surface2,
            title = { Text("Перевод", color = Fusion.colors.textPrimary) },
            text = {
                Column(Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState())) {
                    if (translated == null) CircularProgressIndicator(Modifier.size(22.dp), color = Fusion.colors.textPrimary, strokeWidth = 2.dp)
                    else Text(translated, color = Fusion.colors.textPrimary, fontSize = 15.sp)
                    Spacer(Modifier.height(12.dp))
                    Text(original, color = Fusion.colors.textSecondary, fontSize = 13.sp)
                }
            },
            confirmButton = {
                Row {
                    if (translated != null) TextButton(onClick = { clipboard.setText(AnnotatedString(translated)); notice = "Перевод скопирован"; translation = null }) { Text("Копировать", color = Fusion.colors.textPrimary) }
                    TextButton(onClick = { translation = null }) { Text("Закрыть", color = Fusion.colors.textSecondary) }
                }
            }
        )
    }
    if (videoNote) {
        val chatId = history.chatId
        LaunchedEffect(Unit) { actions.setActivity(chatId, com.kirox.app.telegram.ChatActivity.RecordingVideoNote) }
        VideoNoteRecorder(
            onDone = { note -> videoNote = false; actions.sendVideoNote(note.file.absolutePath, note.durationSeconds, note.length) },
            onCancel = { videoNote = false; actions.setActivity(chatId, null) },
            onError = { message -> videoNote = false; actions.setActivity(chatId, null); notice = message }
        )
    }
    if (stickerPanel) StickerPanel(
        source = stickers,
        onEmoji = { emoji ->
            // Inserted at the cursor, replacing a selection, like a keyboard would.
            val start = minOf(input.selection.start, input.selection.end)
            val end = maxOf(input.selection.start, input.selection.end)
            val text = input.text.replaceRange(start, end, emoji)
            input = TextFieldValue(text, androidx.compose.ui.text.TextRange(start + emoji.length))
        },
        onSend = { item ->
            actions.sendPanelItem(item, replyTo?.id)
            replyTo = null
            stickerPanel = false
        },
        onDismiss = { stickerPanel = false }
    )
    deleting?.let { message ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            containerColor = Fusion.colors.surface2,
            title = { Text("Удалить сообщение?", color = Fusion.colors.textPrimary) },
            text = {
                Text(
                    when {
                        history.peerKind == TelegramPeerKind.Saved -> "Сообщение будет удалено из «Избранного»."
                        message.direction == MessageDirection.Outgoing -> "Можно удалить только у себя или у всех участников."
                        else -> "Сообщение будет удалено у вас."
                    },
                    color = Fusion.colors.textSecondary
                )
            },
            confirmButton = {
                Row {
                    if (message.direction == MessageDirection.Outgoing && history.peerKind != TelegramPeerKind.Saved) {
                        TextButton(onClick = { actions.delete(listOf(message.id), forEveryone = true); deleting = null }) { Text("У всех", color = Fusion.colors.danger) }
                    }
                    TextButton(onClick = { actions.delete(listOf(message.id), forEveryone = false); deleting = null }) { Text("У меня", color = Fusion.colors.danger) }
                }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Отмена", color = Fusion.colors.textPrimary) } }
        )
    }
    deletingMany?.let { ids ->
        val messages = history.messages.filter { it.id in ids }
        val allOwn = messages.isNotEmpty() && messages.all { it.isOutgoing } && history.peerKind != TelegramPeerKind.Saved
        AlertDialog(
            onDismissRequest = { deletingMany = null },
            containerColor = Fusion.colors.surface2,
            title = { Text("Удалить ${ids.size} ${messageWord(ids.size)}?", color = Fusion.colors.textPrimary) },
            text = { Text(if (allOwn) "Можно удалить только у себя или у всех участников." else "Сообщения будут удалены у вас.", color = Fusion.colors.textSecondary) },
            confirmButton = {
                Row {
                    if (allOwn) TextButton(onClick = { actions.delete(ids, forEveryone = true); deletingMany = null; selectedIds = emptySet() }) { Text("У всех", color = Fusion.colors.danger) }
                    TextButton(onClick = { actions.delete(ids, forEveryone = false); deletingMany = null; selectedIds = emptySet() }) { Text("У меня", color = Fusion.colors.danger) }
                }
            },
            dismissButton = { TextButton(onClick = { deletingMany = null }) { Text("Отмена", color = Fusion.colors.textPrimary) } }
        )
    }
    forwarding?.let { forwardIds ->
        ModalBottomSheet(onDismissRequest = { forwarding = null }, containerColor = Fusion.colors.surface1) {
            Text("Переслать в…", color = Fusion.colors.textPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 480.dp)) {
                items(forwardTargets, key = { it.id }) { target ->
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            actions.forward(target.id, forwardIds); forwarding = null; selectedIds = emptySet(); notice = "Переслано в «${target.title}»"
                        }.heightIn(min = 60.dp).padding(horizontal = 20.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TelegramAvatar(target.title, target.photo, 40.dp)
                        Spacer(Modifier.width(12.dp))
                        Text(target.title, color = Fusion.colors.textPrimary, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (forwardTargets.isEmpty()) item { Text("Нет доступных чатов", color = Fusion.colors.textSecondary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(24.dp)) }
            }
            Spacer(Modifier.navigationBarsPadding().height(8.dp))
        }
    }
    if (menuOpen && menu != null) {
        ModalBottomSheet(onDismissRequest = { menuOpen = false }, containerColor = Fusion.colors.surface1) {
            val items = buildList {
                add(Triple(KiroXIcon.Bell, if (muted == true) "Включить звук" else "Без звука", false))
                add(Triple(KiroXIcon.Search, "Поиск", false))
                if (history.peerKind == TelegramPeerKind.User) add(Triple(KiroXIcon.Refresh, "Автоудаление", false))
                add(Triple(KiroXIcon.Delete, "Очистить историю", true))
                add(Triple(KiroXIcon.Delete, if (history.peerKind == TelegramPeerKind.User || history.peerKind == TelegramPeerKind.Bot) "Удалить чат" else "Покинуть", true))
            }
            items.forEach { (icon, title, danger) ->
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable {
                        menuOpen = false
                        when (title) {
                            "Поиск" -> searching = true
                            "Очистить историю" -> confirmClear = true
                            "Автоудаление" -> autoDeleteOpen = true
                            "Удалить чат", "Покинуть" -> confirmDelete = true
                            else -> {
                                val target = muted != true
                                scope.launch {
                                    runCatching { menu.setMuted(history.chatId, target) }
                                        .onSuccess { notice = if (target) "Уведомления выключены" else "Уведомления включены" }
                                        .onFailure { notice = "Не удалось изменить уведомления" }
                                }
                            }
                        }
                    }.padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    com.kirox.app.ui.icons.KiroXIcon(icon, null, Modifier.size(22.dp), if (danger) Fusion.colors.danger else Fusion.colors.textPrimary)
                    Spacer(Modifier.width(16.dp))
                    Text(title, color = if (danger) Fusion.colors.danger else Fusion.colors.textPrimary, fontSize = 15.sp)
                }
            }
            Spacer(Modifier.navigationBarsPadding().height(8.dp))
        }
    }
    if (autoDeleteOpen && menu != null) AlertDialog(
        onDismissRequest = { autoDeleteOpen = false },
        containerColor = Fusion.colors.surface1,
        title = { Text("Автоудаление сообщений", color = Fusion.colors.textPrimary) },
        text = {
            Column {
                listOf(0 to "Выключено", 60 to "1 минута", 3600 to "1 час", 86400 to "1 день", 604800 to "1 неделя").forEach { (seconds, label) ->
                    Text(label, color = Fusion.colors.textPrimary, fontSize = 16.sp, modifier = Modifier.fillMaxWidth().clickable {
                        autoDeleteOpen = false
                        scope.launch {
                            runCatching { menu.setAutoDelete(history.chatId, seconds) }
                                .onSuccess { notice = if (seconds == 0) "Автоудаление выключено" else "Новые сообщения удалятся через: $label" }
                                .onFailure { notice = "Не удалось изменить автоудаление" }
                        }
                    }.padding(vertical = 12.dp))
                }
            }
        },
        confirmButton = { TextButton(onClick = { autoDeleteOpen = false }) { Text("Отмена", color = Fusion.colors.textSecondary) } }
    )
    if (confirmClear && menu != null) AlertDialog(
        onDismissRequest = { confirmClear = false },
        containerColor = Fusion.colors.surface1,
        title = { Text("Очистить историю?", color = Fusion.colors.textPrimary) },
        text = { Text("Сообщения удалятся только у вас.", color = Fusion.colors.textSecondary) },
        confirmButton = {
            TextButton(onClick = {
                confirmClear = false
                scope.launch { runCatching { menu.clearHistory(history.chatId) }.onFailure { notice = "Не удалось очистить историю" } }
            }) { Text("Очистить", color = Fusion.colors.danger) }
        },
        dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Отмена", color = Fusion.colors.textSecondary) } }
    )
    if (confirmDelete && menu != null) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        containerColor = Fusion.colors.surface1,
        title = { Text(if (history.peerKind == TelegramPeerKind.User || history.peerKind == TelegramPeerKind.Bot) "Удалить чат?" else "Покинуть «${history.title}»?", color = Fusion.colors.textPrimary) },
        text = { Text("Чат пропадёт из списка.", color = Fusion.colors.textSecondary) },
        confirmButton = {
            TextButton(onClick = {
                confirmDelete = false
                scope.launch {
                    runCatching { menu.deleteChat(history.chatId) }.onSuccess { onBack() }.onFailure { notice = "Не удалось удалить чат" }
                }
            }) { Text("Удалить", color = Fusion.colors.danger) }
        },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Отмена", color = Fusion.colors.textSecondary) } }
    )
}

private data class ChatRow(val key: String, val message: MessageUi)

/** [newestFirst] messages → rows with a date separator after the last message of each day. */
private fun toRows(newestFirst: List<TelegramMessage>): List<ChatRow> {
    val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    val dayFormat = SimpleDateFormat("d MMMM yyyy", Locale.forLanguageTag("ru"))
    val rows = ArrayList<ChatRow>(newestFirst.size + 8)
    newestFirst.forEachIndexed { index, message ->
        rows += ChatRow("m${message.id}", message.toUi(timeFormat.format(Date(message.date * 1000L))))
        val older = newestFirst.getOrNull(index + 1)
        val day = dayFormat.format(Date(message.date * 1000L))
        if (older == null || dayFormat.format(Date(older.date * 1000L)) != day) {
            rows += ChatRow("d$day", MessageUi(-message.id, "", MessageDirection.System, text = day, time = ""))
        }
    }
    return rows
}

internal fun TelegramMessage.toUi(time: String) = MessageUi(
    id = id,
    author = senderName,
    direction = if (isOutgoing) MessageDirection.Outgoing else MessageDirection.Incoming,
    kind = when (kind) {
        TelegramMessageKind.Photo -> MessageKind.Photo
        TelegramMessageKind.Video -> MessageKind.Video
        TelegramMessageKind.Animation -> MessageKind.Animation
        TelegramMessageKind.VideoNote -> MessageKind.VideoNote
        TelegramMessageKind.Voice -> MessageKind.Voice
        TelegramMessageKind.Audio -> MessageKind.Audio
        TelegramMessageKind.Document -> MessageKind.Document
        TelegramMessageKind.Sticker -> MessageKind.Sticker
        TelegramMessageKind.Text, TelegramMessageKind.Other -> MessageKind.Text
    },
    text = text,
    time = time,
    delivery = when (delivery) {
        TelegramDelivery.Sending -> DeliveryState.Sending
        TelegramDelivery.Failed -> DeliveryState.Failed
        TelegramDelivery.Read -> DeliveryState.Read
        TelegramDelivery.Sent -> DeliveryState.Sent
        TelegramDelivery.None -> DeliveryState.None
    },
    quote = replyText?.let { QuoteUi(replyAuthor.orEmpty(), it, replyToMessageId) },
    forwardedFrom = forwardedFrom,
    reactions = reactions.map { (emoji, count, chosen) -> ReactionUi(emoji, count, chosen) },
    canEdit = canEdit,
    photo = photo,
    photoAspect = photoAspect,
    edited = edited,
    media = media,
    thumbnail = thumbnail,
    entities = entities,
    commentCount = commentCount,
    poll = poll,
    buttons = buttons
)

/** Copies a picked content URI into the app cache so TDLib can upload it from a plain path. */
internal fun copyToCache(context: Context, uri: Uri): String? = runCatching {
    val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0) else null
    }?.replace(Regex("[\\\\/:*?\"<>|]"), "_")?.takeIf { it.isNotBlank() } ?: "file"
    val dir = File(context.cacheDir, "outgoing/${System.currentTimeMillis()}").apply { mkdirs() }
    val target = File(dir, name)
    context.contentResolver.openInputStream(uri)?.use { input -> target.outputStream().use { input.copyTo(it) } } ?: return null
    target.absolutePath
}.getOrNull()

/** Shown instead of the composer while a voice note is being recorded. */
@Composable
private fun VoiceRecordingBar(elapsedMs: Long, level: Float, paused: Boolean, onPause: () -> Unit, onCancel: () -> Unit, onSend: () -> Unit) {
    Surface(color = Fusion.colors.background) {
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 10.dp, vertical = 8.dp)
                .clip(RoundedCornerShape(26.dp)).background(Fusion.colors.surface1).padding(start = 18.dp, end = 6.dp).heightIn(min = 52.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size((10 + 8 * level).dp).clip(androidx.compose.foundation.shape.CircleShape).background(if (paused) Fusion.colors.textTertiary else Fusion.colors.danger))
            Spacer(Modifier.width(12.dp))
            Text("%d:%02d".format(elapsedMs / 60000, elapsedMs / 1000 % 60), color = Fusion.colors.textPrimary, fontSize = 16.sp)
            Spacer(Modifier.weight(1f))
            Text(
                if (paused) "▶" else "❚❚", color = Fusion.colors.textPrimary, fontSize = 14.sp,
                modifier = Modifier.clip(RoundedCornerShape(16.dp)).clickable(onClickLabel = if (paused) "Продолжить запись" else "Пауза", onClick = onPause).padding(horizontal = 14.dp, vertical = 12.dp)
            )
            Text("Отмена", color = Fusion.colors.textSecondary, fontSize = 15.sp, modifier = Modifier.clip(RoundedCornerShape(16.dp)).clickable(onClick = onCancel).padding(horizontal = 14.dp, vertical = 12.dp))
            Box(Modifier.size(44.dp).clip(androidx.compose.foundation.shape.CircleShape).background(Fusion.colors.accent).clickable(onClick = onSend), contentAlignment = Alignment.Center) {
                com.kirox.app.ui.icons.KiroXIcon(com.kirox.app.ui.icons.KiroXIcon.Send, "Отправить голосовое", Modifier.size(22.dp), Fusion.colors.onAccent)
            }
        }
    }
}

/** Duration (s), width and height of a local video; zeros when unreadable. */
internal fun videoMeta(path: String): Triple<Int, Int, Int> = runCatching {
    android.media.MediaMetadataRetriever().use { r ->
        r.setDataSource(path)
        val ms = r.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0
        var w = r.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: 0
        var h = r.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: 0
        val rotation = r.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
        if (rotation == 90 || rotation == 270) { val t = w; w = h; h = t }
        Triple((ms / 1000).toInt(), w, h)
    }
}.getOrDefault(Triple(0, 0, 0))

/** Telegram allows up to 10 photos and videos in one album. */
private const val ALBUM_LIMIT = 10

/** Unsent text of the chat on screen, as of the last composition; not state, so writes cause no recomposition. */
private class DraftHolder {
    var chatId = 0L
    var text: String? = null

    fun save(actions: TelegramChatActions) {
        val value = text ?: return
        if (chatId != 0L) actions.saveDraft(chatId, value)
    }
}


private fun Set<Long>.toggle(id: Long): Set<Long> = if (id in this) this - id else this + id

/** "сообщение", "сообщения", "сообщений" for Russian plural rules. */
internal fun messageWord(count: Int): String {
    val mod100 = count % 100
    val mod10 = count % 10
    return when {
        mod100 in 11..14 -> "сообщений"
        mod10 == 1 -> "сообщение"
        mod10 in 2..4 -> "сообщения"
        else -> "сообщений"
    }
}

/** Replaces the header while messages are selected: count and the actions for all of them. */
@Composable
private fun SelectionBar(count: Int, onClose: () -> Unit, onCopy: () -> Unit, onForward: () -> Unit, onDelete: () -> Unit) {
    Surface(color = Fusion.colors.background, modifier = Modifier.statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            ChatIconButton(KiroXIcon.Back, "Отменить выбор", onClose)
            Text("$count", color = Fusion.colors.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f).padding(start = 6.dp))
            ChatIconButton(KiroXIcon.Copy, "Копировать", onCopy)
            ChatIconButton(KiroXIcon.Forward, "Переслать", onForward)
            ChatIconButton(KiroXIcon.Delete, "Удалить", onDelete)
        }
    }
}

/** Newest pinned message under the header; tap scrolls to it. */
@Composable
private fun PinnedBar(message: TelegramMessage, count: Int, onClick: () -> Unit) {
    Surface(color = Fusion.colors.background) {
        Row(
            Modifier.fillMaxWidth().clickable(onClickLabel = "Перейти к закреплённому", onClick = onClick).padding(horizontal = 14.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.width(2.dp).height(32.dp).background(Fusion.colors.textPrimary))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(if (count > 1) "Закреплённое сообщение · $count" else "Закреплённое сообщение", color = Fusion.colors.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Text(message.text.ifBlank { "Медиа" }.replace('\n', ' '), color = Fusion.colors.textSecondary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            com.kirox.app.ui.icons.KiroXIcon(KiroXIcon.Pin, null, Modifier.size(16.dp), Fusion.colors.textSecondary)
        }
    }
}

/** Server search results of the chat, newest first. */
@Composable
private fun SearchResults(results: List<TelegramMessage>, canLoadMore: Boolean, onLoadMore: () -> Unit, onOpen: (TelegramMessage) -> Unit) {
    if (results.isEmpty()) {
        Box(Modifier.fillMaxSize().background(Fusion.colors.background), Alignment.Center) { Text("Ничего не найдено", color = Fusion.colors.textSecondary) }
        return
    }
    val format = remember { SimpleDateFormat("d MMM yyyy, HH:mm", Locale("ru")) }
    LazyColumn(Modifier.fillMaxSize().background(Fusion.colors.background)) {
        items(results, key = { it.id }) { message ->
            Column(Modifier.fillMaxWidth().clickable { onOpen(message) }.padding(horizontal = 18.dp, vertical = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(message.senderName, color = Fusion.colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(format.format(Date(message.date * 1000L)), color = Fusion.colors.textSecondary, fontSize = 12.sp)
                }
                Text(message.text.ifBlank { "Медиа" }, color = Fusion.colors.textSecondary, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        if (canLoadMore) item(key = "more") {
            LaunchedEffect(results.size) { onLoadMore() }
            Box(Modifier.fillMaxWidth().padding(12.dp), Alignment.Center) { CircularProgressIndicator(Modifier.size(20.dp), color = Fusion.colors.textSecondary, strokeWidth = 2.dp) }
        }
    }
}


/** Topics of a forum supergroup; tapping one opens it as a conversation. */
@Composable
private fun ForumTopicsList(chatId: Long, actions: TelegramChatActions, onOpen: (com.kirox.app.telegram.TelegramForumTopic) -> Unit) {
    var topics by remember(chatId) { mutableStateOf<List<com.kirox.app.telegram.TelegramForumTopic>?>(null) }
    LaunchedEffect(chatId) { topics = actions.forumTopics(chatId) }
    val current = topics
    when {
        current == null -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator(Modifier.size(26.dp), color = Fusion.colors.textSecondary, strokeWidth = 2.dp) }
        current.isEmpty() -> Box(Modifier.fillMaxSize(), Alignment.Center) { Text("Темы не загрузились", color = Fusion.colors.textSecondary) }
        else -> LazyColumn(Modifier.fillMaxSize().background(Fusion.colors.background)) {
            items(current, key = { it.id }) { topic ->
                Row(Modifier.fillMaxWidth().clickable { onOpen(topic) }.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(42.dp).clip(RoundedCornerShape(21.dp)).background(Fusion.colors.surface3), contentAlignment = Alignment.Center) {
                        Text(if (topic.isGeneral) "#" else topic.title.take(1).uppercase(), color = Fusion.colors.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                listOfNotNull(if (topic.isClosed) "🔒" else null, topic.title).joinToString(" "),
                                color = Fusion.colors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)
                            )
                            if (topic.isPinned) com.kirox.app.ui.icons.KiroXIcon(KiroXIcon.Pin, "Закреплена", Modifier.padding(start = 4.dp).size(13.dp), Fusion.colors.textSecondary)
                            if (topic.date > 0) Text(TelegramFormat.chatTime(topic.date), color = Fusion.colors.textSecondary, fontSize = 12.sp, modifier = Modifier.padding(start = 6.dp))
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(topic.preview, color = Fusion.colors.textSecondary, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            if (topic.unreadCount > 0) Text(
                                "${topic.unreadCount}", color = Fusion.colors.background, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(start = 6.dp).clip(RoundedCornerShape(10.dp)).background(Fusion.colors.textPrimary).padding(horizontal = 7.dp, vertical = 1.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}


/** Custom keyboard a bot sent: each button sends its text. */
@Composable
private fun BotKeyboard(rows: List<List<String>>, onPress: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().background(Fusion.colors.surface1).padding(6.dp).heightIn(max = 260.dp).verticalScroll(androidx.compose.foundation.rememberScrollState())) {
        rows.forEach { row ->
            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { text ->
                    Text(
                        text, color = Fusion.colors.textPrimary, fontSize = 14.sp, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(Fusion.colors.surface3).clickable { onPress(text) }.padding(horizontal = 8.dp, vertical = 11.dp)
                    )
                }
            }
        }
    }
}

/** New poll: question, 2–10 options, anonymous, several answers, or a quiz with one right answer. */
@Composable
private fun PollCreateDialog(onDismiss: () -> Unit, onCreate: (String, List<String>, Boolean, Boolean, Int?) -> Unit) {
    var question by remember { mutableStateOf("") }
    var options by remember { mutableStateOf(listOf("", "")) }
    var anonymous by remember { mutableStateOf(true) }
    var multiple by remember { mutableStateOf(false) }
    var quiz by remember { mutableStateOf(false) }
    var correct by remember { mutableStateOf(0) }
    val filled = options.map { it.trim() }.filter { it.isNotEmpty() }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Fusion.colors.surface2,
        title = { Text("Новый опрос", color = Fusion.colors.textPrimary) },
        text = {
            Column(Modifier.verticalScroll(androidx.compose.foundation.rememberScrollState())) {
                PollField("Вопрос", question) { question = it.take(300) }
                options.forEachIndexed { index, value ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (quiz) Text(if (correct == index) "◉" else "○", color = Fusion.colors.textPrimary, fontSize = 16.sp, modifier = Modifier.clickable { correct = index }.padding(end = 8.dp))
                        Box(Modifier.weight(1f)) { PollField("Вариант ${index + 1}", value) { text -> options = options.toMutableList().also { it[index] = text.take(100) } } }
                    }
                }
                if (options.size < 10) Text("+ Добавить вариант", color = Fusion.colors.textPrimary, fontSize = 14.sp, modifier = Modifier.clickable { options = options + "" }.padding(vertical = 8.dp))
                PollToggle("Анонимный", anonymous) { anonymous = it }
                PollToggle("Несколько ответов", multiple && !quiz, enabled = !quiz) { multiple = it }
                PollToggle("Викторина", quiz) { quiz = it; if (it) multiple = false }
            }
        },
        confirmButton = {
            TextButton(
                enabled = question.isNotBlank() && filled.size >= 2 && (!quiz || options.getOrNull(correct)?.isNotBlank() == true),
                onClick = {
                    // The right answer's index among the options that are actually sent.
                    val correctIndex = if (quiz) options.take(correct + 1).count { it.isNotBlank() } - 1 else null
                    onCreate(question.trim(), filled, anonymous, multiple, correctIndex)
                }
            ) { Text("Отправить", color = Fusion.colors.textPrimary) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена", color = Fusion.colors.textSecondary) } }
    )
}

@Composable
private fun PollField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value, onChange, label = { Text(label) }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = Fusion.colors.textPrimary, unfocusedTextColor = Fusion.colors.textPrimary,
            focusedBorderColor = Fusion.colors.textPrimary, unfocusedBorderColor = Fusion.colors.outlineStrong,
            focusedLabelColor = Fusion.colors.textPrimary, unfocusedLabelColor = Fusion.colors.textSecondary, cursorColor = Fusion.colors.textPrimary
        )
    )
}

@Composable
private fun PollToggle(title: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(enabled = enabled) { onChange(!checked) }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = if (enabled) Fusion.colors.textPrimary else Fusion.colors.textTertiary, fontSize = 15.sp, modifier = Modifier.weight(1f))
        Switch(checked, onChange, enabled = enabled)
    }
}
