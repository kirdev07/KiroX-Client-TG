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
internal fun MessageRow(message: MessageUi, grouped: Boolean, appearance: ChatAppearance, pinned: Boolean = false, showAuthor: Boolean = false, onLongClick: () -> Unit, onReply: () -> Unit) {
    if (message.direction == MessageDirection.System) {
        Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
            Text(message.text, color = ChatSecondary, fontSize = 11.sp, modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(Fusion.colors.surface2).padding(horizontal = 12.dp, vertical = 5.dp))
        }
        return
    }
    val outgoing = message.direction == MessageDirection.Outgoing
    var drag by remember(message.id) { mutableFloatStateOf(0f) }
    Row(
        Modifier.fillMaxWidth().padding(top = if (grouped) 0.dp else 5.dp).pointerInput(message.id) {
            detectHorizontalDragGestures(onDragStart = { drag = 0f }, onHorizontalDrag = { _, delta -> if (delta > 0) drag += delta }, onDragEnd = { if (drag > 72f) onReply(); drag = 0f }, onDragCancel = { drag = 0f })
        },
        horizontalArrangement = if (outgoing) Arrangement.End else Arrangement.Start
    ) {
        Column(
            Modifier.widthIn(max = LocalConfiguration.current.screenWidthDp.dp * .84f)
                .clip(RoundedCornerShape(appearance.bubbleRadiusDp.dp))
                .background(Color(if (outgoing) appearance.outgoingColor else appearance.incomingColor))
                .combinedClickable(onClick = {}, onLongClick = onLongClick)
                .padding(horizontal = 12.dp, vertical = appearance.bubblePaddingDp.dp)
        ) {
            if (showAuthor && !outgoing && !grouped) { Text(message.author, color = ChatWhite, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis); Spacer(Modifier.height(3.dp)) }
            if (pinned) { Row(verticalAlignment = Alignment.CenterVertically) { IconGlyph(KiroXIcon.Pin, null, Modifier.size(13.dp), ChatSecondary); Spacer(Modifier.width(4.dp)); Text("Закреплено", color = ChatSecondary, fontSize = 10.sp) }; Spacer(Modifier.height(4.dp)) }
            message.forwardedFrom?.let { Text("Переслано от $it", color = Fusion.colors.textPrimary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold); Spacer(Modifier.height(4.dp)) }
            message.quote?.let { QuoteBlock(it); Spacer(Modifier.height(6.dp)) }
            when (message.kind) {
                MessageKind.Photo -> {
                    val photo = message.photo
                    if (photo != null) com.kirox.app.ui.components.TelegramPhoto(photo, message.photoAspect) else MediaPlaceholder(message.kind)
                    if (message.text.isNotBlank()) { Spacer(Modifier.height(6.dp)); Text(message.text, color = ChatWhite, fontSize = (15f * appearance.textScale).sp, lineHeight = (20f * appearance.textScale).sp) }
                }
                MessageKind.Video -> MediaPlaceholder(message.kind)
                MessageKind.Voice -> VoiceMessage(message.text, message.photo)
                MessageKind.Document -> DocumentMessage(message.text)
                MessageKind.Text -> Text(message.text, color = ChatWhite, fontSize = (15f * appearance.textScale).sp, lineHeight = (20f * appearance.textScale).sp)
            }
            Spacer(Modifier.height(4.dp))
            Row(Modifier.align(Alignment.End), verticalAlignment = Alignment.CenterVertically) {
                Text(if (message.edited) "изм. ${message.time}" else message.time, color = ChatSecondary, fontSize = 10.sp)
                if (outgoing && message.delivery != DeliveryState.None) { Spacer(Modifier.width(4.dp)); Text(deliveryGlyph(message.delivery), color = ChatSecondary, fontSize = 10.sp) }
            }
            if (message.reactions.isNotEmpty()) {
                Row(Modifier.padding(top = 5.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) { message.reactions.forEach { reaction -> Text("${reaction.emoji} ${reaction.count}", color = ChatWhite, fontSize = 11.sp, modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(if (reaction.selected) Fusion.colors.surface3 else Fusion.colors.surface3).padding(horizontal = 8.dp, vertical = 4.dp)) } }
            }
        }
    }
}

