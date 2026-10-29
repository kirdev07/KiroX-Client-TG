package com.kirox.app.telegram

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.drinkless.tdlib.TdApi

/** What a chat member is doing right now, as shown under the chat title. */
enum class ChatActivity(val text: String) {
    Typing("печатает"),
    RecordingVoice("записывает голосовое"),
    RecordingVideoNote("записывает видеосообщение"),
    RecordingVideo("записывает видео"),
    SendingPhoto("отправляет фото"),
    SendingVideo("отправляет видео"),
    SendingVoice("отправляет голосовое"),
    SendingVideoNote("отправляет видеосообщение"),
    SendingFile("отправляет файл"),
    ChoosingSticker("выбирает стикер"),
    Other("что-то делает")
}

internal fun TdApi.ChatAction.toActivity(): ChatActivity? = when (this) {
    is TdApi.ChatActionCancel -> null
    is TdApi.ChatActionTyping -> ChatActivity.Typing
    is TdApi.ChatActionRecordingVoiceNote -> ChatActivity.RecordingVoice
    is TdApi.ChatActionRecordingVideoNote -> ChatActivity.RecordingVideoNote
    is TdApi.ChatActionRecordingVideo -> ChatActivity.RecordingVideo
    is TdApi.ChatActionUploadingPhoto -> ChatActivity.SendingPhoto
    is TdApi.ChatActionUploadingVideo -> ChatActivity.SendingVideo
    is TdApi.ChatActionUploadingVoiceNote -> ChatActivity.SendingVoice
    is TdApi.ChatActionUploadingVideoNote -> ChatActivity.SendingVideoNote
    is TdApi.ChatActionUploadingDocument -> ChatActivity.SendingFile
    is TdApi.ChatActionChoosingSticker -> ChatActivity.ChoosingSticker
    else -> ChatActivity.Other
}

/**
 * Line for a chat's header or list row: "печатает…" in a private chat, "Анна печатает…" for one
 * member of a group, "Анна и ещё 2 печатают…" for several. [names] are in arrival order.
 */
internal fun chatActivityLine(names: List<String>, activity: ChatActivity, isGroup: Boolean): String {
    if (!isGroup || names.isEmpty()) return "${activity.text}…"
    if (names.size == 1) return "${names[0]} ${activity.text}…"
    val verb = if (activity == ChatActivity.Typing) "печатают" else activity.text
    return "${names[0]} и ещё ${names.size - 1} $verb…"
}

/**
 * Members' activity per chat from UpdateChatAction, and our own activity sent with SendChatAction.
 * TDLib ends an activity with ChatActionCancel; entries also expire after [EXPIRE_MS] in case that is missed.
 */
class TelegramChatActionRepository(private val client: TelegramClient, private val chats: TelegramChatRepository, private val users: TelegramUserRepository) {
    private class Entry(val sender: Long, val activity: ChatActivity, val at: Long)

    // Dispatcher-confined: chatId → senders' current activity, in arrival order.
    private val active = HashMap<Long, LinkedHashMap<Long, Entry>>()
    private var lastSent = HashMap<Long, Pair<ChatActivity, Long>>()

    private val _lines = MutableStateFlow<Map<Long, String>>(emptyMap())
    /** chatId → text to show instead of the status while someone there is doing something. */
    val lines: StateFlow<Map<Long, String>> = _lines.asStateFlow()

