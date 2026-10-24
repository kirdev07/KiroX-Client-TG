package com.kirox.app.ui.home

import android.content.Intent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kirox.app.core.demo.*
import com.kirox.app.ui.icons.KiroXIcon
import com.kirox.app.ui.icons.KiroXIcon as IconGlyph
import com.kirox.app.ui.chat.DemoChatScreen
import kotlin.math.abs
import kotlin.math.sin
import java.util.Locale
import kotlinx.coroutines.launch

private val White = Color(0xFFF4F4F4)
private val Secondary = Color(0xFF929296)
private val Surface = Color(0xFF151515)
private val Divider = Color(0xFF222222)

private enum class RootTab(val title: String, val icon: KiroXIcon) {
    Contacts("Контакты", KiroXIcon.Contacts), Chats("Чаты", KiroXIcon.Chat), Settings("Настройки", KiroXIcon.Settings), Profile("Профиль", KiroXIcon.Profile)
}
private enum class ChatSection { Archive, Messages }
private enum class ContactSection { Contacts, Calls }
@Composable
fun DemoMainScreen(onExit: () -> Unit, demoViewModel: DemoViewModel = viewModel()) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snapshot by demoViewModel.state.collectAsStateWithLifecycle()
    var root by rememberSaveable { mutableStateOf(RootTab.Chats) }
    var chatSection by rememberSaveable(snapshot.settings.chatSection) { mutableStateOf(runCatching { ChatSection.valueOf(snapshot.settings.chatSection) }.getOrDefault(ChatSection.Messages)) }
    var contactSection by rememberSaveable(snapshot.settings.contactsSection) { mutableStateOf(runCatching { ContactSection.valueOf(snapshot.settings.contactsSection) }.getOrDefault(ContactSection.Contacts)) }
    var notice by remember { mutableStateOf<String?>(null) }
    var drag by remember { mutableFloatStateOf(0f) }
    var settingsEntranceShown by rememberSaveable { mutableStateOf(false) }
    var accountsSheet by rememberSaveable { mutableStateOf(false) }
    var openChatId by rememberSaveable { mutableStateOf<Long?>(null) }
    val activeAccount = snapshot.activeAccount
    LaunchedEffect(chatSection) { demoViewModel.setChatSection(chatSection.name) }
    LaunchedEffect(contactSection) { demoViewModel.setContactsSection(contactSection.name) }

    openChatId?.let { chatId ->
        DemoChatScreen(chatId = chatId, demoViewModel = demoViewModel, onBack = { openChatId = null })
        return
    }

    fun swipe(delta: Float) {
        if (abs(delta) < 90f) return
        val right = delta > 0
        when (root) {
            RootTab.Chats -> when {
                chatSection == ChatSection.Messages && right -> chatSection = ChatSection.Archive
                chatSection == ChatSection.Archive && !right -> chatSection = ChatSection.Messages
                chatSection == ChatSection.Archive && right -> root = RootTab.Contacts
                chatSection == ChatSection.Messages && !right -> root = RootTab.Settings
            }
            RootTab.Contacts -> when {
                contactSection == ContactSection.Contacts && !right -> contactSection = ContactSection.Calls
                contactSection == ContactSection.Calls && right -> contactSection = ContactSection.Contacts
                contactSection == ContactSection.Calls && !right -> root = RootTab.Chats
            }
            RootTab.Settings -> root = if (right) RootTab.Chats else RootTab.Profile
            RootTab.Profile -> if (right) root = RootTab.Settings
        }
    }

    Scaffold(
        containerColor = Color.Black,
        contentWindowInsets = WindowInsets.statusBars,
        bottomBar = { BottomDock(root, { root = it }, { accountsSheet = true }) }
    ) { scaffoldPadding ->
        Box(Modifier.fillMaxSize().padding(scaffoldPadding).background(Color.Black)) {
            Box(
                Modifier.fillMaxSize().pointerInput(root, chatSection, contactSection) {
                    detectHorizontalDragGestures(
                        onDragStart = { drag = 0f },
                        onHorizontalDrag = { _, amount -> drag += amount },
                        onDragEnd = { swipe(drag); drag = 0f },
                        onDragCancel = { drag = 0f }
                    )
                }
            ) {
                AnimatedContent(root, transitionSpec = { fadeIn(tween(160)) togetherWith fadeOut(tween(120)) }, label = "root_tabs") { tab ->
                    when (tab) {
                        RootTab.Contacts -> ContactsScreen(contactSection, { contactSection = it }, { notice = it }, snapshot.contacts, snapshot.calls, snapshot.settings.callFilter, { contact -> scope.launch { openChatId = demoViewModel.chatFor(contact.contactId).chatId } }, demoViewModel)
                        RootTab.Chats -> ChatsScreen(chatSection, { chatSection = it; demoViewModel.setChatSection(it.name) }, { notice = it }, snapshot.chats, { openChatId = it }, demoViewModel)
                        RootTab.Settings -> SettingsScreen(onProfile = { root = RootTab.Profile }, onNotice = { notice = it }, entranceShown = settingsEntranceShown, onEntranceShown = { settingsEntranceShown = true }, showKiroXBadge = snapshot.settings.showKiroXBadge, onBadgeChange = demoViewModel::setBadge, effects = snapshot.settings.animationsEnabled, onEffectsChange = demoViewModel::setAnimations)
                        RootTab.Profile -> activeAccount?.let { ProfileScreen(it, snapshot.settings.showKiroXBadge, onAccounts = { accountsSheet = true }, onExit = onExit, onNotice = { notice = it }) }
                    }
                }
            }
            notice?.let {
                Surface(
                    color = Color(0xFF303030), shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp, start = 20.dp, end = 20.dp)
                ) {
                    Text(it, color = White, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp))
                }
                LaunchedEffect(it) { kotlinx.coroutines.delay(1800); notice = null }
            }
            if (accountsSheet) AccountsSheet(snapshot.accounts, snapshot.settings.activeAccountId, { demoViewModel.switchAccount(it); openChatId = null; accountsSheet = false }, { accountsSheet = false }, { notice = it })
        }
    }
}

