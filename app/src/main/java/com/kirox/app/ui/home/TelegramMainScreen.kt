package com.kirox.app.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.background
import kotlinx.coroutines.launch
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kirox.app.BuildConfig
import com.kirox.app.telegram.LastMessageState
import com.kirox.app.telegram.TelegramChatItem
import com.kirox.app.telegram.TelegramChatListKey
import com.kirox.app.telegram.TelegramChatLists
import com.kirox.app.telegram.TelegramConnection
import com.kirox.app.telegram.TelegramContact
import com.kirox.app.telegram.TelegramCallsState
import com.kirox.app.telegram.TelegramStoryChat
import com.kirox.app.telegram.TelegramUserProfile
import com.kirox.app.telegram.TelegramUserStatus
import com.kirox.app.ui.components.FusionBottomBar
import androidx.compose.foundation.border
import com.kirox.app.ui.components.FusionNavigationRail
import com.kirox.app.ui.theme.FusionPalette
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.focus.focusRequester
import com.kirox.app.ui.components.FusionNavItem
import com.kirox.app.ui.components.TelegramAvatar
import com.kirox.app.ui.format.TelegramFormat
import com.kirox.app.ui.icons.KiroXIcon
import com.kirox.app.ui.icons.KiroXIcon as IconGlyph
import com.kirox.app.ui.theme.Fusion
import com.kirox.app.ui.theme.FusionMotion
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter

private enum class MainTab(val item: FusionNavItem) {
    Contacts(FusionNavItem("Контакты", KiroXIcon.Contacts)),
    Chats(FusionNavItem("Чаты", KiroXIcon.Chats)),
    Settings(FusionNavItem("Настройки", KiroXIcon.Tune)),
    Profile(FusionNavItem("Профиль", KiroXIcon.ProfileCircle))
}
private enum class SubScreen { None, NewMessage, Customization, Themes, ChatStyle, Animations, Updates, Devices, ProfileEdit, Notifications, Privacy, Storage, Premium, Advanced }
private enum class RealChatSection { Archive, Messages }

