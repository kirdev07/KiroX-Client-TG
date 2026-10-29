@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.kirox.app.ui.home

import com.kirox.app.ui.components.fusionPanel
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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.draw.clip
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

/** The approved Fusion UI v2 dock. Contacts and the own profile are sub-screens now. */
private enum class MainTab(val item: FusionNavItem) {
    Chats(FusionNavItem("Чаты", KiroXIcon.Chats)),
    Calls(FusionNavItem("Звонки", KiroXIcon.Call)),
    Stories(FusionNavItem("Истории", KiroXIcon.Stories)),
    Settings(FusionNavItem("Настройки", KiroXIcon.Tune))
}
private enum class SubScreen { None, Contacts, Profile, NewMessage, Customization, Themes, ChatStyle, Animations, Updates, Devices, ProfileEdit, Notifications, Privacy, Storage, Premium, Advanced }
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
    onAddContact: suspend (phone: String, firstName: String, lastName: String) -> Long? = { _, _, _ -> null },
    /** Screen shown first (screenshots): 0 chats, 1 calls, 2 stories, 3 settings, 4 contacts, 5 profile. */
    initialTab: Int = 0
) = androidx.compose.runtime.CompositionLocalProvider(com.kirox.app.ui.components.LocalSeasonalEffects provides seasonal) {
    var tab by rememberSaveable { mutableStateOf(MainTab.entries.getOrElse(initialTab) { if (initialTab == 5) MainTab.Settings else MainTab.Chats }) }
    var sub by rememberSaveable { mutableStateOf(when (initialTab) { 4 -> SubScreen.Contacts; 5 -> SubScreen.Profile; else -> SubScreen.None }) }
    var notice by remember { mutableStateOf<UiNotice?>(null) }
    var viewingStoriesOf by rememberSaveable { mutableStateOf<Long?>(null) }
    val keyboardOpen = WindowInsets.isImeVisible
    val showNotice: (String) -> Unit = { notice = UiNotice(it) }
    LaunchedEffect(chatError) { if (chatError != null) { notice = UiNotice(chatError); onChatErrorShown() } }
    val navItems = MainTab.entries.map { it.item }
    BoxWithConstraints(Modifier.fillMaxSize()) {
    com.kirox.app.ui.components.FusionBackdrop()
    val wide = maxWidth >= 600.dp
    Row(Modifier.fillMaxSize()) {
    if (wide) FusionNavigationRail(navItems, tab.ordinal, { tab = MainTab.entries[it]; sub = SubScreen.None })
    Scaffold(
        modifier = Modifier.weight(1f),
        containerColor = if (Fusion.colors.backdrop != null) androidx.compose.ui.graphics.Color.Transparent else Fusion.colors.background,
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
                    subScreen == SubScreen.Contacts -> ContactsTab(contacts, contactsLoading, onOpenContact, showNotice, onStartCall, onAddContact, onBack = { sub = SubScreen.None })
                    subScreen == SubScreen.Profile -> ProfileTab(profile, onOpenSettings = { tab = MainTab.Settings; sub = SubScreen.None }, onOpenSaved = onOpenSavedMessages, onLogOut = onLogOut, onEditProfile = { sub = SubScreen.ProfileEdit }, onNotice = showNotice, onBack = { sub = SubScreen.None })
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
                    current == MainTab.Chats -> ChatsTab(lists, chatActions, connection, onOpenChat, storyChats, profile, { viewingStoriesOf = it.chatId }, onNewMessage = { sub = SubScreen.NewMessage }, onOpenContacts = { sub = SubScreen.Contacts }) { notice = it }
                    current == MainTab.Calls -> CallsTab(calls, onLoadCalls, onOpenChat, showNotice, onStartCall, onOpenContacts = { sub = SubScreen.Contacts })
                    current == MainTab.Stories -> StoriesTab(storyChats, profile, onOpen = { viewingStoriesOf = it.chatId }, onNotice = { notice = it })
                    else -> SettingsTab(profile, onOpenProfile = { sub = SubScreen.Profile }, onOpenContacts = { sub = SubScreen.Contacts }, onOpenCustomization = { sub = SubScreen.Customization }, onOpenUpdates = { sub = SubScreen.Updates }, onOpenDevices = { sub = SubScreen.Devices }, onEditProfile = { sub = SubScreen.ProfileEdit }, onOpenSection = { section ->
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
    onOpenContacts: () -> Unit = {},
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
    /** Local filter over the loaded list by TDLib chat type; folders stay as they are. */
    var kindFilter by rememberSaveable { mutableStateOf<com.kirox.app.telegram.TelegramChatKind?>(null) }
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
    val loaded = lists[listKey]
    val activeKind = kindFilter.takeIf { section == RealChatSection.Messages && folderId == null }
    val state = if (activeKind == null) loaded else loaded.copy(chats = loaded.chats.filter { it.kind == activeKind })
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
    // The new-message button hides while scrolling down the list and comes back when scrolling up.
    var fabShown by remember { mutableStateOf(true) }
    val fabScroll = remember {
        object : androidx.compose.ui.input.nestedscroll.NestedScrollConnection {
            override fun onPreScroll(available: androidx.compose.ui.geometry.Offset, source: androidx.compose.ui.input.nestedscroll.NestedScrollSource): androidx.compose.ui.geometry.Offset {
                if (available.y < -4f) fabShown = false else if (available.y > 4f) fabShown = true
                return androidx.compose.ui.geometry.Offset.Zero
            }
        }
    }
    Box(Modifier.fillMaxSize().nestedScroll(fabScroll)) {
    Column(Modifier.fillMaxSize()) {
        // Approved PNG: the archive is a row at the end of the chat list and opens as its own page.
        androidx.activity.compose.BackHandler(enabled = section == RealChatSection.Archive) { section = RealChatSection.Messages }
        if (section == RealChatSection.Archive) BackTitle("Архив", onBack = { section = RealChatSection.Messages })
        else {
            ChatsHeader(
                status = connectionTitle(connection),
                onSearch = { searching = true }, onNewMessage = onNewMessage,
                onOpenContacts = onOpenContacts, onReadAll = { confirmReadAll = true }
            )
            SearchPill("Поиск") { searching = true }
            val postStory = rememberStoryPoster { onNotice(UiNotice(it)) }
            StoriesRow(storyChats, profile?.name.orEmpty(), profile?.photo, onOpenStories) { postStory() }
        }
        var creatingFolder by remember { mutableStateOf(false) }
        var deletingFolder by remember { mutableStateOf<com.kirox.app.telegram.TelegramFolder?>(null) }
        if (creatingFolder) CreateFolderDialog(lists.main.chats, onDismiss = { creatingFolder = false }) { onNotice(UiNotice(it)) }
        deletingFolder?.let { folder -> DeleteFolderDialog(folder, onDismiss = { deletingFolder = null }) { onNotice(UiNotice(it)) } }
        if (section == RealChatSection.Messages) {
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Unread chats per folder, like Telegram's folder tabs.
                fun unreadIn(state: com.kirox.app.telegram.TelegramChatListState) = state.chats.count { it.unreadCount > 0 || it.isMarkedUnread }
                fun withCount(title: String, count: Int) = if (count > 0) "$title · $count" else title
                item { FolderChip(withCount("Все", unreadIn(lists.main)), folderId == null && kindFilter == null) { folderId = null; kindFilter = null } }
                items(KindFilters, key = { "kind_" + it.first.name }) { (kind, title) ->
                    FolderChip(title, folderId == null && kindFilter == kind) { folderId = null; kindFilter = if (kindFilter == kind) null else kind }
                }
                items(lists.folders, key = { it.id }) { folder ->
                    FolderChip(withCount(folder.title.ifBlank { "Папка" }, unreadIn(lists[TelegramChatListKey.Folder(folder.id)])), folderId == folder.id, onLongClick = { deletingFolder = folder }) { folderId = folder.id; kindFilter = null }
                }
                item { FolderChip("+ Папка", false) { creatingFolder = true } }
            }
            HorizontalDivider(color = Fusion.colors.outline)
        }
        key(listKey) {
            val listState = rememberLazyListState()
            // A type filter may hide most of a page: keep loading until the list is complete.
            LoadMoreEffect(listState, state.chats.size, state.complete) { actions.loadMore(listKey) }
            if (activeKind != null && state.chats.size < 20 && !state.complete && !state.loading) LaunchedEffect(loaded.chats.size) { actions.loadMore(listKey) }
            val archiveRow = listKey == TelegramChatListKey.Main && activeKind == null && lists.archive.chats.isNotEmpty()
            when {
                state.chats.isEmpty() && !state.complete -> CenteredProgress()
                state.chats.isEmpty() && !archiveRow -> EmptyState(
                    when (listKey) {
                        TelegramChatListKey.Archive -> "Архив пуст"
                        is TelegramChatListKey.Folder -> "В этой папке нет чатов"
                        TelegramChatListKey.Main -> if (activeKind != null) "Нет таких чатов" else "Здесь появятся ваши чаты"
                    }
                )
                else -> LazyColumn(Modifier.fillMaxSize(), state = listState) {
                    items(state.chats, key = { it.id }) { chat ->
                        SwipeableChatRow(chat, onSwipeRight = { toggleRead(chat) }, onSwipeLeft = { archive(chat) }) {
                            ChatRow(chat, onLongClick = { menuFor = chat }) { onOpenChat(chat.id) }
                        }
                    }
                    if (state.loading) item { Box(Modifier.fillMaxWidth().padding(16.dp), Alignment.Center) { CircularProgressIndicator(Modifier.size(22.dp), color = Fusion.colors.textSecondary, strokeWidth = 2.dp) } }
                    if (archiveRow) item(key = "archive_row") { ArchiveRow(lists.archive) { section = RealChatSection.Archive } }
                    // Room under the last chat so the button never covers it.
                    item { Spacer(Modifier.height(88.dp)) }
                }
            }
        }
    }
    // New message: Material 3 floating action button in the classic palettes; Fusion UI v2 has it in the header.
    if (Fusion.colors.backdrop == null) androidx.compose.animation.AnimatedVisibility(
        fabShown, modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        enter = if (com.kirox.app.ui.theme.LocalMotionFactor.current > 0f) androidx.compose.animation.scaleIn() + androidx.compose.animation.fadeIn() else androidx.compose.animation.EnterTransition.None,
        exit = if (com.kirox.app.ui.theme.LocalMotionFactor.current > 0f) androidx.compose.animation.scaleOut() + androidx.compose.animation.fadeOut() else androidx.compose.animation.ExitTransition.None
    ) {
        androidx.compose.material3.FloatingActionButton(
            onClick = onNewMessage,
            containerColor = Fusion.colors.accent,
            contentColor = Fusion.colors.onAccent
        ) { IconGlyph(KiroXIcon.Compose, "Новое сообщение", Modifier.size(24.dp), Fusion.colors.onAccent) }
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
    val colors = Fusion.colors
    // Fusion UI v2: plain text filters, the active one on a dark pill (approved PNG); classic: filled chips.
    val v2 = colors.backdrop != null
    val background = when {
        selected && v2 -> colors.selectedPill
        selected -> colors.accent
        v2 -> androidx.compose.ui.graphics.Color.Transparent
        else -> colors.surface2
    }
    Text(
        title, color = if (selected) (if (v2) colors.onSelectedPill else colors.onAccent) else colors.textSecondary,
        fontSize = 14.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium, maxLines = 1,
        modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(background)
            .combinedClickable(onLongClick = onLongClick, onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

/** «Архив · N чатов» row with the number of unread archived chats. */
@Composable
private fun ArchiveRow(archive: com.kirox.app.telegram.TelegramChatListState, onClick: () -> Unit) {
    val unread = archive.chats.count { it.unreadCount > 0 || it.isMarkedUnread }
    val n = archive.chats.size
    val count = when {
        !archive.complete -> "Архивные чаты"
        n % 10 == 1 && n % 100 != 11 -> "$n чат"
        n % 10 in 2..4 && n % 100 !in 12..14 -> "$n чата"
        else -> "$n чатов"
    }
    androidx.compose.material3.ListItem(
        headlineContent = { Text("Архив", fontWeight = FontWeight.SemiBold, maxLines = 1) },
        supportingContent = { Text(count, maxLines = 1) },
        leadingContent = {
            Box(Modifier.size(54.dp).clip(CircleShape).background(Fusion.colors.surface2), contentAlignment = Alignment.Center) {
                IconGlyph(KiroXIcon.Folder, null, Modifier.size(24.dp), Fusion.colors.textPrimary)
            }
        },
        trailingContent = if (unread > 0) ({
            Text(compactCount(unread), color = Fusion.colors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clip(CircleShape).background(Fusion.colors.surface3).padding(horizontal = 9.dp, vertical = 3.dp))
        }) else null,
        colors = fusionListColors(),
        modifier = Modifier.clickable(onClickLabel = "Открыть архив", onClick = onClick)
    )
}

/** Chats header from the approved Fusion PNG: menu left, KiroX in the centre, search and new message right. */
@Composable
private fun ChatsHeader(status: String?, onSearch: () -> Unit, onNewMessage: () -> Unit, onOpenContacts: () -> Unit, onReadAll: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 4.dp)) {
        Box(Modifier.align(Alignment.CenterStart)) {
            TitleAction(KiroXIcon.Menu, "Меню") { menu = true }
            androidx.compose.material3.DropdownMenu(menu, { menu = false }, containerColor = Fusion.colors.surface2) {
                androidx.compose.material3.DropdownMenuItem(text = { Text("Контакты", color = Fusion.colors.textPrimary) }, onClick = { menu = false; onOpenContacts() })
                androidx.compose.material3.DropdownMenuItem(text = { Text("Отметить всё прочитанным", color = Fusion.colors.textPrimary) }, onClick = { menu = false; onReadAll() })
            }
        }
        Text(
            status ?: "KiroX", color = Fusion.colors.textPrimary, maxLines = 1,
            style = if (status == null) androidx.compose.material3.MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold) else androidx.compose.material3.MaterialTheme.typography.titleMedium,
            modifier = Modifier.align(Alignment.Center)
        )
        Row(Modifier.align(Alignment.CenterEnd)) {
            TitleAction(KiroXIcon.Search, "Поиск", onClick = onSearch)
            TitleAction(KiroXIcon.Compose, "Новое сообщение", onClick = onNewMessage)
        }
    }
}

/** Always-visible rounded search bar under the header; opens the chat search. */
@Composable
private fun SearchPill(hint: String, onClick: () -> Unit) {
    Row(
        Modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth().heightIn(min = 46.dp).clip(CircleShape)
            .fusionPanel(Fusion.colors, CircleShape, opaque = Fusion.colors.surface2).clickable(onClickLabel = "Открыть поиск", onClick = onClick).padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconGlyph(KiroXIcon.Search, null, Modifier.size(20.dp), Fusion.colors.textSecondary)
        Spacer(Modifier.width(10.dp))
        Text(hint, color = Fusion.colors.textTertiary, fontSize = 16.sp)
    }
}

private fun connectionTitle(state: TelegramConnection): String? = when (state) {
    TelegramConnection.WaitingForNetwork -> "Ожидание сети…"
    TelegramConnection.ConnectingToProxy -> "Подключение к прокси…"
    TelegramConnection.Connecting -> "Соединение…"
    TelegramConnection.Updating -> "Обновление…"
    TelegramConnection.Ready -> null
}

// ---------- Contacts ----------

private val KindFilters = listOf(
    com.kirox.app.telegram.TelegramChatKind.Private to "Личные",
    com.kirox.app.telegram.TelegramChatKind.Channel to "Каналы",
    com.kirox.app.telegram.TelegramChatKind.Group to "Группы"
)

private val noCallsText = "Звонки из KiroX пока недоступны: нужен отдельный VoIP-модуль"

@Composable
private fun ContactsTab(
    contacts: List<TelegramContact>,
    loading: Boolean,
    onOpenContact: (Long) -> Unit,
    onNotice: (String) -> Unit,
    onStartCall: (userId: Long, isVideo: Boolean) -> Unit,
    onAddContact: suspend (phone: String, firstName: String, lastName: String) -> Long?,
    onBack: () -> Unit
) {
    androidx.activity.compose.BackHandler(onBack = onBack)
    var focusSearch by remember { mutableIntStateOf(0) }
    var card by remember { mutableStateOf<Long?>(null) }
    var adding by remember { mutableStateOf(false) }
    // In a private chat the chat id equals the user id.
    val call: (Long, Boolean) -> Unit = { userId, video -> if (com.kirox.app.ui.call.callsEnabled) onStartCall(userId, video) else onNotice(noCallsText) }
    Column(Modifier.fillMaxSize()) {
        BackTitle("Контакты", onBack) {
            TitleAction(KiroXIcon.Search, "Поиск контактов") { focusSearch++ }
            TitleAction(KiroXIcon.Add, "Добавить контакт") { adding = true }
        }
        ContactsList(contacts, loading, focusSearch, onOpen = { card = it }, onCall = call)
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

/** Звонки tab: the real call history from TDLib (SearchCallMessages). */
@Composable
private fun CallsTab(
    calls: TelegramCallsState,
    onLoadCalls: () -> Unit,
    onOpenChat: (Long) -> Unit,
    onNotice: (String) -> Unit,
    onStartCall: (userId: Long, isVideo: Boolean) -> Unit,
    onOpenContacts: () -> Unit
) {
    val call: (Long, Boolean) -> Unit = { userId, video -> if (com.kirox.app.ui.call.callsEnabled) onStartCall(userId, video) else onNotice(noCallsText) }
    Column(Modifier.fillMaxSize()) {
        ScreenTitle("Звонки") { TitleAction(KiroXIcon.Contacts, "Позвонить контакту", onClick = onOpenContacts) }
        CallsList(calls, onLoadCalls, onOpenChat) { item -> call(item.chatId, item.isVideo) }
    }
}

/** Истории tab: own stories and the active stories of chats, as TDLib reports them. */
@Composable
private fun StoriesTab(storyChats: List<TelegramStoryChat>, profile: TelegramUserProfile?, onOpen: (TelegramStoryChat) -> Unit, onNotice: (UiNotice) -> Unit) {
    val postStory = rememberStoryPoster { onNotice(UiNotice(it)) }
    val own = storyChats.firstOrNull { it.isSelf }
    val others = storyChats.filterNot { it.isSelf }
    val (unread, seen) = others.partition { it.hasUnread }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { ScreenTitle("Истории") { TitleAction(KiroXIcon.Camera, "Новая история") { postStory() } } }
        item {
            StoryEntry(
                profile?.name.orEmpty(), profile?.photo, own?.hasUnread == true,
                title = "Моя история",
                subtitle = if (own != null) storiesCount(own.storyIds.size) else "Нажмите, чтобы опубликовать"
            ) { if (own != null) onOpen(own) else postStory() }
        }
        if (others.isEmpty()) item { EmptyState("Активных историй пока нет") }
        if (unread.isNotEmpty()) {
            item { StorySection("Новые") }
            items(unread, key = { it.chatId }) { chat -> StoryEntry(chat.title, chat.photo, true, chat.title, storiesCount(chat.storyIds.size)) { onOpen(chat) } }
        }
        if (seen.isNotEmpty()) {
            item { StorySection("Просмотренные") }
            items(seen, key = { it.chatId }) { chat -> StoryEntry(chat.title, chat.photo, false, chat.title, storiesCount(chat.storyIds.size)) { onOpen(chat) } }
        }
    }
}

private fun storiesCount(n: Int) = when {
    n % 10 == 1 && n % 100 != 11 -> "$n история"
    n % 10 in 2..4 && n % 100 !in 12..14 -> "$n истории"
    else -> "$n историй"
}

@Composable
private fun StorySection(title: String) {
    Text(title, color = Fusion.colors.textSecondary, style = androidx.compose.material3.MaterialTheme.typography.labelLarge, modifier = Modifier.padding(start = 20.dp, top = 14.dp, bottom = 4.dp))
}

@Composable
private fun StoryEntry(name: String, photo: com.kirox.app.telegram.TelegramFileRef?, unread: Boolean, title: String, subtitle: String, onClick: () -> Unit) {
    androidx.compose.material3.ListItem(
        headlineContent = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = { Text(subtitle, maxLines = 1) },
        leadingContent = {
            Box(
                Modifier.size(58.dp).border(if (unread) 2.dp else 1.dp, if (unread) Fusion.colors.accent else Fusion.colors.outlineStrong, CircleShape).padding(4.dp),
                contentAlignment = Alignment.Center
            ) { TelegramAvatar(name, photo, 50.dp) }
        },
        colors = fusionListColors(),
        modifier = Modifier.clickable(onClick = onClick)
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
    }
}

/** Share sheet with an invitation to Telegram. */
private fun inviteFriends(context: android.content.Context) {
    val share = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
        type = "text/plain"; putExtra(android.content.Intent.EXTRA_TEXT, "Давай общаться в Telegram! Я пользуюсь kiroX.")
    }
    runCatching { context.startActivity(android.content.Intent.createChooser(share, "Пригласить друзей")) }
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
            val context = androidx.compose.ui.platform.LocalContext.current
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ContactField(first, "Имя") { first = it }
                ContactField(last, "Фамилия (необязательно)") { last = it }
                ContactField(phone, "Номер телефона", androidx.compose.ui.text.input.KeyboardType.Phone) { phone = it }
                // Someone not in Telegram yet: send them an invitation instead.
                androidx.compose.material3.TextButton(onClick = { inviteFriends(context) }, enabled = !busy) {
                    Text("Пригласить друзей", color = Fusion.colors.textPrimary)
                }
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
private fun SearchField(value: String, hint: String, autoFocus: Boolean = false, focusTrigger: Int = 0, onChange: (String) -> Unit) {
    val focus = remember { androidx.compose.ui.focus.FocusRequester() }
    if (autoFocus) LaunchedEffect(Unit) { focus.requestFocus() }
    LaunchedEffect(focusTrigger) { if (focusTrigger > 0) focus.requestFocus() }
    com.kirox.app.ui.components.FusionSearchField(value, onChange, hint, Modifier.padding(horizontal = 16.dp, vertical = 6.dp), focus)
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