@Composable private fun ScreenHeader(title: String, actions: @Composable RowScope.() -> Unit = {}) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val compact = maxWidth < 360.dp
        Row(Modifier.fillMaxWidth().heightIn(min = 58.dp).padding(horizontal = if (compact) 12.dp else 16.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = White, fontSize = if (compact) 23.sp else 26.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Row(content = actions)
            Text("ДЕМО", color = Color.Black, fontSize = 8.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 3.dp).clip(RoundedCornerShape(8.dp)).background(White).padding(horizontal = 5.dp, vertical = 3.dp))
        }
    }
}

@Composable private fun IconAction(icon: KiroXIcon, description: String, onClick: () -> Unit) {
    Box(Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp).semantics { contentDescription = description }.clip(CircleShape).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        IconGlyph(icon, null, Modifier.size(24.dp), White)
    }
}

@Composable private fun ChatsScreen(section: ChatSection, onSection: (ChatSection) -> Unit, onNotice: (String) -> Unit, chats: List<DemoChat>, onOpenChat: (Long) -> Unit, viewModel: DemoViewModel) {
    var menu by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Чаты") {
            IconAction(KiroXIcon.Search, "Поиск") { onNotice("Поиск чатов — в разработке") }
            IconAction(KiroXIcon.ReadAll, "Отметить всё прочитанным") { viewModel.markSectionRead(section == ChatSection.Archive); onNotice("Текущий раздел отмечен прочитанным") }
            Box { IconAction(KiroXIcon.Compose, "Создать") { menu = true }; CreateMenu(menu, { menu = false }, onNotice) }
        }
        Stories(onNotice)
        FlatTabs(listOf("Архив" to ChatSection.Archive, "Сообщения" to ChatSection.Messages), section, onSection)
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
            items(chats.filter { it.archived == (section == ChatSection.Archive) }, key = { it.chatId }) { chat -> ChatRow(chat, onOpenChat) { viewModel.archive(chat.chatId, !chat.archived) } }
        }
    }
}

