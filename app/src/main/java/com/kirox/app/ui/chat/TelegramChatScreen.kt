package com.kirox.app.ui.chat

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
    menu: TelegramChatMenuActions? = null
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
    var forwarding by remember { mutableStateOf<MessageUi?>(null) }
    var attachments by rememberSaveable { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()

    val isGroup = history.peerKind == TelegramPeerKind.Group
    // Newest first for reverseLayout; date separators are inserted as system rows.
    val recorder = remember { VoiceRecorder(context) }
    var recording by remember { mutableStateOf(false) }
    var recordLevel by remember { mutableStateOf(0f) }
    var recordMs by remember { mutableStateOf(0L) }
    fun startRecording() {
        if (recorder.start()) { recording = true; recordMs = 0 } else notice = "Не удалось начать запись"
    }
    val micPermission = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) startRecording() else notice = "Без доступа к микрофону голосовое не записать" }
    LaunchedEffect(recording) {
        while (recording) {
            recordLevel = recorder.sampleLevel()
            recordMs = recorder.elapsedMs
            kotlinx.coroutines.delay(100)
        }
    }
    DisposableEffect(Unit) { onDispose { recorder.stop(discard = true); VoicePlayer.stop() } }
    var menuOpen by remember { mutableStateOf(false) }
    var searching by remember(history.chatId) { mutableStateOf(false) }
    var searchQuery by remember(history.chatId) { mutableStateOf("") }
    var muted by remember(history.chatId) { mutableStateOf<Boolean?>(null) }
    var confirmClear by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    // Search filters the loaded part of the conversation.
    val shownMessages = remember(history.messages, searchQuery) {
        val q = searchQuery.trim()
        if (q.isEmpty()) history.messages else history.messages.filter { it.text.contains(q, ignoreCase = true) }
    }
    val rows = remember(shownMessages) { toRows(shownMessages.asReversed()) }
    val byId = remember(history.messages) { history.messages.associateBy { it.id } }

    LaunchedEffect(error) { if (error != null) { notice = error; onErrorShown() } }
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
    // Gallery: photos and videos.
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let { sendPicked(it, asPhoto = true) } }
    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> uri?.let { sendPicked(it, asPhoto = false) } }

    val peer = ChatPeerUi(
        id = history.chatId,
        name = if (history.peerKind == TelegramPeerKind.Saved) "Избранное" else history.title,
        status = when (history.peerKind) {
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
            if (searching) Column(Modifier.statusBarsPadding()) {
                ChatSearchField(searchQuery, { searchQuery = it }, onClose = { searching = false; searchQuery = "" })
            } else ChatHeader(peer, onBack, onProfile = {}, onOverflow = {
                if (menu == null) notice = "Меню чата недоступно" else {
                    menuOpen = true
                    scope.launch { muted = runCatching { menu.isMuted(history.chatId) }.getOrNull() }
                }
            }, onCall = { video -> onCall?.invoke(video) })
        },
        bottomBar = {
            if (recording) VoiceRecordingBar(
                elapsedMs = recordMs,
                level = recordLevel,
                onCancel = { recorder.stop(discard = true); recording = false },
                onSend = {
                    val note = recorder.stop()
                    recording = false
                    if (note != null) actions.sendVoice(note.file.absolutePath, note.durationSeconds, note.waveform) else notice = "Слишком короткое голосовое"
                }
            ) else if (history.peerKind != TelegramPeerKind.Channel) ChatComposer(
                value = input,
                reply = replyTo,
                editing = editing,
                onValueChange = { input = it },
                onCancelContext = { replyTo = null; editing = null; input = TextFieldValue() },
                onAttach = { attachments = true },
                onEmoji = { input = input.copy(text = input.text + "🙂", selection = androidx.compose.ui.text.TextRange(input.text.length + "🙂".length)) },
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
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            com.kirox.app.ui.components.ChatWallpaperLayer(style.wallpaper, Modifier.matchParentSize())
            if (history.messages.isEmpty()) {
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
                    MessageRow(row.message, grouped, appearance, showAuthor = isGroup, onLongClick = { if (row.message.direction != MessageDirection.System) selected = row.message }, onReply = { replyTo = row.message; editing = null })
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
            if (choice == "Фото и видео") pickPhoto.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)) else pickFile.launch("*/*")
        },
        actions = listOf(KiroXIcon.Media to "Фото и видео", KiroXIcon.File to "Файл"),
        footer = if (input.text.isNotBlank()) "Текст из поля ввода станет подписью" else null
    )
    selected?.let { message ->
        val source = byId[message.id]
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
                    "Переслать" -> forwarding = message
                }
            },
            reactions = history.peerKind != TelegramPeerKind.Saved,
            hidden = buildSet {
                add("Закрепить"); add("Выбрать")
                if (source?.canEdit != true) add("Редактировать")
                if (source?.delivery == TelegramDelivery.Sending) { add("Переслать"); add("Ответить") }
            }
        )
    }
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
    forwarding?.let { message ->
        ModalBottomSheet(onDismissRequest = { forwarding = null }, containerColor = Fusion.colors.surface1) {
            Text("Переслать в…", color = Fusion.colors.textPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 480.dp)) {
                items(forwardTargets, key = { it.id }) { target ->
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            actions.forward(target.id, listOf(message.id)); forwarding = null; notice = "Переслано в «${target.title}»"
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
        TelegramMessageKind.Voice -> MessageKind.Voice
        TelegramMessageKind.Document -> MessageKind.Document
        TelegramMessageKind.Text, TelegramMessageKind.Other -> MessageKind.Text
    },
    text = if (kind == TelegramMessageKind.Document) listOfNotNull(fileName, text.takeIf { it.isNotBlank() }).joinToString("\n") else text,
    time = time,
    delivery = when (delivery) {
        TelegramDelivery.Sending -> DeliveryState.Sending
        TelegramDelivery.Failed -> DeliveryState.Failed
        TelegramDelivery.Read -> DeliveryState.Read
        TelegramDelivery.Sent -> DeliveryState.Sent
        TelegramDelivery.None -> DeliveryState.None
    },
    quote = replyText?.let { QuoteUi(replyAuthor.orEmpty(), it) },
    forwardedFrom = forwardedFrom,
    reactions = reactions.map { (emoji, count, chosen) -> ReactionUi(emoji, count, chosen) },
    canEdit = canEdit,
    photo = photo,
    photoAspect = photoAspect,
    edited = edited
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
private fun VoiceRecordingBar(elapsedMs: Long, level: Float, onCancel: () -> Unit, onSend: () -> Unit) {
    Surface(color = Fusion.colors.background) {
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 10.dp, vertical = 8.dp)
                .clip(RoundedCornerShape(26.dp)).background(Fusion.colors.surface1).padding(start = 18.dp, end = 6.dp).heightIn(min = 52.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size((10 + 8 * level).dp).clip(androidx.compose.foundation.shape.CircleShape).background(Fusion.colors.danger))
            Spacer(Modifier.width(12.dp))
            Text("%d:%02d".format(elapsedMs / 60000, elapsedMs / 1000 % 60), color = Fusion.colors.textPrimary, fontSize = 16.sp)
            Spacer(Modifier.weight(1f))
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