/** chatId → "печатает…"-style line shown in a chat row instead of its last message. */
val LocalChatActivityLines = androidx.compose.runtime.compositionLocalOf<Map<Long, String>> { emptyMap() }

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TelegramMainScreen(
    lists: TelegramChatLists,
    chatActions: TelegramChatListActions,
    chatError: String?,
    onChatErrorShown: () -> Unit,
    contacts: List<TelegramContact>,
    contactsLoading: Boolean,
    profile: TelegramUserProfile?,
    connection: TelegramConnection,
    onOpenChat: (Long) -> Unit,
    onOpenContact: (Long) -> Unit,
    calls: TelegramCallsState,
    onLoadCalls: () -> Unit,
    storyChats: List<TelegramStoryChat>,
    storyActions: TelegramStoryActions,
    palette: FusionPalette,
    onPalette: (FusionPalette) -> Unit,
    onOpenSavedMessages: () -> Unit,
    seasonal: Boolean,
    onSeasonal: (Boolean) -> Unit,
    bubbleShape: com.kirox.app.settings.BubbleShape,
    onBubbleShape: (com.kirox.app.settings.BubbleShape) -> Unit,
    motion: com.kirox.app.settings.MotionLevel,
    onMotion: (com.kirox.app.settings.MotionLevel) -> Unit,
    chatTextSize: com.kirox.app.settings.ChatTextSize,
    onChatTextSize: (com.kirox.app.settings.ChatTextSize) -> Unit,
    chatWallpaper: com.kirox.app.settings.ChatWallpaper,
    onChatWallpaper: (com.kirox.app.settings.ChatWallpaper) -> Unit,
    chatDensity: com.kirox.app.settings.ChatDensity,
    onChatDensity: (com.kirox.app.settings.ChatDensity) -> Unit,
    effectScene: com.kirox.app.settings.EffectScene,
    onEffectScene: (com.kirox.app.settings.EffectScene) -> Unit,
    effectsQuality: com.kirox.app.settings.EffectsQuality,
    onEffectsQuality: (com.kirox.app.settings.EffectsQuality) -> Unit,
    weakDevice: Boolean,
    onLogOut: () -> Unit,
    onStartCall: (userId: Long, isVideo: Boolean) -> Unit = { _, _ -> },
    onAddContact: suspend (phone: String, firstName: String, lastName: String) -> Long? = { _, _, _ -> null }
) = androidx.compose.runtime.CompositionLocalProvider(com.kirox.app.ui.components.LocalSeasonalEffects provides seasonal) {
    var tab by rememberSaveable { mutableStateOf(MainTab.Chats) }
    var sub by rememberSaveable { mutableStateOf(SubScreen.None) }
    var notice by remember { mutableStateOf<UiNotice?>(null) }
    var viewingStoriesOf by rememberSaveable { mutableStateOf<Long?>(null) }
    val keyboardOpen = WindowInsets.isImeVisible
    val showNotice: (String) -> Unit = { notice = UiNotice(it) }
    LaunchedEffect(chatError) { if (chatError != null) { notice = UiNotice(chatError); onChatErrorShown() } }
    val navItems = MainTab.entries.map { it.item }
    BoxWithConstraints(Modifier.fillMaxSize()) {
    val wide = maxWidth >= 600.dp
    Row(Modifier.fillMaxSize()) {
    if (wide) FusionNavigationRail(navItems, tab.ordinal, { tab = MainTab.entries[it]; sub = SubScreen.None })
    Scaffold(
        modifier = Modifier.weight(1f),
        containerColor = Fusion.colors.background,
        contentWindowInsets = WindowInsets.statusBars,
        bottomBar = {
            if (!keyboardOpen && !wide) FusionBottomBar(navItems, tab.ordinal, { tab = MainTab.entries[it]; sub = SubScreen.None })
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding()) {
            val fadeInMs = com.kirox.app.ui.theme.motionMs(FusionMotion.NORMAL)
            val fadeOutMs = com.kirox.app.ui.theme.motionMs(FusionMotion.FAST)
            AnimatedContent(tab to sub, transitionSpec = { fadeIn(tween(fadeInMs)) togetherWith fadeOut(tween(fadeOutMs)) }, label = "main_tabs") { (current, subScreen) ->
                when {
                    subScreen == SubScreen.NewMessage -> NewMessageScreen(contacts, onBack = { sub = SubScreen.None }, onOpenContact = { sub = SubScreen.None; onOpenContact(it) }, onOpenSaved = { sub = SubScreen.None; onOpenSavedMessages() }, onOpenChat = { sub = SubScreen.None; onOpenChat(it) }, onNotice = showNotice)
                    subScreen == SubScreen.Customization -> CustomizationScreen(
                        palette, bubbleShape,
                        animationsSummary = if (seasonal) effectScene.title else "Выкл.",
                        animationsOn = seasonal,
                        onOpenThemes = { sub = SubScreen.Themes },
                        onOpenChats = { sub = SubScreen.ChatStyle },
                        onOpenAnimations = { sub = SubScreen.Animations },
                        onBack = { sub = SubScreen.None }
                    )
                    subScreen == SubScreen.Notifications -> NotificationSettingsScreen(onBack = { sub = SubScreen.None }, onNotice = showNotice)
                    subScreen == SubScreen.Privacy -> PrivacyScreen(onBack = { sub = SubScreen.None }, onNotice = showNotice)
                    subScreen == SubScreen.Storage -> StorageScreen(onBack = { sub = SubScreen.None }, onNotice = showNotice)
                    subScreen == SubScreen.Premium -> PremiumScreen(profile, onBack = { sub = SubScreen.None })
                    subScreen == SubScreen.Advanced -> AdvancedScreen(onBack = { sub = SubScreen.None }, onLogOut = onLogOut, onNotice = showNotice)
                    subScreen == SubScreen.ProfileEdit -> ProfileEditScreen(profile, onBack = { sub = SubScreen.None }, onNotice = showNotice)
                    subScreen == SubScreen.Devices -> DevicesScreen(
                        (androidx.compose.ui.platform.LocalContext.current.applicationContext as com.kirox.app.KiroXApplication).telegram.sessions,
                        onBack = { sub = SubScreen.None }, onNotice = showNotice
                    )
                    subScreen == SubScreen.Updates -> UpdatesScreen(
                        (androidx.compose.ui.platform.LocalContext.current.applicationContext as com.kirox.app.KiroXApplication).updates,
                        onBack = { sub = SubScreen.None }
                    )
                    subScreen == SubScreen.Themes -> ThemesScreen(palette, onPalette, onBack = { sub = SubScreen.Customization })
                    subScreen == SubScreen.ChatStyle -> ChatStyleScreen(
                        bubbleShape, onBubbleShape, chatTextSize, onChatTextSize, chatWallpaper, onChatWallpaper, chatDensity, onChatDensity,
                        onBack = { sub = SubScreen.Customization }
                    )
                    subScreen == SubScreen.Animations -> AnimationsScreen(
                        motion, onMotion, seasonal, onSeasonal, effectScene, onEffectScene, effectsQuality, onEffectsQuality, weakDevice,
                        onBack = { sub = SubScreen.Customization }
                    )
                    current == MainTab.Chats -> ChatsTab(lists, chatActions, connection, onOpenChat, storyChats, profile, { viewingStoriesOf = it.chatId }, onNewMessage = { sub = SubScreen.NewMessage }) { notice = it }
                    current == MainTab.Contacts -> ContactsTab(contacts, contactsLoading, onOpenContact, calls, onLoadCalls, onOpenChat, showNotice, onStartCall, onAddContact)
                    current == MainTab.Settings -> SettingsTab(profile, onOpenProfile = { tab = MainTab.Profile }, onOpenCustomization = { sub = SubScreen.Customization }, onOpenUpdates = { sub = SubScreen.Updates }, onOpenDevices = { sub = SubScreen.Devices }, onEditProfile = { sub = SubScreen.ProfileEdit }, onOpenSection = { section ->
                        sub = when (section) {
                            "Уведомления" -> SubScreen.Notifications
                            "Конфиденциальность" -> SubScreen.Privacy
                            "Данные и память" -> SubScreen.Storage
                            "Telegram Premium" -> SubScreen.Premium
                            "Чаты и папки" -> SubScreen.ChatStyle
                            "Возможности KiroX" -> SubScreen.Customization
                            else -> SubScreen.Advanced
                        }
                    }, onNotice = showNotice)
                    else -> ProfileTab(profile, onOpenSettings = { tab = MainTab.Settings }, onOpenSaved = onOpenSavedMessages, onLogOut = onLogOut, onEditProfile = { sub = SubScreen.ProfileEdit }, onNotice = showNotice)
                }
            }
            // Material 3 snackbar; an undo action keeps it a little longer.
            val snackbars = remember { androidx.compose.material3.SnackbarHostState() }
            LaunchedEffect(notice) {
                val current = notice ?: return@LaunchedEffect
                val result = snackbars.showSnackbar(
                    current.text, current.actionLabel,
                    duration = if (current.actionLabel != null) androidx.compose.material3.SnackbarDuration.Long else androidx.compose.material3.SnackbarDuration.Short
                )
                if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) current.action?.invoke()
                notice = null
            }
            androidx.compose.material3.SnackbarHost(snackbars, Modifier.align(Alignment.BottomCenter).padding(8.dp)) { data ->
                androidx.compose.material3.Snackbar(
                    data,
                    containerColor = Fusion.colors.surface3,
                    contentColor = Fusion.colors.textPrimary,
                    actionColor = Fusion.colors.textPrimary
                )
            }
        }
    }
    }
    }
    val storyChat = storyChats.firstOrNull { it.chatId == viewingStoriesOf }
    if (storyChat != null) StoryViewer(storyChat, storyActions) { viewingStoriesOf = null }
}