@Composable private fun QuoteBlock(quote: QuoteUi) { Row(Modifier.fillMaxWidth()) { Box(Modifier.width(2.dp).height(36.dp).background(ChatWhite)); Spacer(Modifier.width(7.dp)); Column { Text(quote.author, color = ChatWhite, fontSize = 11.sp, fontWeight = FontWeight.SemiBold); Text(quote.text, color = ChatSecondary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) } } }
@Composable private fun MediaPlaceholder(kind: MessageKind) { Box(Modifier.fillMaxWidth().height(132.dp).clip(RoundedCornerShape(12.dp)).background(Fusion.colors.surface3), contentAlignment = Alignment.Center) { Column(horizontalAlignment = Alignment.CenterHorizontally) { IconGlyph(if (kind == MessageKind.Video) KiroXIcon.Video else KiroXIcon.Media, null, Modifier.size(28.dp), ChatSecondary); Text(if (kind == MessageKind.Video) "Видео" else "Фото", color = ChatSecondary, fontSize = 12.sp) } } }
/** Voice note: tap plays or stops it; the file downloads on first view. */
@Composable private fun VoiceMessage(duration: String, file: com.kirox.app.telegram.TelegramFileRef?) {
    val files = com.kirox.app.ui.components.LocalTelegramFiles.current
    val path by remember(file?.id, files) {
        if (file != null && files != null) files.observe(file, 16) else kotlinx.coroutines.flow.MutableStateFlow(file?.localPath)
    }.collectAsState()
    var playing by remember(path) { mutableStateOf(path != null && VoicePlayer.playingPath == path) }
    Row(
        Modifier.clip(RoundedCornerShape(12.dp)).clickable(enabled = path != null) { path?.let { VoicePlayer.toggle(it) { playing = false }; playing = VoicePlayer.playingPath == it } },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(36.dp).clip(CircleShape).background(Fusion.colors.surface3), contentAlignment = Alignment.Center) {
            if (path == null) androidx.compose.material3.CircularProgressIndicator(Modifier.size(16.dp), color = ChatWhite, strokeWidth = 2.dp)
            else Text(if (playing) "❚❚" else "▶", color = ChatWhite, fontSize = 13.sp)
        }
        Spacer(Modifier.width(9.dp))
        Box(Modifier.width(110.dp).height(2.dp).background(if (playing) ChatWhite else ChatSecondary))
        Spacer(Modifier.width(8.dp))
        Text(duration, color = ChatSecondary, fontSize = 11.sp)
    }
}
@Composable private fun DocumentMessage(text: String) { Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(42.dp).clip(CircleShape).background(Fusion.colors.surface3), contentAlignment = Alignment.Center) { IconGlyph(KiroXIcon.File, null, Modifier.size(22.dp), ChatWhite) }; Spacer(Modifier.width(9.dp)); Text(text, color = ChatWhite, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis) } }
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
@Composable internal fun MessageActionsSheet(message: MessageUi, onDismiss: () -> Unit, onReply: () -> Unit, onReaction: (String) -> Unit, onAction: (String) -> Unit, reactions: Boolean = true, hidden: Set<String> = emptySet()) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Fusion.colors.surface1, contentColor = ChatWhite) {
        if (reactions) Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp), horizontalArrangement = Arrangement.SpaceAround) { listOf("♡", "👍", "🔥", "🙂", "👏").forEach { emoji -> Text(emoji, fontSize = 23.sp, modifier = Modifier.clip(CircleShape).clickable { onReaction(emoji) }.padding(9.dp)) } }
        val actions = buildList { add(KiroXIcon.Reply to "Ответить"); if (message.kind == MessageKind.Text) add(KiroXIcon.Copy to "Копировать"); add(KiroXIcon.Forward to "Переслать"); add(KiroXIcon.Pin to "Закрепить"); if (message.canEdit) add(KiroXIcon.Edit to "Редактировать"); if (message.canDelete) add(KiroXIcon.Delete to "Удалить"); add(KiroXIcon.Check to "Выбрать") }.filterNot { it.second in hidden }
        actions.forEach { (icon, title) -> Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable { if (title == "Ответить") onReply() else onAction(title) }.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) { IconGlyph(icon, null, Modifier.size(22.dp), ChatWhite); Spacer(Modifier.width(16.dp)); Text(title, color = if (title == "Удалить") Fusion.colors.danger else ChatWhite, fontSize = 15.sp) } }
        Spacer(Modifier.navigationBarsPadding())
    }
}

@Composable internal fun ChatIconButton(icon: KiroXIcon, description: String, onClick: () -> Unit) { Box(Modifier.size(48.dp).semantics { contentDescription = description }.clip(CircleShape).clickable(onClick = onClick), contentAlignment = Alignment.Center) { IconGlyph(icon, null, Modifier.size(23.dp), ChatWhite) } }

@Preview(name = "Incoming message", widthDp = 320, backgroundColor = 0xFF000000, showBackground = true)
@Composable private fun IncomingMessagePreview() { MessageRow(MessageUi(1, "Кирилл", MessageDirection.Incoming, text = "Короткое сообщение для проверки узкого экрана", time = "12:40"), false, ChatAppearance(), onLongClick = {}, onReply = {}) }

@Preview(name = "Outgoing long message", widthDp = 480, fontScale = 1.5f, backgroundColor = 0xFF000000, showBackground = true)
@Composable private fun OutgoingMessagePreview() { MessageRow(MessageUi(2, "Вы", MessageDirection.Outgoing, text = "Длинное сообщение проверяет переносы, системный масштаб текста и положение времени.", time = "12:41"), false, ChatAppearance(), onLongClick = {}, onReply = {}) }
