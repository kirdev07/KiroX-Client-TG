package com.kirox.app.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kirox.app.telegram.TelegramCall
import com.kirox.app.telegram.TelegramCallKind
import com.kirox.app.telegram.TelegramCallsState
import com.kirox.app.telegram.TelegramStory
import com.kirox.app.telegram.TelegramStoryChat
import com.kirox.app.ui.components.TelegramAvatar
import com.kirox.app.ui.components.rememberTelegramBitmap
import com.kirox.app.ui.format.TelegramFormat
import com.kirox.app.ui.icons.KiroXIcon
import com.kirox.app.ui.icons.KiroXIcon as IconGlyph
import com.kirox.app.ui.theme.Fusion
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter

/** Viewer data source for stories; implemented over the story repository. */
interface TelegramStoryActions {
    suspend fun story(chatId: Long, storyId: Int): TelegramStory?
    fun open(chatId: Long, storyId: Int)
    fun close(chatId: Long, storyId: Int)
}

// ---------- Calls ----------

private enum class CallFilter(val title: String) { All("Все"), Missed("Пропущенные"), Incoming("Входящие"), Outgoing("Исходящие") }

@Composable
internal fun CallsList(state: TelegramCallsState, onLoadMore: () -> Unit, onOpenChat: (Long) -> Unit, onCall: () -> Unit) {
    var filter by rememberSaveable { mutableStateOf(CallFilter.All) }
    val shown = remember(state.calls, filter) {
        state.calls.filter {
            when (filter) {
                CallFilter.All -> true
                CallFilter.Missed -> it.kind == TelegramCallKind.Missed
                CallFilter.Incoming -> it.kind == TelegramCallKind.Incoming || it.kind == TelegramCallKind.Missed
                CallFilter.Outgoing -> it.kind == TelegramCallKind.Outgoing || it.kind == TelegramCallKind.Unanswered
            }
        }
    }
    val listState = rememberLazyListState()
    LaunchedEffect(Unit) { onLoadMore() }
    PagingEffect(listState, state.complete, onLoadMore)
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CallFilter.entries.forEach { value ->
                Text(
                    value.title, color = if (filter == value) Fusion.colors.onAccent else Fusion.colors.textSecondary, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                    modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(if (filter == value) Fusion.colors.accent else Fusion.colors.surface2)
                        .clickable { filter = value }.padding(horizontal = 14.dp, vertical = 8.dp)
                )
            }
        }
        when {
            state.calls.isEmpty() && !state.complete -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator(Modifier.size(26.dp), color = Fusion.colors.textSecondary, strokeWidth = 2.dp) }
            shown.isEmpty() -> Box(Modifier.fillMaxSize().padding(32.dp), Alignment.Center) {
                Text(if (state.calls.isEmpty()) "Звонков пока нет" else "В этом разделе звонков нет", color = Fusion.colors.textSecondary, textAlign = TextAlign.Center)
            }
            else -> LazyColumn(Modifier.fillMaxSize(), state = listState) {
                items(groupCalls(shown), key = { "${it.first.chatId}:${it.first.messageId}" }) { (call, count) -> CallRow(call, count, { onOpenChat(call.chatId) }, onCall) }
                if (state.loading) item { Box(Modifier.fillMaxWidth().padding(16.dp), Alignment.Center) { CircularProgressIndicator(Modifier.size(20.dp), color = Fusion.colors.textSecondary, strokeWidth = 2.dp) } }
            }
        }
    }
}

@Composable
private fun PagingEffect(listState: LazyListState, complete: Boolean, loadMore: () -> Unit) {
    val latest by rememberUpdatedState(loadMore)
    LaunchedEffect(listState, complete) {
        if (complete) return@LaunchedEffect
        snapshotFlow { listState.layoutInfo.let { (it.visibleItemsInfo.lastOrNull()?.index ?: 0) >= it.totalItemsCount - 10 } }
            .distinctUntilChanged().filter { it }.collect { latest() }
    }
}

