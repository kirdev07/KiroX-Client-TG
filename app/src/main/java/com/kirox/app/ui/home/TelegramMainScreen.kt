package com.kirox.app.ui.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.background
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
private enum class SubScreen { None, NewMessage, Customization }
private enum class RealChatSection { Archive, Messages }

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
    onLogOut: () -> Unit
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
            AnimatedContent(tab to sub, transitionSpec = { fadeIn(tween(FusionMotion.NORMAL)) togetherWith fadeOut(tween(FusionMotion.FAST)) }, label = "main_tabs") { (current, subScreen) ->
                when {
                    subScreen == SubScreen.NewMessage -> NewMessageScreen(contacts, onBack = { sub = SubScreen.None }, onOpenContact = { sub = SubScreen.None; onOpenContact(it) }, onOpenSaved = { sub = SubScreen.None; onOpenSavedMessages() }, onNotice = showNotice)
                    subScreen == SubScreen.Customization -> CustomizationScreen(palette, onPalette, seasonal, onSeasonal, onBack = { sub = SubScreen.None }, onNotice = showNotice)
                    current == MainTab.Chats -> ChatsTab(lists, chatActions, connection, onOpenChat, storyChats, profile, { viewingStoriesOf = it.chatId }, onNewMessage = { sub = SubScreen.NewMessage }) { notice = it }
                    current == MainTab.Contacts -> ContactsTab(contacts, contactsLoading, onOpenContact, calls, onLoadCalls, onOpenChat, showNotice)
                    current == MainTab.Settings -> SettingsTab(profile, onOpenProfile = { tab = MainTab.Profile }, onOpenCustomization = { sub = SubScreen.Customization }, onNotice = showNotice)
                    else -> ProfileTab(profile, onOpenSettings = { tab = MainTab.Settings }, onOpenSaved = onOpenSavedMessages, onLogOut = onLogOut, onNotice = showNotice)
                }
            }
            notice?.let { current ->
                Row(
                    Modifier.align(Alignment.BottomCenter).padding(16.dp).clip(RoundedCornerShape(12.dp)).background(Fusion.colors.surface3).padding(start = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(current.text, color = Fusion.colors.textPrimary, fontSize = 13.sp, modifier = Modifier.padding(vertical = 12.dp).weight(1f, fill = false))
                    if (current.actionLabel != null) Text(
                        current.actionLabel, color = Fusion.colors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable { current.action?.invoke(); notice = null }.heightIn(min = 44.dp).padding(horizontal = 16.dp).wrapContentHeight()
                    ) else Spacer(Modifier.width(16.dp))
                }
                LaunchedEffect(current) { kotlinx.coroutines.delay(if (current.actionLabel != null) 4000 else 1900); notice = null }
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
            ChatSearchResults(query, actions::search) { chat -> ChatRow(chat) { searching = false; query = ""; onOpenChat(chat.id) } }
        }
        return
    }
    menuFor?.let { chat ->
        ChatContextSheet(
            chat, onDismiss = { menuFor = null },
            onPin = { actions.setPinned(if (chat.archived) TelegramChatListKey.Archive else TelegramChatListKey.Main, chat.id, !chat.isPinned) },
            onRead = { toggleRead(chat) },
            onArchive = { archive(chat) }
        )
    }
    var section by rememberSaveable { mutableStateOf(RealChatSection.Messages) }
    var folderId by rememberSaveable { mutableStateOf<Int?>(null) }
    if (folderId != null && lists.folders.none { it.id == folderId }) folderId = null
    val listKey: TelegramChatListKey = when {
        section == RealChatSection.Archive -> TelegramChatListKey.Archive
        folderId != null -> TelegramChatListKey.Folder(folderId!!)
        else -> TelegramChatListKey.Main
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
        StoriesRow(storyChats, profile?.name.orEmpty(), profile?.photo, onOpenStories) { onNotice(UiNotice("Публикация историй — В разработке")) }
        val archiveUnread = lists.archive.chats.count { it.unreadCount > 0 || it.isMarkedUnread }
        SectionTabs(listOf((if (archiveUnread > 0) "Архив · $archiveUnread" else "Архив") to RealChatSection.Archive, "Сообщения" to RealChatSection.Messages), section) { section = it }
        if (section == RealChatSection.Messages && lists.folders.isNotEmpty()) {
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { FolderChip("Все", folderId == null) { folderId = null } }
                items(lists.folders, key = { it.id }) { folder -> FolderChip(folder.title.ifBlank { "Папка" }, folderId == folder.id) { folderId = folder.id } }
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
        snapshotFlow { listState.layoutInfo.let { info -> info.totalItemsCount == 0 || (info.visibleItemsInfo.lastOrNull()?.index ?: 0) >= info.totalItemsCount - 10 } }
            .distinctUntilChanged().filter { it }.collect { latest() }
    }
    LaunchedEffect(count) { if (!complete && listState.layoutInfo.visibleItemsInfo.size >= count) latest() }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun ChatRow(chat: TelegramChatItem, onLongClick: (() -> Unit)? = null, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onLongClick, onLongClickLabel = "Действия с чатом").heightIn(min = 84.dp).padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        TelegramAvatar(chat.title, chat.photo, 60.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(chat.title, color = Fusion.colors.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (chat.isMuted) IconGlyph(KiroXIcon.Bell, "Без звука", Modifier.padding(start = 4.dp).size(13.dp), Fusion.colors.textTertiary)
                Spacer(Modifier.weight(1f))
                when (chat.outgoingState) {
                    LastMessageState.Read -> IconGlyph(KiroXIcon.ReadAll, "Прочитано", Modifier.padding(end = 4.dp).size(15.dp), Fusion.colors.textSecondary)
                    LastMessageState.Sent -> IconGlyph(KiroXIcon.Check, "Отправлено", Modifier.padding(end = 4.dp).size(15.dp), Fusion.colors.textSecondary)
                    LastMessageState.None -> Unit
                }
                Text(TelegramFormat.chatTime(chat.timestamp), color = Fusion.colors.textSecondary, fontSize = 14.sp)
            }
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                val preview = if (chat.draft != null) "Черновик: ${chat.draft}" else chat.preview
                Text(preview, color = if (chat.draft != null) Fusion.colors.danger else Fusion.colors.textSecondary, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                if (chat.unreadMentionCount > 0) Badge("@", strong = true)
                if (chat.unreadCount > 0 || chat.isMarkedUnread) Badge(if (chat.unreadCount > 0) compactCount(chat.unreadCount) else "", strong = !chat.isMuted)
                else if (chat.isPinned) IconGlyph(KiroXIcon.Pin, "Закреплён", Modifier.padding(start = 6.dp).size(15.dp), Fusion.colors.textTertiary)
            }
        }
    }
    HorizontalDivider(Modifier.padding(start = 92.dp), color = Fusion.colors.outline)
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
private fun FolderChip(title: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        title, color = if (selected) Fusion.colors.onAccent else Fusion.colors.textSecondary, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1,
        modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(if (selected) Fusion.colors.accent else Fusion.colors.surface2)
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp)
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
    onNotice: (String) -> Unit
) {
    var showCalls by rememberSaveable { mutableStateOf(false) }
    var focusSearch by remember { mutableIntStateOf(0) }
    val noCalls = { onNotice("Звонки из KiroX пока недоступны: нужен отдельный VoIP-модуль") }
    Column(Modifier.fillMaxSize()) {
        ScreenTitle("Контакты") {
            TitleAction(KiroXIcon.Search, "Поиск контактов") { showCalls = false; focusSearch++ }
            TitleAction(KiroXIcon.Add, "Добавить контакт") { onNotice("Добавление контакта — В разработке") }
        }
        SectionTabs(listOf("Контакты" to false, "Звонки" to true), showCalls) { showCalls = it }
        if (showCalls) CallsList(calls, onLoadCalls, onOpenChat, noCalls)
        else ContactsList(contacts, loading, focusSearch, onOpenContact, noCalls)
    }
}

