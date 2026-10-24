package com.kirox.app.ui.home

import androidx.compose.runtime.ReadOnlyComposable
import com.kirox.app.ui.theme.Fusion
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.TextButton
import com.kirox.app.telegram.TelegramChatItem
import com.kirox.app.telegram.TelegramConnection
import com.kirox.app.telegram.TelegramUserProfile
import com.kirox.app.ui.icons.KiroXIcon
import com.kirox.app.ui.icons.KiroXIcon as IconGlyph
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class RealSection { Archive, Messages }
private val RealWhite: Color @Composable @ReadOnlyComposable get() = Fusion.colors.textPrimary
private val RealSecondary: Color @Composable @ReadOnlyComposable get() = Fusion.colors.textSecondary
private val RealDivider: Color @Composable @ReadOnlyComposable get() = Fusion.colors.outline

@Composable
fun TelegramMainScreen(
    mainChats: List<TelegramChatItem>,
    archiveChats: List<TelegramChatItem>,
    connection: TelegramConnection,
    profile: TelegramUserProfile?,
    onLogOut: () -> Unit
) {
    var section by rememberSaveable { mutableStateOf(RealSection.Messages) }
    var notice by remember { mutableStateOf<String?>(null) }
    var accountSheet by rememberSaveable { mutableStateOf(false) }
    Scaffold(
        containerColor = Fusion.colors.background,
        contentWindowInsets = WindowInsets.statusBars,
        bottomBar = {
            RealBottomDock(
                onAccount = { accountSheet = true },
                onUnavailable = { notice = "Этот раздел реального аккаунта будет подключён следующим этапом" }
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).background(Fusion.colors.background)) {
            Row(Modifier.fillMaxWidth().heightIn(min = 58.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(connectionTitle(connection) ?: "Чаты", color = RealWhite, fontSize = 26.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                listOf(KiroXIcon.Search, KiroXIcon.ReadAll, KiroXIcon.Compose).forEach { icon ->
                    Box(Modifier.size(48.dp).clip(CircleShape).clickable { notice = "Действие будет доступно после подключения переписки" }, contentAlignment = Alignment.Center) {
                        IconGlyph(icon, null, Modifier.size(24.dp), RealWhite)
                    }
                }
            }
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                listOf("Архив" to RealSection.Archive, "Сообщения" to RealSection.Messages).forEach { (label, value) ->
                    Column(Modifier.weight(1f).heightIn(min = 48.dp).clickable { section = value }, horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Bottom) {
                        Text(label, color = if (section == value) RealWhite else RealSecondary, fontSize = 14.sp, fontWeight = if (section == value) FontWeight.SemiBold else FontWeight.Normal)
                        Spacer(Modifier.height(10.dp)); Box(Modifier.width(94.dp).height(2.dp).background(if (section == value) RealWhite else Color.Transparent))
                    }
                }
            }
            HorizontalDivider(color = RealDivider)
            val chats = if (section == RealSection.Archive) archiveChats else mainChats
            if (chats.isEmpty()) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(if (section == RealSection.Archive) "Архив пуст" else "Загрузка диалогов…", color = RealSecondary)
            } else LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
                items(chats, key = { it.id }) { chat -> RealChatRow(chat) }
            }
        }
        notice?.let { message ->
            Box(Modifier.fillMaxSize().padding(padding).padding(20.dp), contentAlignment = Alignment.BottomCenter) {
                Text(message, color = RealWhite, fontSize = 13.sp, modifier = Modifier.background(Fusion.colors.surface3).padding(14.dp))
            }
            LaunchedEffect(message) { kotlinx.coroutines.delay(1900); notice = null }
        }
    }
    if (accountSheet) AccountSheet(profile, onDismiss = { accountSheet = false }, onLogOut = { accountSheet = false; onLogOut() })
}