@Composable private fun Stories(onNotice: (String) -> Unit) {
    val stories = listOf("+" to "Моя история", "К" to "Кирилл", "Д" to "Друзья", "Р" to "Работа", "И" to "Игры", "С" to "Семья")
    LazyRow(Modifier.heightIn(min = 86.dp), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(stories) { (letter, name) ->
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(57.dp).clickable { onNotice("Истории — в разработке") }) {
                Box(Modifier.size(50.dp).border(if (letter == "+") 1.dp else 2.dp, if (letter == "+") Color(0xFF666666) else Color(0xFFBDBDBD), CircleShape).clip(CircleShape).background(if (letter == "+") Color.Black else Color(0xFF333438)), contentAlignment = Alignment.Center) {
                    if (letter == "+") IconGlyph(KiroXIcon.Add, "Добавить историю", Modifier.size(20.dp), White) else Text(letter, color = White, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(5.dp)); Text(name, color = Secondary, fontSize = 9.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable private fun <T> FlatTabs(tabs: List<Pair<String, T>>, selected: T, onSelect: (T) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).border(width = 0.dp, color = Color.Transparent)) {
        tabs.forEach { (title, value) ->
            Column(Modifier.weight(1f).heightIn(min = 48.dp).clickable { onSelect(value) }.padding(top = 9.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                Text(title, color = if (value == selected) White else Secondary, fontSize = 14.sp, fontWeight = if (value == selected) FontWeight.SemiBold else FontWeight.Normal)
                Spacer(Modifier.height(10.dp)); Box(Modifier.width(94.dp).height(2.dp).background(if (value == selected) White else Color.Transparent))
            }
        }
    }
    HorizontalDivider(color = Divider)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable private fun ChatRow(chat: DemoChat, onOpenChat: (Long) -> Unit, onArchive: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 76.dp).combinedClickable(onClick = { onOpenChat(chat.chatId) }, onLongClick = onArchive).padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        Avatar(chat.title.take(1)); Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) { Text(chat.title, color = White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)); if (chat.pinned) { IconGlyph(KiroXIcon.Pin, "Закреплённый чат", Modifier.padding(horizontal = 5.dp).size(14.dp), Secondary) }; Text(formatTime(chat.lastMessageAt), color = Secondary, fontSize = 11.sp) }
            Spacer(Modifier.height(5.dp)); Row { Text(if (chat.draft.isNotBlank()) "Черновик: ${chat.draft}" else chat.lastMessage, color = if (chat.draft.isNotBlank()) Color(0xFFFF7770) else Secondary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)); if (chat.unreadCount > 0) Badge { Text(chat.unreadCount.toString()) } }
        }
    }
    HorizontalDivider(color = Divider)
}

@Composable private fun CreateMenu(expanded: Boolean, dismiss: () -> Unit, onNotice: (String) -> Unit) {
    DropdownMenu(expanded, dismiss, modifier = Modifier.background(Surface)) {
        listOf(
            KiroXIcon.Chat to "Новое сообщение", KiroXIcon.Contacts to "Создать группу",
            KiroXIcon.Add to "Создать канал", KiroXIcon.Bookmark to "Избранное",
            KiroXIcon.Shield to "Секретный чат"
        ).forEach { (icon, label) ->
            DropdownMenuItem(
                text = { Text(label, color = White) },
                leadingIcon = { IconGlyph(icon, null, Modifier.size(22.dp), White) },
                onClick = { dismiss(); onNotice("$label — в разработке") }
            )
        }
    }
}

@Composable private fun ContactsScreen(section: ContactSection, onSection: (ContactSection) -> Unit, onNotice: (String) -> Unit, people: List<DemoContact>, calls: List<DemoCall>, savedFilter: String, onOpenChat: (DemoContact) -> Unit, viewModel: DemoViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var filter by rememberSaveable(savedFilter) { mutableStateOf(savedFilter) }
    var query by rememberSaveable { mutableStateOf("") }
    var addContact by rememberSaveable { mutableStateOf(false) }
    var callTarget by remember { mutableStateOf<DemoContact?>(null) }
    val shownPeople = people.filter { it.name.contains(query.trim(), ignoreCase = true) || it.status.contains(query.trim(), ignoreCase = true) }
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Контакты") { IconAction(KiroXIcon.Search, "Перейти к поиску") { onNotice("Поле поиска находится под вкладками") }; IconAction(KiroXIcon.Add, "Добавить контакт") { addContact = true } }
        FlatTabs(listOf("Контакты" to ContactSection.Contacts, "Звонки" to ContactSection.Calls), section, onSection)
        if (section == ContactSection.Contacts) {
            SearchBox(query, "Поиск контактов", { query = it })
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 16.dp)) {
                if (shownPeople.isEmpty()) item { Text("Контакты не найдены", color = Secondary, modifier = Modifier.fillMaxWidth().padding(28.dp), textAlign = TextAlign.Center) }
                items(shownPeople, key = { it.contactId }) { PersonRow(it, onMessage = { onOpenChat(it) }, onCall = { callTarget = it }) }
            }
            OutlineButton("Пригласить друзей", KiroXIcon.Contacts) {
                val share = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, "Присоединяйтесь ко мне в KiroX") }
                context.startActivity(Intent.createChooser(share, "Пригласить друзей"))
            }
        } else {
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(16.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                listOf("Все", "Пропущенные", "Входящие", "Исходящие").forEach { Chip(it, filter == it) { filter = it; viewModel.setCallFilter(it) } }
            }
            val shown = calls.filter { filter == "Все" || (filter == "Пропущенные" && it.kind == "missed") || (filter == "Входящие" && it.kind == "incoming") || (filter == "Исходящие" && it.kind == "outgoing") }
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 16.dp)) {
                if (shown.isEmpty()) item { Text("В этом разделе звонков нет", color = Secondary, modifier = Modifier.fillMaxWidth().padding(28.dp), textAlign = TextAlign.Center) }
                items(shown, key = { it.callId }) { call -> CallRow(call) { callTarget = people.firstOrNull { it.contactId == call.contactId } } }
            }
        }
    }
    if (addContact) AddContactDialog(onDismiss = { addContact = false }, onAdd = { name, _ ->
        scope.launch { runCatching { viewModel.addContact(name) }.onSuccess { onNotice("Контакт сохранён в DEMO") }.onFailure { onNotice("Контакт с таким именем уже существует") } }
        addContact = false
    })
    callTarget?.let { person -> DemoCallDialog(person, onDismiss = { callTarget = null }, onStart = {
        viewModel.addCall(person)
        callTarget = null; onSection(ContactSection.Calls); filter = "Все"; onNotice("Демонстрационный вызов добавлен в журнал")
    }) }
}

