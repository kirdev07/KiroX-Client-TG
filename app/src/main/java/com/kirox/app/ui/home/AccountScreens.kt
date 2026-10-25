package com.kirox.app.ui.home

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
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
internal fun SettingsTab(profile: TelegramUserProfile?, onOpenProfile: () -> Unit, onOpenCustomization: () -> Unit, onOpenUpdates: () -> Unit, onOpenDevices: () -> Unit, onEditProfile: () -> Unit, onOpenSection: (String) -> Unit, onNotice: (String) -> Unit) {
    val pickPhoto = rememberProfilePhotoPicker(onNotice)
    var showAccounts by remember { mutableStateOf(false) }
    if (showAccounts) AccountsSheet { showAccounts = false }
    val entries = listOf(
        KiroXIcon.Palette to "Кастомизация", KiroXIcon.Chats to "Чаты и папки", KiroXIcon.Bell to "Уведомления",
        KiroXIcon.Shield to "Конфиденциальность", KiroXIcon.Storage to "Данные и память", KiroXIcon.Devices to "Устройства",
        KiroXIcon.Star to "Telegram Premium", KiroXIcon.Sliders to "Возможности KiroX", KiroXIcon.Settings to "Дополнительно",
        KiroXIcon.Refresh to "Обновления", KiroXIcon.Info to "О KiroX"
    )
    // Search filters the rows by name and by what each section contains.
    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    val keywords = mapOf(
        "Кастомизация" to "тема цвет пузыри анимации обои шрифт",
        "Чаты и папки" to "чат обои размер текста плотность",
        "Уведомления" to "звук вибрация превью",
        "Конфиденциальность" to "приватность номер телефона время захода фото звонки приглашения",
        "Данные и память" to "кэш память хранилище очистить",
        "Устройства" to "сеансы сессии выйти",
        "Дополнительно" to "язык сброс выход лицензии",
        "Обновления" to "версия бета github"
    )
    val shown = if (query.isBlank()) entries else entries.filter { (_, title) ->
        title.contains(query, true) || keywords[title]?.contains(query, true) == true
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            Box {
                SeasonalLayer(Modifier.matchParentSize())
                Column {
                    ScreenTitle("Настройки", seasonal = false) { TitleAction(KiroXIcon.Search, "Поиск настроек") { searching = !searching; if (!searching) query = "" } }
                    if (searching) androidx.compose.foundation.text.BasicTextField(
                        query, { query = it }, singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(Fusion.colors.textPrimary, 16.sp),
                        cursorBrush = androidx.compose.ui.graphics.SolidColor(Fusion.colors.textPrimary),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth().heightIn(min = 48.dp)
                            .clip(RoundedCornerShape(24.dp)).background(Fusion.colors.surface1).padding(horizontal = 18.dp, vertical = 14.dp),
                        decorationBox = { inner -> Box { if (query.isEmpty()) Text("Поиск настроек", color = Fusion.colors.textTertiary, fontSize = 16.sp); inner() } }
                    )
                    ProfileHeader(profile, onEdit = onEditProfile, onAccounts = { showAccounts = true }, onAvatar = onOpenProfile, onCamera = pickPhoto)
                }
            }
        }
        item { HorizontalDivider(Modifier.padding(horizontal = 18.dp, vertical = 8.dp), color = Fusion.colors.outline) }
        if (shown.isEmpty()) item { Text("Ничего не найдено", color = Fusion.colors.textSecondary, modifier = Modifier.padding(20.dp)) }
        items(shown, key = { it.second }) { (icon, title) ->
            TileRow(icon, title, trailing = if (title == "Обновления") BuildConfig.VERSION_NAME else null) {
                when (title) {
                    "Кастомизация" -> onOpenCustomization()
                    "Обновления" -> onOpenUpdates()
                    "Устройства" -> onOpenDevices()
                    "Чаты и папки", "Возможности KiroX" -> onOpenSection(title)
                    "Уведомления", "Конфиденциальность", "Данные и память", "Telegram Premium", "Дополнительно" -> onOpenSection(title)
                    "О KiroX" -> onNotice("KiroX ${BuildConfig.VERSION_NAME} — независимый клиент Telegram на TDLib")
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

/** Customization hub: three clear sections, each with its current value. */
@Composable
internal fun CustomizationScreen(
    palette: FusionPalette,
    bubbleShape: com.kirox.app.settings.BubbleShape,
    animationsSummary: String,
    animationsOn: Boolean,
    onOpenThemes: () -> Unit,
    onOpenChats: () -> Unit,
    onOpenAnimations: () -> Unit,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)
    LazyColumn(Modifier.fillMaxSize().background(Fusion.colors.background), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { BackTitle("Кастомизация", onBack) }
        item { StyleBanner(palette.title, animationsSummary, animationsOn) }
        item { TileRow(KiroXIcon.Palette, "Темы", trailing = palette.title, onClick = onOpenThemes) }
        item { TileRow(KiroXIcon.Chat, "Чаты", trailing = bubbleShape.title, onClick = onOpenChats) }
        item { TileRow(KiroXIcon.Star, "Анимации", trailing = animationsSummary, onClick = onOpenAnimations) }
    }
}

/** kiroX logo over the chosen animation, with the current theme and animation under it. */
@Composable
private fun StyleBanner(theme: String, animation: String, animationsOn: Boolean) {
    Box(
        Modifier.padding(16.dp).fillMaxWidth().height(150.dp).clip(RoundedCornerShape(20.dp))
            .background(Fusion.colors.surface1).border(1.dp, Fusion.colors.outline, RoundedCornerShape(20.dp))
    ) {
        if (animationsOn) SeasonalLayer(Modifier.matchParentSize(), mode = com.kirox.app.ui.components.SeasonalMode.Banner, force = true)
        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                androidx.compose.ui.text.buildAnnotatedString {
                    append("kiro")
                    withStyle(androidx.compose.ui.text.SpanStyle(color = Fusion.colors.textSecondary)) { append("X") }
                },
                color = Fusion.colors.textPrimary, fontSize = 36.sp, fontWeight = FontWeight.Light
            )
            Text("$theme · $animation", color = Fusion.colors.textSecondary, fontSize = 13.sp)
        }
    }
}

/** Theme picker: a grid of cards, each a small screen painted in that theme. Dark themes only. */
@Composable
internal fun ThemesScreen(palette: FusionPalette, onPalette: (FusionPalette) -> Unit, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    LazyColumn(Modifier.fillMaxSize().background(Fusion.colors.background), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { BackTitle("Темы", onBack) }
        FusionPalette.entries.chunked(2).forEach { pair ->
            item(key = pair.first().name) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    pair.forEach { option -> ThemeCard(option, option == palette, Modifier.weight(1f)) { onPalette(option) } }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
        item {
            Text("Все темы тёмные и с белыми акцентами — в стиле kiroX.", color = Fusion.colors.textTertiary, fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp))
        }
    }
}

/** Chat appearance: live preview, bubble shape, text size, wallpaper and density. */
@Composable
internal fun ChatStyleScreen(
    bubbleShape: com.kirox.app.settings.BubbleShape, onBubbleShape: (com.kirox.app.settings.BubbleShape) -> Unit,
    textSize: com.kirox.app.settings.ChatTextSize, onTextSize: (com.kirox.app.settings.ChatTextSize) -> Unit,
    wallpaper: com.kirox.app.settings.ChatWallpaper, onWallpaper: (com.kirox.app.settings.ChatWallpaper) -> Unit,
    density: com.kirox.app.settings.ChatDensity, onDensity: (com.kirox.app.settings.ChatDensity) -> Unit,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)
    LazyColumn(Modifier.fillMaxSize().background(Fusion.colors.background), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { BackTitle("Чаты", onBack) }
        item { LivePreview(bubbleShape.radiusDp, textSize.scale, wallpaper, density) }
        item { SectionCaption("Пузыри сообщений") }
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                com.kirox.app.settings.BubbleShape.entries.forEach { shape ->
                    BubbleCard(shape, shape == bubbleShape, Modifier.weight(1f)) { onBubbleShape(shape) }
                }
            }
        }
        item { SectionCaption("Обои") }
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                com.kirox.app.settings.ChatWallpaper.entries.forEach { option ->
                    OptionCard(option == wallpaper, Modifier.weight(1f), onClick = { onWallpaper(option) }) {
                        Box(Modifier.fillMaxWidth().height(54.dp).clip(RoundedCornerShape(8.dp))) {
                            com.kirox.app.ui.components.ChatWallpaperLayer(option, Modifier.matchParentSize(), patternScale = 0.45f)
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(option.title, color = if (option == wallpaper) Fusion.colors.textPrimary else Fusion.colors.textSecondary, fontSize = 12.sp,
                            fontWeight = if (option == wallpaper) FontWeight.SemiBold else FontWeight.Medium, maxLines = 1)
                    }
                }
            }
        }
        item { SectionCaption("Размер текста") }
        item { PillSegment(com.kirox.app.settings.ChatTextSize.entries, textSize, { it.title }, onTextSize) }
        item { SectionCaption("Плотность") }
        item { PillSegment(com.kirox.app.settings.ChatDensity.entries, density, { it.title }, onDensity) }
    }
}