@Composable
private fun CallRow(call: TelegramCall, count: Int, onOpen: () -> Unit, onCall: () -> Unit) {
    val missed = call.kind == TelegramCallKind.Missed
    Row(Modifier.fillMaxWidth().clickable(onClick = onOpen).heightIn(min = 64.dp).padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        TelegramAvatar(call.peerName, call.photo, 56.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(if (count > 1) "${call.peerName} ($count)" else call.peerName, color = if (missed) Fusion.colors.danger else Fusion.colors.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically) {
                val icon = when (call.kind) {
                    TelegramCallKind.Incoming -> KiroXIcon.CallIncoming
                    TelegramCallKind.Outgoing, TelegramCallKind.Unanswered -> KiroXIcon.CallOutgoing
                    TelegramCallKind.Missed -> KiroXIcon.CallMissed
                }
                IconGlyph(icon, null, Modifier.size(14.dp), if (missed) Fusion.colors.danger else Fusion.colors.textSecondary)
                Spacer(Modifier.width(4.dp))
                Text(callSubtitle(call), color = Fusion.colors.textSecondary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Box(Modifier.size(48.dp).clip(CircleShape).clickable(onClick = onCall), contentAlignment = Alignment.Center) {
            IconGlyph(if (call.isVideo) KiroXIcon.Video else KiroXIcon.Call, "Позвонить", Modifier.size(22.dp), Fusion.colors.textPrimary)
        }
    }
    HorizontalDivider(Modifier.padding(start = 74.dp), color = Fusion.colors.outline)
}

internal fun callSubtitle(call: TelegramCall): String {
    val type = if (call.isVideo) "видеозвонок" else "звонок"
    val kind = when (call.kind) {
        TelegramCallKind.Incoming -> "Входящий $type"
        TelegramCallKind.Outgoing -> "Исходящий $type"
        TelegramCallKind.Missed -> "Пропущенный $type"
        TelegramCallKind.Unanswered -> "Без ответа"
    }
    val withDuration = if (call.durationSeconds > 0) "$kind · ${TelegramFormat.duration(call.durationSeconds)}" else kind
    return "$withDuration · ${TelegramFormat.callTime(call.date)}"
}

/** Merges consecutive calls with the same person and kind into one row with a count; history order is kept. */
internal fun groupCalls(calls: List<TelegramCall>): List<Pair<TelegramCall, Int>> {
    val groups = ArrayList<Pair<TelegramCall, Int>>()
    for (call in calls) {
        val last = groups.lastOrNull()
        if (last != null && last.first.chatId == call.chatId && last.first.kind == call.kind && last.first.isVideo == call.isVideo) {
            groups[groups.lastIndex] = last.first to last.second + 1
        } else groups += call to 1
    }
    return groups
}

// ---------- Stories ----------

@Composable
internal fun StoriesRow(chats: List<TelegramStoryChat>, myName: String, myPhoto: com.kirox.app.telegram.TelegramFileRef?, onOpen: (TelegramStoryChat) -> Unit, onAddStory: () -> Unit) {
    val own = chats.firstOrNull { it.isSelf }
    val others = chats.filterNot { it.isSelf }
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        // "Моя история" first, as in the old Fusion: own avatar with a "+" badge (or own stories when posted).
        Column(
            Modifier.padding(start = 12.dp).width(84.dp).clip(RoundedCornerShape(12.dp)).clickable { if (own != null) onOpen(own) else onAddStory() }.padding(vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(Modifier.size(70.dp)) {
                Box(
                    Modifier.size(70.dp).border(if (own?.hasUnread == true) 2.dp else 1.5.dp, if (own?.hasUnread == true) Fusion.colors.accent else Fusion.colors.outlineStrong, CircleShape).padding(4.dp),
                    contentAlignment = Alignment.Center
                ) { TelegramAvatar(myName, myPhoto, 62.dp) }
                if (own == null) Box(
                    Modifier.align(Alignment.BottomEnd).size(26.dp).clip(CircleShape).background(Fusion.colors.accent).border(2.5.dp, Fusion.colors.background, CircleShape),
                    contentAlignment = Alignment.Center
                ) { IconGlyph(KiroXIcon.Add, "Добавить историю", Modifier.size(15.dp), Fusion.colors.onAccent) }
            }
            Spacer(Modifier.height(6.dp))
            Text("Моя история", color = Fusion.colors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (others.isEmpty()) {
            Column(Modifier.weight(1f).padding(start = 12.dp, end = 16.dp)) {
                Text("Историй пока нет", color = Fusion.colors.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                Text("Нажмите +, чтобы поделиться своей", color = Fusion.colors.textSecondary, fontSize = 15.sp)
            }
        } else LazyRow(Modifier.weight(1f), contentPadding = PaddingValues(start = 4.dp, end = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(others, key = { it.chatId }) { chat ->
                Column(Modifier.width(78.dp).clip(RoundedCornerShape(12.dp)).clickable { onOpen(chat) }.padding(vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier.size(70.dp).border(if (chat.hasUnread) 2.dp else 1.dp, if (chat.hasUnread) Fusion.colors.accent else Fusion.colors.outlineStrong, CircleShape).padding(4.dp),
                        contentAlignment = Alignment.Center
                    ) { TelegramAvatar(chat.title, chat.photo, 62.dp) }
                    Spacer(Modifier.height(6.dp))
                    Text(chat.title, color = if (chat.hasUnread) Fusion.colors.textPrimary else Fusion.colors.textSecondary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

/** Full-screen viewer for one chat's active stories: tap right/left to move, photos advance after 6 s. */
@Composable
internal fun StoryViewer(chat: TelegramStoryChat, actions: TelegramStoryActions, onClose: () -> Unit) {
    var index by rememberSaveable(chat.chatId) { mutableIntStateOf(0) }
    val storyId = chat.storyIds.getOrNull(index)
    var story by remember(storyId) { mutableStateOf<TelegramStory?>(null) }
    var failed by remember(storyId) { mutableStateOf(false) }
    val progress = remember(storyId) { Animatable(0f) }
    BackHandler(onBack = onClose)

    fun next() { if (index < chat.storyIds.lastIndex) index++ else onClose() }
    fun previous() { if (index > 0) index-- }

    LaunchedEffect(storyId) {
        val id = storyId ?: return@LaunchedEffect onClose()
        val loaded = actions.story(chat.chatId, id)
        story = loaded
        failed = loaded == null
    }
    DisposableEffect(storyId) {
        val id = storyId
        if (id != null) actions.open(chat.chatId, id)
        onDispose { if (id != null) actions.close(chat.chatId, id) }
    }
    LaunchedEffect(storyId, story, failed) {
        if (story == null && !failed) return@LaunchedEffect
        progress.animateTo(1f, tween(STORY_MS, easing = LinearEasing))
        next()
    }

    Box(
        Modifier.fillMaxSize().background(Color.Black).pointerInput(storyId) {
            detectTapGestures { offset -> if (offset.x < size.width / 3f) previous() else next() }
        }
    ) {
        val current = story
        val widthPx = with(LocalDensity.current) { LocalConfiguration.current.screenWidthDp.dp.roundToPx() }
        val bitmap = rememberTelegramBitmap(current?.image, widthPx, priority = 24)
        when {
            bitmap != null -> Image(bitmap, contentDescription = "История", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
            failed -> Text("История недоступна", color = Color.White, modifier = Modifier.align(Alignment.Center))
            else -> CircularProgressIndicator(Modifier.align(Alignment.Center).size(28.dp), color = Color.White, strokeWidth = 2.dp)
        }
        Column(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                chat.storyIds.indices.forEach { i ->
                    val fill = when { i < index -> 1f; i == index -> progress.value; else -> 0f }
                    Box(Modifier.weight(1f).height(2.dp).clip(RoundedCornerShape(1.dp)).background(Color.White.copy(alpha = .3f))) {
                        Box(Modifier.fillMaxHeight().fillMaxWidth(fill).background(Color.White))
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                TelegramAvatar(chat.title, chat.photo, 34.dp)
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(if (chat.isSelf) "Моя история" else chat.title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    current?.let { Text(TelegramFormat.chatTime(it.date), color = Color.White.copy(alpha = .7f), fontSize = 12.sp) }
                }
                Text("×", color = Color.White, fontSize = 28.sp, modifier = Modifier.clip(CircleShape).clickable(onClick = onClose).padding(horizontal = 14.dp))
            }
        }
        val caption = listOfNotNull(
            current?.caption?.takeIf { it.isNotBlank() },
            if (current?.isVideo == true) "Видеоистории пока показываются обложкой" else null
        ).joinToString("\n")
        if (caption.isNotEmpty()) Text(
            caption, color = Color.White, fontSize = 14.sp, lineHeight = 19.sp,
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().background(Color.Black.copy(alpha = .45f)).padding(16.dp)
        )
    }
}

// Stories are photo/video content on a black stage in every theme, like the QR code.
private const val STORY_MS = 6_000
