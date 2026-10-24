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
import com.kirox.app.telegram.TelegramUserProfile
import com.kirox.app.telegram.TelegramUserStatus
import com.kirox.app.ui.components.FusionBottomBar
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
    Chats(FusionNavItem("Чаты", KiroXIcon.Chat)),
    Settings(FusionNavItem("Настройки", KiroXIcon.Settings))
}
private enum class RealChatSection { Archive, Messages }

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TelegramMainScreen(
    lists: TelegramChatLists,
    onLoadMore: (TelegramChatListKey) -> Unit,
    contacts: List<TelegramContact>,
    contactsLoading: Boolean,
    profile: TelegramUserProfile?,
    connection: TelegramConnection,
    onLogOut: () -> Unit
) {
    var tab by rememberSaveable { mutableStateOf(MainTab.Chats) }
    var notice by remember { mutableStateOf<String?>(null) }
    val keyboardOpen = WindowInsets.isImeVisible
    Scaffold(
        containerColor = Fusion.colors.background,
        contentWindowInsets = WindowInsets.statusBars,
        bottomBar = {
            if (!keyboardOpen) FusionBottomBar(MainTab.entries.map { it.item }, tab.ordinal, { tab = MainTab.entries[it] })
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding()) {
            AnimatedContent(tab, transitionSpec = { fadeIn(tween(FusionMotion.NORMAL)) togetherWith fadeOut(tween(FusionMotion.FAST)) }, label = "main_tabs") { current ->
                when (current) {
                    MainTab.Chats -> ChatsTab(lists, onLoadMore, connection) { notice = it }
                    MainTab.Contacts -> ContactsTab(contacts, contactsLoading) { notice = it }
                    MainTab.Settings -> SettingsTab(profile, onLogOut) { notice = it }
                }
            }
            notice?.let { message ->
                Text(
                    message, color = Fusion.colors.textPrimary, fontSize = 13.sp,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp).clip(RoundedCornerShape(12.dp))
                        .background(Fusion.colors.surface3).padding(horizontal = 16.dp, vertical = 12.dp)
                )
                LaunchedEffect(message) { kotlinx.coroutines.delay(1900); notice = null }
            }
        }
    }
}

// ---------- Chats ----------