    init {
        client.addUpdateHandler { update ->
            when (update) {
                is TdApi.UpdateChatAction -> {
                    // Topic activity (forums, comments) is not shown in the chat itself.
                    if (update.topicId != null) return@addUpdateHandler
                    val sender = when (val id = update.senderId) {
                        is TdApi.MessageSenderUser -> id.userId
                        is TdApi.MessageSenderChat -> -id.chatId
                        else -> return@addUpdateHandler
                    }
                    val activity = update.action.toActivity()
                    val chat = active.getOrPut(update.chatId) { LinkedHashMap() }
                    if (activity == null) chat.remove(sender) else chat[sender] = Entry(sender, activity, now())
                    if (chat.isEmpty()) active.remove(update.chatId)
                    publish()
                    if (activity != null) client.post(EXPIRE_MS) { expire() }
                }
                is TdApi.UpdateNewMessage -> {
                    // A message from someone ends what they were doing, even before the cancel arrives.
                    val sender = (update.message.senderId as? TdApi.MessageSenderUser)?.userId ?: return@addUpdateHandler
                    val chat = active[update.message.chatId] ?: return@addUpdateHandler
                    if (chat.remove(sender) != null) {
                        if (chat.isEmpty()) active.remove(update.message.chatId)
                        publish()
                    }
                }
                is TdApi.UpdateAuthorizationState -> if (update.authorizationState is TdApi.AuthorizationStateClosed) {
                    active.clear(); lastSent.clear(); _lines.value = emptyMap()
                }
            }
        }
    }

    /**
     * Tells the chat what we are doing; repeated at most every [RESEND_MS] for the same activity,
     * as Telegram shows an activity for about 5 seconds. Null cancels it.
     */
    fun send(chatId: Long, activity: ChatActivity?) = client.post {
        val previous = lastSent[chatId]
        val time = now()
        if (activity == null) {
            if (previous == null) return@post
            lastSent.remove(chatId)
            client.send(TdApi.SendChatAction(chatId, null, null, TdApi.ChatActionCancel()))
            return@post
        }
        if (previous != null && previous.first == activity && time - previous.second < RESEND_MS) return@post
        lastSent[chatId] = activity to time
        client.send(TdApi.SendChatAction(chatId, null, null, activity.toTd()))
    }

    private fun ChatActivity.toTd(): TdApi.ChatAction = when (this) {
        ChatActivity.Typing -> TdApi.ChatActionTyping()
        ChatActivity.RecordingVoice -> TdApi.ChatActionRecordingVoiceNote()
        ChatActivity.RecordingVideoNote -> TdApi.ChatActionRecordingVideoNote()
        ChatActivity.RecordingVideo -> TdApi.ChatActionRecordingVideo()
        ChatActivity.SendingPhoto -> TdApi.ChatActionUploadingPhoto(0)
        ChatActivity.SendingVideo -> TdApi.ChatActionUploadingVideo(0)
        ChatActivity.SendingVoice -> TdApi.ChatActionUploadingVoiceNote(0)
        ChatActivity.SendingVideoNote -> TdApi.ChatActionUploadingVideoNote(0)
        ChatActivity.SendingFile -> TdApi.ChatActionUploadingDocument(0)
        ChatActivity.ChoosingSticker -> TdApi.ChatActionChoosingSticker()
        ChatActivity.Other -> TdApi.ChatActionTyping()
    }

    private fun expire() {
        val limit = now() - EXPIRE_MS
        var changed = false
        val iterator = active.entries.iterator()
        while (iterator.hasNext()) {
            val chat = iterator.next().value
            changed = chat.values.removeAll { it.at <= limit } || changed
            if (chat.isEmpty()) iterator.remove()
        }
        if (changed) publish()
    }

    private fun publish() {
        _lines.value = active.filterValues { it.isNotEmpty() }.mapValues { (chatId, entries) ->
            val list = entries.values.toList()
            val type = chats.chat(chatId)?.type
            val isGroup = type is TdApi.ChatTypeBasicGroup || type is TdApi.ChatTypeSupergroup
            chatActivityLine(list.map { name(it.sender) }, list.first().activity, isGroup)
        }
    }

    private fun name(sender: Long): String =
        if (sender > 0) users.user(sender)?.firstName?.takeIf { it.isNotBlank() } ?: "Кто-то"
        else chats.chat(-sender)?.title ?: "Кто-то"

    private fun now() = android.os.SystemClock.elapsedRealtime()

    private companion object {
        const val EXPIRE_MS = 6_000L
        const val RESEND_MS = 4_000L
    }
}