@Composable private fun SearchBox(value: String, hint: String, onChange: (String) -> Unit) { Row(Modifier.padding(16.dp).fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(14.dp)).background(Surface).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) { IconGlyph(KiroXIcon.Search, null, Modifier.size(20.dp), Secondary); Spacer(Modifier.width(10.dp)); BasicTextField(value, onChange, Modifier.weight(1f), textStyle = TextStyle(color = White, fontSize = 14.sp), singleLine = true, cursorBrush = SolidColor(White), decorationBox = { inner -> Box { if (value.isBlank()) Text(hint, color = Secondary, fontSize = 13.sp); inner() } }); if (value.isNotEmpty()) Text("×", color = Secondary, fontSize = 21.sp, modifier = Modifier.clickable { onChange("") }.padding(5.dp)) } }
@Composable private fun Chip(text: String, selected: Boolean, click: () -> Unit) { Text(text, color = if (selected) Color.Black else Secondary, fontSize = 12.sp, modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(if (selected) White else Surface).clickable(onClick = click).padding(horizontal = 13.dp, vertical = 8.dp)) }

@Composable private fun PersonRow(person: DemoContact, onMessage: () -> Unit, onCall: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 74.dp).padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Avatar(person.name.take(1)); Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text(person.name, color = White, fontWeight = FontWeight.SemiBold); Text(person.status, color = Secondary, fontSize = 12.sp) }
        IconAction(KiroXIcon.Chat, "Написать ${person.name}", onMessage); IconAction(KiroXIcon.Call, "Позвонить ${person.name}", onCall)
    }; HorizontalDivider(color = Divider)
}
@Composable private fun CallRow(call: DemoCall, onCall: () -> Unit) { val typeIcon = when (call.kind) { "incoming" -> KiroXIcon.CallIncoming; "outgoing" -> KiroXIcon.CallOutgoing; else -> KiroXIcon.CallMissed }; Row(Modifier.fillMaxWidth().heightIn(min = 68.dp).padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) { Avatar(call.name.take(1), 46); Spacer(Modifier.width(11.dp)); Column(Modifier.weight(1f)) { Text(call.name, color = White, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis); Row(verticalAlignment = Alignment.CenterVertically) { IconGlyph(typeIcon, null, Modifier.size(14.dp), if (call.kind == "missed") Color(0xFFFF7770) else Secondary); Spacer(Modifier.width(4.dp)); Text("${callKind(call.kind)} · ${formatTime(call.occurredAt)}", color = if (call.kind == "missed") Color(0xFFFF7770) else Secondary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) } }; IconAction(KiroXIcon.Call, "Повторить демонстрационный звонок", onCall) }; HorizontalDivider(color = Divider) }

@Composable private fun AddContactDialog(onDismiss: () -> Unit, onAdd: (String, String) -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }; var status by rememberSaveable { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, containerColor = Surface, title = { Text("Новый DEMO-контакт", color = White) }, text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { DemoTextField(name, "Имя", { name = it }); DemoTextField(status, "Статус (необязательно)", { status = it }) } }, confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = { onAdd(name.trim(), status.trim().ifBlank { "добавлен локально" }) }) { Text("Добавить", color = if (name.isNotBlank()) White else Secondary) } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена", color = Secondary) } })
}

@Composable private fun DemoTextField(value: String, hint: String, onChange: (String) -> Unit) { BasicTextField(value, onChange, Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF242426)).padding(14.dp), textStyle = TextStyle(color = White, fontSize = 14.sp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text), singleLine = true, cursorBrush = SolidColor(White), decorationBox = { inner -> Box { if (value.isBlank()) Text(hint, color = Secondary, fontSize = 14.sp); inner() } }) }

