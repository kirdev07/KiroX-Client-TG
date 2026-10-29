package com.kirox.app.ui.chat

// Shared Fusion chat components used by TelegramChatScreen.

import androidx.compose.runtime.ReadOnlyComposable
import com.kirox.app.ui.theme.Fusion
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kirox.app.ui.icons.KiroXIcon
import com.kirox.app.ui.icons.KiroXIcon as IconGlyph
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

private val ChatWhite: Color @Composable @ReadOnlyComposable get() = Fusion.colors.textPrimary
private val ChatSecondary: Color @Composable @ReadOnlyComposable get() = Fusion.colors.textSecondary
private val ChatDivider: Color @Composable @ReadOnlyComposable get() = Fusion.colors.surface2

@Composable
internal fun ChatHeader(peer: ChatPeerUi, onBack: () -> Unit, onProfile: () -> Unit, onOverflow: () -> Unit, onCall: (isVideo: Boolean) -> Unit = {}) {
    val narrow = LocalConfiguration.current.screenWidthDp < 360
    Surface(color = Fusion.colors.background, tonalElevation = 0.dp, modifier = Modifier.statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            ChatIconButton(KiroXIcon.Back, "Назад", onBack)
            Row(Modifier.weight(1f).height(56.dp).clickable(onClick = onProfile).padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                if (peer.photo != null) com.kirox.app.ui.components.TelegramAvatar(peer.name, peer.photo, 40.dp)
                else Box(Modifier.size(40.dp).border(1.dp, Fusion.colors.outlineStrong, CircleShape).clip(CircleShape).background(Fusion.colors.avatar), contentAlignment = Alignment.Center) { Text(peer.initial, color = ChatWhite, fontWeight = FontWeight.Bold) }
                Spacer(Modifier.width(9.dp))
                Column(Modifier.weight(1f)) {
                    Text(peer.name, color = ChatWhite, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(peer.status, color = ChatSecondary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (peer.canVoiceCall) ChatIconButton(KiroXIcon.Call, "Голосовой звонок") { onCall(false) }
            if (peer.canVideoCall && !narrow) ChatIconButton(KiroXIcon.Video, "Видеозвонок") { onCall(true) }
            ChatIconButton(KiroXIcon.More, "Дополнительно", onOverflow)
        }
        HorizontalDivider(color = ChatDivider)
    }
}

@Composable
internal fun ChatSearchField(value: String, onChange: (String) -> Unit, onClose: () -> Unit) {
    Row(Modifier.fillMaxWidth().background(Fusion.colors.background).padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        TextField(value = value, onValueChange = onChange, modifier = Modifier.weight(1f).heightIn(min = 48.dp), singleLine = true, placeholder = { Text("Поиск в переписке", color = ChatSecondary) }, leadingIcon = { IconGlyph(KiroXIcon.Search, null, Modifier.size(20.dp), ChatSecondary) }, colors = TextFieldDefaults.colors(focusedContainerColor = Fusion.colors.surface2, unfocusedContainerColor = Fusion.colors.surface2, focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent, focusedTextColor = ChatWhite, unfocusedTextColor = ChatWhite), shape = RoundedCornerShape(16.dp))
        Text("×", color = ChatSecondary, fontSize = 24.sp, modifier = Modifier.clickable(onClick = onClose).padding(12.dp))
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun MessageRow(message: MessageUi, grouped: Boolean, appearance: ChatAppearance, album: List<MessageUi> = emptyList(), pinned: Boolean = false, showAuthor: Boolean = false, onLongClick: () -> Unit, onReply: () -> Unit, onNotice: (String) -> Unit = {}, selected: Boolean = false, onClick: (() -> Unit)? = null, onQuote: (Long) -> Unit = {}, highlighted: Boolean = false, onComments: (() -> Unit)? = null, onVote: (List<Int>) -> Unit = {}, onButton: (com.kirox.app.telegram.TelegramButton) -> Unit = {}) {
    if (message.direction == MessageDirection.System) {
        Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
            Text(message.text, color = ChatSecondary, fontSize = 11.sp, modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(Fusion.colors.surface2).padding(horizontal = 12.dp, vertical = 5.dp))
        }
        return
    }
    val outgoing = message.direction == MessageDirection.Outgoing
    var drag by remember(message.id) { mutableFloatStateOf(0f) }
    Row(
        Modifier.fillMaxWidth()
            .background(if (selected || highlighted) Fusion.colors.surface2 else Color.Transparent)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(top = if (grouped) 0.dp else 5.dp).pointerInput(message.id) {
            detectHorizontalDragGestures(onDragStart = { drag = 0f }, onHorizontalDrag = { _, delta -> if (delta > 0) drag += delta }, onDragEnd = { if (drag > 72f) onReply(); drag = 0f }, onDragCancel = { drag = 0f })
        },
        horizontalArrangement = if (outgoing) Arrangement.End else Arrangement.Start
    ) {
        Column(
            Modifier.widthIn(max = LocalConfiguration.current.screenWidthDp.dp * .84f)
                .clip(RoundedCornerShape(appearance.bubbleRadiusDp.dp))
                .background(Color(if (outgoing) appearance.outgoingColor else appearance.incomingColor))
                .combinedClickable(onClick = { onClick?.invoke() }, onLongClick = onLongClick)
                .padding(horizontal = 12.dp, vertical = appearance.bubblePaddingDp.dp)
        ) {
            if (showAuthor && !outgoing && !grouped) { Text(message.author, color = ChatWhite, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis); Spacer(Modifier.height(3.dp)) }
            if (pinned) { Row(verticalAlignment = Alignment.CenterVertically) { IconGlyph(KiroXIcon.Pin, null, Modifier.size(13.dp), ChatSecondary); Spacer(Modifier.width(4.dp)); Text("Закреплено", color = ChatSecondary, fontSize = 10.sp) }; Spacer(Modifier.height(4.dp)) }
            message.forwardedFrom?.let { Text("Переслано от $it", color = Fusion.colors.textPrimary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold); Spacer(Modifier.height(4.dp)) }
            message.quote?.let { quote -> Box(Modifier.clickable(enabled = quote.messageId != 0L) { onQuote(quote.messageId) }) { QuoteBlock(quote) }; Spacer(Modifier.height(6.dp)) }
            val textScale = appearance.textScale
            @Composable fun Caption() { if (message.text.isNotBlank()) { Spacer(Modifier.height(6.dp)); MessageText(message.text, message.entities, textScale) } }
            val media = message.media
            if (album.size > 1) {
                AlbumGrid(album)
                Caption()
            } else when (message.kind) {
                MessageKind.Photo -> {
                    val photo = message.photo
                    var open by remember(message.id) { mutableStateOf(false) }
                    if (photo != null) Box(Modifier.clickable { open = true }) { com.kirox.app.ui.components.TelegramPhoto(photo, message.photoAspect) } else MediaPlaceholder(message.kind)
                    if (open && photo != null) FullscreenPhoto(photo) { open = false }
                    Caption()
                }
                MessageKind.Video -> { if (media != null) VideoMessage(media, message.thumbnail) else MediaPlaceholder(message.kind); Caption() }
                MessageKind.Animation -> { if (media != null) AnimationMessage(media, message.thumbnail) else MediaPlaceholder(message.kind); Caption() }
                MessageKind.VideoNote -> if (media != null) VideoNoteMessage(media, message.thumbnail) else MediaPlaceholder(message.kind)
                MessageKind.Voice -> VoiceMessage(message.text, message.photo, message.media?.waveform.orEmpty())
                MessageKind.Audio -> { if (media != null) AudioMessage(media); Caption() }
                MessageKind.Document -> if (media != null) { DocumentMessage(media, onNotice); Caption() } else MessageText(message.text, message.entities, textScale)
                MessageKind.Sticker -> if (media != null) StickerMessage(media, message.thumbnail) else MessageText(message.text, message.entities, textScale)
                MessageKind.Text -> {
                    val poll = message.poll
                    if (poll != null) PollView(poll, onVote) else MessageText(message.text, message.entities, textScale)
                }
            }
            Spacer(Modifier.height(4.dp))
            Row(Modifier.align(Alignment.End), verticalAlignment = Alignment.CenterVertically) {
                Text(if (message.edited) "изм. ${message.time}" else message.time, color = ChatSecondary, fontSize = 10.sp)
                if (outgoing && message.delivery != DeliveryState.None) { Spacer(Modifier.width(4.dp)); Text(deliveryGlyph(message.delivery), color = ChatSecondary, fontSize = 10.sp) }
            }
            if (message.reactions.isNotEmpty()) {
                Row(Modifier.padding(top = 5.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) { message.reactions.forEach { reaction -> Text("${reaction.emoji} ${reaction.count}", color = ChatWhite, fontSize = 11.sp, modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(if (reaction.selected) Fusion.colors.textTertiary else Fusion.colors.surface3).padding(horizontal = 8.dp, vertical = 4.dp)) } }
            }
            if (message.buttons.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                message.buttons.forEach { row ->
                    Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        row.forEach { button ->
                            Text(
                                button.text + if (button.kind == com.kirox.app.telegram.TelegramButton.Kind.Url) " ↗" else "",
                                color = if (button.kind == com.kirox.app.telegram.TelegramButton.Kind.Other) ChatSecondary else ChatWhite,
                                fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                modifier = Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).background(Fusion.colors.surface3)
                                    .clickable(enabled = button.kind != com.kirox.app.telegram.TelegramButton.Kind.Other) { onButton(button) }.padding(horizontal = 8.dp, vertical = 9.dp)
                            )
                        }
                    }
                }
            }
            val comments = message.commentCount
            if (comments != null && onComments != null) {
                Spacer(Modifier.height(6.dp))
                HorizontalDivider(color = Fusion.colors.outline)
                Row(Modifier.fillMaxWidth().clickable(onClick = onComments).padding(top = 7.dp, bottom = 1.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconGlyph(KiroXIcon.Chat, null, Modifier.size(16.dp), ChatWhite)
                    Spacer(Modifier.width(8.dp))
                    Text(commentsLabel(comments), color = ChatWhite, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    Text("›", color = ChatSecondary, fontSize = 16.sp)
                }
            }
        }
    }
}

@Composable private fun QuoteBlock(quote: QuoteUi) { Row(Modifier.fillMaxWidth()) { Box(Modifier.width(2.dp).height(36.dp).background(ChatWhite)); Spacer(Modifier.width(7.dp)); Column { Text(quote.author, color = ChatWhite, fontSize = 11.sp, fontWeight = FontWeight.SemiBold); Text(quote.text, color = ChatSecondary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) } } }
@Composable private fun MediaPlaceholder(kind: MessageKind) { Box(Modifier.fillMaxWidth().height(132.dp).clip(RoundedCornerShape(12.dp)).background(Fusion.colors.surface3), contentAlignment = Alignment.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally) { IconGlyph(if (kind == MessageKind.Video) KiroXIcon.Video else KiroXIcon.Media, null, Modifier.size(28.dp), ChatSecondary); Text(if (kind == MessageKind.Video) "Видео" else "Фото", color = ChatSecondary, fontSize = 12.sp) } } }
/**
 * Voice note: play/pause, waveform that fills while playing (tap or drag on it to seek),
 * elapsed time and a 1× / 1.5× / 2× speed chip. The file downloads on first view.
 */
@Composable private fun VoiceMessage(duration: String, file: com.kirox.app.telegram.TelegramFileRef?, waveform: List<Int>) {
    val files = com.kirox.app.ui.components.LocalTelegramFiles.current
    val path by remember(file?.id, files) {
        if (file != null && files != null) files.observe(file, 16) else kotlinx.coroutines.flow.MutableStateFlow(file?.localPath)
    }.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    val playingPath by VoicePlayer.playingPath.collectAsState()
    val speed by VoicePlayer.speed.collectAsState()
    val playing = path != null && playingPath == path
    var progress by remember(path) { mutableFloatStateOf(0f) }
    var positionMs by remember(path) { mutableStateOf(0) }
    LaunchedEffect(playing) {
        while (playing) { progress = VoicePlayer.progress; positionMs = VoicePlayer.positionMs; kotlinx.coroutines.delay(50) }
        progress = 0f
    }
    val bars = remember(waveform) { voiceBars(waveform) }
    val played = ChatWhite
    val rest = ChatSecondary.copy(alpha = 0.5f)
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(38.dp).clip(CircleShape).background(Fusion.colors.surface3).clickable(enabled = path != null) { path?.let { VoicePlayer.toggle(context, it) } },
            contentAlignment = Alignment.Center
        ) {
            if (path == null) androidx.compose.material3.CircularProgressIndicator(Modifier.size(16.dp), color = ChatWhite, strokeWidth = 2.dp)
            else Text(if (playing) "❚❚" else "▶", color = ChatWhite, fontSize = 13.sp)
        }
        Spacer(Modifier.width(9.dp))
        Column {
            androidx.compose.foundation.Canvas(
                Modifier.width(150.dp).height(26.dp).pointerInput(path, playing) {
                    if (!playing) return@pointerInput
                    detectHorizontalDragGestures(
                        onDragEnd = {},
                        onHorizontalDrag = { change, _ -> path?.let { VoicePlayer.seek(it, change.position.x / size.width) } }
                    )
                }.pointerInput(path, playing) {
                    detectTapGestures { offset ->
                        val p = path ?: return@detectTapGestures
                        if (playing) VoicePlayer.seek(p, offset.x / size.width) else VoicePlayer.toggle(context, p)
                    }
                }
            ) {
                val step = size.width / bars.size
                val barWidth = (step * 0.6f).coerceAtLeast(1.5f)
                bars.forEachIndexed { index, level ->
                    val height = (size.height * (0.15f + 0.85f * level)).coerceAtLeast(2f)
                    val x = index * step + (step - barWidth) / 2
                    drawRoundRect(
                        if (index.toFloat() / bars.size < progress) played else rest,
                        topLeft = androidx.compose.ui.geometry.Offset(x, (size.height - height) / 2),
                        size = androidx.compose.ui.geometry.Size(barWidth, height),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth / 2)
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (playing) "%d:%02d".format(positionMs / 60000, positionMs / 1000 % 60) else duration, color = ChatSecondary, fontSize = 11.sp)
                if (playing) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (speed == 1f) "1×" else if (speed == 1.5f) "1.5×" else "2×", color = ChatWhite, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(Fusion.colors.surface3).clickable(onClickLabel = "Скорость", onClick = VoicePlayer::cycleSpeed).padding(horizontal = 6.dp, vertical = 1.dp)
                    )
                }
            }
        }
    }
}

