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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
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
import kotlinx.coroutines.launch

/** Viewer data source for stories; implemented over the story repository. */
interface TelegramStoryActions {
    suspend fun story(chatId: Long, storyId: Int): TelegramStory?
    fun open(chatId: Long, storyId: Int)
    fun close(chatId: Long, storyId: Int)
    suspend fun react(chatId: Long, storyId: Int, emoji: String?) {}
    suspend fun reply(chatId: Long, storyId: Int, text: String) {}
    suspend fun delete(chatId: Long, storyId: Int) {}
    suspend fun viewers(storyId: Int): List<com.kirox.app.telegram.TelegramStoryViewer> = emptyList()
}

// ---------- Calls ----------

private enum class CallFilter(val title: String) { All("Все"), Missed("Пропущенные"), Incoming("Входящие"), Outgoing("Исходящие") }

@Composable
internal fun CallsList(state: TelegramCallsState, onLoadMore: () -> Unit, onOpenChat: (Long) -> Unit, onCall: (TelegramCall) -> Unit) {
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
    // A page of 50 calls can fold into a few rows, or none for "Пропущенные": keep loading until the
    // screen is filled or the history ends, without waiting for a scroll that cannot happen.
    LaunchedEffect(shown.size, state.loading, state.complete) {
        if (!state.complete && !state.loading && shown.size < 20) onLoadMore()
    }
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
                items(groupCalls(shown), key = { "${it.first.chatId}:${it.first.messageId}" }) { (call, count) -> CallRow(call, count, { onOpenChat(call.chatId) }, { onCall(call) }) }
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
        // The item count is part of the key: near the end after a new page arrived asks again.
        snapshotFlow { listState.layoutInfo.let { ((it.visibleItemsInfo.lastOrNull()?.index ?: 0) >= it.totalItemsCount - 10) to it.totalItemsCount } }
            .distinctUntilChanged().filter { it.first }.collect { latest() }
    }
}

