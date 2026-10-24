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
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
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
import com.kirox.app.ui.components.SeasonalLayer
import com.kirox.app.ui.components.TelegramAvatar
import com.kirox.app.ui.format.TelegramFormat
import com.kirox.app.ui.icons.KiroXIcon
import com.kirox.app.ui.icons.KiroXIcon as IconGlyph
import com.kirox.app.ui.theme.Fusion
import com.kirox.app.ui.theme.FusionPalette

private const val IN_PROGRESS = "В разработке"

// ---------- Settings: profile header + icon-tile rows (old Fusion layout) ----------

@Composable
internal fun SettingsTab(profile: TelegramUserProfile?, onOpenProfile: () -> Unit, onOpenCustomization: () -> Unit, onNotice: (String) -> Unit) {
    val entries = listOf(
        KiroXIcon.Palette to "Кастомизация", KiroXIcon.Chats to "Чаты и папки", KiroXIcon.Bell to "Уведомления",
        KiroXIcon.Shield to "Конфиденциальность", KiroXIcon.Storage to "Данные и память", KiroXIcon.Devices to "Устройства",
        KiroXIcon.Star to "Telegram Premium", KiroXIcon.Sliders to "Возможности KiroX", KiroXIcon.Settings to "Дополнительно",
        KiroXIcon.Refresh to "Обновления", KiroXIcon.Info to "О KiroX"
    )
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Box {
                SeasonalLayer(Modifier.matchParentSize())
                Column {
                    ScreenTitle("Настройки", seasonal = false) { TitleAction(KiroXIcon.Search, "Поиск настроек") { onNotice("Поиск по настройкам — $IN_PROGRESS") } }
                    ProfileHeader(profile, onEdit = { onNotice("Редактирование профиля — $IN_PROGRESS") }, onAccounts = { onNotice("Несколько аккаунтов — $IN_PROGRESS") }, onAvatar = onOpenProfile, onCamera = { onNotice("Смена фото — $IN_PROGRESS") })
                }
            }
        }
        item { HorizontalDivider(Modifier.padding(horizontal = 18.dp, vertical = 8.dp), color = Fusion.colors.outline) }
        items(entries, key = { it.second }) { (icon, title) ->
            TileRow(icon, title, trailing = if (title == "Обновления") BuildConfig.VERSION_NAME else null) {
                when (title) {
                    "Кастомизация" -> onOpenCustomization()
                    "О KiroX" -> onNotice("KiroX ${BuildConfig.VERSION_NAME} — независимый клиент Telegram на TDLib")
                    "Telegram Premium" -> onNotice(if (profile?.isPremium == true) "Telegram Premium активен" else "Telegram Premium — $IN_PROGRESS")
                    else -> onNotice("«$title» — $IN_PROGRESS")
                }
            }
        }
    }
}

@Composable
private fun ProfileHeader(profile: TelegramUserProfile?, onEdit: () -> Unit, onAccounts: () -> Unit, onAvatar: () -> Unit, onCamera: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(96.dp)) {
            Box(Modifier.size(96.dp).border(1.5.dp, Fusion.colors.outlineStrong, CircleShape).padding(4.dp).clip(CircleShape).clickable(onClick = onAvatar), contentAlignment = Alignment.Center) {
                TelegramAvatar(profile?.name.orEmpty(), profile?.photo, 88.dp)
            }
            Box(
                Modifier.align(Alignment.BottomEnd).size(32.dp).clip(CircleShape).background(Fusion.colors.surface2).border(3.dp, Fusion.colors.background, CircleShape).clickable(onClick = onCamera),
                contentAlignment = Alignment.Center
            ) { IconGlyph(KiroXIcon.Camera, "Сменить фото", Modifier.size(16.dp), Fusion.colors.textPrimary) }
        }
        Spacer(Modifier.height(10.dp))
        Text(profile?.name ?: "Загрузка…", color = Fusion.colors.textPrimary, fontSize = 21.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        profile?.username?.let { Text("@$it", color = Fusion.colors.textSecondary, fontSize = 14.sp) }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier.clip(CircleShape).background(Fusion.colors.surface2).clickable(onClick = onEdit).heightIn(min = 44.dp).padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconGlyph(KiroXIcon.Edit, null, Modifier.size(17.dp), Fusion.colors.textPrimary)
                Spacer(Modifier.width(8.dp))
                Text("Редактировать профиль", color = Fusion.colors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.width(8.dp))
            Box(Modifier.size(44.dp).clip(CircleShape).background(Fusion.colors.surface2).clickable(onClick = onAccounts), contentAlignment = Alignment.Center) {
                IconGlyph(KiroXIcon.Contacts, "Переключить аккаунт", Modifier.size(20.dp), Fusion.colors.textPrimary)
            }
        }
    }
}

