package com.kirox.app.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kirox.app.telegram.TelegramChatItem
import com.kirox.app.telegram.TelegramChatListKey
import com.kirox.app.ui.icons.KiroXIcon
import com.kirox.app.ui.icons.KiroXIcon as IconGlyph
import com.kirox.app.ui.theme.Fusion
import com.kirox.app.ui.theme.FusionColors
import com.kirox.app.ui.theme.FusionMotion
import com.kirox.app.ui.theme.FusionPalette
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

/** Chat list operations the main screen can trigger; implemented over the chat repository. */
interface TelegramChatListActions {
    fun loadMore(key: TelegramChatListKey)
    fun setArchived(chatId: Long, archived: Boolean)
    fun setPinned(key: TelegramChatListKey, chatId: Long, pinned: Boolean)
    fun markRead(chatId: Long)
    fun markUnread(chatId: Long)
    suspend fun search(query: String): List<TelegramChatItem>
    /** Messages in all chats matching the query, newest first. */
    suspend fun searchMessages(query: String): List<com.kirox.app.telegram.TelegramFoundMessage> = emptyList()
    /** After opening a chat from a message result: scroll it to that message. */
    fun jumpToMessage(messageId: Long) {}
}

/** Bottom toast; [actionLabel] adds a tappable action such as «Отменить». */
data class UiNotice(val text: String, val actionLabel: String? = null, val action: (() -> Unit)? = null)

/**
 * Row with two swipe actions: right → read/unread, left → archive/unarchive. The row snaps back after
 * the action, so the list itself reflects the result once TDLib confirms it.
 */
