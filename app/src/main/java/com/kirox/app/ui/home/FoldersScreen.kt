package com.kirox.app.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.kirox.app.KiroXApplication
import com.kirox.app.telegram.TelegramChatItem
import com.kirox.app.telegram.TelegramChatListKey
import com.kirox.app.telegram.TelegramChatLists
import com.kirox.app.telegram.TelegramFolder
import com.kirox.app.ui.components.TelegramAvatar
import com.kirox.app.ui.icons.KiroXIcon
import com.kirox.app.ui.theme.Fusion
import kotlinx.coroutines.launch

/**
 * «Папки чатов» and «Редактировать папку» from the approved groups/channels/folders board:
 * the list of folders with their chat counts, and an editor for the name and the chats.
 * [startWith] opens the editor right away: a folder id, or -1 for a new folder.
 */
@Composable
internal fun FoldersScreen(lists: TelegramChatLists, startWith: Int?, onNotice: (String) -> Unit, onDismiss: () -> Unit) {
    var editing by remember { mutableStateOf(startWith) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize().background(Fusion.colors.background).statusBarsPadding().navigationBarsPadding().imePadding()) {
            val id = editing
            if (id == null) FolderList(lists, onEdit = { editing = it }, onBack = onDismiss)
            else FolderEditor(
                folder = lists.folders.firstOrNull { it.id == id }, chats = lists.main.chats, onNotice = onNotice,
                onBack = { if (startWith != null) onDismiss() else editing = null }
            )
        }
    }
}

@Composable
private fun FolderList(lists: TelegramChatLists, onEdit: (Int) -> Unit, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
        item { BackTitle("Папки чатов", onBack) }
        item {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Fusion.colors.surface1)) {
                FolderRow(KiroXIcon.Chats, "Все", "Все чаты", lists.main.chats.size, onClick = null)
                lists.folders.forEach { folder ->
                    FolderRow(KiroXIcon.Folder, folder.title.ifBlank { "Папка" }, "Своя папка", lists[TelegramChatListKey.Folder(folder.id)].chats.size) { onEdit(folder.id) }
                }
            }
        }
        item {
            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Fusion.colors.surface1)
                    .clickable { onEdit(-1) }.padding(16.dp),
                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically
            ) {
                KiroXIcon(KiroXIcon.Add, null, Modifier.size(20.dp), Fusion.colors.accent)
                Spacer(Modifier.width(10.dp))
                Text("Создать новую папку", color = Fusion.colors.accent, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            }
        }
        item {
            Text(
                "Личные, Группы и Каналы — быстрые фильтры над списком чатов, они всегда есть и не настраиваются.",
                color = Fusion.colors.textTertiary, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
            )
        }
    }
}

@Composable
private fun FolderRow(icon: KiroXIcon, title: String, subtitle: String, count: Int, onClick: (() -> Unit)?) {
    Row(
        Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(Fusion.colors.accent.copy(alpha = 0.18f)), contentAlignment = Alignment.Center) {
            KiroXIcon(icon, null, Modifier.size(20.dp), Fusion.colors.accent)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Fusion.colors.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, color = Fusion.colors.textSecondary, fontSize = 13.sp)
        }
        Text(count.toString(), color = Fusion.colors.textPrimary, fontSize = 13.sp, modifier = Modifier.clip(CircleShape).background(Fusion.colors.surface2).padding(horizontal = 10.dp, vertical = 4.dp))
    }
}

