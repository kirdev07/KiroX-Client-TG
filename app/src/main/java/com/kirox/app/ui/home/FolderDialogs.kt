package com.kirox.app.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kirox.app.KiroXApplication
import com.kirox.app.telegram.TelegramChatItem
import com.kirox.app.telegram.TelegramFolder
import com.kirox.app.ui.theme.Fusion
import kotlinx.coroutines.launch

/** Name plus chats picked from the main list; Telegram allows up to 100 chats per folder. */
@Composable
internal fun CreateFolderDialog(chats: List<TelegramChatItem>, onDismiss: () -> Unit, onNotice: (String) -> Unit) {
    val repo = (LocalContext.current.applicationContext as KiroXApplication).telegram.chats
    var name by remember { mutableStateOf("") }
    var picked by remember { mutableStateOf(setOf<Long>()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Fusion.colors.surface2,
        title = { Text("Новая папка", color = Fusion.colors.textPrimary) },
        text = {
            Column {
                OutlinedTextField(name, { name = it.take(12) }, placeholder = { Text("Название") }, singleLine = true)
                Spacer(Modifier.height(8.dp))
                Text("Чаты: ${picked.size}", color = Fusion.colors.textSecondary)
                LazyColumn(Modifier.heightIn(max = 320.dp)) {
                    items(chats, key = { it.id }) { chat ->
                        val checked = chat.id in picked
                        Row(Modifier.fillMaxWidth().clickable { picked = if (checked) picked - chat.id else if (picked.size < 100) picked + chat.id else picked }, verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked, null)
                            Text(chat.title, color = Fusion.colors.textPrimary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(enabled = name.isNotBlank() && picked.isNotEmpty(), onClick = {
                onDismiss()
                // The dialog is gone right after this: its own scope would cancel the request.
                com.kirox.app.AppScope.launch {
                    runCatching { repo.createFolder(name.trim(), picked.toList()) }
                        .onSuccess { onNotice("Папка создана") }.onFailure { onNotice("Не удалось создать папку") }
                }
            }) { Text("Создать", color = Fusion.colors.textPrimary) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена", color = Fusion.colors.textSecondary) } }
    )
}

@Composable
internal fun DeleteFolderDialog(folder: TelegramFolder, onDismiss: () -> Unit, onNotice: (String) -> Unit) {
    val repo = (LocalContext.current.applicationContext as KiroXApplication).telegram.chats
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Fusion.colors.surface2,
        title = { Text("Удалить папку «${folder.title}»?", color = Fusion.colors.textPrimary) },
        text = { Text("Чаты останутся в общем списке.", color = Fusion.colors.textSecondary) },
        confirmButton = {
            TextButton(onClick = {
                onDismiss()
                com.kirox.app.AppScope.launch { runCatching { repo.deleteFolder(folder.id) }.onSuccess { onNotice("Папка удалена") }.onFailure { onNotice("Не удалось удалить папку") } }
            }) { Text("Удалить", color = Fusion.colors.textPrimary) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена", color = Fusion.colors.textSecondary) } }
    )
}
