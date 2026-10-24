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
            Modifier.graphicsLayer { translationX = offset.value }.background(Fusion.colors.background).draggable(
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
                    offset.animateTo(0f, tween(FusionMotion.NORMAL))
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
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).heightIn(min = 52.dp).padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
        IconGlyph(icon, null, Modifier.size(22.dp), Fusion.colors.textPrimary)
        Spacer(Modifier.width(16.dp))
        Text(title, color = Fusion.colors.textPrimary, fontSize = 15.sp)
    }
}

/** Search over all chats TDLib knows; debounced so typing does not flood the client. */
@Composable
internal fun ChatSearchResults(query: String, search: suspend (String) -> List<TelegramChatItem>, row: @Composable (TelegramChatItem) -> Unit) {
    var results by remember { mutableStateOf<List<TelegramChatItem>?>(null) }
    LaunchedEffect(query) {
        val q = query.trim()
        if (q.isEmpty()) { results = emptyList(); return@LaunchedEffect }
        results = null
        delay(250)
        results = search(q)
    }
    val current = results
    when {
        query.isBlank() -> Hint("Введите название чата или имя")
        current == null -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator(Modifier.size(24.dp), color = Fusion.colors.textSecondary, strokeWidth = 2.dp) }
        current.isEmpty() -> Hint("Ничего не найдено")
        else -> LazyColumn(Modifier.fillMaxSize()) { items(current, key = { it.id }) { row(it) } }
    }
}

@Composable
private fun Hint(text: String) {
    Box(Modifier.fillMaxSize().padding(32.dp), Alignment.Center) { Text(text, color = Fusion.colors.textSecondary, textAlign = TextAlign.Center) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ThemeSheet(palette: FusionPalette, onSelect: (FusionPalette) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Fusion.colors.surface1) {
        Text("Тема", color = Fusion.colors.textPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
        listOf(
            FusionPalette.AmoledBlack to ("AMOLED Black" to "Чистый чёрный фон, экономит заряд на OLED-экранах"),
            FusionPalette.GraphiteDark to ("Graphite Dark" to "Мягкий тёмно-серый фон")
        ).forEach { (value, texts) ->
            Row(Modifier.fillMaxWidth().clickable { onSelect(value) }.heightIn(min = 64.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                RadioButton(palette == value, { onSelect(value) }, colors = RadioButtonDefaults.colors(selectedColor = Fusion.colors.accent, unselectedColor = Fusion.colors.outlineStrong))
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(texts.first, color = Fusion.colors.textPrimary, fontSize = 15.sp)
                    Text(texts.second, color = Fusion.colors.textSecondary, fontSize = 12.sp)
                }
                Box(Modifier.size(28.dp).clip(CircleShape).background(if (value == FusionPalette.AmoledBlack) FusionColors.Amoled.background else FusionColors.Graphite.background).border(1.dp, Fusion.colors.outline, CircleShape))
            }
        }
        Spacer(Modifier.navigationBarsPadding().height(12.dp))
    }
}
