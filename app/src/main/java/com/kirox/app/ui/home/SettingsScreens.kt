package com.kirox.app.ui.home

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kirox.app.BuildConfig
import com.kirox.app.KiroXApplication
import com.kirox.app.telegram.NotifyScope
import com.kirox.app.telegram.PrivacyItem
import com.kirox.app.telegram.PrivacyLevel
import com.kirox.app.telegram.ScopeNotifications
import com.kirox.app.telegram.StorageUsage
import com.kirox.app.telegram.TelegramUserProfile
import com.kirox.app.ui.theme.Fusion
import kotlinx.coroutines.launch

@Composable
private fun settingsRepo() = (LocalContext.current.applicationContext as KiroXApplication).telegram.settings

@Composable
private fun ToggleRow(title: String, subtitle: String? = null, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(enabled = enabled) { onChange(!checked) }.heightIn(min = 56.dp).padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Fusion.colors.textPrimary, fontSize = 16.sp)
            if (subtitle != null) Text(subtitle, color = Fusion.colors.textSecondary, fontSize = 12.sp)
        }
        Switch(
            checked, onChange, enabled = enabled,
            colors = SwitchDefaults.colors(checkedThumbColor = Fusion.colors.onAccent, checkedTrackColor = Fusion.colors.accent, uncheckedThumbColor = Fusion.colors.textTertiary, uncheckedTrackColor = Fusion.colors.surface2, uncheckedBorderColor = Fusion.colors.outlineStrong)
        )
    }
}

@Composable
private fun LinkRow(title: String, subtitle: String? = null, color: Color = Fusion.colors.textPrimary, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).heightIn(min = 56.dp).padding(horizontal = 20.dp, vertical = 10.dp), verticalArrangement = Arrangement.Center) {
        Text(title, color = color, fontSize = 16.sp)
        if (subtitle != null) Text(subtitle, color = Fusion.colors.textSecondary, fontSize = 12.sp)
    }
}

@Composable
private fun Loading() {
    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Fusion.colors.textPrimary) }
}

// ---------- Notifications ----------