/** Old Fusion settings row: icon in a rounded tile, large title, chevron. */
@Composable
internal fun TileRow(icon: KiroXIcon, title: String, trailing: String? = null, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).heightIn(min = 58.dp).padding(horizontal = 16.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(Fusion.colors.iconContainer), contentAlignment = Alignment.Center) {
            IconGlyph(icon, null, Modifier.size(21.dp), Fusion.colors.textPrimary)
        }
        Spacer(Modifier.width(14.dp))
        Text(title, color = Fusion.colors.textPrimary, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        if (trailing != null) Text(trailing, color = Fusion.colors.textTertiary, fontSize = 13.sp, modifier = Modifier.padding(end = 6.dp))
        IconGlyph(KiroXIcon.Chevron, null, Modifier.size(17.dp), Fusion.colors.textTertiary)
    }
}

@Composable
internal fun CustomizationScreen(
    palette: FusionPalette, onPalette: (FusionPalette) -> Unit,
    seasonal: Boolean, onSeasonal: (Boolean) -> Unit,
    bubbleShape: com.kirox.app.settings.BubbleShape, onBubbleShape: (com.kirox.app.settings.BubbleShape) -> Unit,
    motion: com.kirox.app.settings.MotionLevel, onMotion: (com.kirox.app.settings.MotionLevel) -> Unit,
    onBack: () -> Unit
) {
    var themeSheet by rememberSaveable { mutableStateOf(false) }
    BackHandler(onBack = onBack)
    LazyColumn(Modifier.fillMaxSize().background(Fusion.colors.background), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { BackTitle("Кастомизация", onBack) }
        item { BubblePreview(bubbleShape.radiusDp) }
        item {
            Box(
                Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(210.dp).clip(RoundedCornerShape(20.dp))
                    .background(Fusion.colors.surface1).border(1.dp, Fusion.colors.outline, RoundedCornerShape(20.dp))
            ) {
                SeasonalLayer(Modifier.matchParentSize(), mode = com.kirox.app.ui.components.SeasonalMode.Showcase, force = true)
                Text("Сезонная анимация", color = Fusion.colors.textSecondary, fontSize = 12.sp, modifier = Modifier.align(Alignment.BottomCenter).padding(8.dp))
            }
        }
        item { TileRow(KiroXIcon.Palette, "Тема", trailing = themeName(palette)) { themeSheet = true } }
        item {
            SegmentRow(KiroXIcon.Chat, "Форма пузырей", com.kirox.app.settings.BubbleShape.entries, bubbleShape, { it.title }, onBubbleShape)
        }
        item {
            SegmentRow(KiroXIcon.Star, "Анимации", com.kirox.app.settings.MotionLevel.entries, motion, { it.title }, onMotion)
        }
        item {
            Row(Modifier.fillMaxWidth().clickable { onSeasonal(!seasonal) }.heightIn(min = 58.dp).padding(horizontal = 16.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(Fusion.colors.iconContainer), contentAlignment = Alignment.Center) {
                    IconGlyph(KiroXIcon.Stories, null, Modifier.size(21.dp), Fusion.colors.textPrimary)
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("Сезонные эффекты", color = Fusion.colors.textPrimary, fontSize = 16.sp)
                    Text("Частицы в шапках экранов", color = Fusion.colors.textSecondary, fontSize = 13.sp)
                }
                Switch(
                    seasonal, onSeasonal,
                    colors = SwitchDefaults.colors(checkedThumbColor = Fusion.colors.onAccent, checkedTrackColor = Fusion.colors.accent, uncheckedThumbColor = Fusion.colors.textTertiary, uncheckedTrackColor = Fusion.colors.surface2, uncheckedBorderColor = Fusion.colors.outlineStrong)
                )
            }
        }
    }
    if (themeSheet) ThemeSheet(palette, onSelect = onPalette, onDismiss = { themeSheet = false })
}