@Composable private fun DemoCallDialog(person: DemoContact, onDismiss: () -> Unit, onStart: () -> Unit) { AlertDialog(onDismissRequest = onDismiss, containerColor = Surface, icon = { IconGlyph(KiroXIcon.Call, null, Modifier.size(28.dp), White) }, title = { Text("Демонстрационный звонок", color = White, textAlign = TextAlign.Center) }, text = { Text("Вызов ${person.name} не соединяется с Telegram. В журнал будет добавлена только локальная запись.", color = Secondary, textAlign = TextAlign.Center) }, confirmButton = { TextButton(onClick = onStart) { Text("Добавить в журнал", color = White) } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена", color = Secondary) } }) }

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun SettingsScreen(onProfile: () -> Unit, onNotice: (String) -> Unit, entranceShown: Boolean, onEntranceShown: () -> Unit, showKiroXBadge: Boolean, onBadgeChange: (Boolean) -> Unit, effects: Boolean, onEffectsChange: (Boolean) -> Unit) {
    data class Item(val icon: KiroXIcon, val title: String)
    val rows = listOf(
        Item(KiroXIcon.Palette, "Кастомизация"), Item(KiroXIcon.Folder, "Чаты и папки"),
        Item(KiroXIcon.Bell, "Уведомления"), Item(KiroXIcon.Shield, "Конфиденциальность"),
        Item(KiroXIcon.Storage, "Данные и память"), Item(KiroXIcon.Devices, "Устройства"),
        Item(KiroXIcon.Star, "Telegram Premium"), Item(KiroXIcon.Sliders, "Возможности KiroX"),
        Item(KiroXIcon.Settings, "Дополнительно"), Item(KiroXIcon.Refresh, "Обновления"),
        Item(KiroXIcon.Info, "О KiroX")
    )
    var customization by rememberSaveable { mutableStateOf(false) }
    val identityAlpha by animateFloatAsState(if (entranceShown) 1f else 0f, tween(260), label = "identity_entrance")
    LaunchedEffect(Unit) { onEntranceShown() }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 20.dp)) {
        item { ScreenHeader("Настройки") { IconAction(KiroXIcon.Search, "Поиск настроек") { onNotice("Поиск настроек — в разработке") } } }
        item { Box(Modifier.graphicsLayer { alpha = identityAlpha; translationY = (1f - identityAlpha) * 12.dp.toPx() }) { CenterIdentity(effects, { onEffectsChange(!effects) }, onProfile, onNotice) } }
        items(rows, key = { it.title }) { row -> CompactSettingRow(row.icon, row.title) { if (row.title == "Кастомизация") customization = true else onNotice("${row.title}: функция пока доступна только как демо") } }
    }
    if (customization) ModalBottomSheet(onDismissRequest = { customization = false }, containerColor = Color(0xFF101010), dragHandle = { BottomSheetDefaults.DragHandle(color = Secondary) }) { Text("Кастомизация", color = White, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 20.dp)); Text("ПРОФИЛЬ", color = Secondary, fontSize = 10.sp, letterSpacing = 1.2.sp, modifier = Modifier.padding(start = 20.dp, top = 22.dp, bottom = 8.dp)); Row(Modifier.fillMaxWidth().heightIn(min = 60.dp).padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text("Показывать отметку KiroX", color = White); Text("Только локальное оформление", color = Secondary, fontSize = 11.sp) }; Switch(showKiroXBadge, onBadgeChange, colors = monoSwitchColors()) }; Spacer(Modifier.navigationBarsPadding().height(20.dp)) }
}

@Composable private fun CenterIdentity(effects: Boolean, toggleEffects: () -> Unit, openProfile: () -> Unit, notice: (String) -> Unit) {
    Box(Modifier.fillMaxWidth().heightIn(min = 238.dp)) {
        if (effects) SeasonalParticles(Modifier.matchParentSize())
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(112.dp), contentAlignment = Alignment.Center) {
                Box(Modifier.size(96.dp).border(1.5.dp, Color(0xFF77777C), CircleShape).padding(2.dp).clickable(onClick = openProfile), contentAlignment = Alignment.Center) { Avatar("К", 88) }
                Box(Modifier.align(Alignment.BottomEnd).size(48.dp).clip(CircleShape).clickable { notice("Смена фотографии — демо") }, contentAlignment = Alignment.Center) { Box(Modifier.size(31.dp).clip(CircleShape).background(Surface).border(1.dp, Divider, CircleShape), contentAlignment = Alignment.Center) { IconGlyph(KiroXIcon.Camera, "Сменить фото", Modifier.size(17.dp), White) } }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 32.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) { Text("Кирилл", color = White, fontSize = 20.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis); Spacer(Modifier.width(6.dp)); IconGlyph(KiroXIcon.Star, "Демо emoji-статус", Modifier.size(18.dp), White) }
            Text("@username", color = Secondary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(8.dp)); Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                CompactAction(KiroXIcon.Edit, "Редактировать профиль") { notice("Редактирование профиля — демо") }
                IconAction(KiroXIcon.Contacts, "Переключить аккаунт") { notice("Переключение аккаунтов — демо, состояние сохранено") }
                IconAction(KiroXIcon.Stories, if (effects) "Отключить сезонные эффекты" else "Включить сезонные эффекты", toggleEffects)
            }
        }
    }
    HorizontalDivider(color = Divider)
}

