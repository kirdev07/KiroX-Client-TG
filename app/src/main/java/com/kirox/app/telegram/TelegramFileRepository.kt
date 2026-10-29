package com.kirox.app.telegram

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.drinkless.tdlib.TdApi
import java.util.concurrent.ConcurrentHashMap

/** Download state of one file; [path] is set once the whole file is on disk. */
data class TelegramFileState(
    val downloaded: Long = 0,
    val total: Long = 0,
    val downloading: Boolean = false,
    val path: String? = null
) {
    val fraction: Float get() = if (total > 0) (downloaded.toFloat() / total).coerceIn(0f, 1f) else 0f
}

/**
 * File downloads. Small files (avatars, previews) download as soon as they are observed; message
 * files (documents, videos, music) only on [download], with progress and [cancel].
 * One flow per file id; TDLib dedupes repeated requests.
 */
class TelegramFileRepository(private val client: TelegramClient) {
    private val paths = ConcurrentHashMap<Int, MutableStateFlow<String?>>()
    private val states = ConcurrentHashMap<Int, MutableStateFlow<TelegramFileState>>()

    init {
        client.addUpdateHandler { update ->
            when (update) {
                is TdApi.UpdateFile -> {
                    val file = update.file
                    val local = file.local ?: return@addUpdateHandler
                    if (local.isDownloadingCompleted && local.path.isNotBlank()) paths[file.id]?.value = local.path
                    states[file.id]?.value = file.toState()
                }
                is TdApi.UpdateAuthorizationState -> if (update.authorizationState is TdApi.AuthorizationStateClosed) {
                    if (client.paused) {
                        // Same account after the pause: file ids stay valid and screens keep these flows.
                        // Downloads stopped with TDLib; the next start resumes them on request.
                        states.values.forEach { flow -> flow.value = flow.value.copy(downloading = false) }
                    } else {
                        paths.clear(); states.clear()
                    }
                }
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

    /** Progress of [file] without downloading it; refreshed from TDLib on first use. */
    fun state(file: TelegramFileRef): StateFlow<TelegramFileState> = mutableState(file)

    private fun mutableState(file: TelegramFileRef): MutableStateFlow<TelegramFileState> {
        var created = false
        val flow = states.getOrPut(file.id) { created = true; MutableStateFlow(TelegramFileState(path = file.localPath)) }
        if (created) client.send(TdApi.GetFile(file.id)) { result -> if (result is TdApi.File) flow.value = result.toState() }
        return flow
    }

    /** Starts or resumes downloading [file]; progress arrives in [state]. */
    fun download(file: TelegramFileRef, priority: Int = MEDIA_PRIORITY) {
        val flow = mutableState(file)
        if (flow.value.path != null) return
        flow.value = flow.value.copy(downloading = true)
        client.send(TdApi.DownloadFile(file.id, priority, 0, 0, false)) { result ->
            when (result) {
                is TdApi.File -> flow.value = result.toState()
                is TdApi.Error -> flow.value = flow.value.copy(downloading = false)
            }
        }
    }

    /** Stops a download; what is already on disk is kept for a later resume. */
    fun cancel(file: TelegramFileRef) {
        client.send(TdApi.CancelDownloadFile(file.id, false))
        states[file.id]?.let { it.value = it.value.copy(downloading = false) }
    }

    private fun TdApi.File.toState(): TelegramFileState {
        val local = local
        val done = local != null && local.isDownloadingCompleted && local.path.isNotBlank()
        return TelegramFileState(
            downloaded = local?.downloadedSize ?: 0,
            total = if (size > 0) size else expectedSize,
            downloading = local?.isDownloadingActive == true,
            path = if (done) local!!.path else null
        )
    }

    private companion object {
        const val AVATAR_PRIORITY = 8
        const val MEDIA_PRIORITY = 24
    }
}