/** Two sample bubbles that follow the chosen shape and theme. */
@Composable
private fun BubblePreview(radius: Float) {
    Column(
        Modifier.padding(16.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Fusion.colors.surface1)
            .border(1.dp, Fusion.colors.outline, RoundedCornerShape(20.dp)).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("Привет! Как тебе новый kiroX?", color = Fusion.colors.textPrimary, fontSize = 15.sp,
            modifier = Modifier.clip(RoundedCornerShape(radius.dp)).background(Fusion.colors.surface2).padding(horizontal = 12.dp, vertical = 8.dp))
        Text("Чисто и быстро 👌", color = Fusion.colors.textPrimary, fontSize = 15.sp,
            modifier = Modifier.align(Alignment.End).clip(RoundedCornerShape(radius.dp)).background(Fusion.colors.surface3).padding(horizontal = 12.dp, vertical = 8.dp))
    }
}

@Composable
private fun <T> SegmentRow(icon: KiroXIcon, title: String, options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(Fusion.colors.iconContainer), contentAlignment = Alignment.Center) {
                IconGlyph(icon, null, Modifier.size(21.dp), Fusion.colors.textPrimary)
            }
            Spacer(Modifier.width(14.dp))
            Text(title, color = Fusion.colors.textPrimary, fontSize = 16.sp)
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth().clip(CircleShape).background(Fusion.colors.surface1).padding(3.dp)) {
            options.forEach { option ->
                val active = option == selected
                Text(
                    label(option), color = if (active) Fusion.colors.textPrimary else Fusion.colors.textSecondary,
                    fontSize = 14.sp, fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium, textAlign = TextAlign.Center, maxLines = 1,
                    modifier = Modifier.weight(1f).clip(CircleShape).background(if (active) Fusion.colors.surface3 else Fusion.colors.surface1)
                        .clickable { onSelect(option) }.padding(vertical = 8.dp)
                )
            }
        }
    }
}

private fun themeName(palette: FusionPalette) = if (palette == FusionPalette.GraphiteDark) "Graphite Dark" else "AMOLED Black"

// ---------- My profile (old Fusion layout) ----------