/** 40 bars of 0–1 from Telegram's 0–31 waveform; a flat line when the note has none. */
internal fun voiceBars(waveform: List<Int>, count: Int = 40): List<Float> {
    if (waveform.isEmpty()) return List(count) { 0.1f }
    val peak = waveform.maxOrNull()?.coerceAtLeast(1) ?: 1
    return List(count) { i ->
        val from = i * waveform.size / count
        val to = ((i + 1) * waveform.size / count).coerceAtLeast(from + 1).coerceAtMost(waveform.size)
        (waveform.subList(from, to).maxOrNull() ?: 0).toFloat() / peak
    }
}
private fun deliveryGlyph(state: DeliveryState) = when (state) { DeliveryState.Sending -> "◷"; DeliveryState.Sent -> "✓"; DeliveryState.Delivered, DeliveryState.Read -> "✓✓"; DeliveryState.Failed -> "!"; DeliveryState.None -> "" }

@Composable
internal fun ChatComposer(value: TextFieldValue, reply: MessageUi?, editing: MessageUi?, onValueChange: (TextFieldValue) -> Unit, onCancelContext: () -> Unit, onAttach: () -> Unit, onEmoji: () -> Unit, onSend: () -> Unit, onMic: () -> Unit, appearance: ChatAppearance) {
    Surface(color = Fusion.colors.background) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().imePadding()) {
            AnimatedVisibility(reply != null || editing != null) { (editing ?: reply)?.let { context -> Row(Modifier.fillMaxWidth().background(Fusion.colors.surface1).padding(horizontal = 14.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) { Box(Modifier.width(2.dp).height(34.dp).background(ChatWhite)); Spacer(Modifier.width(8.dp)); Column(Modifier.weight(1f)) { Text(if (editing != null) "Редактирование сообщения" else "Ответ: ${context.author}", color = ChatWhite, fontSize = 11.sp); Text(context.text, color = ChatSecondary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }; Text("×", color = ChatSecondary, fontSize = 22.sp, modifier = Modifier.clickable(onClick = onCancelContext).padding(8.dp)) } } }
            Surface(
                color = Color(appearance.inputColor), shape = RoundedCornerShape(26.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp).border(1.dp, Fusion.colors.outline, RoundedCornerShape(26.dp))
            ) {
                Row(verticalAlignment = Alignment.Bottom) {
                    ChatIconButton(KiroXIcon.Add, "Вложения", onAttach)
                    TextField(value, onValueChange, modifier = Modifier.weight(1f).heightIn(min = 48.dp, max = 140.dp), placeholder = { Text("Сообщение", color = ChatSecondary, fontSize = 16.sp) }, colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent, focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent, focusedTextColor = ChatWhite, unfocusedTextColor = ChatWhite, cursorColor = ChatWhite), maxLines = 5)
                    AnimatedContent(value.text.isNotBlank(), transitionSpec = { (fadeIn() + scaleIn()).togetherWith(fadeOut() + scaleOut()) }, label = "send_mic") { hasText -> ChatIconButton(if (hasText) KiroXIcon.Send else KiroXIcon.Voice, if (hasText) "Отправить" else "Голосовое сообщение", if (hasText) onSend else onMic) }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun AttachmentSheet(
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
    actions: List<Pair<KiroXIcon, String>> = listOf(KiroXIcon.Media to "Галерея", KiroXIcon.Camera to "Камера", KiroXIcon.File to "Документ", KiroXIcon.Music to "Музыка", KiroXIcon.Contacts to "Контакт", KiroXIcon.Location to "Местоположение"),
    footer: String? = null
) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Fusion.colors.surface1, contentColor = ChatWhite) {
        Text("Вложения", fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
        actions.chunked(3).forEach { row -> Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) { row.forEach { (icon, title) -> Column(Modifier.weight(1f).clickable { onPick(title) }.padding(vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) { Box(Modifier.size(48.dp).clip(CircleShape).background(Fusion.colors.surface2), contentAlignment = Alignment.Center) { IconGlyph(icon, null, Modifier.size(24.dp), ChatWhite) }; Spacer(Modifier.height(7.dp)); Text(title, color = ChatWhite, fontSize = 12.sp, maxLines = 1) } } } }
        if (footer != null) Text(footer, color = ChatSecondary, fontSize = 11.sp, modifier = Modifier.padding(20.dp)); Spacer(Modifier.navigationBarsPadding().height(12.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable internal fun MessageActionsSheet(message: MessageUi, onDismiss: () -> Unit, onReply: () -> Unit, onReaction: (String) -> Unit, onAction: (String) -> Unit, reactions: Boolean = true, hidden: Set<String> = emptySet(), isPinned: Boolean = false, reactionChoices: List<String> = DEFAULT_REACTIONS) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Fusion.colors.surface1, contentColor = ChatWhite) {
        if (reactions) Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp), horizontalArrangement = Arrangement.SpaceAround) { reactionChoices.forEach { emoji -> Text(emoji, fontSize = 23.sp, modifier = Modifier.clip(CircleShape).clickable { onReaction(emoji) }.padding(9.dp)) } }
        val actions = buildList { add(KiroXIcon.Reply to "Ответить"); if (message.text.isNotBlank() && message.kind != MessageKind.Voice) add(KiroXIcon.Copy to "Копировать"); if (message.text.isNotBlank() && message.kind == MessageKind.Text && message.poll == null) add(KiroXIcon.Info to "Перевести"); if (message.media != null && message.kind != MessageKind.Sticker || message.kind == MessageKind.Photo) add(KiroXIcon.Storage to "Сохранить"); add(KiroXIcon.Forward to "Переслать"); add(KiroXIcon.Pin to if (isPinned) "Открепить" else "Закрепить"); if (message.canEdit) add(KiroXIcon.Edit to "Редактировать"); if (message.canDelete) add(KiroXIcon.Delete to "Удалить"); add(KiroXIcon.Check to "Выбрать") }.filterNot { it.second in hidden }
        actions.forEach { (icon, title) -> Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable { if (title == "Ответить") onReply() else onAction(title) }.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) { IconGlyph(icon, null, Modifier.size(22.dp), ChatWhite); Spacer(Modifier.width(16.dp)); Text(title, color = if (title == "Удалить") Fusion.colors.danger else ChatWhite, fontSize = 15.sp) } }
        Spacer(Modifier.navigationBarsPadding())
    }
}

/** "Оставить комментарий", "1 комментарий", "5 комментариев". */
internal fun commentsLabel(count: Int): String {
    if (count <= 0) return "Оставить комментарий"
    val mod100 = count % 100
    val mod10 = count % 10
    val word = when {
        mod100 in 11..14 -> "комментариев"
        mod10 == 1 -> "комментарий"
        mod10 in 2..4 -> "комментария"
        else -> "комментариев"
    }
    return "$count $word"
}

/**
 * Poll or quiz: tap an option to vote (several, then «Голосовать», when multiple answers are
 * allowed); after voting or closing, results with percentages; the quiz answer is marked.
 */
@Composable
private fun PollView(poll: com.kirox.app.telegram.TelegramPoll, onVote: (List<Int>) -> Unit) {
    // Kept while others vote (the poll object changes); cleared once this account has voted.
    var picked by remember(poll.question, poll.voted) { mutableStateOf(setOf<Int>()) }
    val showResults = poll.voted || poll.isClosed
    Column(Modifier.widthIn(min = 220.dp)) {
        Text(poll.question, color = ChatWhite, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Text(
            listOf(if (poll.isQuiz) "Викторина" else if (poll.isAnonymous) "Анонимный опрос" else "Опрос", if (poll.isClosed) "завершён" else null).filterNotNull().joinToString(" · "),
            color = ChatSecondary, fontSize = 11.sp
        )
        Spacer(Modifier.height(6.dp))
        poll.options.forEachIndexed { index, option ->
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable(enabled = poll.canVote && !showResults || poll.canVote && poll.multipleAnswers) {
                    if (poll.multipleAnswers) picked = if (index in picked) picked - index else picked + index else onVote(listOf(index))
                }.padding(vertical = 5.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val mark = when {
                        showResults && poll.isQuiz && option.correct -> "✓"
                        showResults -> "${option.percent}%"
                        poll.multipleAnswers -> if (index in picked) "☑" else "☐"
                        else -> "○"
                    }
                    Text(mark, color = ChatWhite, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.widthIn(min = 36.dp))
                    Text(option.text, color = ChatWhite, fontSize = 14.sp, modifier = Modifier.weight(1f))
                    if (option.chosen) Text("•", color = ChatWhite, fontSize = 16.sp)
                }
                if (showResults) Box(Modifier.padding(start = 36.dp, top = 3.dp).fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)).background(Fusion.colors.surface3)) {
                    Box(Modifier.fillMaxWidth(option.percent / 100f).height(3.dp).background(if (poll.isQuiz && !option.correct && option.chosen) Fusion.colors.danger else ChatWhite))
                }
            }
        }
        if (poll.multipleAnswers && poll.canVote && picked.isNotEmpty()) Text(
            "Голосовать", color = ChatWhite, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 6.dp).clip(RoundedCornerShape(12.dp)).background(Fusion.colors.surface3).clickable { onVote(picked.sorted()); picked = emptySet() }.padding(horizontal = 14.dp, vertical = 7.dp)
        )
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(pollVotersLabel(poll.totalVoters), color = ChatSecondary, fontSize = 11.sp, modifier = Modifier.weight(1f))
            if (poll.voted && poll.canVote && !poll.isQuiz) Text("Отменить голос", color = ChatSecondary, fontSize = 11.sp, modifier = Modifier.clickable { onVote(emptyList()) })
        }
    }
}