@Composable
private fun FolderEditor(folder: TelegramFolder?, chats: List<TelegramChatItem>, onNotice: (String) -> Unit, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val repo = (LocalContext.current.applicationContext as KiroXApplication).telegram.chats
    val scope = rememberCoroutineScope()
    var name by remember(folder?.id) { mutableStateOf(folder?.title.orEmpty()) }
    var picked by remember(folder?.id) { mutableStateOf<Set<Long>>(emptySet()) }
    var loaded by remember(folder?.id) { mutableStateOf(folder == null) }
    var adding by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    LaunchedEffect(folder?.id) {
        if (folder != null) { picked = runCatching { repo.folderChatIds(folder.id) }.getOrDefault(emptyList()).toSet(); loaded = true }
    }
    if (confirmDelete && folder != null) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        containerColor = Fusion.colors.surface1,
        title = { Text("Удалить папку «${folder.title}»?", color = Fusion.colors.textPrimary) },
        text = { Text("Чаты останутся в общем списке.", color = Fusion.colors.textSecondary) },
        confirmButton = {
            TextButton(onClick = {
                confirmDelete = false
                scope.launch { runCatching { repo.deleteFolder(folder.id) }.onSuccess { onNotice("Папка удалена"); onBack() }.onFailure { onNotice("Не удалось удалить папку") } }
            }) { Text("Удалить", color = Fusion.colors.danger) }
        },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Отмена", color = Fusion.colors.textSecondary) } }
    )
    val selected = chats.filter { it.id in picked }
    val canSave = name.isNotBlank() && picked.isNotEmpty() && loaded && !busy
    Column(Modifier.fillMaxSize()) {
        BackTitle(if (folder == null) "Новая папка" else "Редактировать папку", onBack) {
            if (folder != null) TitleAction(KiroXIcon.Delete, "Удалить папку") { confirmDelete = true }
        }
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp)) {
            item {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)).background(Fusion.colors.accent), contentAlignment = Alignment.Center) {
                        KiroXIcon(KiroXIcon.Folder, null, Modifier.size(28.dp), Fusion.colors.onAccent)
                    }
                    Spacer(Modifier.height(12.dp))
                    BasicTextField(
                        name, { name = it.take(12) }, singleLine = true,
                        textStyle = TextStyle(Fusion.colors.textPrimary, 17.sp, textAlign = TextAlign.Center), cursorBrush = SolidColor(Fusion.colors.accent),
                        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Fusion.colors.surface1)
                            .border(1.dp, Fusion.colors.outline, RoundedCornerShape(16.dp)).padding(16.dp),
                        decorationBox = { inner -> Box(contentAlignment = Alignment.Center) { if (name.isEmpty()) Text("Название папки", color = Fusion.colors.textTertiary, fontSize = 17.sp); inner() } }
                    )
                }
            }
            item {
                Text("Выбранные чаты (${picked.size})", color = Fusion.colors.textSecondary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 20.dp, bottom = 8.dp))
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Fusion.colors.surface1).clickable { adding = !adding }.padding(14.dp),
                    horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically
                ) {
                    KiroXIcon(if (adding) KiroXIcon.Check else KiroXIcon.Add, null, Modifier.size(18.dp), Fusion.colors.textPrimary)
                    Spacer(Modifier.width(8.dp))
                    Text(if (adding) "Готово" else "Добавить чаты", color = Fusion.colors.textPrimary, fontSize = 15.sp)
                }
                Spacer(Modifier.height(6.dp))
            }
            // While adding, every chat of the main list can be ticked; otherwise only the folder's chats.
            items(if (adding) chats else selected, key = { it.id }) { chat ->
                val checked = chat.id in picked
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).clickable {
                        picked = if (checked) picked - chat.id else if (picked.size < 100) picked + chat.id else picked
                    }.padding(vertical = 8.dp, horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TelegramAvatar(chat.title, chat.photo, 40.dp)
                    Spacer(Modifier.width(12.dp))
                    Text(chat.title, color = Fusion.colors.textPrimary, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                    Box(
                        Modifier.size(24.dp).clip(CircleShape).background(if (checked) Fusion.colors.accent else Color.Transparent)
                            .border(2.dp, if (checked) Fusion.colors.accent else Fusion.colors.outlineStrong, CircleShape),
                        contentAlignment = Alignment.Center
                    ) { if (checked) KiroXIcon(KiroXIcon.Check, null, Modifier.size(14.dp), Fusion.colors.onAccent) }
                }
            }
        }
        Box(Modifier.padding(16.dp)) {
            com.kirox.app.ui.auth.AuthGradientButton("Сохранить", enabled = canSave, busy = busy) {
                busy = true
                scope.launch {
                    runCatching { if (folder == null) repo.createFolder(name.trim(), picked.toList()) else repo.editFolder(folder.id, name.trim(), picked.toList()) }
                        .onSuccess { onNotice(if (folder == null) "Папка создана" else "Папка сохранена"); onBack() }
                        .onFailure { onNotice("Не удалось сохранить папку") }
                    busy = false
                }
            }
        }
    }
}
