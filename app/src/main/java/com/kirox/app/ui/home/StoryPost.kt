package com.kirox.app.ui.home

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import com.kirox.app.KiroXApplication
import com.kirox.app.ui.chat.copyToCache
import com.kirox.app.ui.chat.videoMeta
import com.kirox.app.ui.theme.Fusion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Gallery picker plus a caption/audience dialog that posts a story; call the returned lambda to start. */
@Composable
internal fun rememberStoryPoster(onNotice: (String) -> Unit): () -> Unit {
    val context = LocalContext.current
    val stories = (context.applicationContext as KiroXApplication).telegram.stories
    val scope = rememberCoroutineScope()
    var picked by remember { mutableStateOf<Uri?>(null) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { picked = it }
    picked?.let { uri ->
        var caption by remember { mutableStateOf("") }
        var everyone by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { picked = null },
            containerColor = Fusion.colors.surface2,
            title = { Text("Новая история", color = Fusion.colors.textPrimary) },
            text = {
                Column {
                    OutlinedTextField(caption, { caption = it.take(1024) }, placeholder = { Text("Подпись") })
                    listOf(false to "Мои контакты", true to "Все").forEach { (value, label) ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(everyone == value, { everyone = value })
                            Text(label, color = Fusion.colors.textPrimary)
                        }
                    }
                    Text("История будет видна 24 часа.", color = Fusion.colors.textSecondary)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    picked = null
                    scope.launch {
                        val path = withContext(Dispatchers.IO) { copyToCache(context, uri) }
                        if (path == null) { onNotice("Не удалось прочитать файл"); return@launch }
                        val isVideo = context.contentResolver.getType(uri)?.startsWith("video/") == true
                        val duration = if (isVideo) withContext(Dispatchers.IO) { videoMeta(path).first } else 0
                        runCatching { stories.postStory(path, isVideo, duration, caption.trim(), everyone) }
                            .onSuccess { onNotice("История публикуется") }
                            .onFailure { onNotice("Не удалось опубликовать историю") }
                    }
                }) { Text("Опубликовать", color = Fusion.colors.textPrimary) }
            },
            dismissButton = { TextButton(onClick = { picked = null }) { Text("Отмена", color = Fusion.colors.textSecondary) } }
        )
    }
    return { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)) }
}