@Composable
private fun ContactsList(contacts: List<TelegramContact>, loading: Boolean, focusSearch: Int, onOpenContact: (Long) -> Unit, onCall: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var query by rememberSaveable { mutableStateOf("") }
    val shown = remember(contacts, query) {
        val q = query.trim()
        if (q.isEmpty()) contacts else contacts.filter { it.name.contains(q, ignoreCase = true) || it.username?.contains(q.removePrefix("@"), ignoreCase = true) == true }
    }
    Column(Modifier.fillMaxSize()) {
        SearchField(query, "Поиск контактов", focusTrigger = focusSearch) { query = it }
        Box(Modifier.weight(1f)) {
            when {
                contacts.isEmpty() && loading -> CenteredProgress()
                contacts.isEmpty() -> EmptyState("Контактов пока нет")
                shown.isEmpty() -> EmptyState("Ничего не найдено")
                else -> LazyColumn(Modifier.fillMaxSize()) {
                    items(shown, key = { it.id }) { contact -> ContactRow(contact, onMessage = { onOpenContact(contact.id) }, onCall = onCall) }
                }
            }
        }
        Text(
            "Пригласить друзей", color = Fusion.colors.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().clickable {
                val share = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                    type = "text/plain"; putExtra(android.content.Intent.EXTRA_TEXT, "Давай общаться в Telegram! Я пользуюсь kiroX.")
                }
                context.startActivity(android.content.Intent.createChooser(share, "Пригласить друзей"))
            }.heightIn(min = 56.dp).wrapContentHeight()
        )
    }
}