@Composable
internal fun SwipeableChatRow(chat: TelegramChatItem, onSwipeRight: () -> Unit, onSwipeLeft: () -> Unit, content: @Composable () -> Unit) {
    val offset = remember(chat.id) { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val threshold = with(LocalDensity.current) { 88.dp.toPx() }
    var armed by remember(chat.id) { mutableStateOf(false) }
    val returnMs = com.kirox.app.ui.theme.motionMs(FusionMotion.NORMAL)
    Box(Modifier.fillMaxWidth()) {
        val value = offset.value
        if (value != 0f) {
            val right = value > 0
            Row(
                Modifier.matchParentSize().background(if (abs(value) >= threshold) Fusion.colors.surface3 else Fusion.colors.surface2).padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = if (right) Arrangement.Start else Arrangement.End
            ) {
                val unread = chat.unreadCount > 0 || chat.isMarkedUnread
                IconGlyph(if (right) KiroXIcon.ReadAll else KiroXIcon.Folder, null, Modifier.size(22.dp), Fusion.colors.textPrimary)
                Spacer(Modifier.width(8.dp))
                Text(
                    if (right) (if (unread) "Прочитано" else "Непрочитано") else (if (chat.archived) "Из архива" else "В архив"),
                    color = Fusion.colors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium
                )
            }
        }
        Box(
            Modifier.graphicsLayer { translationX = offset.value }.background(if (offset.value != 0f) Fusion.colors.background else Fusion.colors.screen).draggable(
                orientation = Orientation.Horizontal,
                state = rememberDraggableState { delta ->
                    scope.launch {
                        offset.snapTo((offset.value + delta).coerceIn(-threshold * 1.4f, threshold * 1.4f))
                        val past = abs(offset.value) >= threshold
                        if (past != armed) { armed = past; if (past) haptics.performHapticFeedback(HapticFeedbackType.LongPress) }
                    }
                },
                onDragStopped = {
                    if (abs(offset.value) >= threshold) { if (offset.value > 0) onSwipeRight() else onSwipeLeft() }
                    armed = false
                    offset.animateTo(0f, tween(returnMs))
                }
            )
        ) { content() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ChatContextSheet(chat: TelegramChatItem, onDismiss: () -> Unit, onPin: () -> Unit, onRead: () -> Unit, onArchive: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Fusion.colors.surface1) {
        Text(chat.title, color = Fusion.colors.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
        val unread = chat.unreadCount > 0 || chat.isMarkedUnread
        SheetAction(KiroXIcon.Pin, if (chat.isPinned) "Открепить" else "Закрепить") { onPin(); onDismiss() }
        SheetAction(KiroXIcon.ReadAll, if (unread) "Отметить прочитанным" else "Отметить непрочитанным") { onRead(); onDismiss() }
        SheetAction(KiroXIcon.Folder, if (chat.archived) "Вернуть из архива" else "В архив") { onArchive(); onDismiss() }
        Spacer(Modifier.navigationBarsPadding().height(12.dp))
    }
}

@Composable
private fun SheetAction(icon: KiroXIcon, title: String, onClick: () -> Unit) {
    // Material 3 list item on the sheet surface.
    androidx.compose.material3.ListItem(
        headlineContent = { Text(title) },
        leadingContent = { IconGlyph(icon, null, Modifier.size(22.dp), Fusion.colors.textPrimary) },
        colors = androidx.compose.material3.ListItemDefaults.colors(containerColor = Fusion.colors.surface1, headlineColor = Fusion.colors.textPrimary),
        modifier = Modifier.clickable(onClick = onClick)
    )
}

/**
 * Global search: chats (own, server and public) and then messages from all chats, debounced so
 * typing does not flood the client.
 */
@Composable
internal fun ChatSearchResults(
    query: String,
    search: suspend (String) -> List<TelegramChatItem>,
    searchMessages: suspend (String) -> List<com.kirox.app.telegram.TelegramFoundMessage> = { emptyList() },
    onOpenMessage: (com.kirox.app.telegram.TelegramFoundMessage) -> Unit = {},
    row: @Composable (TelegramChatItem) -> Unit
) {
    var results by remember { mutableStateOf<List<TelegramChatItem>?>(null) }
    var messages by remember { mutableStateOf<List<com.kirox.app.telegram.TelegramFoundMessage>>(emptyList()) }
    LaunchedEffect(query) {
        val q = query.trim()
        messages = emptyList()
        if (q.isEmpty()) { results = emptyList(); return@LaunchedEffect }
        results = null
        delay(250)
        results = search(q)
        if (q.length >= 2) messages = searchMessages(q)
    }
    // Tabs of the approved search board; filtering is local over what the search returned.
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val tabs = listOf("Все", "Люди", "Каналы", "Группы", "Сообщения")
    val kind = when (tab) { 1 -> com.kirox.app.telegram.TelegramChatKind.Private; 2 -> com.kirox.app.telegram.TelegramChatKind.Channel; 3 -> com.kirox.app.telegram.TelegramChatKind.Group; else -> null }
    val current = results?.let { list -> when { tab == 4 -> emptyList(); kind != null -> list.filter { it.kind == kind }; else -> list } }
    val shownMessages = if (tab == 0 || tab == 4) messages else emptyList()
    if (query.isNotBlank()) androidx.compose.foundation.lazy.LazyRow(
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        items(tabs.size) { i ->
            val selected = i == tab
            Text(
                tabs[i], color = if (selected) Fusion.colors.onSelectedPill else Fusion.colors.textSecondary, fontSize = 14.sp, fontWeight = FontWeight.Medium,
                modifier = Modifier.clip(CircleShape).background(if (selected) Fusion.colors.selectedPill else androidx.compose.ui.graphics.Color.Transparent)
                    .clickable { tab = i }.padding(horizontal = 14.dp, vertical = 8.dp)
            )
        }
    }
    when {
        query.isBlank() -> Hint("Введите название чата, имя или текст сообщения")
        current == null -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator(Modifier.size(24.dp), color = Fusion.colors.textSecondary, strokeWidth = 2.dp) }
        current.isEmpty() && shownMessages.isEmpty() -> Hint("Ничего не найдено")
        else -> LazyColumn(Modifier.fillMaxSize()) {
            if (current.isNotEmpty()) item(key = "chats") { SectionTitle("Чаты и каналы") }
            items(current, key = { it.id }) { row(it) }
            if (shownMessages.isNotEmpty()) item(key = "messages") { SectionTitle("Сообщения") }
            items(shownMessages, key = { "m${it.chatId}_${it.messageId}" }) { found ->
                Row(Modifier.fillMaxWidth().clickable { onOpenMessage(found) }.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    com.kirox.app.ui.components.TelegramAvatar(found.chatTitle, found.photo, 44.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(found.chatTitle, color = Fusion.colors.textPrimary, fontSize = 15.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                            Text(com.kirox.app.ui.format.TelegramFormat.chatTime(found.date), color = Fusion.colors.textSecondary, fontSize = 12.sp)
                        }
                        Text(found.text, color = Fusion.colors.textSecondary, fontSize = 14.sp, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, color = Fusion.colors.textSecondary, fontSize = 13.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold, modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp))
}

@Composable
private fun Hint(text: String) {
    Box(Modifier.fillMaxSize().padding(32.dp), Alignment.Center) { Text(text, color = Fusion.colors.textSecondary, textAlign = TextAlign.Center) }
}