private fun connectionTitle(state: TelegramConnection): String? = when (state) {
    TelegramConnection.WaitingForNetwork -> "Ожидание сети…"
    TelegramConnection.ConnectingToProxy -> "Подключение к прокси…"
    TelegramConnection.Connecting -> "Соединение…"
    TelegramConnection.Updating -> "Обновление…"
    TelegramConnection.Ready -> null
}

/** Temporary account entry until the merged Settings/Profile tab lands in stage C. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccountSheet(profile: TelegramUserProfile?, onDismiss: () -> Unit, onLogOut: () -> Unit) {
    var confirm by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Fusion.colors.surface1) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
            Text(profile?.name?.ifBlank { null } ?: "Аккаунт Telegram", color = RealWhite, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            profile?.username?.let { Text("@$it", color = RealSecondary, fontSize = 14.sp) }
            Spacer(Modifier.height(20.dp))
            Text(
                "Выйти из аккаунта", color = Fusion.colors.danger, fontSize = 16.sp, fontWeight = FontWeight.Medium,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable { confirm = true }.wrapContentHeight()
            )
            Spacer(Modifier.navigationBarsPadding().height(16.dp))
        }
    }
    if (confirm) AlertDialog(
        onDismissRequest = { confirm = false },
        containerColor = Fusion.colors.surface2,
        title = { Text("Выйти из аккаунта?", color = RealWhite) },
        text = { Text("Сессия на этом устройстве будет завершена, локальный кеш Telegram удалён.", color = RealSecondary) },
        confirmButton = { TextButton(onClick = { confirm = false; onLogOut() }) { Text("Выйти", color = Fusion.colors.danger) } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text("Отмена", color = RealWhite) } }
    )
}

@Composable
private fun RealChatRow(chat: TelegramChatItem) {
    Row(Modifier.fillMaxWidth().heightIn(min = 76.dp).padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(52.dp).clip(CircleShape).background(Fusion.colors.avatar).border(1.dp, Fusion.colors.outlineStrong, CircleShape), contentAlignment = Alignment.Center) {
            Text(chat.title.trim().take(1).uppercase(), color = RealWhite, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(chat.title, color = RealWhite, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                if (chat.isPinned) IconGlyph(KiroXIcon.Pin, "Закреплённый чат", Modifier.padding(horizontal = 5.dp).size(14.dp), RealSecondary)
                Text(realTime(chat.timestamp), color = RealSecondary, fontSize = 11.sp)
            }
            Spacer(Modifier.height(5.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(chat.preview, color = RealSecondary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                if (chat.unreadCount > 0) Box(Modifier.defaultMinSize(minWidth = 20.dp, minHeight = 20.dp).clip(CircleShape).background(Fusion.colors.accent).padding(horizontal = 5.dp), contentAlignment = Alignment.Center) {
                    Text(chat.unreadCount.toString(), color = Fusion.colors.onAccent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
    HorizontalDivider(color = RealDivider)
}

@Composable
private fun RealBottomDock(onAccount: () -> Unit, onUnavailable: () -> Unit) {
    Row(Modifier.fillMaxWidth().navigationBarsPadding().heightIn(min = 70.dp).background(Fusion.colors.background).border(1.dp, RealDivider), verticalAlignment = Alignment.CenterVertically) {
        listOf(
            "Контакты" to KiroXIcon.Contacts, "Чаты" to KiroXIcon.Chat,
            "Настройки" to KiroXIcon.Settings, "Профиль" to KiroXIcon.Profile
        ).forEach { (title, icon) ->
            val selected = title == "Чаты"
            Column(Modifier.weight(1f).heightIn(min = 64.dp).clickable(enabled = !selected, onClick = if (title == "Настройки" || title == "Профиль") onAccount else onUnavailable), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                IconGlyph(icon, title, Modifier.size(25.dp), if (selected) RealWhite else RealSecondary)
                Spacer(Modifier.height(4.dp)); Text(title, color = if (selected) RealWhite else RealSecondary, fontSize = 10.sp)
            }
        }
    }
}

private fun realTime(timestamp: Int): String = if (timestamp <= 0) "" else SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp.toLong() * 1000))