@Composable
private fun ChatsTab(lists: TelegramChatLists, onLoadMore: (TelegramChatListKey) -> Unit, connection: TelegramConnection, onNotice: (String) -> Unit) {
    var section by rememberSaveable { mutableStateOf(RealChatSection.Messages) }
    var folderId by rememberSaveable { mutableStateOf<Int?>(null) }
    if (folderId != null && lists.folders.none { it.id == folderId }) folderId = null
    val listKey: TelegramChatListKey = when {
        section == RealChatSection.Archive -> TelegramChatListKey.Archive
        folderId != null -> TelegramChatListKey.Folder(folderId!!)
        else -> TelegramChatListKey.Main
    }
    val state = lists[listKey]
    Column(Modifier.fillMaxSize()) {
        Header(connectionTitle(connection) ?: "Чаты") {
            HeaderAction(KiroXIcon.Search, "Поиск") { onNotice("Поиск появится вместе с перепиской (этап D)") }
            HeaderAction(KiroXIcon.Compose, "Новый чат") { onNotice("Создание чатов появится на этапе D") }
        }
        SectionTabs(listOf("Архив" to RealChatSection.Archive, "Сообщения" to RealChatSection.Messages), section) { section = it }
        if (section == RealChatSection.Messages && lists.folders.isNotEmpty()) {
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { FolderChip("Все", folderId == null) { folderId = null } }
                items(lists.folders, key = { it.id }) { folder -> FolderChip(folder.title.ifBlank { "Папка" }, folderId == folder.id) { folderId = folder.id } }
            }
            HorizontalDivider(color = Fusion.colors.outline)
        }
        key(listKey) {
            val listState = rememberLazyListState()
            LoadMoreEffect(listState, state.chats.size, state.complete) { onLoadMore(listKey) }
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
                    items(state.chats, key = { it.id }) { chat -> ChatRow(chat) { onNotice("Открытие переписки появится на этапе D") } }
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

@Composable
private fun ChatRow(chat: TelegramChatItem, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).heightIn(min = 72.dp).padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        TelegramAvatar(chat.title, chat.photo, 52.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(chat.title, color = Fusion.colors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (chat.isMuted) IconGlyph(KiroXIcon.Bell, "Без звука", Modifier.padding(start = 4.dp).size(13.dp), Fusion.colors.textTertiary)
                Spacer(Modifier.weight(1f))
                when (chat.outgoingState) {
                    LastMessageState.Read -> IconGlyph(KiroXIcon.ReadAll, "Прочитано", Modifier.padding(end = 4.dp).size(15.dp), Fusion.colors.textSecondary)
                    LastMessageState.Sent -> IconGlyph(KiroXIcon.Check, "Отправлено", Modifier.padding(end = 4.dp).size(15.dp), Fusion.colors.textSecondary)
                    LastMessageState.None -> Unit
                }
                Text(TelegramFormat.chatTime(chat.timestamp), color = Fusion.colors.textSecondary, fontSize = 12.sp)
            }
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                val preview = if (chat.draft != null) "Черновик: ${chat.draft}" else chat.preview
                Text(preview, color = if (chat.draft != null) Fusion.colors.danger else Fusion.colors.textSecondary, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                if (chat.unreadMentionCount > 0) Badge("@", strong = true)
                if (chat.unreadCount > 0 || chat.isMarkedUnread) Badge(if (chat.unreadCount > 0) compactCount(chat.unreadCount) else "", strong = !chat.isMuted)
                else if (chat.isPinned) IconGlyph(KiroXIcon.Pin, "Закреплён", Modifier.padding(start = 6.dp).size(15.dp), Fusion.colors.textTertiary)
            }
        }
    }
    HorizontalDivider(Modifier.padding(start = 80.dp), color = Fusion.colors.outline)
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
private fun ContactsTab(contacts: List<TelegramContact>, loading: Boolean, onNotice: (String) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val shown = remember(contacts, query) {
        val q = query.trim()
        if (q.isEmpty()) contacts else contacts.filter { it.name.contains(q, ignoreCase = true) || it.username?.contains(q.removePrefix("@"), ignoreCase = true) == true }
    }
    Column(Modifier.fillMaxSize()) {
        Header("Контакты")
        SearchField(query, "Поиск контактов") { query = it }
        when {
            contacts.isEmpty() && loading -> CenteredProgress()
            contacts.isEmpty() -> EmptyState("Контактов пока нет")
            shown.isEmpty() -> EmptyState("Ничего не найдено")
            else -> LazyColumn(Modifier.fillMaxSize()) {
                items(shown, key = { it.id }) { contact -> ContactRow(contact) { onNotice("Профиль и переписка с контактом появятся на этапе D") } }
            }
        }
    }
}

@Composable
private fun ContactRow(contact: TelegramContact, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).heightIn(min = 64.dp).padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        TelegramAvatar(contact.name, contact.photo, 46.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(contact.name, color = Fusion.colors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                TelegramFormat.status(contact.status),
                color = if (contact.status == TelegramUserStatus.Online) Fusion.colors.textPrimary else Fusion.colors.textSecondary,
                fontSize = 13.sp, maxLines = 1
            )
        }
    }
    HorizontalDivider(Modifier.padding(start = 74.dp), color = Fusion.colors.outline)
}

// ---------- Settings + profile (merged tab) ----------