@Composable
private fun <T> PillSegment(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    Row(Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(CircleShape).background(Fusion.colors.surface1).padding(3.dp)) {
        options.forEach { option ->
            val active = option == selected
            Text(
                label(option), color = if (active) Fusion.colors.textPrimary else Fusion.colors.textSecondary,
                fontSize = 13.sp, fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium, textAlign = TextAlign.Center, maxLines = 1,
                modifier = Modifier.weight(1f).clip(CircleShape).background(if (active) Fusion.colors.surface3 else Fusion.colors.surface1)
                    .clickable { onSelect(option) }.padding(vertical = 8.dp)
            )
        }
    }
}

/** A small chat mock that redraws instantly with the chosen theme and chat settings. */
@Composable
private fun LivePreview(
    radius: Float,
    textScale: Float = 1f,
    wallpaper: com.kirox.app.settings.ChatWallpaper = com.kirox.app.settings.ChatWallpaper.None,
    density: com.kirox.app.settings.ChatDensity = com.kirox.app.settings.ChatDensity.Normal
) {
    Column(
        Modifier.padding(16.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp)).border(1.dp, Fusion.colors.outline, RoundedCornerShape(20.dp))
    ) {
        Row(Modifier.fillMaxWidth().background(Fusion.colors.surface1).padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(32.dp).clip(CircleShape).background(Fusion.colors.avatar), contentAlignment = Alignment.Center) {
                Text("K", color = Fusion.colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(10.dp))
            Column {
                Text("kiroX", color = Fusion.colors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text("в сети", color = Fusion.colors.textSecondary, fontSize = 12.sp)
            }
        }
        Box(Modifier.fillMaxWidth()) {
            com.kirox.app.ui.components.ChatWallpaperLayer(wallpaper, Modifier.matchParentSize())
            Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(density.spacingDp.dp + 2.dp)) {
                listOf("Привет! Как тебе новый kiroX?" to false, "Чисто и быстро 👌" to true, "Обои и размер текста тоже меняются" to false).forEach { (text, mine) ->
                    Text(text, color = Fusion.colors.textPrimary, fontSize = (14f * textScale).sp,
                        modifier = Modifier.align(if (mine) Alignment.End else Alignment.Start).clip(RoundedCornerShape(radius.dp))
                            .background(if (mine) Fusion.colors.surface3 else Fusion.colors.surface2).padding(horizontal = 12.dp, vertical = density.paddingDp.dp))
                }
            }
        }
    }
}

