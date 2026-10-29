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
import com.kirox.app.telegram.BlockedSender
import com.kirox.app.telegram.CloudPasswordState
import com.kirox.app.telegram.NotificationException
import com.kirox.app.telegram.NotifyScope
import com.kirox.app.telegram.ProxyKind
import com.kirox.app.telegram.TelegramProxy
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
    var exceptionsPage by remember { mutableStateOf(false) }
    if (exceptionsPage) { NotificationExceptionsScreen(onBack = { exceptionsPage = false }); return }
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
        item { SmallCaption("Доставка") }
        item {
            var keep by remember { mutableStateOf(com.kirox.app.notify.KeepConnectedService.isEnabled(context)) }
            ToggleRow("Фоновое соединение", "Сообщения приходят сразу, даже если система не будит KiroX по пушу. В шторке будет тихое уведомление «KiroX на связи».", keep) {
                keep = it; com.kirox.app.notify.KeepConnectedService.setEnabled(context, it)
            }
        }
        item { LinkRow("Исключения", "Чаты, где уведомления настроены отдельно") { exceptionsPage = true } }
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
    var page by remember { mutableStateOf("") }
    when (page) {
        "password" -> { CloudPasswordScreen(onBack = { page = "" }, onNotice = onNotice); return }
        "blocked" -> { BlockedUsersScreen(onBack = { page = "" }, onNotice = onNotice); return }
    }
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
        item { SmallCaption("Безопасность") }
        item { LinkRow("Облачный пароль", "Двухэтапная проверка при входе") { page = "password" } }
        item { LinkRow("Чёрный список", "Заблокированные пользователи и чаты") { page = "blocked" } }
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
    var proxyPage by remember { mutableStateOf(false) }
    if (proxyPage) { ProxyScreen(onBack = { proxyPage = false }, onNotice = onNotice); return }
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
        item { SmallCaption("Сеть") }
        item { LinkRow("Прокси", "SOCKS5, HTTP или MTProto, если Telegram недоступен напрямую") { proxyPage = true } }
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
        item { SmallCaption("Тестирование") }
        item {
            var crashes by remember { mutableStateOf(com.kirox.app.CrashLog.count(context)) }
            LinkRow("Журнал ошибок", if (crashes == 0) "Ошибок не записано" else "Записей: $crashes · нажмите, чтобы отправить разработчику") {
                if (crashes > 0) com.kirox.app.CrashLog.share(context) else onNotice("Журнал пуст")
            }
            if (crashes > 0) LinkRow("Очистить журнал", null, Fusion.colors.textSecondary) { com.kirox.app.CrashLog.clear(context); crashes = 0; onNotice("Журнал очищен") }
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


// ---------- Cloud password ----------

/** Settings → Privacy → Cloud password: set, change or remove it, recovery email with its code. */
@Composable
internal fun CloudPasswordScreen(onBack: () -> Unit, onNotice: (String) -> Unit) {
    BackHandler(onBack = onBack)
    val repo = settingsRepo()
    val scope = rememberCoroutineScope()
    var state by remember { mutableStateOf<CloudPasswordState?>(null) }
    var busy by remember { mutableStateOf(false) }
    var current by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var repeat by remember { mutableStateOf("") }
    var hint by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var confirmRemove by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { runCatching { repo.passwordState() }.onSuccess { state = it }.onFailure { onNotice("Не удалось загрузить настройки пароля") } }
    fun run(block: suspend () -> CloudPasswordState, done: String) {
        busy = true
        scope.launch {
            runCatching { block() }
                .onSuccess { state = it; current = ""; password = ""; repeat = ""; code = ""; onNotice(done) }
                .onFailure { onNotice(com.kirox.app.telegram.passwordError(it)) }
            busy = false
        }
    }
    LazyColumn(Modifier.fillMaxSize().background(Fusion.colors.background), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { BackTitle("Облачный пароль", onBack) }
        val s = state
        if (s == null) { item { Loading() }; return@LazyColumn }
        item {
            Text(
                if (s.hasPassword) "Пароль включён. Его спросят при входе на новом устройстве." + (if (s.hasRecoveryEmail) " Почта для восстановления указана." else "")
                else "Пароль не задан. С ним войти в аккаунт по одному коду из SMS не получится.",
                color = Fusion.colors.textSecondary, fontSize = 14.sp, lineHeight = 19.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
            )
        }
        s.pendingEmailPattern?.let { pattern ->
            item { SmallCaption("Подтверждение почты") }
            item { PasswordField("Код из письма на $pattern", code, secret = false) { code = it } }
            item { ActionButton("Подтвердить", enabled = !busy && code.isNotBlank()) { run({ repo.confirmRecoveryEmail(code.trim()) }, "Почта подтверждена") } }
        }
        item { SmallCaption(if (s.hasPassword) "Сменить пароль" else "Новый пароль") }
        if (s.hasPassword) item { PasswordField("Текущий пароль" + (if (s.hint.isNotBlank()) " (подсказка: ${s.hint})" else ""), current) { current = it } }
        item { PasswordField("Новый пароль", password) { password = it } }
        item { PasswordField("Повторите пароль", repeat) { repeat = it } }
        item { PasswordField("Подсказка (необязательно)", hint, secret = false) { hint = it } }
        if (!s.hasRecoveryEmail) item { PasswordField("Почта для восстановления (необязательно)", email, secret = false) { email = it } }
        item {
            val valid = password.isNotEmpty() && password == repeat && hint != password && (!s.hasPassword || current.isNotEmpty())
            ActionButton(if (s.hasPassword) "Сменить пароль" else "Включить пароль", enabled = !busy && valid) {
                run({ repo.setPassword(current, password, hint.trim(), email.trim().takeIf { it.isNotEmpty() && !s.hasRecoveryEmail }) }, "Пароль сохранён")
            }
            if (password.isNotEmpty() && repeat.isNotEmpty() && password != repeat) Text("Пароли не совпадают", color = Fusion.colors.danger, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 20.dp))
        }
        if (s.hasPassword) item {
            LinkRow("Отключить пароль", "Нужен текущий пароль", color = Fusion.colors.danger) { if (current.isEmpty()) onNotice("Введите текущий пароль") else confirmRemove = true }
        }
    }
    if (confirmRemove) AlertDialog(
        onDismissRequest = { confirmRemove = false },
        containerColor = Fusion.colors.surface1,
        title = { Text("Отключить облачный пароль?", color = Fusion.colors.textPrimary) },
        text = { Text("Войти в аккаунт можно будет только по коду.", color = Fusion.colors.textSecondary) },
        confirmButton = { TextButton(onClick = { confirmRemove = false; run({ repo.setPassword(current, "", "", null) }, "Пароль отключён") }) { Text("Отключить", color = Fusion.colors.danger) } },
        dismissButton = { TextButton(onClick = { confirmRemove = false }) { Text("Отмена", color = Fusion.colors.textSecondary) } }
    )
}