@Composable private fun SeasonalParticles(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "settings_particles")
    val phase by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(7000, easing = LinearEasing), RepeatMode.Restart), label = "particle_phase")
    Canvas(modifier.alpha(.55f)) { repeat(12) { index -> val x = size.width * ((index * 37 % 101) / 100f); val base = size.height * ((index * 23 % 89) / 100f); val y = (base + sin((phase + index * .13f) * 6.283f) * 10.dp.toPx()).coerceIn(0f, size.height); drawCircle(Color.White.copy(alpha = .18f + (index % 3) * .08f), (index % 2 + 1) * .65.dp.toPx(), Offset(x, y)) } }
}

@Composable private fun CompactAction(icon: KiroXIcon, text: String, click: () -> Unit) { Row(Modifier.heightIn(min = 40.dp).clip(RoundedCornerShape(20.dp)).background(Surface).clickable(onClick = click).padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) { IconGlyph(icon, null, Modifier.size(17.dp), White); Spacer(Modifier.width(7.dp)); Text(text, color = White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1) } }

@Composable private fun CompactSettingRow(icon: KiroXIcon, title: String, click: () -> Unit) { Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable(onClick = click).padding(horizontal = 18.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) { IconGlyph(icon, null, Modifier.size(22.dp), White); Spacer(Modifier.width(16.dp)); Text(title, color = White, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)); IconGlyph(KiroXIcon.Chevron, null, Modifier.size(18.dp), Secondary) }; HorizontalDivider(Modifier.padding(horizontal = 18.dp), color = Divider) }

@Composable private fun ProfileScreen(account: DemoAccount, showBadge: Boolean, onAccounts: () -> Unit, onExit: () -> Unit, onNotice: (String) -> Unit) {
    var mediaSection by rememberSaveable { mutableStateOf("Медиа") }
    var contentDrag by remember { mutableFloatStateOf(0f) }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val collapse by remember { derivedStateOf { if (listState.firstVisibleItemIndex > 1) 1f else (listState.firstVisibleItemScrollOffset / 180f).coerceIn(0f, 1f) } }
    LazyColumn(Modifier.fillMaxSize(), state = listState, contentPadding = PaddingValues(bottom = 24.dp)) {
        item { ScreenHeader("Мой профиль") { IconAction(KiroXIcon.Contacts, "Открыть аккаунты", onAccounts); IconAction(KiroXIcon.Edit, "Редактировать профиль") { onNotice("Редактирование профиля — DEMO") } } }
        item { ProfileCompactIdentity(account, showBadge, collapse, onAccounts, onNotice) }
        item {
            ProfileInfoRow(KiroXIcon.Info, "О себе", "Создаю KiroX", true)
            account.username?.let { ProfileInfoRow(KiroXIcon.At, "Имя пользователя", it, true) }
            ProfileInfoRow(KiroXIcon.Cake, "Дата рождения", "Не указана", true)
        }
        item { SectionLabel("МОЙ КОНТЕНТ"); Box(Modifier.fillMaxWidth().pointerInput(mediaSection) { detectHorizontalDragGestures(onDragStart = { contentDrag = 0f }, onHorizontalDrag = { change, amount -> change.consume(); contentDrag += amount }, onDragEnd = { val tabs = listOf("Медиа", "Файлы", "Ссылки", "Истории"); val i = tabs.indexOf(mediaSection); if (contentDrag < -70 && i < tabs.lastIndex) mediaSection = tabs[i + 1]; if (contentDrag > 70 && i > 0) mediaSection = tabs[i - 1] }) }) { LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(7.dp)) { items(listOf("Медиа", "Файлы", "Ссылки", "Истории")) { title -> Chip(title, mediaSection == title) { mediaSection = title } } } }; ProfileMediaPreview(mediaSection, onNotice) }
        item { TextButton(onClick = onExit, modifier = Modifier.fillMaxWidth().padding(12.dp)) { Text("Выйти из DEMO", color = Secondary) } }
    }
}

