package com.kirox.app.telegram

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.drinkless.tdlib.TdApi
import java.util.concurrent.ConcurrentHashMap

/** Lazy downloads of small files (avatars). One flow per file id; TDLib dedupes repeated requests. */
class TelegramFileRepository(private val client: TelegramClient) {
    private val paths = ConcurrentHashMap<Int, MutableStateFlow<String?>>()

    init {
        client.addUpdateHandler { update ->
            when (update) {
                is TdApi.UpdateFile -> {
                    val local = update.file.local ?: return@addUpdateHandler
                    if (local.isDownloadingCompleted && local.path.isNotBlank()) paths[update.file.id]?.value = local.path
                }
                is TdApi.UpdateAuthorizationState -> if (update.authorizationState is TdApi.AuthorizationStateClosed) paths.clear()
            }
        }
    }

    /** Local path of [file], starting a background download when it is not on disk yet. */
    fun observe(file: TelegramFileRef, priority: Int = AVATAR_PRIORITY): StateFlow<String?> {
        var created = false
        val flow = paths.getOrPut(file.id) { created = true; MutableStateFlow(file.localPath) }
        if (file.localPath != null && flow.value == null) flow.value = file.localPath
        if (created && file.localPath == null) {
            client.send(TdApi.DownloadFile(file.id, priority, 0, 0, false)) { result ->
                if (result is TdApi.File && result.local.isDownloadingCompleted) flow.value = result.local.path
            }
        }
        return flow
    }

    private companion object { const val AVATAR_PRIORITY = 8 }
}
