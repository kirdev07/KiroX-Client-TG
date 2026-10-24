package com.kirox.app.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kirox.app.BuildConfig
import com.kirox.app.telegram.TelegramContact
import com.kirox.app.telegram.TelegramUserProfile
import com.kirox.app.ui.components.TelegramAvatar
import com.kirox.app.ui.format.TelegramFormat
import com.kirox.app.ui.icons.KiroXIcon
import com.kirox.app.ui.icons.KiroXIcon as IconGlyph
import com.kirox.app.ui.theme.Fusion
import com.kirox.app.ui.theme.FusionPalette

private const val IN_PROGRESS = "В разработке"

// ---------- Settings (app settings only; the profile has its own tab) ----------

private data class SettingsEntry(val icon: KiroXIcon, val title: String, val subtitle: String)

@Composable
internal fun SettingsTab(profile: TelegramUserProfile?, palette: FusionPalette, onOpenProfile: () -> Unit, onOpenCustomization: () -> Unit, onNotice: (String) -> Unit) {
    val entries = listOf(
        SettingsEntry(KiroXIcon.Palette, "Кастомизация", "Темы, обои, анимации"),
        SettingsEntry(KiroXIcon.Chat, "Чаты и папки", "Папки и поведение"),
        SettingsEntry(KiroXIcon.Bell, "Уведомления", "Звуки и оповещения"),
        SettingsEntry(KiroXIcon.Shield, "Конфиденциальность", "Безопасность и приватность"),
        SettingsEntry(KiroXIcon.Storage, "Данные и память", "Хранилище и загрузки"),
        SettingsEntry(KiroXIcon.Devices, "Устройства", "Активные сеансы"),
        SettingsEntry(KiroXIcon.Star, "Telegram Premium", if (profile?.isPremium == true) "Подписка активна" else "Официальная подписка"),
        SettingsEntry(KiroXIcon.Sliders, "Возможности KiroX", "Реальные статусы функций"),
        SettingsEntry(KiroXIcon.Settings, "Дополнительно", "Язык, энергосбережение"),
        SettingsEntry(KiroXIcon.Refresh, "Обновления", "Версия ${BuildConfig.VERSION_NAME}"),
        SettingsEntry(KiroXIcon.Info, "О KiroX", "Лицензии и разработчик")
    )
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { ScreenTitle("Настройки") { TitleAction(KiroXIcon.Search, "Поиск настроек") { onNotice("Поиск по настройкам — $IN_PROGRESS") } } }
        item {
            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Fusion.colors.surface2)
                    .clickable(onClick = onOpenProfile).padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TelegramAvatar(profile?.name.orEmpty(), profile?.photo, 44.dp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Мой аккаунт", color = Fusion.colors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Text("Профиль и управление аккаунтами", color = Fusion.colors.textSecondary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                IconGlyph(KiroXIcon.Chevron, null, Modifier.size(18.dp), Fusion.colors.textSecondary)
            }
        }
        items(entries, key = { it.title }) { entry ->
            val subtitle = if (entry.title == "Кастомизация") "${if (palette == FusionPalette.GraphiteDark) "Graphite Dark" else "AMOLED Black"} · темы, обои, анимации" else entry.subtitle
            TwoLineRow(entry.icon, entry.title, subtitle) {
                when (entry.title) {
                    "Кастомизация" -> onOpenCustomization()
                    "О KiroX" -> onNotice("KiroX ${BuildConfig.VERSION_NAME} — независимый клиент Telegram на TDLib")
                    else -> onNotice("«${entry.title}» — $IN_PROGRESS")
                }
            }
        }
    }
}