@Composable private fun ProfileCompactIdentity(account: DemoAccount, badge: Boolean, collapse: Float, accounts: () -> Unit, notice: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().graphicsLayer { alpha = 1f - collapse * .16f; scaleX = 1f - collapse * .04f; scaleY = 1f - collapse * .04f }.padding(horizontal = 16.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(96.dp).border(1.5.dp, Color(0xFF77777C), CircleShape).padding(2.dp).clickable { notice("Фото профиля и истории — DEMO") }, contentAlignment = Alignment.Center) { Avatar(account.initial, 88) }
        Spacer(Modifier.height(7.dp)); Row(Modifier.fillMaxWidth().padding(horizontal = 30.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) { Text(account.name, color = White, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false)); if (badge) { Spacer(Modifier.width(6.dp)); HexBadge(Modifier.size(20.dp)) } }
        account.username?.let { Text(it, color = Secondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        if (badge) Text("Пользователь KiroX", color = Secondary, fontSize = 11.sp)
        Spacer(Modifier.height(9.dp)); Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { CompactProfileButton(KiroXIcon.Edit, "Редактировать", Modifier.weight(1f)) { notice("Редактирование профиля — DEMO") }; CompactProfileButton(KiroXIcon.Contacts, "Аккаунты", Modifier.weight(1f), accounts) }
    }
    HorizontalDivider(color = Divider)
}

@Composable private fun CompactProfileButton(icon: KiroXIcon, text: String, modifier: Modifier, click: () -> Unit) { Row(modifier.heightIn(min = 44.dp).clip(RoundedCornerShape(14.dp)).background(Surface).clickable(onClick = click).padding(horizontal = 10.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) { IconGlyph(icon, null, Modifier.size(18.dp), White); Spacer(Modifier.width(7.dp)); Text(text, color = White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis) } }

@Composable private fun ProfileInfoRow(icon: KiroXIcon, title: String, value: String, divider: Boolean) { Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 18.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) { IconGlyph(icon, null, Modifier.size(21.dp), Secondary); Spacer(Modifier.width(15.dp)); Column(Modifier.weight(1f)) { Text(title, color = White, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1); Text(value, color = Secondary, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis) } }; if (divider) HorizontalDivider(Modifier.padding(start = 54.dp), color = Divider) }

@Composable private fun ProfileMediaPreview(section: String, notice: (String) -> Unit) { val icon = when(section) { "Файлы" -> KiroXIcon.File; "Ссылки" -> KiroXIcon.Link; "Музыка" -> KiroXIcon.Music; "Голосовые" -> KiroXIcon.Voice; "Истории" -> KiroXIcon.Stories; else -> KiroXIcon.Media }; Row(Modifier.padding(16.dp).fillMaxWidth().heightIn(min = 64.dp).clip(RoundedCornerShape(13.dp)).background(Surface).clickable { notice("$section: демонстрационный раздел") }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) { IconGlyph(icon, null, Modifier.size(23.dp), White); Spacer(Modifier.width(13.dp)); Column(Modifier.weight(1f)) { Text(section, color = White, fontWeight = FontWeight.SemiBold); Text("Данные появятся после подключения TDLib", color = Secondary, fontSize = 11.sp) }; IconGlyph(KiroXIcon.Chevron, null, Modifier.size(18.dp), Secondary) } }

@OptIn(ExperimentalMaterial3Api::class)
@Composable private fun AccountsSheet(accounts: List<DemoAccount>, activeId: Long, select: (Long) -> Unit, dismiss: () -> Unit, notice: (String) -> Unit) {
    ModalBottomSheet(onDismissRequest = dismiss, containerColor = Color(0xFF101010), dragHandle = { BottomSheetDefaults.DragHandle(color = Secondary) }) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) { Text("Выберите аккаунт", color = White, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)); IconAction(KiroXIcon.More, "Управление аккаунтами") { notice("Управление реальными аккаунтами появится с TDLib") } }
        Text("DEMO · изолированные данные", color = Secondary, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
        accounts.forEach { account -> Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).clickable { select(account.accountId) }.padding(horizontal = 20.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) { Avatar(account.initial, 44); Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text(account.name, color = White, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis); account.username?.let { Text(it, color = Secondary, fontSize = 11.sp, maxLines = 1) } }; if (account.accountId == activeId) IconGlyph(KiroXIcon.Check, "Активный аккаунт", Modifier.size(20.dp), White); IconAction(KiroXIcon.More, "Меню аккаунта ${account.name}") { notice("Управление DEMO-аккаунтом недоступно") } }; HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = Divider) }
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable { notice("Добавление аккаунта недоступно: TDLib-авторизация ещё не подключена") }.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) { IconGlyph(KiroXIcon.Add, null, Modifier.size(22.dp), White); Spacer(Modifier.width(14.dp)); Text("Добавить аккаунт", color = White, fontWeight = FontWeight.Medium) }
        Spacer(Modifier.navigationBarsPadding().height(16.dp))
    }
}