@Composable
private fun PasswordField(label: String, value: String, secret: Boolean = true, onChange: (String) -> Unit) {
    androidx.compose.material3.OutlinedTextField(
        value, onChange, label = { Text(label) }, singleLine = true,
        visualTransformation = if (secret) androidx.compose.ui.text.input.PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
            focusedTextColor = Fusion.colors.textPrimary, unfocusedTextColor = Fusion.colors.textPrimary,
            focusedBorderColor = Fusion.colors.textPrimary, unfocusedBorderColor = Fusion.colors.outlineStrong,
            focusedLabelColor = Fusion.colors.textPrimary, unfocusedLabelColor = Fusion.colors.textSecondary, cursorColor = Fusion.colors.textPrimary
        )
    )
}

@Composable
private fun ActionButton(title: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.padding(16.dp).fillMaxWidth().heightIn(min = 52.dp).clip(RoundedCornerShape(26.dp))
            .background(if (enabled) Fusion.colors.textPrimary else Fusion.colors.surface2).clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Text(title, color = if (enabled) Fusion.colors.background else Fusion.colors.textTertiary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold) }
}

// ---------- Blocked users ----------

@Composable
internal fun BlockedUsersScreen(onBack: () -> Unit, onNotice: (String) -> Unit) {
    BackHandler(onBack = onBack)
    val repo = settingsRepo()
    val scope = rememberCoroutineScope()
    var list by remember { mutableStateOf<List<BlockedSender>?>(null) }
    var confirm by remember { mutableStateOf<BlockedSender?>(null) }
    LaunchedEffect(Unit) { list = runCatching { repo.blocked() }.getOrElse { onNotice("Не удалось загрузить список"); emptyList() } }
    LazyColumn(Modifier.fillMaxSize().background(Fusion.colors.background), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { BackTitle("Чёрный список", onBack) }
        val current = list
        when {
            current == null -> item { Loading() }
            current.isEmpty() -> item { Text("Никто не заблокирован", color = Fusion.colors.textSecondary, modifier = Modifier.padding(20.dp)) }
            else -> items(current, key = { "${it.isChat}${it.id}" }) { sender ->
                Row(Modifier.fillMaxWidth().clickable { confirm = sender }.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    com.kirox.app.ui.components.TelegramAvatar(sender.name, sender.photo, 42.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(sender.name, color = Fusion.colors.textPrimary, fontSize = 16.sp, modifier = Modifier.weight(1f))
                    Text("Разблокировать", color = Fusion.colors.textSecondary, fontSize = 13.sp)
                }
            }
        }
    }
    confirm?.let { sender ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            containerColor = Fusion.colors.surface1,
            title = { Text("Разблокировать ${sender.name}?", color = Fusion.colors.textPrimary) },
            confirmButton = {
                TextButton(onClick = {
                    confirm = null
                    scope.launch {
                        runCatching { repo.unblock(sender) }
                            .onSuccess { list = list?.filterNot { it == sender }; onNotice("Разблокирован") }
                            .onFailure { onNotice("Не удалось разблокировать") }
                    }
                }) { Text("Разблокировать", color = Fusion.colors.textPrimary) }
            },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Отмена", color = Fusion.colors.textSecondary) } }
        )
    }
}