// ---------- Chats ----------

@Composable
private fun ChatsTab(
    lists: TelegramChatLists,
    actions: TelegramChatListActions,
    connection: TelegramConnection,
    onOpenChat: (Long) -> Unit,
    storyChats: List<TelegramStoryChat>,
    profile: TelegramUserProfile?,
    onOpenStories: (TelegramStoryChat) -> Unit,
    onNewMessage: () -> Unit,
    onNotice: (UiNotice) -> Unit
) {
    var confirmReadAll by remember { mutableStateOf(false) }
    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var menuFor by remember { mutableStateOf<TelegramChatItem?>(null) }
    androidx.activity.compose.BackHandler(enabled = searching) { searching = false; query = "" }
    fun archive(chat: TelegramChatItem) {
        val archived = !chat.archived
        actions.setArchived(chat.id, archived)
        onNotice(UiNotice(if (archived) "Чат перемещён в архив" else "Чат возвращён из архива", "Отменить") { actions.setArchived(chat.id, !archived) })
    }
    fun toggleRead(chat: TelegramChatItem) = if (chat.unreadCount > 0 || chat.isMarkedUnread) actions.markRead(chat.id) else actions.markUnread(chat.id)
    if (searching) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().heightIn(min = 58.dp).padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                HeaderAction(KiroXIcon.Back, "Закрыть поиск") { searching = false; query = "" }
                Box(Modifier.weight(1f)) { SearchField(query, "Поиск чатов", autoFocus = true) { query = it } }
            }
            ChatSearchResults(
                query, actions::search, actions::searchMessages,
                onOpenMessage = { found -> searching = false; query = ""; onOpenChat(found.chatId); actions.jumpToMessage(found.messageId) }
            ) { chat -> ChatRow(chat) { searching = false; query = ""; onOpenChat(chat.id) } }
        }
        return
    }
    var section by rememberSaveable { mutableStateOf(RealChatSection.Messages) }
    var folderId by rememberSaveable { mutableStateOf<Int?>(null) }
    // Folders arrive a moment after start: a saved folder is dropped only once the list is known.
    if (folderId != null && (lists.folders.isNotEmpty() || lists.main.chats.isNotEmpty()) && lists.folders.none { it.id == folderId }) folderId = null
    val listKey: TelegramChatListKey = when {
        section == RealChatSection.Archive -> TelegramChatListKey.Archive
        folderId != null -> TelegramChatListKey.Folder(folderId!!)
        else -> TelegramChatListKey.Main
    }
    menuFor?.let { chat ->
        ChatContextSheet(
            chat, onDismiss = { menuFor = null },
            onPin = { actions.setPinned(if (chat.archived) TelegramChatListKey.Archive else listKey, chat.id, !chat.isPinned) },
            onRead = { toggleRead(chat) },
            onArchive = { archive(chat) }
        )
    }
    val state = lists[listKey]
    if (confirmReadAll) {
        val unread = state.chats.filter { it.unreadCount > 0 || it.isMarkedUnread }
        AlertDialog(
            onDismissRequest = { confirmReadAll = false },
            containerColor = Fusion.colors.surface2,
            title = { Text("Отметить прочитанным?", color = Fusion.colors.textPrimary) },
            text = { Text(if (unread.isEmpty()) "В этом разделе нет непрочитанных чатов." else "Чатов в текущем разделе: ${unread.size}. Другие разделы не изменятся.", color = Fusion.colors.textSecondary) },
            confirmButton = { TextButton(onClick = { unread.forEach { actions.markRead(it.id) }; confirmReadAll = false }, enabled = unread.isNotEmpty()) { Text("Прочитать", color = Fusion.colors.textPrimary) } },
            dismissButton = { TextButton(onClick = { confirmReadAll = false }) { Text("Отмена", color = Fusion.colors.textSecondary) } }
        )
    }
    Column(Modifier.fillMaxSize()) {
        ScreenTitle(connectionTitle(connection) ?: "Чаты") {
            TitleAction(KiroXIcon.ReadAll, "Отметить всё прочитанным") { confirmReadAll = true }
            TitleAction(KiroXIcon.Compose, "Новое сообщение", onClick = onNewMessage)
            TitleAction(KiroXIcon.Search, "Поиск") { searching = true }
        }
        val postStory = rememberStoryPoster { onNotice(UiNotice(it)) }
        StoriesRow(storyChats, profile?.name.orEmpty(), profile?.photo, onOpenStories) { postStory() }
        val archiveUnread = lists.archive.chats.count { it.unreadCount > 0 || it.isMarkedUnread }
        SectionTabs(listOf((if (archiveUnread > 0) "Архив · $archiveUnread" else "Архив") to RealChatSection.Archive, "Сообщения" to RealChatSection.Messages), section) { section = it }
        var creatingFolder by remember { mutableStateOf(false) }
        var deletingFolder by remember { mutableStateOf<com.kirox.app.telegram.TelegramFolder?>(null) }
        if (creatingFolder) CreateFolderDialog(lists.main.chats, onDismiss = { creatingFolder = false }) { onNotice(UiNotice(it)) }
        deletingFolder?.let { folder -> DeleteFolderDialog(folder, onDismiss = { deletingFolder = null }) { onNotice(UiNotice(it)) } }
        if (section == RealChatSection.Messages) {
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { FolderChip("Все", folderId == null) { folderId = null } }
                items(lists.folders, key = { it.id }) { folder -> FolderChip(folder.title.ifBlank { "Папка" }, folderId == folder.id, onLongClick = { deletingFolder = folder }) { folderId = folder.id } }
                item { FolderChip("+ Папка", false) { creatingFolder = true } }
            }
            HorizontalDivider(color = Fusion.colors.outline)
        }
        key(listKey) {
            val listState = rememberLazyListState()
            LoadMoreEffect(listState, state.chats.size, state.complete) { actions.loadMore(listKey) }
            when {
                state.chats.isEmpty() && !state.complete -> CenteredProgress()
                state.chats.isEmpty() -> EmptyState(
                    when (listKey) {
                        TelegramChatListKey.Archive -> "Архив пуст"
                        is TelegramChatListKey.Folder -> "В этой папке нет чатов"
                        TelegramChatListKey.Main -> "Здесь появятся ваши чаты"
                    }
                )
                else -> LazyColumn(Modifier.fillMaxSize(), state = listState) {
                    items(state.chats, key = { it.id }) { chat ->
                        SwipeableChatRow(chat, onSwipeRight = { toggleRead(chat) }, onSwipeLeft = { archive(chat) }) {
                            ChatRow(chat, onLongClick = { menuFor = chat }) { onOpenChat(chat.id) }
                        }
                    }
                    if (state.loading) item { Box(Modifier.fillMaxWidth().padding(16.dp), Alignment.Center) { CircularProgressIndicator(Modifier.size(22.dp), color = Fusion.colors.textSecondary, strokeWidth = 2.dp) } }
                }
            }
        }
    }
}