@Composable
private fun SettingsTab(profile: TelegramUserProfile?, onLogOut: () -> Unit, onNotice: (String) -> Unit) {
    var confirmLogout by remember { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { Header("Настройки") }
        item {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                TelegramAvatar(profile?.name.orEmpty(), profile?.photo, 88.dp)
                Spacer(Modifier.height(10.dp))
                Text(profile?.name ?: "Загрузка…", color = Fusion.colors.textPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val subtitle = listOfNotNull(profile?.username?.let { "@$it" }, profile?.phoneNumber?.let(TelegramFormat::phone)?.takeIf { it.isNotBlank() }).joinToString(" · ")
                if (subtitle.isNotEmpty()) Text(subtitle, color = Fusion.colors.textSecondary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (!profile?.bio.isNullOrBlank()) item { InfoRow(KiroXIcon.Info, "О себе", profile!!.bio) }
        item { SectionLabel("АККАУНТ И ПРИЛОЖЕНИЕ") }
        val soon = listOf(
            KiroXIcon.Edit to "Изменить профиль", KiroXIcon.Folder to "Чаты и папки", KiroXIcon.Bell to "Уведомления",
            KiroXIcon.Shield to "Конфиденциальность", KiroXIcon.Storage to "Данные и память", KiroXIcon.Devices to "Устройства",
            KiroXIcon.Palette to "Кастомизация"
        )
        items(soon, key = { it.second }) { (icon, title) -> SettingRow(icon, title, trailing = "скоро") { onNotice("«$title» появится в следующих этапах") } }
        item { SettingRow(KiroXIcon.Info, "О KiroX", trailing = BuildConfig.VERSION_NAME) { onNotice("KiroX ${BuildConfig.VERSION_NAME} — независимый клиент Telegram") } }
        item {
            Text(
                "Выйти из аккаунта", color = Fusion.colors.danger, fontSize = 15.sp, fontWeight = FontWeight.Medium,
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp).clickable { confirmLogout = true }.heightIn(min = 52.dp).padding(horizontal = 18.dp).wrapContentHeight()
            )
        }
    }
    if (confirmLogout) AlertDialog(
        onDismissRequest = { confirmLogout = false },
        containerColor = Fusion.colors.surface2,
        title = { Text("Выйти из аккаунта?", color = Fusion.colors.textPrimary) },
        text = { Text("Сессия на этом устройстве будет завершена, локальный кеш Telegram удалён.", color = Fusion.colors.textSecondary) },
        confirmButton = { TextButton(onClick = { confirmLogout = false; onLogOut() }) { Text("Выйти", color = Fusion.colors.danger) } },
        dismissButton = { TextButton(onClick = { confirmLogout = false }) { Text("Отмена", color = Fusion.colors.textPrimary) } }
    )
}

@Composable
private fun SettingRow(icon: KiroXIcon, title: String, trailing: String? = null, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).heightIn(min = 52.dp).padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
        IconGlyph(icon, null, Modifier.size(22.dp), Fusion.colors.textPrimary)
        Spacer(Modifier.width(16.dp))
        Text(title, color = Fusion.colors.textPrimary, fontSize = 15.sp, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (trailing != null) Text(trailing, color = Fusion.colors.textTertiary, fontSize = 13.sp)
        IconGlyph(KiroXIcon.Chevron, null, Modifier.padding(start = 6.dp).size(16.dp), Fusion.colors.textTertiary)
    }
}

@Composable
private fun InfoRow(icon: KiroXIcon, title: String, value: String) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 18.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        IconGlyph(icon, null, Modifier.size(22.dp), Fusion.colors.textSecondary)
        Spacer(Modifier.width(16.dp))
        Column { Text(value, color = Fusion.colors.textPrimary, fontSize = 15.sp); Text(title, color = Fusion.colors.textSecondary, fontSize = 12.sp) }
    }
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
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
        tabs.forEach { (label, value) ->
            val active = value == selected
            Column(Modifier.weight(1f).heightIn(min = 48.dp).clickable { onSelect(value) }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                Text(label, color = if (active) Fusion.colors.textPrimary else Fusion.colors.textSecondary, fontSize = 14.sp, fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal)
                Spacer(Modifier.height(10.dp))
                Box(Modifier.width(94.dp).height(2.dp).background(if (active) Fusion.colors.textPrimary else Fusion.colors.background))
            }
        }
    }
    HorizontalDivider(color = Fusion.colors.outline)
}

@Composable
private fun SearchField(value: String, hint: String, onChange: (String) -> Unit) {
    Row(
        Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth().heightIn(min = 44.dp).clip(RoundedCornerShape(12.dp))
            .background(Fusion.colors.surface2).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconGlyph(KiroXIcon.Search, null, Modifier.size(18.dp), Fusion.colors.textSecondary)
        Spacer(Modifier.width(10.dp))
        BasicTextField(
            value, onChange, Modifier.weight(1f), singleLine = true,
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