private fun formatTime(value: Long): String = if (value == 0L) "" else java.text.SimpleDateFormat("HH:mm", Locale.getDefault()).format(java.util.Date(value))
private fun callKind(value: String) = when (value) { "incoming" -> "Входящий"; "outgoing" -> "Исходящий"; else -> "Пропущенный" }

@Composable private fun monoSwitchColors() = SwitchDefaults.colors(checkedThumbColor = Color.Black, checkedTrackColor = White, checkedBorderColor = White, uncheckedThumbColor = Secondary, uncheckedTrackColor = Surface, uncheckedBorderColor = Color(0xFF555555))

@Composable private fun SectionLabel(text: String) { Text(text, color = Secondary, fontSize = 10.sp, letterSpacing = 1.2.sp, modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp)) }
@Composable private fun Tile(icon: KiroXIcon, text: String, modifier: Modifier, click: () -> Unit) { Column(modifier.heightIn(min = 64.dp).clip(RoundedCornerShape(13.dp)).background(Surface).border(1.dp, Divider, RoundedCornerShape(13.dp)).clickable(onClick = click).padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) { IconGlyph(icon, null, Modifier.size(20.dp), White); Spacer(Modifier.height(4.dp)); Text(text, color = White, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) } }
@Composable private fun OutlineButton(text: String, icon: KiroXIcon? = null, click: () -> Unit) { Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth().heightIn(min = 52.dp).border(1.dp, Color(0xFF363636), RoundedCornerShape(14.dp)).clip(RoundedCornerShape(14.dp)).clickable(onClick = click).padding(horizontal = 14.dp, vertical = 10.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) { if (icon != null) { IconGlyph(icon, null, Modifier.size(20.dp), White); Spacer(Modifier.width(8.dp)) }; Text(text, color = White, fontSize = 14.sp, fontWeight = FontWeight.Medium) } }

@Composable private fun Avatar(letter: String, size: Int = 50) { Box(Modifier.size(size.dp).clip(CircleShape).background(Color(0xFF40505C)), contentAlignment = Alignment.Center) { Text(letter, color = White, fontSize = (size * .36f).sp, fontWeight = FontWeight.Bold) } }
@Composable private fun HexBadge(modifier: Modifier = Modifier.size(24.dp)) { Canvas(modifier.semantics { contentDescription = "Фирменная локальная отметка KiroX" }) { val p = androidx.compose.ui.graphics.Path(); p.moveTo(size.width*.25f, 0f); p.lineTo(size.width*.75f,0f); p.lineTo(size.width,size.height*.5f); p.lineTo(size.width*.75f,size.height); p.lineTo(size.width*.25f,size.height); p.lineTo(0f,size.height*.5f); p.close(); drawPath(p, Color(0xFF444448)); drawLine(Color.White, Offset(size.width*.32f,size.height*.3f), Offset(size.width*.68f,size.height*.7f), 1.6.dp.toPx(), StrokeCap.Round); drawLine(Color.White, Offset(size.width*.68f,size.height*.3f), Offset(size.width*.32f,size.height*.7f), 1.6.dp.toPx(), StrokeCap.Round) } }

@OptIn(ExperimentalFoundationApi::class)
@Composable private fun BottomDock(selected: RootTab, onSelect: (RootTab) -> Unit, onProfileLongClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().background(Color(0xFF040404)).border(1.dp, Divider).navigationBarsPadding().heightIn(min = 72.dp).padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        RootTab.entries.forEach { tab -> Column(Modifier.weight(1f).sizeIn(minWidth = 48.dp, minHeight = 64.dp).clip(RoundedCornerShape(10.dp)).combinedClickable(indication = null, interactionSource = remember { MutableInteractionSource() }, onClick = { onSelect(tab) }, onLongClick = { if (tab == RootTab.Profile) onProfileLongClick() }), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) { IconGlyph(tab.icon, tab.title, Modifier.size(24.dp), if (selected == tab) White else Secondary); Spacer(Modifier.height(3.dp)); Text(tab.title, color = if (selected == tab) White else Secondary, fontSize = 10.sp, maxLines = 1) } }
    }
}