// ---------- Notification exceptions ----------

@Composable
internal fun NotificationExceptionsScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val repo = settingsRepo()
    var list by remember { mutableStateOf<List<NotificationException>?>(null) }
    LaunchedEffect(Unit) { list = runCatching { repo.notificationExceptions() }.getOrDefault(emptyList()) }
    LazyColumn(Modifier.fillMaxSize().background(Fusion.colors.background), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { BackTitle("Исключения", onBack) }
        val current = list
        when {
            current == null -> item { Loading() }
            current.isEmpty() -> item { Text("Все чаты следуют общим настройкам", color = Fusion.colors.textSecondary, modifier = Modifier.padding(20.dp)) }
            else -> items(current, key = { it.chatId }) { e -> LinkRow(e.title, if (e.muted) "Без звука" else "Уведомления включены") {} }
        }
        item {
            Text(
                "Изменить исключение можно в меню чата (⋮ → «Без звука»).",
                color = Fusion.colors.textTertiary, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
            )
        }
    }
}

// ---------- Proxy ----------

/** Settings → Data → Proxy: list, switch, add by fields or by pasting a tg://proxy link, remove. */
@Composable
internal fun ProxyScreen(onBack: () -> Unit, onNotice: (String) -> Unit) {
    BackHandler(onBack = onBack)
    val repo = settingsRepo()
    val scope = rememberCoroutineScope()
    var list by remember { mutableStateOf<List<TelegramProxy>?>(null) }
    var adding by remember { mutableStateOf(false) }
    fun reload() { scope.launch { list = runCatching { repo.proxies() }.getOrDefault(emptyList()) } }
    fun act(block: suspend () -> Unit, failure: String) { scope.launch { runCatching { block() }.onFailure { onNotice(failure) }; reload() } }
    LaunchedEffect(Unit) { reload() }
    LazyColumn(Modifier.fillMaxSize().background(Fusion.colors.background), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { BackTitle("Прокси", onBack) }
        val current = list
        if (current == null) { item { Loading() }; return@LazyColumn }
        item { ToggleRow("Использовать прокси", if (current.none { it.enabled }) "Сейчас прямое подключение" else null, checked = current.any { it.enabled }, enabled = current.isNotEmpty()) { on ->
            if (on) current.firstOrNull()?.let { p -> act({ repo.enableProxy(p.id) }, "Не удалось включить прокси") } else act({ repo.disableProxy() }, "Не удалось отключить прокси")
        } }
        if (current.isNotEmpty()) item { SmallCaption("Сохранённые") }
        items(current, key = { it.id }) { proxy ->
            Row(Modifier.fillMaxWidth().clickable { act({ repo.enableProxy(proxy.id) }, "Не удалось включить прокси") }.heightIn(min = 56.dp).padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(if (proxy.enabled) "●" else "○", color = Fusion.colors.textPrimary, fontSize = 16.sp)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("${proxy.server}:${proxy.port}", color = Fusion.colors.textPrimary, fontSize = 16.sp)
                    Text(proxy.kind.title, color = Fusion.colors.textSecondary, fontSize = 12.sp)
                }
                Text("Удалить", color = Fusion.colors.danger, fontSize = 13.sp, modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable { act({ repo.removeProxy(proxy.id) }, "Не удалось удалить") }.padding(8.dp))
            }
        }
        item { LinkRow("Добавить прокси") { adding = true } }
    }
    if (adding) AddProxyDialog(onDismiss = { adding = false }) { server, port, kind, secret ->
        adding = false
        act({ repo.addProxy(server, port, kind, secret) }, "Не удалось добавить прокси")
    }
}