/** Requests the next page when the list is short or the user nears its end. */
@Composable
private fun LoadMoreEffect(listState: LazyListState, count: Int, complete: Boolean, loadMore: () -> Unit) {
    val latest by rememberUpdatedState(loadMore)
    LaunchedEffect(listState, complete) {
        if (complete) return@LaunchedEffect
        // With the item count in the key, staying near the end after a page arrived asks for the next one.
        snapshotFlow { listState.layoutInfo.let { info -> (info.totalItemsCount == 0 || (info.visibleItemsInfo.lastOrNull()?.index ?: 0) >= info.totalItemsCount - 10) to info.totalItemsCount } }
            .distinctUntilChanged().filter { it.first }.collect { latest() }
    }
    LaunchedEffect(count) { if (!complete && listState.layoutInfo.visibleItemsInfo.size >= count) latest() }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun ChatRow(chat: TelegramChatItem, onLongClick: (() -> Unit)? = null, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onLongClick, onLongClickLabel = "Действия с чатом").heightIn(min = 74.dp).padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (chat.isSaved) Box(Modifier.size(54.dp).clip(CircleShape).background(Fusion.colors.surface3), contentAlignment = Alignment.Center) {
            IconGlyph(KiroXIcon.Bookmark, null, Modifier.size(24.dp), Fusion.colors.textPrimary)
        } else TelegramAvatar(chat.title, chat.photo, 54.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                    Text(chat.title, color = Fusion.colors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    if (chat.isMuted) IconGlyph(KiroXIcon.Bell, "Без звука", Modifier.padding(start = 4.dp).size(13.dp), Fusion.colors.textTertiary)
                }
                Spacer(Modifier.width(8.dp))
                when (chat.outgoingState) {
                    LastMessageState.Read -> IconGlyph(KiroXIcon.ReadAll, "Прочитано", Modifier.padding(end = 4.dp).size(15.dp), Fusion.colors.textSecondary)
                    LastMessageState.Sent -> IconGlyph(KiroXIcon.Check, "Отправлено", Modifier.padding(end = 4.dp).size(15.dp), Fusion.colors.textSecondary)
                    LastMessageState.None -> Unit
                }
                Text(TelegramFormat.chatTime(chat.timestamp), color = Fusion.colors.textSecondary, fontSize = 13.sp)
            }
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                val activity = LocalChatActivityLines.current[chat.id]
                val preview = activity ?: if (chat.draft != null) "Черновик: ${chat.draft}" else chat.preview
                Text(preview, color = if (activity != null) Fusion.colors.textPrimary else if (chat.draft != null) Fusion.colors.danger else Fusion.colors.textSecondary, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                if (chat.unreadMentionCount > 0) Badge("@", strong = true)
                if (chat.unreadCount > 0 || chat.isMarkedUnread) Badge(if (chat.unreadCount > 0) compactCount(chat.unreadCount) else "", strong = !chat.isMuted)
                else if (chat.isPinned) IconGlyph(KiroXIcon.Pin, "Закреплён", Modifier.padding(start = 6.dp).size(15.dp), Fusion.colors.textTertiary)
            }
        }
    }
    HorizontalDivider(Modifier.padding(start = 82.dp), color = Fusion.colors.outline)
}