@Composable
internal fun CustomizationScreen(palette: FusionPalette, onPalette: (FusionPalette) -> Unit, onBack: () -> Unit, onNotice: (String) -> Unit) {
    var themeSheet by rememberSaveable { mutableStateOf(false) }
    BackHandler(onBack = onBack)
    val entries = listOf(
        Triple(KiroXIcon.Chat, "Главный экран", "Истории, архив, диалоги"),
        Triple(KiroXIcon.Sliders, "Нижняя панель", "Иконки и анимации"),
        Triple(KiroXIcon.Chat, "Chat Studio", "Пузыри и поле ввода"),
        Triple(KiroXIcon.Profile, "Профили", "Размер аватара и строки"),
        Triple(KiroXIcon.Star, "Анимация запуска", "Частицы, волна, сезоны"),
        Triple(KiroXIcon.Photo, "Обои", "Для всех или отдельных чатов")
    )
    LazyColumn(Modifier.fillMaxSize().background(Fusion.colors.background), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { BackTitle("Кастомизация", onBack) }
        item {
            Column(
                Modifier.padding(16.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Fusion.colors.surface1)
                    .border(1.dp, Fusion.colors.outline, RoundedCornerShape(20.dp)).padding(vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("kiroX", color = Fusion.colors.textPrimary, fontSize = 34.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                Text(if (palette == FusionPalette.GraphiteDark) "Graphite Dark" else "AMOLED Black", color = Fusion.colors.textSecondary, fontSize = 13.sp)
            }
        }
        item { TwoLineRow(KiroXIcon.Palette, "Мои темы", "${if (palette == FusionPalette.GraphiteDark) "Graphite Dark" else "AMOLED Black"} — активна") { themeSheet = true } }
        items(entries, key = { it.second }) { (icon, title, subtitle) -> TwoLineRow(icon, title, subtitle) { onNotice("«$title» — $IN_PROGRESS") } }
    }
    if (themeSheet) ThemeSheet(palette, onSelect = onPalette, onDismiss = { themeSheet = false })
}

// ---------- My profile ----------

@Composable
internal fun ProfileTab(profile: TelegramUserProfile?, onOpenSaved: () -> Unit, onLogOut: () -> Unit, onNotice: (String) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    var confirmLogout by remember { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            ScreenTitle("Мой профиль") {
                TitleAction(KiroXIcon.Edit, "Редактировать профиль") { onNotice("Редактирование профиля — $IN_PROGRESS") }
                Box {
                    TitleAction(KiroXIcon.More, "Ещё") { menu = true }
                    DropdownMenu(menu, { menu = false }, modifier = Modifier.background(Fusion.colors.surface2)) {
                        DropdownMenuItem(text = { Text("Выйти из аккаунта", color = Fusion.colors.danger) }, onClick = { menu = false; confirmLogout = true })
                    }
                }
            }
        }
        item {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(124.dp).border(1.5.dp, Fusion.colors.outlineStrong, CircleShape).padding(5.dp), contentAlignment = Alignment.Center) {
                    TelegramAvatar(profile?.name.orEmpty(), profile?.photo, 112.dp)
                }
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(profile?.name ?: "Загрузка…", color = Fusion.colors.textPrimary, fontSize = 26.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    Spacer(Modifier.width(8.dp))
                    KiroXBadge(Modifier.size(24.dp))
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.clip(RoundedCornerShape(20.dp)).border(1.dp, Fusion.colors.outlineStrong, RoundedCornerShape(20.dp)).padding(horizontal = 12.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconGlyph(KiroXIcon.Check, null, Modifier.size(14.dp), Fusion.colors.textPrimary)
                    Spacer(Modifier.width(4.dp))
                    Text("Пользователь KiroX", color = Fusion.colors.textPrimary, fontSize = 13.sp)
                }
                profile?.username?.let { Spacer(Modifier.height(8.dp)); Text("@$it", color = Fusion.colors.textSecondary, fontSize = 15.sp) }
                Spacer(Modifier.height(4.dp))
                Text(
                    buildString {
                        append(if (profile?.isPremium == true) "Telegram Premium: активен" else "Telegram Premium: не активен")
                        if (profile?.isVerified == true) append(" · Проверено Telegram")
                    },
                    color = Fusion.colors.textTertiary, fontSize = 13.sp
                )
                Spacer(Modifier.height(14.dp))
                Row(
                    Modifier.clip(RoundedCornerShape(16.dp)).background(Fusion.colors.accent).clickable { onNotice("Редактирование профиля — $IN_PROGRESS") }
                        .heightIn(min = 52.dp).padding(horizontal = 28.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconGlyph(KiroXIcon.Edit, null, Modifier.size(18.dp), Fusion.colors.onAccent)
                    Spacer(Modifier.width(10.dp))
                    Text("Редактировать профиль", color = Fusion.colors.onAccent, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        item { HorizontalDivider(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), color = Fusion.colors.outline) }
        item { TwoLineRow(KiroXIcon.Profile, "О себе", profile?.bio?.ifBlank { null } ?: "Добавить описание", chevron = false) { if (profile?.bio.isNullOrBlank()) onNotice("Редактирование описания — $IN_PROGRESS") } }
        item { TwoLineRow(KiroXIcon.Info, "Дата рождения", profile?.birthday?.ifBlank { null } ?: "Не указана в Telegram", chevron = false) {} }
        profile?.phoneNumber?.takeIf { it.isNotBlank() }?.let { phone -> item { TwoLineRow(KiroXIcon.Phone, "Телефон", TelegramFormat.phone(phone), chevron = false) {} } }
        item { Text("МОЙ КОНТЕНТ", color = Fusion.colors.textSecondary, fontSize = 12.sp, letterSpacing = 1.2.sp, modifier = Modifier.padding(start = 16.dp, top = 18.dp, bottom = 10.dp)) }
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ContentTile(KiroXIcon.Stories, "Истории", Modifier.weight(1f)) { onNotice("Ваши истории — $IN_PROGRESS") }
                ContentTile(KiroXIcon.Media, "Медиа", Modifier.weight(1f)) { onNotice("Медиа профиля — $IN_PROGRESS") }
                ContentTile(KiroXIcon.Bookmark, "Избранное", Modifier.weight(1f), onOpenSaved)
            }
        }
        item {
            Row(
                Modifier.padding(16.dp).fillMaxWidth().heightIn(min = 52.dp).clip(RoundedCornerShape(16.dp)).border(1.dp, Fusion.colors.outline, RoundedCornerShape(16.dp))
                    .clickable { onNotice("Несколько аккаунтов — $IN_PROGRESS") },
                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically
            ) {
                IconGlyph(KiroXIcon.Contacts, null, Modifier.size(20.dp), Fusion.colors.textPrimary)
                Spacer(Modifier.width(10.dp))
                Text("Переключить аккаунт", color = Fusion.colors.textPrimary, fontSize = 16.sp)
            }
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

/** Local KiroX mark: a monochrome hexagon, deliberately unlike Telegram's verification check. */
@Composable
internal fun KiroXBadge(modifier: Modifier) {
    val fill = Fusion.colors.surface3
    val stroke = Fusion.colors.textPrimary
    Canvas(modifier.semantics { contentDescription = "Отметка KiroX (локальная)" }) {
        val w = size.width; val h = size.height
        val hex = Path().apply {
            moveTo(w * .25f, h * .06f); lineTo(w * .75f, h * .06f); lineTo(w, h * .5f)
            lineTo(w * .75f, h * .94f); lineTo(w * .25f, h * .94f); lineTo(0f, h * .5f); close()
        }
        drawPath(hex, fill)
        val sw = w * .09f
        drawLine(stroke, Offset(w * .3f, h * .52f), Offset(w * .44f, h * .66f), sw, StrokeCap.Round)
        drawLine(stroke, Offset(w * .44f, h * .66f), Offset(w * .7f, h * .36f), sw, StrokeCap.Round)
    }
}

@Composable
private fun ContentTile(icon: KiroXIcon, title: String, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.heightIn(min = 84.dp).clip(RoundedCornerShape(16.dp)).background(Fusion.colors.surface1).border(1.dp, Fusion.colors.outline, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick).padding(vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
    ) {
        IconGlyph(icon, null, Modifier.size(24.dp), Fusion.colors.textPrimary)
        Spacer(Modifier.height(8.dp))
        Text(title, color = Fusion.colors.textPrimary, fontSize = 13.sp, maxLines = 1)
    }
}

// ---------- New message ----------

@Composable
internal fun NewMessageScreen(contacts: List<TelegramContact>, onBack: () -> Unit, onOpenContact: (Long) -> Unit, onOpenSaved: () -> Unit, onNotice: (String) -> Unit) {
    var picking by rememberSaveable { mutableStateOf(false) }
    BackHandler { if (picking) picking = false else onBack() }
    LazyColumn(Modifier.fillMaxSize().background(Fusion.colors.background), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { BackTitle(if (picking) "Выберите контакт" else "Новое сообщение") { if (picking) picking = false else onBack() } }
        if (!picking) {
            item { Text("СОЗДАТЬ", color = Fusion.colors.textSecondary, fontSize = 12.sp, letterSpacing = 1.2.sp, modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 6.dp)) }
            item { TwoLineRow(KiroXIcon.Chat, "Новое сообщение", null) { picking = true } }
            item { TwoLineRow(KiroXIcon.Contacts, "Создать группу", null) { onNotice("Создание групп — $IN_PROGRESS") } }
            item { TwoLineRow(KiroXIcon.Add, "Создать канал", null) { onNotice("Создание каналов — $IN_PROGRESS") } }
            item { TwoLineRow(KiroXIcon.Bookmark, "Избранное", null, onClick = onOpenSaved) }
            item { TwoLineRow(KiroXIcon.Shield, "Секретный чат", null) { onNotice("Секретные чаты — $IN_PROGRESS") } }
        } else {
            if (contacts.isEmpty()) item { Text("Контактов пока нет", color = Fusion.colors.textSecondary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(32.dp)) }
            items(contacts, key = { it.id }) { contact ->
                Row(Modifier.fillMaxWidth().clickable { onOpenContact(contact.id) }.heightIn(min = 64.dp).padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    TelegramAvatar(contact.name, contact.photo, 46.dp)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(contact.name, color = Fusion.colors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(TelegramFormat.status(contact.status), color = Fusion.colors.textSecondary, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

// ---------- Shared building blocks for these screens ----------

@Composable
internal fun ScreenTitle(title: String, actions: @Composable RowScope.() -> Unit = {}) {
    Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = Fusion.colors.textPrimary, fontSize = 30.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Row(content = actions)
    }
}

@Composable
internal fun TitleAction(icon: KiroXIcon, description: String, size: Dp = 24.dp, onClick: () -> Unit) {
    Box(Modifier.size(48.dp).clip(CircleShape).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        IconGlyph(icon, description, Modifier.size(size), Fusion.colors.textPrimary)
    }
}

@Composable
private fun BackTitle(title: String, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        TitleAction(KiroXIcon.Back, "Назад", onClick = onBack)
        Text(title, color = Fusion.colors.textPrimary, fontSize = 28.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
internal fun TwoLineRow(icon: KiroXIcon, title: String, subtitle: String?, chevron: Boolean = true, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).heightIn(min = if (subtitle != null) 66.dp else 58.dp).padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        IconGlyph(icon, null, Modifier.size(24.dp), Fusion.colors.textPrimary)
        Spacer(Modifier.width(18.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Fusion.colors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) Text(subtitle, color = Fusion.colors.textSecondary, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (chevron) IconGlyph(KiroXIcon.Chevron, null, Modifier.size(18.dp), Fusion.colors.textSecondary)
    }
    HorizontalDivider(Modifier.padding(start = 58.dp, end = 16.dp), color = Fusion.colors.outline)
}