@Composable
private fun CallRow(call: TelegramCall, count: Int, onOpen: () -> Unit, onCall: () -> Unit) {
    val missed = call.kind == TelegramCallKind.Missed
    Row(Modifier.fillMaxWidth().clickable(onClick = onOpen).heightIn(min = 64.dp).padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        TelegramAvatar(call.peerName, call.photo, 50.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(if (count > 1) "${call.peerName} ($count)" else call.peerName, color = if (missed) Fusion.colors.danger else Fusion.colors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
            Modifier.padding(start = 8.dp).width(84.dp).clip(RoundedCornerShape(12.dp)).clickable { if (own != null) onOpen(own) else onAddStory() }.padding(vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(Modifier.size(60.dp)) {
                Box(
                    Modifier.size(60.dp).border(if (own?.hasUnread == true) 2.dp else 1.5.dp, if (own?.hasUnread == true) Fusion.colors.accent else Fusion.colors.outlineStrong, CircleShape).padding(4.dp),
                    contentAlignment = Alignment.Center
                ) { TelegramAvatar(myName, myPhoto, 52.dp) }
                if (own == null) Box(
                    Modifier.align(Alignment.BottomEnd).size(22.dp).clip(CircleShape).background(Fusion.colors.accent).border(2.5.dp, Fusion.colors.background, CircleShape),
                    contentAlignment = Alignment.Center
                ) { IconGlyph(KiroXIcon.Add, "Добавить историю", Modifier.size(13.dp), Fusion.colors.onAccent) }
            }
            Spacer(Modifier.height(4.dp))
            Text("Моя история", color = Fusion.colors.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (others.isEmpty()) {
            Column(Modifier.weight(1f).padding(start = 12.dp, end = 16.dp)) {
                Text("Историй пока нет", color = Fusion.colors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text("Нажмите +, чтобы поделиться своей", color = Fusion.colors.textSecondary, fontSize = 13.sp, maxLines = 2)
            }
        } else LazyRow(Modifier.weight(1f), contentPadding = PaddingValues(start = 4.dp, end = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(others, key = { it.chatId }) { chat ->
                Column(Modifier.width(68.dp).clip(RoundedCornerShape(12.dp)).clickable { onOpen(chat) }.padding(vertical = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier.size(60.dp).border(if (chat.hasUnread) 2.dp else 1.dp, if (chat.hasUnread) Fusion.colors.accent else Fusion.colors.outlineStrong, CircleShape).padding(4.dp),
                        contentAlignment = Alignment.Center
                    ) { TelegramAvatar(chat.title, chat.photo, 52.dp) }
                    Spacer(Modifier.height(4.dp))
                    Text(chat.title, color = if (chat.hasUnread) Fusion.colors.textPrimary else Fusion.colors.textSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
    /** 0–1 of the current story; photos run for STORY_MS, videos follow the player. */
    var progress by remember(storyId) { mutableFloatStateOf(0f) }
    var holding by remember { mutableStateOf(false) }
    var replying by remember { mutableStateOf(false) }
    var viewersOpen by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }
    var inBackground by remember { mutableStateOf(false) }
    androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_STOP) { inBackground = true }
    androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_START) { inBackground = false }
    val paused = holding || replying || viewersOpen || confirmDelete || inBackground
    val scope = rememberCoroutineScope()
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
    // Photo stories (and failed ones) advance on a timer that stops while held.
    LaunchedEffect(storyId, story, failed, paused) {
        val current = story
        if (current == null && !failed) return@LaunchedEffect
        if (current?.isVideo == true || paused) return@LaunchedEffect
        val step = 50L
        while (progress < 1f) { kotlinx.coroutines.delay(step); progress += step.toFloat() / STORY_MS }
        next()
    }
    LaunchedEffect(notice) { if (notice != null) { kotlinx.coroutines.delay(2200); notice = null } }

    Box(
        Modifier.fillMaxSize().background(Color.Black).pointerInput(storyId) {
            detectTapGestures(
                onPress = { holding = true; tryAwaitRelease(); holding = false },
                onTap = { offset -> if (offset.x < size.width / 3f) previous() else next() }
            )
        }
    ) {
        val current = story
        val widthPx = with(LocalDensity.current) { com.kirox.app.ui.chat.windowWidthDp().coerceAtLeast(320).dp.roundToPx() }
        val bitmap = rememberTelegramBitmap(current?.image, widthPx, priority = 24)
        bitmap?.let { Image(it, contentDescription = "История", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize()) }
        val video = current?.video
        if (current != null && video != null) StoryVideo(video, current.durationS, paused, onProgress = { progress = it }, onEnd = { next() })
        when {
            failed -> Text("История недоступна", color = Color.White, modifier = Modifier.align(Alignment.Center))
            current == null || (bitmap == null && video == null) -> CircularProgressIndicator(Modifier.align(Alignment.Center).size(28.dp), color = Color.White, strokeWidth = 2.dp)
        }
        Column(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                chat.storyIds.indices.forEach { i ->
                    val fill = when { i < index -> 1f; i == index -> progress; else -> 0f }
                    Box(Modifier.weight(1f).height(2.dp).clip(RoundedCornerShape(1.dp)).background(Color.White.copy(alpha = .3f))) {
                        Box(Modifier.fillMaxHeight().fillMaxWidth(fill.coerceIn(0f, 1f)).background(Color.White))
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
                if (current?.canDelete == true) Text(
                    "Удалить", color = Color.White, fontSize = 13.sp,
                    modifier = Modifier.clip(RoundedCornerShape(16.dp)).clickable { confirmDelete = true }.padding(horizontal = 10.dp, vertical = 8.dp)
                )
                Text("×", color = Color.White, fontSize = 28.sp, modifier = Modifier.clip(CircleShape).clickable(onClick = onClose).padding(horizontal = 14.dp))
            }
        }
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().imePadding()) {
            current?.caption?.takeIf { it.isNotBlank() }?.let {
                Text(it, color = Color.White, fontSize = 14.sp, lineHeight = 19.sp, modifier = Modifier.fillMaxWidth().background(Color.Black.copy(alpha = .45f)).padding(16.dp))
            }
            notice?.let { Text(it, color = Color.White, fontSize = 13.sp, modifier = Modifier.align(Alignment.CenterHorizontally).padding(8.dp).clip(RoundedCornerShape(16.dp)).background(Color(0xCC1C1C1E)).padding(horizontal = 14.dp, vertical = 8.dp)) }
            if (current != null) Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                if (current.canSeeViews) Text(
                    "👁 ${current.viewCount}", color = Color.White, fontSize = 14.sp,
                    modifier = Modifier.clip(RoundedCornerShape(18.dp)).background(Color(0x33FFFFFF)).clickable { viewersOpen = true }.padding(horizontal = 14.dp, vertical = 9.dp)
                )
                if (current.canReply) StoryReplyField(
                    onFocus = { replying = it },
                    onSend = { text ->
                        scope.launch {
                            notice = runCatching { actions.reply(current.chatId, current.id, text) }.fold({ "Ответ отправлен" }, { "Не удалось отправить" })
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) else Spacer(Modifier.weight(1f))
                if (!current.canSeeViews) {
                    val liked = current.chosenReaction != null
                    Text(
                        if (liked) "❤" else "♡", color = Color.White, fontSize = 26.sp,
                        modifier = Modifier.padding(start = 10.dp).clip(CircleShape).clickable {
                            val updated = current.copy(chosenReaction = if (liked) null else "❤")
                            story = updated
                            scope.launch { runCatching { actions.react(current.chatId, current.id, updated.chosenReaction) }.onFailure { story = current; notice = "Реакция недоступна" } }
                        }.padding(8.dp)
                    )
                }
            }
        }
    }
    if (viewersOpen) story?.let { own -> StoryViewersSheet(own.id, actions) { viewersOpen = false } }
    if (confirmDelete) story?.let { own ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = Fusion.colors.surface1,
            title = { Text("Удалить историю?", color = Fusion.colors.textPrimary) },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    confirmDelete = false
                    scope.launch { runCatching { actions.delete(own.chatId, own.id) }.onSuccess { next() }.onFailure { notice = "Не удалось удалить" } }
                }) { Text("Удалить", color = Fusion.colors.danger) }
            },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { confirmDelete = false }) { Text("Отмена", color = Fusion.colors.textSecondary) } }
        )
    }
}

/** Video story: downloads, then plays with sound; pauses with [paused] and reports its progress. */
@Composable
private fun StoryVideo(file: com.kirox.app.telegram.TelegramFileRef, durationS: Double, paused: Boolean, onProgress: (Float) -> Unit, onEnd: () -> Unit) {
    val files = com.kirox.app.ui.components.LocalTelegramFiles.current
    val state by remember(file.id, files) { files?.state(file) ?: kotlinx.coroutines.flow.MutableStateFlow(com.kirox.app.telegram.TelegramFileState(path = file.localPath)) }.collectAsState()
    LaunchedEffect(file.id) { if (state.path == null) files?.download(file, priority = 32) }
    val path = state.path ?: return
    val context = androidx.compose.ui.platform.LocalContext.current
    val player = remember(path) {
        com.kirox.app.ui.chat.VoicePlayer.stop()
        androidx.media3.exoplayer.ExoPlayer.Builder(context.applicationContext).build().apply {
            // Takes audio focus like any video: music pauses, a call interrupts it.
            setAudioAttributes(
                androidx.media3.common.AudioAttributes.Builder().setUsage(androidx.media3.common.C.USAGE_MEDIA).setContentType(androidx.media3.common.C.AUDIO_CONTENT_TYPE_MOVIE).build(),
                true
            )
            setMediaItem(androidx.media3.common.MediaItem.fromUri(android.net.Uri.fromFile(java.io.File(path))))
            prepare()
        }
    }
    DisposableEffect(player) {
        val listener = object : androidx.media3.common.Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) { if (playbackState == androidx.media3.common.Player.STATE_ENDED) onEnd() }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener); player.release() }
    }
    var background by remember { mutableStateOf(false) }
    androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_STOP) { background = true }
    androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_START) { background = false }
    LaunchedEffect(player, paused, background) { player.playWhenReady = !paused && !background }
    LaunchedEffect(player) {
        while (true) {
            val total = player.duration.takeIf { it > 0 } ?: (durationS * 1000).toLong().coerceAtLeast(1)
            onProgress(player.currentPosition.toFloat() / total)
            kotlinx.coroutines.delay(50)
        }
    }
    androidx.compose.ui.viewinterop.AndroidView(
        factory = { ctx -> androidx.media3.ui.PlayerView(ctx).apply { useController = false; this.player = player } },
        modifier = Modifier.fillMaxSize()
    )
}