@Composable
private fun Badge(text: String, strong: Boolean) {
    Box(
        Modifier.padding(start = 6.dp).defaultMinSize(minWidth = 20.dp, minHeight = 20.dp).clip(CircleShape)
            .background(if (strong) Fusion.colors.accent else Fusion.colors.surface3).padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center
    ) { Text(text, color = if (strong) Fusion.colors.onAccent else Fusion.colors.textPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
}

private fun compactCount(count: Int) = if (count > 999) "${count / 1000}K" else count.toString()

@Composable
private fun FolderChip(title: String, selected: Boolean, onLongClick: (() -> Unit)? = null, onClick: () -> Unit) {
    Text(
        title, color = if (selected) Fusion.colors.onAccent else Fusion.colors.textSecondary, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1,
        modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(if (selected) Fusion.colors.accent else Fusion.colors.surface2)
            .combinedClickable(onLongClick = onLongClick, onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp)
    )
}

private fun connectionTitle(state: TelegramConnection): String? = when (state) {
    TelegramConnection.WaitingForNetwork -> "Ожидание сети…"
    TelegramConnection.ConnectingToProxy -> "Подключение к прокси…"
    TelegramConnection.Connecting -> "Соединение…"
    TelegramConnection.Updating -> "Обновление…"
    TelegramConnection.Ready -> null
}

// ---------- Contacts ----------

@Composable
private fun ContactsTab(
    contacts: List<TelegramContact>,
    loading: Boolean,
    onOpenContact: (Long) -> Unit,
    calls: TelegramCallsState,
    onLoadCalls: () -> Unit,
    onOpenChat: (Long) -> Unit,
    onNotice: (String) -> Unit,
    onStartCall: (userId: Long, isVideo: Boolean) -> Unit,
    onAddContact: suspend (phone: String, firstName: String, lastName: String) -> Long?
) {
    var showCalls by rememberSaveable { mutableStateOf(false) }
    var focusSearch by remember { mutableIntStateOf(0) }
    var card by remember { mutableStateOf<Long?>(null) }
    var adding by remember { mutableStateOf(false) }
    val noCalls = { onNotice("Звонки из KiroX пока недоступны: нужен отдельный VoIP-модуль") }
    // In a private chat the chat id equals the user id.
    val call: (Long, Boolean) -> Unit = { userId, video -> if (com.kirox.app.ui.call.callsEnabled) onStartCall(userId, video) else noCalls() }
    Column(Modifier.fillMaxSize()) {
        ScreenTitle("Контакты") {
            TitleAction(KiroXIcon.Search, "Поиск контактов") { showCalls = false; focusSearch++ }
            TitleAction(KiroXIcon.Add, "Добавить контакт") { adding = true }
        }
        SectionTabs(listOf("Контакты" to false, "Звонки" to true), showCalls) { showCalls = it }
        if (showCalls) CallsList(calls, onLoadCalls, onOpenChat) { item -> call(item.chatId, item.isVideo) }
        else ContactsList(contacts, loading, focusSearch, onOpen = { card = it }, onCall = call)
    }
    contacts.firstOrNull { it.id == card }?.let { contact ->
        ContactCard(
            contact,
            onDismiss = { card = null },
            onMessage = { card = null; onOpenContact(contact.id) },
            onCall = { video -> card = null; call(contact.id, video) }
        )
    }
    if (adding) AddContactDialog(
        onDismiss = { adding = false },
        onAdd = onAddContact,
        onAdded = { userId -> adding = false; card = userId },
        onNotice = onNotice
    )
}

/** Online first, then alphabetical; search matches name, @username and phone digits. */
internal fun filterContacts(contacts: List<TelegramContact>, query: String): List<TelegramContact> {
    val q = query.trim()
    val digits = q.filter(Char::isDigit)
    val found = if (q.isEmpty()) contacts else contacts.filter {
        it.name.contains(q, ignoreCase = true) ||
            it.username?.contains(q.removePrefix("@"), ignoreCase = true) == true ||
            (digits.length >= 3 && it.phone.contains(digits))
    }
    return found.sortedWith(compareBy<TelegramContact> { it.status != TelegramUserStatus.Online }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.name })
}

@Composable
private fun ContactsList(contacts: List<TelegramContact>, loading: Boolean, focusSearch: Int, onOpen: (Long) -> Unit, onCall: (Long, Boolean) -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var query by rememberSaveable { mutableStateOf("") }
    val shown = remember(contacts, query) { filterContacts(contacts, query) }
    Column(Modifier.fillMaxSize()) {
        SearchField(query, "Имя, @ник или номер", focusTrigger = focusSearch) { query = it }
        Box(Modifier.weight(1f)) {
            when {
                contacts.isEmpty() && loading -> CenteredProgress()
                contacts.isEmpty() -> EmptyState("Контактов пока нет")
                shown.isEmpty() -> EmptyState("Ничего не найдено")
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    val online = shown.takeWhile { it.status == TelegramUserStatus.Online }
                    val rest = shown.drop(online.size)
                    if (online.isNotEmpty() && query.isBlank()) item(key = "h_online") { ContactSection("В сети") }
                    items(online, key = { it.id }) { contact -> ContactRow(contact, onOpen = { onOpen(contact.id) }, onCall = { onCall(contact.id, false) }) }
                    rest.groupBy { it.name.firstOrNull()?.uppercaseChar() ?: '#' }.forEach { (letter, group) ->
                        if (query.isBlank()) item(key = "h_$letter") { ContactSection(letter.toString()) }
                        items(group, key = { it.id }) { contact -> ContactRow(contact, onOpen = { onOpen(contact.id) }, onCall = { onCall(contact.id, false) }) }
                    }
                }
            }
        }
        Text(
            "Пригласить друзей", color = Fusion.colors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().clickable {
                val share = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                    type = "text/plain"; putExtra(android.content.Intent.EXTRA_TEXT, "Давай общаться в Telegram! Я пользуюсь kiroX.")
                }
                context.startActivity(android.content.Intent.createChooser(share, "Пригласить друзей"))
            }.heightIn(min = 52.dp).wrapContentHeight()
        )
    }
}