@Composable
private fun ContactRow(contact: TelegramContact, onMessage: () -> Unit, onCall: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onMessage).heightIn(min = 72.dp).padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        TelegramAvatar(contact.name, contact.photo, 56.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(contact.name, color = Fusion.colors.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                TelegramFormat.status(contact.status),
                color = if (contact.status == TelegramUserStatus.Online) Fusion.colors.textPrimary else Fusion.colors.textSecondary,
                fontSize = 13.sp, maxLines = 1
            )
        }
        HeaderAction(KiroXIcon.Chat, "Написать ${contact.name}", onMessage)
        HeaderAction(KiroXIcon.Call, "Позвонить ${contact.name}", onCall)
    }
    HorizontalDivider(Modifier.padding(start = 88.dp), color = Fusion.colors.outline)
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
    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).padding(horizontal = 12.dp), verticalAlignment = Alignment.Bottom) {
        tabs.forEach { (label, value) ->
            val active = value == selected
            Column(Modifier.width(IntrinsicSize.Max).clip(RoundedCornerShape(8.dp)).clickable { onSelect(value) }.padding(horizontal = 12.dp), verticalArrangement = Arrangement.Bottom) {
                Text(label, color = if (active) Fusion.colors.textPrimary else Fusion.colors.textTertiary, fontSize = 18.sp, fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium, maxLines = 1)
                Spacer(Modifier.height(8.dp))
                Box(Modifier.fillMaxWidth().height(2.5.dp).clip(RoundedCornerShape(2.dp)).background(if (active) Fusion.colors.textPrimary else Fusion.colors.background))
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
        Modifier.padding(horizontal = 18.dp, vertical = 6.dp).fillMaxWidth().heightIn(min = 52.dp).clip(RoundedCornerShape(16.dp))
            .background(Fusion.colors.surface1).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconGlyph(KiroXIcon.Search, null, Modifier.size(22.dp), Fusion.colors.textSecondary)
        Spacer(Modifier.width(12.dp))
        BasicTextField(
            value, onChange, Modifier.weight(1f).focusRequester(focus), singleLine = true,
            textStyle = TextStyle(color = Fusion.colors.textPrimary, fontSize = 17.sp), cursorBrush = SolidColor(Fusion.colors.textPrimary),
            decorationBox = { inner -> Box { if (value.isEmpty()) Text(hint, color = Fusion.colors.textTertiary, fontSize = 17.sp); inner() } }
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