/** Settings → Notifications: per-type on/off and previews (stored in Telegram), system settings, MIUI tips. */
@Composable
internal fun NotificationSettingsScreen(onBack: () -> Unit, onNotice: (String) -> Unit) {
    BackHandler(onBack = onBack)
    val repo = settingsRepo()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state = remember { mutableStateMapOf<NotifyScope, ScopeNotifications>() }
    LaunchedEffect(Unit) { NotifyScope.entries.forEach { s -> runCatching { repo.notifications(s) }.onSuccess { state[s] = it } } }
    fun update(s: NotifyScope, value: ScopeNotifications) {
        val old = state[s]
        state[s] = value
        scope.launch { runCatching { repo.setNotifications(s, value.enabled, value.showPreview) }.onFailure { old?.let { state[s] = it }; onNotice("Не удалось сохранить") } }
    }
    LazyColumn(Modifier.fillMaxSize().background(Fusion.colors.background), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { BackTitle("Уведомления", onBack) }
        NotifyScope.entries.forEach { s ->
            item(key = s.name) {
                SmallCaption(s.title)
                val value = state[s]
                if (value == null) Loading() else {
                    ToggleRow("Уведомления", checked = value.enabled) { update(s, value.copy(enabled = it)) }
                    ToggleRow("Показывать текст", "Имя и текст сообщения в уведомлении", value.showPreview, enabled = value.enabled) { update(s, value.copy(showPreview = it)) }
                }
            }
        }
        item { SmallCaption("Система") }
        item {
            LinkRow("Звук и вибрация", "Системные настройки уведомлений KiroX") {
                context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
        item {
            LinkRow("Работа в фоне", "Чтобы уведомления и звонки приходили, когда KiroX свёрнут") {
                runCatching {
                    context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            }
        }
        if (android.os.Build.MANUFACTURER.equals("Xiaomi", ignoreCase = true)) item {
            Text(
                "На Xiaomi включите для KiroX «Автозапуск» и в «Контроле активности» выберите «Нет ограничений».",
                color = Fusion.colors.textTertiary, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
            )
        }
    }
}

// ---------- Privacy ----------

/** Settings → Privacy: who sees the number, last seen, photo; who can call and invite. */
@Composable
internal fun PrivacyScreen(onBack: () -> Unit, onNotice: (String) -> Unit) {
    BackHandler(onBack = onBack)
    val repo = settingsRepo()
    val scope = rememberCoroutineScope()
    val levels = remember { mutableStateMapOf<PrivacyItem, PrivacyLevel>() }
    var choosing by remember { mutableStateOf<PrivacyItem?>(null) }
    LaunchedEffect(Unit) { PrivacyItem.entries.forEach { item -> runCatching { repo.privacy(item) }.onSuccess { levels[item] = it } } }
    LazyColumn(Modifier.fillMaxSize().background(Fusion.colors.background), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { BackTitle("Конфиденциальность", onBack) }
        item { SmallCaption("Кто видит и может") }
        items(PrivacyItem.entries.toList(), key = { it.name }) { item ->
            LinkRow(item.title, levels[item]?.title ?: "…") { if (levels[item] != null) choosing = item }
        }
        item {
            Text(
                "Исключения для отдельных людей, настроенные в другом клиенте Telegram, сохраняются.",
                color = Fusion.colors.textTertiary, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
            )
        }
    }
    choosing?.let { item ->
        AlertDialog(
            onDismissRequest = { choosing = null },
            containerColor = Fusion.colors.surface1,
            title = { Text(item.title, color = Fusion.colors.textPrimary) },
            text = {
                Column {
                    PrivacyLevel.entries.forEach { level ->
                        val selected = levels[item] == level
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable {
                                choosing = null
                                val old = levels[item]
                                levels[item] = level
                                scope.launch { runCatching { repo.setPrivacy(item, level) }.onFailure { old?.let { levels[item] = it }; onNotice("Не удалось сохранить") } }
                            }.heightIn(min = 48.dp).padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(if (selected) "●" else "○", color = Fusion.colors.textPrimary, fontSize = 16.sp)
                            Spacer(Modifier.width(12.dp))
                            Text(level.title, color = Fusion.colors.textPrimary, fontSize = 16.sp)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { choosing = null }) { Text("Отмена", color = Fusion.colors.textSecondary) } }
        )
    }
}

// ---------- Data and storage ----------

/** Settings → Data and storage: cache size and clearing. */
@Composable
internal fun StorageScreen(onBack: () -> Unit, onNotice: (String) -> Unit) {
    BackHandler(onBack = onBack)
    val repo = settingsRepo()
    val scope = rememberCoroutineScope()
    var usage by remember { mutableStateOf<StorageUsage?>(null) }
    var clearing by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
    fun reload() { scope.launch { runCatching { repo.storage() }.onSuccess { usage = it } } }
    LaunchedEffect(Unit) { reload() }
    LazyColumn(Modifier.fillMaxSize().background(Fusion.colors.background), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { BackTitle("Данные и память", onBack) }
        item { SmallCaption("Использование памяти") }
        item {
            val u = usage
            if (u == null) Loading() else Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                Text(formatBytes(u.filesBytes + u.databaseBytes), color = Fusion.colors.textPrimary, fontSize = 28.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                Text("Медиа и файлы: ${formatBytes(u.filesBytes)} (${u.fileCount}) · база: ${formatBytes(u.databaseBytes)}", color = Fusion.colors.textSecondary, fontSize = 13.sp)
            }
        }
        item {
            Box(
                Modifier.padding(16.dp).fillMaxWidth().heightIn(min = 52.dp).clip(RoundedCornerShape(26.dp))
                    .background(Fusion.colors.surface2).clickable(enabled = !clearing && (usage?.filesBytes ?: 0) > 0) { confirm = true },
                contentAlignment = Alignment.Center
            ) {
                if (clearing) CircularProgressIndicator(Modifier.size(22.dp), color = Fusion.colors.textPrimary, strokeWidth = 2.dp)
                else Text("Очистить кэш", color = Fusion.colors.danger, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        item {
            Text(
                "Удаляются скачанные фото, видео, голосовые и файлы. Сообщения остаются в облаке Telegram и загрузятся снова при открытии.",
                color = Fusion.colors.textTertiary, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(horizontal = 20.dp)
            )
        }
    }
    if (confirm) AlertDialog(
        onDismissRequest = { confirm = false },
        containerColor = Fusion.colors.surface1,
        title = { Text("Очистить кэш?", color = Fusion.colors.textPrimary) },
        text = { Text("Будет освобождено около ${formatBytes(usage?.filesBytes ?: 0)}.", color = Fusion.colors.textSecondary) },
        confirmButton = {
            TextButton(onClick = {
                confirm = false; clearing = true
                scope.launch {
                    runCatching { repo.clearCache() }.onSuccess { onNotice("Кэш очищен") }.onFailure { onNotice("Не удалось очистить кэш") }
                    clearing = false; reload()
                }
            }) { Text("Очистить", color = Fusion.colors.danger) }
        },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text("Отмена", color = Fusion.colors.textSecondary) } }
    )
}

internal fun formatBytes(bytes: Long): String = when {
    bytes >= 1L shl 30 -> "%.1f ГБ".format(bytes / (1L shl 30).toDouble())
    bytes >= 1L shl 20 -> "%.1f МБ".format(bytes / (1L shl 20).toDouble())
    bytes >= 1L shl 10 -> "%d КБ".format(bytes / (1L shl 10))
    else -> "$bytes Б"
}

// ---------- Premium ----------

@Composable
internal fun PremiumScreen(profile: TelegramUserProfile?, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    LazyColumn(Modifier.fillMaxSize().background(Fusion.colors.background), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { BackTitle("Telegram Premium", onBack) }
        item {
            Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("★", color = Fusion.colors.textPrimary, fontSize = 56.sp)
                Spacer(Modifier.height(8.dp))
                Text(
                    if (profile?.isPremium == true) "Telegram Premium активен" else "Telegram Premium не подключён",
                    color = Fusion.colors.textPrimary, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Подписка оформляется и управляется в официальном приложении Telegram. Возможности Premium работают и в KiroX там, где их поддерживает TDLib.",
                    color = Fusion.colors.textSecondary, fontSize = 14.sp, lineHeight = 20.sp, textAlign = TextAlign.Center
                )
            }
        }
        item {
            LinkRow("Открыть Telegram Premium", "telegram.org/premium") {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://telegram.org/faq_premium")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
    }
}

// ---------- Advanced ----------

/** Settings → Advanced: app info, reset of local KiroX settings, log out. */
@Composable
internal fun AdvancedScreen(onBack: () -> Unit, onLogOut: () -> Unit, onNotice: (String) -> Unit) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    var confirmReset by remember { mutableStateOf(false) }
    var confirmLogout by remember { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize().background(Fusion.colors.background), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { BackTitle("Дополнительно", onBack) }
        item { SmallCaption("О приложении") }
        item { LinkRow("KiroX ${BuildConfig.VERSION_NAME}", "Независимый клиент Telegram на TDLib") {} }
        item {
            LinkRow("Исходный код и лицензии", "github.com/kirdev07/KiroX-Client-TG") {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/kirdev07/KiroX-Client-TG")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
        item {
            LinkRow("Язык", "Русский · системные настройки языка") {
                runCatching { context.startActivity(Intent(Settings.ACTION_APP_LOCALE_SETTINGS, Uri.parse("package:${context.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    .onFailure { onNotice("Пока доступен только русский язык") }
            }
        }
        item { SmallCaption("Сброс") }
        item { LinkRow("Сбросить настройки KiroX", "Тема, анимации, чаты — к значениям по умолчанию. Telegram не затрагивается", Fusion.colors.danger) { confirmReset = true } }
        item { LinkRow("Выйти из аккаунта", null, Fusion.colors.danger) { confirmLogout = true } }
    }
    if (confirmReset) AlertDialog(
        onDismissRequest = { confirmReset = false },
        containerColor = Fusion.colors.surface1,
        title = { Text("Сбросить настройки KiroX?", color = Fusion.colors.textPrimary) },
        text = { Text("Тема, пузыри, обои, анимации и размер текста вернутся к значениям по умолчанию.", color = Fusion.colors.textSecondary) },
        confirmButton = {
            TextButton(onClick = {
                confirmReset = false
                (context.applicationContext as KiroXApplication).settings.resetAll()
                onNotice("Настройки сброшены")
            }) { Text("Сбросить", color = Fusion.colors.danger) }
        },
        dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Отмена", color = Fusion.colors.textSecondary) } }
    )
    if (confirmLogout) AlertDialog(
        onDismissRequest = { confirmLogout = false },
        containerColor = Fusion.colors.surface1,
        title = { Text("Выйти из аккаунта?", color = Fusion.colors.textPrimary) },
        text = { Text("Сеанс KiroX на этом устройстве будет завершён.", color = Fusion.colors.textSecondary) },
        confirmButton = { TextButton(onClick = { confirmLogout = false; onLogOut() }) { Text("Выйти", color = Fusion.colors.danger) } },
        dismissButton = { TextButton(onClick = { confirmLogout = false }) { Text("Отмена", color = Fusion.colors.textSecondary) } }
    )
}

// ---------- QR profile ----------

/** QR code with the t.me link of the profile. */
@Composable
internal fun ProfileQrDialog(profile: TelegramUserProfile?, onDismiss: () -> Unit) {
    val username = profile?.username
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Fusion.colors.surface1,
        title = { Text("QR-код профиля", color = Fusion.colors.textPrimary) },
        text = {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                if (username.isNullOrBlank()) {
                    Text("Чтобы получить QR-код, задайте имя пользователя в «Редактировать профиль».", color = Fusion.colors.textSecondary, textAlign = TextAlign.Center)
                } else {
                    val link = "https://t.me/$username"
                    val matrix = remember(link) {
                        runCatching {
                            com.google.zxing.qrcode.QRCodeWriter().encode(link, com.google.zxing.BarcodeFormat.QR_CODE, 0, 0, mapOf(com.google.zxing.EncodeHintType.MARGIN to 0))
                        }.getOrNull()
                    }
                    Box(Modifier.size(220.dp).clip(RoundedCornerShape(16.dp)).background(Color.White).padding(12.dp)) {
                        if (matrix != null) Canvas(Modifier.fillMaxSize()) {
                            val cell = size.minDimension / matrix.width
                            for (y in 0 until matrix.height) for (x in 0 until matrix.width) {
                                if (matrix[x, y]) drawRect(Color.Black, Offset(x * cell, y * cell), Size(cell + 0.5f, cell + 0.5f))
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text("@$username", color = Fusion.colors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Готово", color = Fusion.colors.textPrimary) } }
    )
}
