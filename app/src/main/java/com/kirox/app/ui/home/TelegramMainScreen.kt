package com.kirox.app.ui.home

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
import com.kirox.app.telegram.TelegramChatItem
import com.kirox.app.ui.icons.KiroXIcon
import com.kirox.app.ui.icons.KiroXIcon as IconGlyph
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class RealSection { Archive, Messages }
private val RealWhite = Color(0xFFF4F4F4)
private val RealSecondary = Color(0xFF929296)
private val RealDivider = Color(0xFF222222)

@Composable
fun TelegramMainScreen(mainChats: List<TelegramChatItem>, archiveChats: List<TelegramChatItem>) {
    var section by rememberSaveable { mutableStateOf(RealSection.Messages) }
    var notice by remember { mutableStateOf<String?>(null) }
    Scaffold(
        containerColor = Color.Black,
        contentWindowInsets = WindowInsets.statusBars,
        bottomBar = { RealBottomDock { notice = "Этот раздел реального аккаунта будет подключён следующим этапом" } }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).background(Color.Black)) {
            Row(Modifier.fillMaxWidth().heightIn(min = 58.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Чаты", color = RealWhite, fontSize = 26.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
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
                Text(message, color = RealWhite, fontSize = 13.sp, modifier = Modifier.background(Color(0xFF303030)).padding(14.dp))
            }
            LaunchedEffect(message) { kotlinx.coroutines.delay(1900); notice = null }
        }
    }
}

@Composable
private fun RealChatRow(chat: TelegramChatItem) {
    Row(Modifier.fillMaxWidth().heightIn(min = 76.dp).padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(52.dp).clip(CircleShape).background(Color(0xFF405363)).border(1.dp, Color(0xFF72777C), CircleShape), contentAlignment = Alignment.Center) {
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
                if (chat.unreadCount > 0) Box(Modifier.defaultMinSize(minWidth = 20.dp, minHeight = 20.dp).clip(CircleShape).background(Color.White).padding(horizontal = 5.dp), contentAlignment = Alignment.Center) {
                    Text(chat.unreadCount.toString(), color = Color.Black, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
    HorizontalDivider(color = RealDivider)
}

@Composable
private fun RealBottomDock(onUnavailable: () -> Unit) {
    Row(Modifier.fillMaxWidth().navigationBarsPadding().heightIn(min = 70.dp).background(Color(0xFF070707)).border(1.dp, RealDivider), verticalAlignment = Alignment.CenterVertically) {
        listOf(
            "Контакты" to KiroXIcon.Contacts, "Чаты" to KiroXIcon.Chat,
            "Настройки" to KiroXIcon.Settings, "Профиль" to KiroXIcon.Profile
        ).forEach { (title, icon) ->
            val selected = title == "Чаты"
            Column(Modifier.weight(1f).heightIn(min = 64.dp).clickable(enabled = !selected, onClick = onUnavailable), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                IconGlyph(icon, title, Modifier.size(25.dp), if (selected) RealWhite else RealSecondary)
                Spacer(Modifier.height(4.dp)); Text(title, color = if (selected) RealWhite else RealSecondary, fontSize = 10.sp)
            }
        }
    }
}

private fun realTime(timestamp: Int): String = if (timestamp <= 0) "" else SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp.toLong() * 1000))