/** "Нет голосов", "1 голос", "3 голоса", "7 голосов". */
internal fun pollVotersLabel(count: Int): String {
    if (count <= 0) return "Нет голосов"
    val mod100 = count % 100
    val mod10 = count % 10
    return "$count " + when {
        mod100 in 11..14 -> "голосов"
        mod10 == 1 -> "голос"
        mod10 in 2..4 -> "голоса"
        else -> "голосов"
    }
}

/** Photos and videos of one album in a grid (2 per row, 3 when there are many), each opens full screen. */
@Composable
private fun AlbumGrid(items: List<MessageUi>) {
    val columns = if (items.size >= 5) 3 else 2
    Column(Modifier.widthIn(max = 280.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        items.chunked(columns).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                row.forEach { item ->
                    Box(Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(8.dp)).background(Fusion.colors.surface3)) {
                        val photo = item.photo
                        val media = item.media
                        when {
                            item.kind == MessageKind.Photo && photo != null -> {
                                var open by remember(item.id) { mutableStateOf(false) }
                                Box(Modifier.fillMaxSize().clickable { open = true }) { com.kirox.app.ui.components.TelegramPhoto(photo, 1f, Modifier.fillMaxSize()) }
                                if (open) FullscreenPhoto(photo) { open = false }
                            }
                            item.kind == MessageKind.Video && media != null -> VideoMessage(media, item.thumbnail)
                            media != null -> DocumentMessage(media) {}
                            else -> MediaPlaceholder(item.kind)
                        }
                    }
                }
                // Keep tiles square in a short last row.
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** Reactions offered before the chat's own list loads. */
internal val DEFAULT_REACTIONS = listOf("❤", "👍", "🔥", "😁", "👏")

@Composable internal fun ChatIconButton(icon: KiroXIcon, description: String, onClick: () -> Unit) { Box(Modifier.size(48.dp).semantics { contentDescription = description }.clip(CircleShape).clickable(onClick = onClick), contentAlignment = Alignment.Center) { IconGlyph(icon, null, Modifier.size(23.dp), ChatWhite) } }

@Preview(name = "Incoming message", widthDp = 320, backgroundColor = 0xFF000000, showBackground = true)
@Composable private fun IncomingMessagePreview() { MessageRow(MessageUi(1, "Кирилл", MessageDirection.Incoming, text = "Короткое сообщение для проверки узкого экрана", time = "12:40"), false, ChatAppearance(), onLongClick = {}, onReply = {}) }

@Preview(name = "Outgoing long message", widthDp = 480, fontScale = 1.5f, backgroundColor = 0xFF000000, showBackground = true)
@Composable private fun OutgoingMessagePreview() { MessageRow(MessageUi(2, "Вы", MessageDirection.Outgoing, text = "Длинное сообщение проверяет переносы, системный масштаб текста и положение времени.", time = "12:41"), false, ChatAppearance(), onLongClick = {}, onReply = {}) }