@Composable
private fun StoryReplyField(onFocus: (Boolean) -> Unit, onSend: (String) -> Unit, modifier: Modifier) {
    var text by remember { mutableStateOf("") }
    Row(modifier.clip(RoundedCornerShape(22.dp)).border(1.dp, Color(0x66FFFFFF), RoundedCornerShape(22.dp)).padding(start = 14.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.foundation.text.BasicTextField(
            text, { text = it }, singleLine = true,
            textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 15.sp),
            cursorBrush = androidx.compose.ui.graphics.SolidColor(Color.White),
            modifier = Modifier.weight(1f).padding(vertical = 11.dp).onFocusChanged { onFocus(it.isFocused) },
            decorationBox = { inner -> if (text.isEmpty()) Text("Ответить…", color = Color.White.copy(alpha = .6f), fontSize = 15.sp); inner() }
        )
        if (text.isNotBlank()) Text(
            "➤", color = Color.White, fontSize = 18.sp,
            modifier = Modifier.clip(CircleShape).clickable { onSend(text.trim()); text = "" }.padding(10.dp)
        )
    }
}

/** Who viewed an own story, with their reactions. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun StoryViewersSheet(storyId: Int, actions: TelegramStoryActions, onDismiss: () -> Unit) {
    var viewers by remember { mutableStateOf<List<com.kirox.app.telegram.TelegramStoryViewer>?>(null) }
    LaunchedEffect(storyId) { viewers = runCatching { actions.viewers(storyId) }.getOrDefault(emptyList()) }
    androidx.compose.material3.ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Fusion.colors.surface1) {
        Text("Просмотры", color = Fusion.colors.textPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
        val list = viewers
        when {
            list == null -> Box(Modifier.fillMaxWidth().padding(24.dp), Alignment.Center) { CircularProgressIndicator(Modifier.size(24.dp), color = Fusion.colors.textPrimary, strokeWidth = 2.dp) }
            list.isEmpty() -> Text("Пока никто не посмотрел", color = Fusion.colors.textSecondary, modifier = Modifier.padding(20.dp))
            else -> LazyColumn(Modifier.fillMaxWidth().heightIn(max = 480.dp)) {
                items(list) { viewer ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        TelegramAvatar(viewer.name, viewer.photo, 40.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(viewer.name, color = Fusion.colors.textPrimary, fontSize = 15.sp)
                            Text(TelegramFormat.chatTime(viewer.date), color = Fusion.colors.textSecondary, fontSize = 12.sp)
                        }
                        viewer.reaction?.let { Text(it, fontSize = 20.sp) }
                    }
                }
            }
        }
        Spacer(Modifier.navigationBarsPadding().height(12.dp))
    }
}

// Stories are photo/video content on a black stage in every theme, like the QR code.
private const val STORY_MS = 6_000
