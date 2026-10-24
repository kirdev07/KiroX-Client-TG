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
interface TelegramChatActions {
    fun loadOlder()
    fun sendText(text: String, replyToMessageId: Long?)
    fun sendPhoto(path: String, caption: String)
    fun sendDocument(path: String, caption: String)
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
    onBack: () -> Unit
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
    val rows = remember(history.messages) { toRows(history.messages.asReversed()) }
    val byId = remember(history.messages) { history.messages.associateBy { it.id } }

    LaunchedEffect(error) { if (error != null) { notice = error; onErrorShown() } }
    LaunchedEffect(listState, history.chatId) {
        snapshotFlow { listState.layoutInfo.let { (it.visibleItemsInfo.lastOrNull()?.index ?: 0) >= it.totalItemsCount - 15 } }
            .distinctUntilChanged().filter { it }.collect { actions.loadOlder() }
    }
    // Keep the newest message in view when it arrives while the user is at the bottom.
    LaunchedEffect(history.messages.lastOrNull()?.id) { if (listState.firstVisibleItemIndex <= 1) listState.animateScrollToItem(0) }

    BackHandler(enabled = editing != null || replyTo != null) { editing = null; replyTo = null; input = TextFieldValue() }

    fun sendPicked(uri: Uri, asPhoto: Boolean) {
        scope.launch {
            val path = withContext(Dispatchers.IO) { copyToCache(context, uri) }
            if (path == null) { notice = "Не удалось прочитать файл"; return@launch }
            val caption = input.text.trim()
            if (asPhoto) actions.sendPhoto(path, caption) else actions.sendDocument(path, caption)
            input = TextFieldValue()
        }
    }
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> uri?.let { sendPicked(it, asPhoto = true) } }
    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> uri?.let { sendPicked(it, asPhoto = false) } }

    val peer = ChatPeerUi(
        id = history.chatId,
        name = if (history.peerKind == TelegramPeerKind.Saved) "Избранное" else history.title,
        status = when (history.peerKind) {
            TelegramPeerKind.User -> history.peerStatus?.let { TelegramFormat.status(it) }.orEmpty()
            TelegramPeerKind.Bot -> "бот"
            TelegramPeerKind.Group -> "группа"
            TelegramPeerKind.Channel -> "канал"
            else -> ""
        },
        initial = history.title.take(1).uppercase(),
        photo = history.photo
    )

    Scaffold(
        containerColor = Fusion.colors.background,
        contentWindowInsets = WindowInsets.statusBars,
        topBar = { ChatHeader(peer, onBack, onProfile = {}, onOverflow = { notice = "Меню чата появится позже" }) },
        bottomBar = {
            if (history.peerKind != TelegramPeerKind.Channel) ChatComposer(
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
                onMic = { notice = "Голосовые сообщения появятся позже" },
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
            if (choice == "Фото") pickPhoto.launch("image/*") else pickFile.launch("*/*")
        },
        actions = listOf(KiroXIcon.Media to "Фото", KiroXIcon.File to "Файл"),
        footer = if (input.text.isNotBlank()) "Текст из поля ввода станет подписью" else null
    )
    selected?.let { message ->
        val source = byId[message.id]
        MessageActionsSheet(
            message,
            onDismiss = { selected = null },
            onReply = { replyTo = message; editing = null; selected = null },
            onReaction = {},
            onAction = { action ->
                selected = null
                when (action) {
                    "Копировать" -> { clipboard.setText(AnnotatedString(message.text)); notice = "Сообщение скопировано" }
                    "Редактировать" -> { editing = message; replyTo = null; input = TextFieldValue(message.text) }
                    "Удалить" -> deleting = message
                    "Переслать" -> forwarding = message
                }
            },
            reactions = false,
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
            text = { Text(if (message.direction == MessageDirection.Outgoing) "Можно удалить только у себя или у всех участников." else "Сообщение будет удалено у вас.", color = Fusion.colors.textSecondary) },
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
        TelegramDelivery.Sent, TelegramDelivery.None -> DeliveryState.Sent
    },
    quote = replyText?.let { QuoteUi(replyAuthor.orEmpty(), it) },
    forwardedFrom = forwardedFrom,
    canEdit = canEdit,
    photo = photo,
    photoAspect = photoAspect,
    edited = edited
)

/** Copies a picked content URI into the app cache so TDLib can upload it from a plain path. */
private fun copyToCache(context: Context, uri: Uri): String? = runCatching {
    val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) cursor.getString(0) else null
    }?.replace(Regex("[\\\\/:*?\"<>|]"), "_")?.takeIf { it.isNotBlank() } ?: "file"
    val dir = File(context.cacheDir, "outgoing/${System.currentTimeMillis()}").apply { mkdirs() }
    val target = File(dir, name)
    context.contentResolver.openInputStream(uri)?.use { input -> target.outputStream().use { input.copyTo(it) } } ?: return null
    target.absolutePath
}.getOrNull()
