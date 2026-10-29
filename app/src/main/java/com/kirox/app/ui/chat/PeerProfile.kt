package com.kirox.app.ui.chat

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kirox.app.KiroXApplication
import com.kirox.app.telegram.SharedMediaKind
import com.kirox.app.telegram.TelegramFileRef
import com.kirox.app.telegram.TelegramProfileInfo
import com.kirox.app.telegram.TelegramSharedItem
import com.kirox.app.ui.components.TelegramAvatar
import com.kirox.app.ui.components.rememberTelegramBitmap
import com.kirox.app.ui.format.TelegramFormat
import com.kirox.app.ui.icons.KiroXIcon
import com.kirox.app.ui.theme.Fusion

/**
 * Full-screen profile of a chat, after the approved contacts board (3 «Карточка пользователя»,
 * 8 «Медиа пользователя»): cover with the avatar, quick actions, info card and shared media tabs.
 */
@Composable
internal fun PeerProfileScreen(
    chatId: Long,
    title: String,
    photo: TelegramFileRef?,
    status: String,
    canCall: Boolean,
    onCall: (isVideo: Boolean) -> Unit,
    muted: Boolean?,
    onToggleMute: ((Boolean) -> Unit)?,
    onSearch: () -> Unit,
    onOpenMessage: (Long) -> Unit,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)
    val chats = (LocalContext.current.applicationContext as KiroXApplication).telegram.chats
    var info by remember(chatId) { mutableStateOf(TelegramProfileInfo()) }
    var tab by remember { mutableStateOf(SharedMediaKind.Media) }
    var items by remember(chatId, tab) { mutableStateOf<List<TelegramSharedItem>?>(null) }
    LaunchedEffect(chatId) { info = chats.profileInfo(chatId) }
    LaunchedEffect(chatId, tab) { items = chats.sharedMedia(chatId, tab) }
    val accent = Fusion.colors.accent
    LazyVerticalGrid(
        GridCells.Fixed(3),
        Modifier.fillMaxSize().background(Fusion.colors.background),
        contentPadding = PaddingValues(bottom = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        item(span = { GridItemSpan(3) }) {
            Box(Modifier.fillMaxWidth().background(Brush.verticalGradient(listOf(accent.copy(alpha = 0.38f), Fusion.colors.background))).statusBarsPadding()) {
                Box(Modifier.padding(4.dp).size(48.dp).clip(CircleShape).clickable(onClick = onBack), contentAlignment = Alignment.Center) {
                    KiroXIcon(KiroXIcon.Back, "Назад", Modifier.size(24.dp), Fusion.colors.textPrimary)
                }
                Column(Modifier.fillMaxWidth().padding(top = 40.dp, bottom = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(128.dp).border(2.dp, accent, CircleShape).padding(5.dp), contentAlignment = Alignment.Center) { TelegramAvatar(title, photo, 116.dp) }
                    Spacer(Modifier.height(12.dp))
                    Text(title, color = Fusion.colors.textPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 24.dp))
                    if (status.isNotBlank()) Text(status, color = accent, fontSize = 14.sp)
                }
            }
        }
        item(span = { GridItemSpan(3) }) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (canCall) {
                    ProfileAction(KiroXIcon.Call, "Звонок", Modifier.weight(1f)) { onCall(false) }
                    ProfileAction(KiroXIcon.Video, "Видео", Modifier.weight(1f)) { onCall(true) }
                }
                ProfileAction(KiroXIcon.Search, "Поиск", Modifier.weight(1f), onSearch)
                if (muted != null && onToggleMute != null) ProfileAction(KiroXIcon.Bell, if (muted) "Вкл. звук" else "Без звука", Modifier.weight(1f)) { onToggleMute(!muted) }
            }
        }
        item(span = { GridItemSpan(3) }) {
            Column(Modifier.padding(16.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Fusion.colors.surface1)) {
                if (info.username.isNotBlank()) InfoRow(KiroXIcon.At, "@${info.username}", "Имя пользователя")
                if (info.about.isNotBlank()) InfoRow(KiroXIcon.Info, info.about, "О себе")
                if (info.phone.isNotBlank()) InfoRow(KiroXIcon.Phone, TelegramFormat.phone(info.phone), "Телефон")
                if (muted != null && onToggleMute != null) Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    KiroXIcon(KiroXIcon.Bell, null, Modifier.size(20.dp), Fusion.colors.textSecondary)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Уведомления", color = Fusion.colors.textPrimary, fontSize = 15.sp)
                        Text(if (muted) "Выключены" else "Включены", color = Fusion.colors.textSecondary, fontSize = 13.sp)
                    }
                    Switch(!muted, { onToggleMute(!it) }, colors = SwitchDefaults.colors(checkedTrackColor = accent))
                }
            }
        }
        item(span = { GridItemSpan(3) }) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SharedMediaKind.entries.forEach { kind ->
                    val selected = kind == tab
                    Text(
                        kind.title, color = if (selected) Fusion.colors.onAccent else Fusion.colors.textSecondary, fontSize = 14.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f).clip(CircleShape).background(if (selected) accent else Color.Transparent).clickable { tab = kind }.padding(vertical = 8.dp)
                    )
                }
            }
        }
        val list = items
        when {
            list == null -> item(span = { GridItemSpan(3) }) { Box(Modifier.fillMaxWidth().padding(32.dp), Alignment.Center) { CircularProgressIndicator(Modifier.size(24.dp), color = accent, strokeWidth = 2.dp) } }
            list.isEmpty() -> item(span = { GridItemSpan(3) }) { Text("Здесь пока ничего нет", color = Fusion.colors.textSecondary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(32.dp)) }
            tab == SharedMediaKind.Media -> items(list, key = { it.messageId }) { item -> MediaTile(item) { onOpenMessage(item.messageId) } }
            else -> items(list, key = { it.messageId }, span = { GridItemSpan(3) }) { item ->
                Row(Modifier.fillMaxWidth().clickable { onOpenMessage(item.messageId) }.padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(accent.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
                        KiroXIcon(when (tab) { SharedMediaKind.Files -> KiroXIcon.File; SharedMediaKind.Voice -> KiroXIcon.Voice; else -> KiroXIcon.Link }, null, Modifier.size(22.dp), accent)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(item.title, color = Fusion.colors.textPrimary, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(listOf(item.subtitle, TelegramFormat.chatTime(item.date)).filter { it.isNotBlank() }.joinToString(" · "), color = Fusion.colors.textSecondary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileAction(icon: KiroXIcon, label: String, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier.clip(RoundedCornerShape(18.dp)).background(Fusion.colors.surface1).clickable(onClick = onClick).padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        KiroXIcon(icon, null, Modifier.size(22.dp), Fusion.colors.accent)
        Spacer(Modifier.height(6.dp))
        Text(label, color = Fusion.colors.textPrimary, fontSize = 12.sp, maxLines = 1)
    }
}

@Composable
private fun InfoRow(icon: KiroXIcon, value: String, label: String) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        KiroXIcon(icon, null, Modifier.size(20.dp), Fusion.colors.textSecondary)
        Spacer(Modifier.width(14.dp))
        Column {
            Text(value, color = Fusion.colors.textPrimary, fontSize = 15.sp)
            Text(label, color = Fusion.colors.textSecondary, fontSize = 12.sp)
        }
    }
}

@Composable
private fun MediaTile(item: TelegramSharedItem, onClick: () -> Unit) {
    val bitmap = rememberTelegramBitmap(item.thumb, with(LocalDensity.current) { 140.dp.roundToPx() })
    Box(Modifier.aspectRatio(1f).background(Fusion.colors.surface2).clickable(onClick = onClick)) {
        if (bitmap != null) Image(bitmap, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        if (item.isVideo) Text(
            item.subtitle, color = Color.White, fontSize = 11.sp,
            modifier = Modifier.align(Alignment.BottomStart).padding(4.dp).clip(RoundedCornerShape(6.dp)).background(Color(0x99000000)).padding(horizontal = 5.dp, vertical = 1.dp)
        )
    }
}