@Composable
private fun SectionCaption(text: String) {
    Text(text.uppercase(), color = Fusion.colors.textSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium, letterSpacing = 1.sp,
        modifier = Modifier.padding(start = 20.dp, top = 14.dp, bottom = 8.dp))
}

/** Selectable card: thick outline when chosen. */
@Composable
private fun OptionCard(selected: Boolean, modifier: Modifier, onClick: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.clip(RoundedCornerShape(16.dp)).background(Fusion.colors.surface1)
            .border(if (selected) 2.dp else 1.dp, if (selected) Fusion.colors.accent else Fusion.colors.outline, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick).padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        content = content
    )
}

@Composable
private fun ThemeCard(palette: FusionPalette, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val colors = com.kirox.app.ui.theme.FusionColors.of(palette)
    OptionCard(selected, modifier, onClick) {
        // Mini chat painted with that theme's own colours.
        Column(
            Modifier.fillMaxWidth().height(86.dp).clip(RoundedCornerShape(10.dp)).background(colors.background)
                .border(1.dp, colors.outline, RoundedCornerShape(10.dp))
        ) {
            Row(Modifier.fillMaxWidth().background(colors.surface1).padding(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(12.dp).clip(CircleShape).background(colors.avatar))
                Spacer(Modifier.width(5.dp))
                Box(Modifier.width(34.dp).height(6.dp).clip(RoundedCornerShape(3.dp)).background(colors.textSecondary))
            }
            Column(Modifier.fillMaxWidth().padding(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(Modifier.fillMaxWidth(0.7f).height(11.dp).clip(RoundedCornerShape(6.dp)).background(colors.surface2))
                Box(Modifier.fillMaxWidth(0.55f).height(11.dp).clip(RoundedCornerShape(6.dp)).background(colors.surface3).align(Alignment.End))
                Box(Modifier.fillMaxWidth(0.45f).height(11.dp).clip(RoundedCornerShape(6.dp)).background(colors.surface2))
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (selected) { IconGlyph(KiroXIcon.Check, null, Modifier.size(15.dp), Fusion.colors.textPrimary); Spacer(Modifier.width(4.dp)) }
            Text(palette.title, color = if (selected) Fusion.colors.textPrimary else Fusion.colors.textSecondary, fontSize = 14.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium, maxLines = 1)
        }
        Text(palette.subtitle, color = Fusion.colors.textTertiary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun BubbleCard(shape: com.kirox.app.settings.BubbleShape, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    OptionCard(selected, modifier, onClick) {
        Box(Modifier.fillMaxWidth().height(44.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.fillMaxWidth(0.8f).height(30.dp).clip(RoundedCornerShape((shape.radiusDp * 0.75f).dp)).background(Fusion.colors.surface3))
        }
        Spacer(Modifier.height(6.dp))
        Text(shape.title, color = if (selected) Fusion.colors.textPrimary else Fusion.colors.textSecondary, fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium, maxLines = 1)
    }
}

/** Animations: on/off, which animation, interface speed and effect quality — compact, one tap each. */
@Composable
internal fun AnimationsScreen(
    motion: com.kirox.app.settings.MotionLevel, onMotion: (com.kirox.app.settings.MotionLevel) -> Unit,
    enabled: Boolean, onEnabled: (Boolean) -> Unit,
    scene: com.kirox.app.settings.EffectScene, onScene: (com.kirox.app.settings.EffectScene) -> Unit,
    quality: com.kirox.app.settings.EffectsQuality, onQuality: (com.kirox.app.settings.EffectsQuality) -> Unit,
    weakDevice: Boolean,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)
    LazyColumn(Modifier.fillMaxSize().background(Fusion.colors.background), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { BackTitle("Анимации", onBack) }
        item {
            Box(
                Modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth().height(150.dp).clip(RoundedCornerShape(18.dp))
                    .background(Fusion.colors.surface1).border(1.dp, Fusion.colors.outline, RoundedCornerShape(18.dp))
            ) {
                if (enabled) SeasonalLayer(Modifier.matchParentSize(), mode = com.kirox.app.ui.components.SeasonalMode.Showcase, force = true, scene = scene)
                else Text("Анимации выключены", color = Fusion.colors.textSecondary, fontSize = 13.sp, modifier = Modifier.align(Alignment.Center))
            }
        }
        item {
            Row(Modifier.fillMaxWidth().clickable { onEnabled(!enabled) }.heightIn(min = 52.dp).padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Анимации", color = Fusion.colors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                Switch(
                    enabled, onEnabled,
                    colors = SwitchDefaults.colors(checkedThumbColor = Fusion.colors.onAccent, checkedTrackColor = Fusion.colors.accent, uncheckedThumbColor = Fusion.colors.textTertiary, uncheckedTrackColor = Fusion.colors.surface2, uncheckedBorderColor = Fusion.colors.outlineStrong)
                )
            }
        }
        item { SmallCaption("Анимация") }
        com.kirox.app.settings.EffectScene.entries.forEach { option ->
            item(key = option.name) { SceneRow(option, option == scene, enabled) { onScene(option) } }
        }
        item { SmallCaption("Настройки") }
        item { CompactSegment("Скорость интерфейса", com.kirox.app.settings.MotionLevel.entries, motion, { it.title }, onMotion) }
        item { CompactSegment("Качество эффектов", com.kirox.app.settings.EffectsQuality.entries, quality, { it.title }, onQuality) }
        item {
            Text(
                "Лёгкое — для слабых телефонов и экономии батареи." +
                    if (quality == com.kirox.app.settings.EffectsQuality.Auto) (if (weakDevice) " Сейчас: лёгкое." else " Сейчас: высокое.") else "",
                color = Fusion.colors.textTertiary, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
private fun SceneRow(scene: com.kirox.app.settings.EffectScene, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).heightIn(min = 60.dp).padding(horizontal = 16.dp, vertical = 6.dp)
            .graphicsLayer { alpha = if (enabled) 1f else 0.5f },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(64.dp, 44.dp).clip(RoundedCornerShape(10.dp)).background(Fusion.colors.surface1)
                .border(if (selected) 1.5.dp else 1.dp, if (selected) Fusion.colors.accent else Fusion.colors.outline, RoundedCornerShape(10.dp))
        ) { SeasonalLayer(Modifier.matchParentSize(), mode = com.kirox.app.ui.components.SeasonalMode.Thumbnail, force = true, scene = scene, freeZoneFraction = 0.05f..0.95f) }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(scene.title, color = Fusion.colors.textPrimary, fontSize = 16.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
            val subtitle = if (scene == com.kirox.app.settings.EffectScene.Auto) "Сейчас: " + scene.resolved().title else scene.subtitle
            Text(subtitle, color = Fusion.colors.textSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Box(
            Modifier.size(22.dp).clip(CircleShape).border(2.dp, if (selected) Fusion.colors.accent else Fusion.colors.outlineStrong, CircleShape),
            contentAlignment = Alignment.Center
        ) { if (selected) Box(Modifier.size(11.dp).clip(CircleShape).background(Fusion.colors.accent)) }
    }
}

@Composable
internal fun SmallCaption(text: String) {
    Text(text.uppercase(), color = Fusion.colors.textSecondary, fontSize = 12.sp, fontWeight = FontWeight.Medium, letterSpacing = 1.sp,
        modifier = Modifier.padding(start = 20.dp, top = 12.dp, bottom = 4.dp))
}

/** Label with a slim segmented control under it. */
@Composable
private fun <T> CompactSegment(title: String, options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        Text(title, color = Fusion.colors.textPrimary, fontSize = 15.sp, modifier = Modifier.padding(start = 4.dp, bottom = 6.dp))
        Row(Modifier.fillMaxWidth().clip(CircleShape).background(Fusion.colors.surface1).padding(3.dp)) {
            options.forEach { option ->
                val active = option == selected
                Text(
                    label(option), color = if (active) Fusion.colors.textPrimary else Fusion.colors.textSecondary,
                    fontSize = 13.sp, fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium, textAlign = TextAlign.Center, maxLines = 1,
                    modifier = Modifier.weight(1f).clip(CircleShape).background(if (active) Fusion.colors.surface3 else Fusion.colors.surface1)
                        .clickable { onSelect(option) }.padding(vertical = 7.dp)
                )
            }
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, color = Fusion.colors.textTertiary, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(start = 70.dp, end = 16.dp, bottom = 10.dp))
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


// ---------- My profile (old Fusion layout) ----------

@Composable
internal fun ProfileTab(profile: TelegramUserProfile?, onOpenSettings: () -> Unit, onOpenSaved: () -> Unit, onLogOut: () -> Unit, onEditProfile: () -> Unit, onNotice: (String) -> Unit) {
    var showQr by remember { mutableStateOf(false) }
    if (showQr) ProfileQrDialog(profile) { showQr = false }
    val pickPhoto = rememberProfilePhotoPicker(onNotice)
    var menu by remember { mutableStateOf(false) }
    var confirmLogout by remember { mutableStateOf(false) }
    var archived by rememberSaveable { mutableStateOf(false) }
    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 96.dp)) {
            item {
                Box(Modifier.fillMaxWidth()) {
                SeasonalLayer(Modifier.matchParentSize(), freeZoneFraction = 0.2f..0.8f)
                Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    TitleAction(KiroXIcon.Qr, "QR-код профиля") { showQr = true }
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
                        ActionTile(KiroXIcon.Camera, "Выбрать фото", Modifier.weight(1f), pickPhoto)
                        ActionTile(KiroXIcon.Edit, "Изменить", Modifier.weight(1f), onEditProfile)
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

private enum class NewChatMode { Menu, Message, Secret, GroupMembers, GroupTitle, Channel }

@Composable
internal fun NewMessageScreen(
    contacts: List<TelegramContact>,
    onBack: () -> Unit,
    onOpenContact: (Long) -> Unit,
    onOpenSaved: () -> Unit,
    onOpenChat: (Long) -> Unit,
    onNotice: (String) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val chats = (context.applicationContext as com.kirox.app.KiroXApplication).telegram.chats
    val scope = rememberCoroutineScope()
    var mode by rememberSaveable { mutableStateOf(NewChatMode.Menu) }
    var members by rememberSaveable { mutableStateOf(listOf<Long>()) }
    var title by rememberSaveable { mutableStateOf("") }
    var description by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val back = {
        mode = when (mode) {
            NewChatMode.GroupTitle -> NewChatMode.GroupMembers
            NewChatMode.Menu -> { onBack(); NewChatMode.Menu }
            else -> NewChatMode.Menu
        }
    }
    BackHandler { back() }
    fun create(group: Boolean) {
        busy = true
        scope.launch {
            runCatching { if (group) chats.createGroup(title.trim(), members) else chats.createChannel(title.trim(), description.trim()) }
                .onSuccess { id -> onNotice(if (group) "Группа создана" else "Канал создан"); onOpenChat(id) }
                .onFailure { onNotice(if (group) "Не удалось создать группу" else "Не удалось создать канал") }
            busy = false
        }
    }
    LazyColumn(Modifier.fillMaxSize().background(Fusion.colors.background).imePadding(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item {
            BackTitle(
                when (mode) {
                    NewChatMode.Menu -> "Новое сообщение"
                    NewChatMode.Message -> "Выберите контакт"
                    NewChatMode.Secret -> "Секретный чат с…"
                    NewChatMode.GroupMembers -> if (members.isEmpty()) "Участники группы" else "Выбрано: ${members.size}"
                    NewChatMode.GroupTitle -> "Новая группа"
                    NewChatMode.Channel -> "Новый канал"
                }
            ) { back() }
        }
        when (mode) {
            NewChatMode.Menu -> {
                item { TileRow(KiroXIcon.Chats, "Новое сообщение") { mode = NewChatMode.Message } }
                item { TileRow(KiroXIcon.Contacts, "Создать группу") { members = emptyList(); title = ""; mode = NewChatMode.GroupMembers } }
                item { TileRow(KiroXIcon.Add, "Создать канал") { title = ""; description = ""; mode = NewChatMode.Channel } }
                item { TileRow(KiroXIcon.Bookmark, "Избранное", onClick = onOpenSaved) }
                item { TileRow(KiroXIcon.Shield, "Секретный чат") { mode = NewChatMode.Secret } }
            }
            NewChatMode.Message, NewChatMode.Secret, NewChatMode.GroupMembers -> {
                if (mode == NewChatMode.Secret) item {
                    Text(
                        "Сообщения шифруются на устройствах и хранятся только здесь и у собеседника — их нет в облаке и других клиентах.",
                        color = Fusion.colors.textTertiary, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                    )
                }
                val picking = mode == NewChatMode.GroupMembers
                if (picking && members.isNotEmpty()) item {
                    Box(Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(24.dp)).background(Fusion.colors.accent).clickable { mode = NewChatMode.GroupTitle }, contentAlignment = Alignment.Center) {
                        Text("Далее", color = Fusion.colors.onAccent, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                if (contacts.isEmpty()) item { Text("Контактов пока нет", color = Fusion.colors.textSecondary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(32.dp)) }
                items(contacts, key = { it.id }) { contact ->
                    val checked = contact.id in members
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            when {
                                picking -> members = if (checked) members - contact.id else members + contact.id
                                mode == NewChatMode.Secret -> scope.launch {
                                    runCatching { chats.createSecretChat(contact.id) }.onSuccess(onOpenChat).onFailure { onNotice("Не удалось создать секретный чат") }
                                }
                                else -> onOpenContact(contact.id)
                            }
                        }.heightIn(min = 66.dp).padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TelegramAvatar(contact.name, contact.photo, 50.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(contact.name, color = Fusion.colors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(TelegramFormat.status(contact.status), color = Fusion.colors.textSecondary, fontSize = 14.sp)
                        }
                        if (picking) Box(
                            Modifier.size(24.dp).clip(CircleShape).border(2.dp, if (checked) Fusion.colors.accent else Fusion.colors.outlineStrong, CircleShape).background(if (checked) Fusion.colors.accent else androidx.compose.ui.graphics.Color.Transparent),
                            contentAlignment = Alignment.Center
                        ) { if (checked) IconGlyph(KiroXIcon.Check, null, Modifier.size(16.dp), Fusion.colors.onAccent) }
                    }
                }
            }
            NewChatMode.GroupTitle, NewChatMode.Channel -> {
                val group = mode == NewChatMode.GroupTitle
                item { SmallCaption(if (group) "Название группы" else "Название канала") }
                item { EditField(title, "Название") { title = it.take(128) } }
                if (!group) {
                    item { SmallCaption("Описание") }
                    item { EditField(description, "Необязательно", singleLine = false) { description = it.take(255) } }
                } else item {
                    Text("Участники: ${contacts.filter { it.id in members }.joinToString { it.name }}", color = Fusion.colors.textSecondary, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
                }
                item {
                    val canCreate = title.isNotBlank() && !busy
                    Box(
                        Modifier.padding(16.dp).fillMaxWidth().heightIn(min = 52.dp).clip(RoundedCornerShape(26.dp))
                            .background(if (canCreate) Fusion.colors.accent else Fusion.colors.surface2).clickable(enabled = canCreate) { create(group) },
                        contentAlignment = Alignment.Center
                    ) {
                        if (busy) androidx.compose.material3.CircularProgressIndicator(Modifier.size(22.dp), color = Fusion.colors.onAccent, strokeWidth = 2.dp)
                        else Text(if (group) "Создать группу" else "Создать канал", color = if (canCreate) Fusion.colors.onAccent else Fusion.colors.textTertiary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
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
internal fun BackTitle(title: String, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 60.dp).padding(end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        TitleAction(KiroXIcon.Back, "Назад", onClick = onBack)
        Text(title, color = Fusion.colors.textPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Updates: current version, beta channel, check / download / install from GitHub releases. */
@Composable
internal fun UpdatesScreen(updates: com.kirox.app.update.UpdateCenter, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val context = androidx.compose.ui.platform.LocalContext.current
    val state by updates.state.collectAsStateWithLifecycle()
    var beta by remember { mutableStateOf(updates.betaChannel) }
    LazyColumn(Modifier.fillMaxSize().background(Fusion.colors.background), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { BackTitle("Обновления", onBack) }
        item {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                Text("KiroX ${updates.currentVersion}", color = Fusion.colors.textPrimary, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                val status = when (val s = state) {
                    com.kirox.app.update.UpdateState.Idle -> "Нажмите «Проверить», чтобы узнать о новой версии"
                    com.kirox.app.update.UpdateState.Checking -> "Проверяю…"
                    com.kirox.app.update.UpdateState.UpToDate -> "У вас последняя версия"
                    is com.kirox.app.update.UpdateState.Available -> "Доступна версия ${s.release.version}"
                    is com.kirox.app.update.UpdateState.Downloading -> "Скачиваю ${s.release.version}: ${(s.progress * 100).toInt()}%"
                    is com.kirox.app.update.UpdateState.Ready -> "Версия ${s.release.version} скачана и проверена"
                    is com.kirox.app.update.UpdateState.Failed -> s.message
                }
                Text(status, color = if (state is com.kirox.app.update.UpdateState.Failed) Fusion.colors.danger else Fusion.colors.textSecondary, fontSize = 14.sp)
            }
        }
        item {
            val s = state
            val (label, action) = when (s) {
                is com.kirox.app.update.UpdateState.Available -> "Скачать ${s.release.version}" to { updates.download(s.release) }
                is com.kirox.app.update.UpdateState.Ready -> "Установить" to { updates.install(s.file) }
                is com.kirox.app.update.UpdateState.Failed -> (if (s.release != null) "Повторить" else "Проверить снова") to { s.release?.let(updates::download) ?: updates.check() }
                is com.kirox.app.update.UpdateState.Checking, is com.kirox.app.update.UpdateState.Downloading -> "Подождите…" to {}
                else -> "Проверить обновления" to { updates.check() }
            }
            Box(
                Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth().heightIn(min = 52.dp).clip(RoundedCornerShape(16.dp))
                    .background(Fusion.colors.accent).clickable(enabled = s !is com.kirox.app.update.UpdateState.Checking && s !is com.kirox.app.update.UpdateState.Downloading, onClick = action),
                contentAlignment = Alignment.Center
            ) { Text(label, color = Fusion.colors.onAccent, fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
            if (s is com.kirox.app.update.UpdateState.Downloading) androidx.compose.material3.LinearProgressIndicator(
                progress = { s.progress }, modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(), color = Fusion.colors.accent, trackColor = Fusion.colors.surface2
            )
        }
        item {
            Row(Modifier.fillMaxWidth().clickable { beta = !beta; updates.betaChannel = beta }.heightIn(min = 56.dp).padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Бета-версии", color = Fusion.colors.textPrimary, fontSize = 16.sp)
                    Text("Новые функции раньше, но возможны ошибки", color = Fusion.colors.textSecondary, fontSize = 12.sp)
                }
                Switch(beta, { beta = it; updates.betaChannel = it }, colors = SwitchDefaults.colors(checkedThumbColor = Fusion.colors.onAccent, checkedTrackColor = Fusion.colors.accent, uncheckedThumbColor = Fusion.colors.textTertiary, uncheckedTrackColor = Fusion.colors.surface2, uncheckedBorderColor = Fusion.colors.outlineStrong))
            }
        }
        val release = when (val s = state) {
            is com.kirox.app.update.UpdateState.Available -> s.release
            is com.kirox.app.update.UpdateState.Downloading -> s.release
            is com.kirox.app.update.UpdateState.Ready -> s.release
            else -> null
        }
        if (release != null && release.notes.isNotBlank()) {
            item { SmallCaption("Что нового в ${release.version}") }
            item { Text(releaseNotesText(release.notes, Fusion.colors.textPrimary), color = Fusion.colors.textSecondary, fontSize = 13.sp, lineHeight = 19.sp, modifier = Modifier.padding(horizontal = 20.dp)) }
        }
        item {
            Text(
                "Все версии на GitHub", color = Fusion.colors.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp).clickable {
                    context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(com.kirox.app.update.UpdateCenter.RELEASES_URL)))
                }
            )
        }
    }
}

/** Settings → Devices: active sessions of the account; others can be terminated. */
@Composable
internal fun DevicesScreen(sessions: com.kirox.app.telegram.TelegramSessionsRepository, onBack: () -> Unit, onNotice: (String) -> Unit) {
    BackHandler(onBack = onBack)
    val scope = rememberCoroutineScope()
    var list by remember { mutableStateOf<List<com.kirox.app.telegram.TelegramSession>?>(null) }
    var failed by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf<com.kirox.app.telegram.TelegramSession?>(null) }
    var confirmAll by remember { mutableStateOf(false) }
    fun reload() {
        scope.launch { runCatching { sessions.sessions() }.onSuccess { list = it; failed = false }.onFailure { failed = true } }
    }
    LaunchedEffect(Unit) { reload() }
    LazyColumn(Modifier.fillMaxSize().background(Fusion.colors.background), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { BackTitle("Устройства", onBack) }
        val current = list?.firstOrNull { it.isCurrent }
        val others = list?.filterNot { it.isCurrent }.orEmpty()
        when {
            failed -> item { Text("Не удалось загрузить список устройств", color = Fusion.colors.textSecondary, modifier = Modifier.padding(20.dp)) }
            list == null -> item {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { androidx.compose.material3.CircularProgressIndicator(color = Fusion.colors.textPrimary) }
            }
            else -> {
                if (current != null) {
                    item { SmallCaption("Это устройство") }
                    item { SessionRow(current, onTerminate = null) }
                }
                if (others.isNotEmpty()) {
                    item {
                        Text(
                            "Завершить все другие сеансы", color = Fusion.colors.danger, fontSize = 15.sp, fontWeight = FontWeight.Medium,
                            modifier = Modifier.fillMaxWidth().clickable { confirmAll = true }.padding(horizontal = 20.dp, vertical = 14.dp)
                        )
                    }
                    item { SmallCaption("Активные сеансы") }
                    items(others, key = { it.id }) { session -> SessionRow(session, onTerminate = { confirm = session }) }
                } else {
                    item { Text("Других активных сеансов нет", color = Fusion.colors.textSecondary, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) }
                }
            }
        }
    }
    confirm?.let { session ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirm = null },
            containerColor = Fusion.colors.surface1,
            title = { Text("Завершить сеанс?", color = Fusion.colors.textPrimary) },
            text = { Text("«${session.device.ifBlank { session.application }}» выйдет из аккаунта.", color = Fusion.colors.textSecondary) },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    confirm = null
                    scope.launch { runCatching { sessions.terminate(session.id) }.onSuccess { reload() }.onFailure { onNotice("Не удалось завершить сеанс") } }
                }) { Text("Завершить", color = Fusion.colors.danger) }
            },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { confirm = null }) { Text("Отмена", color = Fusion.colors.textSecondary) } }
        )
    }
    if (confirmAll) androidx.compose.material3.AlertDialog(
        onDismissRequest = { confirmAll = false },
        containerColor = Fusion.colors.surface1,
        title = { Text("Завершить все другие сеансы?", color = Fusion.colors.textPrimary) },
        text = { Text("Все устройства, кроме этого, выйдут из аккаунта.", color = Fusion.colors.textSecondary) },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = {
                confirmAll = false
                scope.launch { runCatching { sessions.terminateOthers() }.onSuccess { reload() }.onFailure { onNotice("Не удалось завершить сеансы") } }
            }) { Text("Завершить", color = Fusion.colors.danger) }
        },
        dismissButton = { androidx.compose.material3.TextButton(onClick = { confirmAll = false }) { Text("Отмена", color = Fusion.colors.textSecondary) } }
    )
}

@Composable
private fun SessionRow(session: com.kirox.app.telegram.TelegramSession, onTerminate: (() -> Unit)?) {
    Row(Modifier.fillMaxWidth().heightIn(min = 72.dp).padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(44.dp).clip(CircleShape).background(Fusion.colors.surface2), contentAlignment = Alignment.Center) {
            IconGlyph(KiroXIcon.Devices, null, Modifier.size(22.dp), Fusion.colors.textPrimary)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(session.device.ifBlank { "Устройство" }, color = Fusion.colors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(session.application, color = Fusion.colors.textSecondary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val details = listOfNotNull(
                session.location.takeIf { it.isNotBlank() },
                if (session.isCurrent) "в сети" else com.kirox.app.ui.format.TelegramFormat.chatTime(session.lastActive)
            ).joinToString(" · ")
            Text(details, color = Fusion.colors.textTertiary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (onTerminate != null) Text(
            "Завершить", color = Fusion.colors.danger, fontSize = 14.sp,
            modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onTerminate).padding(horizontal = 12.dp, vertical = 10.dp)
        )
    }
}

/** Picks a photo and sets it as the profile photo; returns the launcher action. */
@Composable
internal fun rememberProfilePhotoPicker(onNotice: (String) -> Unit): () -> Unit {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val users = (context.applicationContext as com.kirox.app.KiroXApplication).telegram.users
    val launcher = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val path = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                runCatching {
                    val out = java.io.File(context.cacheDir, "profile_photo_${System.currentTimeMillis()}.jpg")
                    context.contentResolver.openInputStream(uri)?.use { input -> out.outputStream().use { input.copyTo(it) } }
                    out.absolutePath
                }.getOrNull()
            }
            if (path == null) { onNotice("Не удалось прочитать фото"); return@launch }
            onNotice("Загружаю фото…")
            runCatching { users.setProfilePhoto(path) }
                .onSuccess { onNotice("Фото профиля обновлено") }
                .onFailure { onNotice("Не удалось обновить фото") }
        }
    }
    return { launcher.launch(androidx.activity.result.PickVisualMediaRequest(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly)) }
}

/** Edit name, bio and username. */
@Composable
internal fun ProfileEditScreen(profile: TelegramUserProfile?, onBack: () -> Unit, onNotice: (String) -> Unit) {
    BackHandler(onBack = onBack)
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val users = (context.applicationContext as com.kirox.app.KiroXApplication).telegram.users
    var first by rememberSaveable(profile?.id) { mutableStateOf(profile?.firstName.orEmpty()) }
    var last by rememberSaveable(profile?.id) { mutableStateOf(profile?.lastName.orEmpty()) }
    var bio by rememberSaveable(profile?.id) { mutableStateOf(profile?.bio.orEmpty()) }
    var username by rememberSaveable(profile?.id) { mutableStateOf(profile?.username.orEmpty()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val pickPhoto = rememberProfilePhotoPicker(onNotice)
    LazyColumn(Modifier.fillMaxSize().background(Fusion.colors.background).imePadding(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { BackTitle("Профиль", onBack) }
        item {
            Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.clip(CircleShape).clickable(onClick = pickPhoto)) { TelegramAvatar(profile?.name.orEmpty(), profile?.photo, 96.dp) }
                Text(
                    "Изменить фото", color = Fusion.colors.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium,
                    modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = pickPhoto).padding(horizontal = 12.dp, vertical = 10.dp)
                )
            }
        }
        item { SmallCaption("Имя") }
        item { EditField(first, "Имя") { first = it.take(64) } }
        item { EditField(last, "Фамилия (необязательно)") { last = it.take(64) } }
        item { SmallCaption("О себе") }
        item { EditField(bio, "Пара слов о себе", singleLine = false) { bio = it.take(140) } }
        item { Text("${bio.length}/140", color = Fusion.colors.textTertiary, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 20.dp)) }
        item { SmallCaption("Имя пользователя") }
        item { EditField(username, "username") { username = it.filter { c -> c.isLetterOrDigit() || c == '_' }.take(32) } }
        item { Text("Можно использовать a–z, 0–9 и _, от 5 символов. Пустое поле — без имени пользователя.", color = Fusion.colors.textTertiary, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)) }
        error?.let { message -> item { Text(message, color = Fusion.colors.danger, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) } }
        item {
            val canSave = first.isNotBlank() && !busy && (username.isEmpty() || username.length >= 5)
            Box(
                Modifier.padding(16.dp).fillMaxWidth().heightIn(min = 52.dp).clip(RoundedCornerShape(26.dp))
                    .background(if (canSave) Fusion.colors.accent else Fusion.colors.surface2)
                    .clickable(enabled = canSave) {
                        busy = true; error = null
                        scope.launch {
                            runCatching { users.updateProfile(first.trim(), last.trim(), bio.trim(), username.trim(), profile) }
                                .onSuccess { onNotice("Профиль сохранён"); onBack() }
                                .onFailure { error = (it as? com.kirox.app.telegram.TdException)?.let { e -> profileError(e.message.orEmpty()) } ?: "Не удалось сохранить профиль" }
                            busy = false
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                if (busy) androidx.compose.material3.CircularProgressIndicator(Modifier.size(22.dp), color = Fusion.colors.onAccent, strokeWidth = 2.dp)
                else Text("Сохранить", color = if (canSave) Fusion.colors.onAccent else Fusion.colors.textTertiary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

private fun profileError(message: String): String = when {
    message.contains("USERNAME_OCCUPIED", true) -> "Это имя пользователя уже занято"
    message.contains("USERNAME_INVALID", true) -> "Недопустимое имя пользователя"
    message.contains("FIRSTNAME_INVALID", true) -> "Недопустимое имя"
    message.contains("ABOUT_TOO_LONG", true) -> "Слишком длинное «О себе»"
    else -> "Не удалось сохранить профиль"
}

@Composable
private fun EditField(value: String, hint: String, singleLine: Boolean = true, onChange: (String) -> Unit) {
    androidx.compose.foundation.text.BasicTextField(
        value, onChange, singleLine = singleLine,
        textStyle = androidx.compose.ui.text.TextStyle(Fusion.colors.textPrimary, 16.sp),
        cursorBrush = androidx.compose.ui.graphics.SolidColor(Fusion.colors.textPrimary),
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth().heightIn(min = 52.dp)
            .clip(RoundedCornerShape(14.dp)).background(Fusion.colors.surface1).padding(horizontal = 16.dp, vertical = 15.dp),
        decorationBox = { inner -> Box { if (value.isEmpty()) Text(hint, color = Fusion.colors.textTertiary, fontSize = 16.sp); inner() } }
    )
}

/** The small Markdown subset used in release notes: headings, "- " bullets and **bold**. The top "## KiroX …" title is dropped. */
private fun releaseNotesText(markdown: String, strong: androidx.compose.ui.graphics.Color): androidx.compose.ui.text.AnnotatedString =
    androidx.compose.ui.text.buildAnnotatedString {
        var lines = markdown.replace("\r", "").lines().dropWhile { it.isBlank() }
        if (lines.firstOrNull()?.startsWith("## ") == true) lines = lines.drop(1).dropWhile { it.isBlank() }
        lines.forEachIndexed { index, raw ->
            if (index > 0) append('\n')
            if (raw.startsWith("#")) {
                withStyle(androidx.compose.ui.text.SpanStyle(color = strong, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)) { append(raw.trimStart('#').trim()) }
                return@forEachIndexed
            }
            val line = if (raw.startsWith("- ")) "•  " + raw.drop(2) else raw
            line.split("**").forEachIndexed { i, part ->
                if (i % 2 == 1) withStyle(androidx.compose.ui.text.SpanStyle(color = strong, fontWeight = FontWeight.SemiBold)) { append(part) } else append(part)
            }
        }
    }

/** Accounts on this device: switch between them or add one. Only the chosen account is online. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun AccountsSheet(onDismiss: () -> Unit) {
    val client = (androidx.compose.ui.platform.LocalContext.current.applicationContext as com.kirox.app.KiroXApplication).telegram.client
    val accounts by client.accounts.accounts.collectAsState()
    val active = client.activeSession
    androidx.compose.material3.ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Fusion.colors.surface1) {
        Text("Аккаунты", color = Fusion.colors.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
        accounts.forEach { account ->
            val current = account.session == active
            Row(
                Modifier.fillMaxWidth().clickable { onDismiss(); if (!current) client.switchTo(account.session) }.heightIn(min = 60.dp).padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TelegramAvatar(account.title.ifBlank { "?" }, null, 40.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(account.title.ifBlank { "Вход не завершён" }, color = Fusion.colors.textPrimary, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (account.phone.isNotBlank()) Text("+" + account.phone.trimStart('+'), color = Fusion.colors.textSecondary, fontSize = 13.sp)
                }
                if (current) IconGlyph(KiroXIcon.Check, null, Modifier.size(22.dp), Fusion.colors.accent)
            }
        }
        if (accounts.size < MAX_ACCOUNTS) TileRow(KiroXIcon.Add, "Добавить аккаунт") { onDismiss(); client.addAccount() }
        Text(
            "Уведомления приходят только от выбранного аккаунта.",
            color = Fusion.colors.textTertiary, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
        )
        Spacer(Modifier.navigationBarsPadding().height(8.dp))
    }
}

private const val MAX_ACCOUNTS = 3