@Composable
private fun ContactSection(title: String) {
    Text(title, color = Fusion.colors.textTertiary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 20.dp, top = 12.dp, bottom = 4.dp))
}

@Composable
private fun ContactRow(contact: TelegramContact, onOpen: () -> Unit, onCall: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onOpen).heightIn(min = 72.dp).padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        TelegramAvatar(contact.name, contact.photo, 50.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(contact.name, color = Fusion.colors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                TelegramFormat.status(contact.status),
                color = if (contact.status == TelegramUserStatus.Online) Fusion.colors.textPrimary else Fusion.colors.textSecondary,
                fontSize = 13.sp, maxLines = 1
            )
        }
        HeaderAction(KiroXIcon.Call, "Позвонить ${contact.name}", onCall)
    }
    HorizontalDivider(Modifier.padding(start = 78.dp), color = Fusion.colors.outline)
}

/** Bottom card: who it is and the three things you do with a contact. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun ContactCard(contact: TelegramContact, onDismiss: () -> Unit, onMessage: () -> Unit, onCall: (isVideo: Boolean) -> Unit) {
    androidx.compose.material3.ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Fusion.colors.surface1) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            TelegramAvatar(contact.name, contact.photo, 88.dp)
            Spacer(Modifier.height(12.dp))
            Text(contact.name, color = Fusion.colors.textPrimary, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            val subtitle = listOfNotNull(TelegramFormat.status(contact.status), contact.username?.let { "@$it" }).joinToString(" · ")
            Text(subtitle, color = Fusion.colors.textSecondary, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(20.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                CardAction(KiroXIcon.Chat, "Написать", Modifier.weight(1f), onMessage)
                CardAction(KiroXIcon.Call, "Звонок", Modifier.weight(1f)) { onCall(false) }
                CardAction(KiroXIcon.Video, "Видео", Modifier.weight(1f)) { onCall(true) }
            }
            if (contact.phone.isNotBlank()) {
                Spacer(Modifier.height(16.dp))
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Fusion.colors.surface2).padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Text("Телефон", color = Fusion.colors.textSecondary, fontSize = 12.sp)
                    Text(TelegramFormat.phone(contact.phone), color = Fusion.colors.textPrimary, fontSize = 16.sp)
                }
            }
        }
    }
}

@Composable
private fun CardAction(icon: KiroXIcon, label: String, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).background(Fusion.colors.surface2).clickable(onClick = onClick).padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        IconGlyph(icon, null, Modifier.size(24.dp), Fusion.colors.textPrimary)
        Spacer(Modifier.height(6.dp))
        Text(label, color = Fusion.colors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

@Composable
private fun AddContactDialog(
    onDismiss: () -> Unit,
    onAdd: suspend (phone: String, firstName: String, lastName: String) -> Long?,
    onAdded: (Long) -> Unit,
    onNotice: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    var first by rememberSaveable { mutableStateOf("") }
    var last by rememberSaveable { mutableStateOf("") }
    var phone by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val digits = phone.filter(Char::isDigit)
    val canAdd = first.isNotBlank() && digits.length >= 7 && !busy
    androidx.compose.material3.AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        containerColor = Fusion.colors.surface1,
        title = { Text("Новый контакт", color = Fusion.colors.textPrimary) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ContactField(first, "Имя") { first = it }
                ContactField(last, "Фамилия (необязательно)") { last = it }
                ContactField(phone, "Номер телефона", androidx.compose.ui.text.input.KeyboardType.Phone) { phone = it }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(enabled = canAdd, onClick = {
                busy = true
                scope.launch {
                    val result = runCatching { onAdd(digits, first.trim(), last.trim()) }
                    busy = false
                    result.onSuccess { id -> if (id != null) onAdded(id) else onNotice("Этого номера нет в Telegram") }
                        .onFailure { onNotice("Не удалось добавить контакт") }
                }
            }) { Text(if (busy) "Добавляю…" else "Добавить", color = if (canAdd) Fusion.colors.textPrimary else Fusion.colors.textTertiary) }
        },
        dismissButton = { androidx.compose.material3.TextButton(onClick = onDismiss, enabled = !busy) { Text("Отмена", color = Fusion.colors.textSecondary) } }
    )
}

@Composable
private fun ContactField(value: String, label: String, keyboard: androidx.compose.ui.text.input.KeyboardType = androidx.compose.ui.text.input.KeyboardType.Text, onChange: (String) -> Unit) {
    androidx.compose.material3.OutlinedTextField(
        value, onChange, singleLine = true, label = { Text(label) },
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = keyboard),
        modifier = Modifier.fillMaxWidth()
    )
}

// ---------- Shared pieces ----------

@Composable
private fun Header(title: String, actions: @Composable RowScope.() -> Unit = {}) {
    Row(Modifier.fillMaxWidth().heightIn(min = 58.dp).padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = Fusion.colors.textPrimary, fontSize = 26.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Row(content = actions)
    }
}

@Composable
private fun HeaderAction(icon: KiroXIcon, description: String, onClick: () -> Unit) {
    Box(Modifier.size(48.dp).clip(CircleShape).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        IconGlyph(icon, description, Modifier.size(24.dp), Fusion.colors.textPrimary)
    }
}

@Composable
private fun <T> SectionTabs(tabs: List<Pair<String, T>>, selected: T, onSelect: (T) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 44.dp).padding(horizontal = 6.dp), verticalAlignment = Alignment.Bottom) {
        tabs.forEach { (label, value) ->
            val active = value == selected
            Column(Modifier.width(IntrinsicSize.Max).clip(RoundedCornerShape(8.dp)).clickable { onSelect(value) }.padding(horizontal = 12.dp), verticalArrangement = Arrangement.Bottom) {
                Text(label, color = if (active) Fusion.colors.textPrimary else Fusion.colors.textTertiary, fontSize = 16.sp, fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium, maxLines = 1)
                Spacer(Modifier.height(6.dp))
                Box(Modifier.fillMaxWidth().height(2.dp).clip(RoundedCornerShape(2.dp)).background(if (active) Fusion.colors.textPrimary else Fusion.colors.background))
            }
        }
    }
    Spacer(Modifier.height(4.dp))
}

@Composable
private fun SearchField(value: String, hint: String, autoFocus: Boolean = false, focusTrigger: Int = 0, onChange: (String) -> Unit) {
    val focus = remember { androidx.compose.ui.focus.FocusRequester() }
    if (autoFocus) LaunchedEffect(Unit) { focus.requestFocus() }
    LaunchedEffect(focusTrigger) { if (focusTrigger > 0) focus.requestFocus() }
    Row(
        Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth().heightIn(min = 44.dp).clip(RoundedCornerShape(14.dp))
            .background(Fusion.colors.surface1).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconGlyph(KiroXIcon.Search, null, Modifier.size(19.dp), Fusion.colors.textSecondary)
        Spacer(Modifier.width(10.dp))
        BasicTextField(
            value, onChange, Modifier.weight(1f).focusRequester(focus), singleLine = true,
            textStyle = TextStyle(color = Fusion.colors.textPrimary, fontSize = 15.sp), cursorBrush = SolidColor(Fusion.colors.textPrimary),
            decorationBox = { inner -> Box { if (value.isEmpty()) Text(hint, color = Fusion.colors.textTertiary, fontSize = 15.sp); inner() } }
        )
        if (value.isNotEmpty()) Text("×", color = Fusion.colors.textSecondary, fontSize = 20.sp, modifier = Modifier.clip(CircleShape).clickable { onChange("") }.padding(horizontal = 8.dp))
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, color = Fusion.colors.textSecondary, fontSize = 11.sp, letterSpacing = 1.sp, modifier = Modifier.padding(start = 18.dp, top = 20.dp, bottom = 6.dp))
}

@Composable
private fun CenteredProgress() {
    Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator(Modifier.size(26.dp), color = Fusion.colors.textSecondary, strokeWidth = 2.dp) }
}

@Composable
private fun EmptyState(text: String) {
    Box(Modifier.fillMaxSize().padding(32.dp), Alignment.Center) { Text(text, color = Fusion.colors.textSecondary, fontSize = 15.sp, textAlign = TextAlign.Center) }
}