@Composable
private fun AddProxyDialog(onDismiss: () -> Unit, onAdd: (String, Int, ProxyKind, String) -> Unit) {
    var link by remember { mutableStateOf("") }
    var server by remember { mutableStateOf("") }
    var port by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf(ProxyKind.MtProto) }
    var secret by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Fusion.colors.surface1,
        title = { Text("Новый прокси", color = Fusion.colors.textPrimary) },
        text = {
            Column {
                PasswordField("Ссылка tg://proxy (необязательно)", link, secret = false) { value ->
                    link = value
                    com.kirox.app.telegram.parseProxyLink(value)?.let { (s, p, k) -> server = s; port = p.toString(); kind = k.first; secret = k.second }
                }
                Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ProxyKind.entries.forEach { k ->
                        Text(
                            k.title, color = if (kind == k) Fusion.colors.background else Fusion.colors.textPrimary, fontSize = 13.sp,
                            modifier = Modifier.clip(RoundedCornerShape(50)).background(if (kind == k) Fusion.colors.textPrimary else Fusion.colors.surface3).clickable { kind = k }.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
                PasswordField("Сервер", server, secret = false) { server = it.trim() }
                PasswordField("Порт", port, secret = false) { port = it.filter(Char::isDigit).take(5) }
                PasswordField(if (kind == ProxyKind.MtProto) "Секрет" else "Логин:пароль (необязательно)", secret) { secret = it.trim() }
            }
        },
        confirmButton = {
            val portNumber = port.toIntOrNull()
            TextButton(
                enabled = server.isNotBlank() && portNumber != null && portNumber in 1..65535 && (kind != ProxyKind.MtProto || secret.isNotBlank()),
                onClick = { onAdd(server, portNumber!!, kind, secret) }
            ) { Text("Добавить", color = Fusion.colors.textPrimary) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена", color = Fusion.colors.textSecondary) } }
    )
}