@Composable
internal fun ProfileTab(profile: TelegramUserProfile?, onOpenSettings: () -> Unit, onOpenSaved: () -> Unit, onLogOut: () -> Unit, onNotice: (String) -> Unit) {
    var menu by remember { mutableStateOf(false) }
    var confirmLogout by remember { mutableStateOf(false) }
    var archived by rememberSaveable { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 96.dp)) {
            item {
                Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    TitleAction(KiroXIcon.Qr, "QR-код профиля") { onNotice("QR-код профиля — $IN_PROGRESS") }
                    Spacer(Modifier.weight(1f))
                    Box {
                        TitleAction(KiroXIcon.More, "Ещё") { menu = true }
                        DropdownMenu(menu, { menu = false }, modifier = Modifier.background(Fusion.colors.surface2)) {
                            DropdownMenuItem(text = { Text("Избранное", color = Fusion.colors.textPrimary) }, onClick = { menu = false; onOpenSaved() })
                            DropdownMenuItem(text = { Text("Переключить аккаунт", color = Fusion.colors.textPrimary) }, onClick = { menu = false; onNotice("Несколько аккаунтов — $IN_PROGRESS") })
                            DropdownMenuItem(text = { Text("Выйти из аккаунта", color = Fusion.colors.danger) }, onClick = { menu = false; confirmLogout = true })
                        }
                    }
                }
            }
            item {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    TelegramAvatar(profile?.name.orEmpty(), profile?.photo, 92.dp)
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(profile?.name ?: "Загрузка…", color = Fusion.colors.textPrimary, fontSize = 22.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                        Spacer(Modifier.width(8.dp))
                        KiroXBadge(Modifier.size(20.dp))
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        listOfNotNull("в сети", "Пользователь KiroX", if (profile?.isPremium == true) "Premium" else null, if (profile?.isVerified == true) "Проверено Telegram" else null).joinToString(" · "),
                        color = Fusion.colors.textSecondary, fontSize = 14.sp, textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(16.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ActionTile(KiroXIcon.Camera, "Выбрать фото", Modifier.weight(1f)) { onNotice("Смена фото — $IN_PROGRESS") }
                        ActionTile(KiroXIcon.Edit, "Изменить", Modifier.weight(1f)) { onNotice("Редактирование профиля — $IN_PROGRESS") }
                        ActionTile(KiroXIcon.Tune, "Настройки", Modifier.weight(1f), onOpenSettings)
                    }
                }
            }
            item { Spacer(Modifier.height(12.dp)) }
            profile?.phoneNumber?.takeIf { it.isNotBlank() }?.let { phone -> item { InfoLine(KiroXIcon.Phone, TelegramFormat.phone(phone), "Телефон") } }
            profile?.username?.let { username -> item { InfoLine(KiroXIcon.At, "@$username", "Имя пользователя") } }
            profile?.bio?.takeIf { it.isNotBlank() }?.let { bio -> item { InfoLine(KiroXIcon.Info, bio, "О себе") } }
            profile?.birthday?.takeIf { it.isNotBlank() }?.let { birthday -> item { InfoLine(KiroXIcon.Cake, birthday, "Дата рождения") } }
            item {
                Row(
                    Modifier.padding(horizontal = 40.dp, vertical = 12.dp).fillMaxWidth().clip(CircleShape).background(Fusion.colors.surface1).padding(4.dp)
                ) {
                    listOf("Публикации" to false, "Архив публикаций" to true).forEach { (title, value) ->
                        Text(
                            title, color = if (archived == value) Fusion.colors.textPrimary else Fusion.colors.textSecondary,
                            fontSize = 14.sp, fontWeight = if (archived == value) FontWeight.SemiBold else FontWeight.Medium, textAlign = TextAlign.Center, maxLines = 1,
                            modifier = Modifier.weight(1f).clip(CircleShape).background(if (archived == value) Fusion.colors.surface3 else Fusion.colors.surface1)
                                .clickable { archived = value }.padding(vertical = 8.dp)
                        )
                    }
                }
            }
            item {
                Column(Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(if (archived) "Архив пуст" else "Публикаций пока нет…", color = Fusion.colors.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Text("Публикуйте фотографии и видео в своём профиле", color = Fusion.colors.textSecondary, fontSize = 14.sp, textAlign = TextAlign.Center)
                }
            }
        }
        Row(
            Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp).clip(CircleShape).background(Fusion.colors.surface3)
                .clickable { onNotice("Публикации в профиле — $IN_PROGRESS") }.heightIn(min = 44.dp).padding(horizontal = 22.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconGlyph(KiroXIcon.Camera, null, Modifier.size(19.dp), Fusion.colors.textPrimary)
            Spacer(Modifier.width(8.dp))
            Text("Добавить", color = Fusion.colors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
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
private fun ActionTile(icon: KiroXIcon, title: String, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.heightIn(min = 64.dp).clip(RoundedCornerShape(18.dp)).background(Fusion.colors.surface2).clickable(onClick = onClick).padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center
    ) {
        IconGlyph(icon, null, Modifier.size(21.dp), Fusion.colors.textPrimary)
        Spacer(Modifier.height(5.dp))
        Text(title, color = Fusion.colors.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun InfoLine(icon: KiroXIcon, value: String, label: String) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(horizontal = 18.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        IconGlyph(icon, null, Modifier.size(22.dp), Fusion.colors.textSecondary)
        Spacer(Modifier.width(18.dp))
        Column {
            Text(value, color = Fusion.colors.textPrimary, fontSize = 16.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
            Text(label, color = Fusion.colors.textSecondary, fontSize = 13.sp)
        }
    }
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

// ---------- New message ----------

@Composable
internal fun NewMessageScreen(contacts: List<TelegramContact>, onBack: () -> Unit, onOpenContact: (Long) -> Unit, onOpenSaved: () -> Unit, onNotice: (String) -> Unit) {
    var picking by rememberSaveable { mutableStateOf(false) }
    BackHandler { if (picking) picking = false else onBack() }
    LazyColumn(Modifier.fillMaxSize().background(Fusion.colors.background), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { BackTitle(if (picking) "Выберите контакт" else "Новое сообщение") { if (picking) picking = false else onBack() } }
        if (!picking) {
            item { TileRow(KiroXIcon.Chats, "Новое сообщение") { picking = true } }
            item { TileRow(KiroXIcon.Contacts, "Создать группу") { onNotice("Создание групп — $IN_PROGRESS") } }
            item { TileRow(KiroXIcon.Add, "Создать канал") { onNotice("Создание каналов — $IN_PROGRESS") } }
            item { TileRow(KiroXIcon.Bookmark, "Избранное", onClick = onOpenSaved) }
            item { TileRow(KiroXIcon.Shield, "Секретный чат") { onNotice("Секретные чаты — $IN_PROGRESS") } }
        } else {
            if (contacts.isEmpty()) item { Text("Контактов пока нет", color = Fusion.colors.textSecondary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(32.dp)) }
            items(contacts, key = { it.id }) { contact ->
                Row(Modifier.fillMaxWidth().clickable { onOpenContact(contact.id) }.heightIn(min = 66.dp).padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    TelegramAvatar(contact.name, contact.photo, 50.dp)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(contact.name, color = Fusion.colors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(TelegramFormat.status(contact.status), color = Fusion.colors.textSecondary, fontSize = 14.sp)
                    }
                }
            }
        }
    }
}

// ---------- Shared building blocks ----------

/** Large screen title (old Fusion: ~32 sp) with optional seasonal particles behind it. */
@Composable
internal fun ScreenTitle(title: String, seasonal: Boolean = true, actions: @Composable RowScope.() -> Unit = {}) {
    var titleEnd by remember { mutableFloatStateOf(0f) }
    var actionsStart by remember { mutableFloatStateOf(0f) }
    val density = androidx.compose.ui.platform.LocalDensity.current
    val gap = with(density) { 12.dp.toPx() }
    val startPad = with(density) { 16.dp.toPx() }
    Box(Modifier.fillMaxWidth()) {
        if (seasonal) SeasonalLayer(Modifier.matchParentSize(), freeZone = if (actionsStart > titleEnd) (titleEnd + gap)..(actionsStart - gap) else null)
        Row(Modifier.fillMaxWidth().heightIn(min = 60.dp).padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f)) {
                Text(
                    title, color = Fusion.colors.textPrimary, fontSize = 26.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.onGloballyPositioned { titleEnd = it.positionInParent().x + it.size.width + startPad }
                )
            }
            Row(Modifier.onGloballyPositioned { actionsStart = it.positionInParent().x }, content = actions)
        }
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
    Row(Modifier.fillMaxWidth().heightIn(min = 60.dp).padding(end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        TitleAction(KiroXIcon.Back, "Назад", onClick = onBack)
        Text(title, color = Fusion.colors.textPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
