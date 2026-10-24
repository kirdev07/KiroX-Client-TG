package com.kirox.app.telegram

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.drinkless.tdlib.TdApi

/**
 * Call history. TDLib has no separate call log: it is the list of MessageCall messages from
 * SearchCallMessages, paged by an opaque offset, plus new call messages from updates.
 */
class TelegramCallRepository(private val client: TelegramClient, private val chats: TelegramChatRepository) {
    private val calls = HashMap<Pair<Long, Long>, TdApi.Message>() // (chatId, messageId)
    private var nextOffset = ""
    private var loading = false
    private var complete = false
    private var started = false

    private val _state = MutableStateFlow(TelegramCallsState())
    val state: StateFlow<TelegramCallsState> = _state.asStateFlow()

    init {
        client.addUpdateHandler { update ->
            when (update) {
                is TdApi.UpdateNewMessage -> if (started && update.message.content is TdApi.MessageCall) {
                    calls[key(update.message)] = update.message; publish()
                }
                is TdApi.UpdateDeleteMessages -> if (update.isPermanent && started) {
                    update.messageIds.forEach { calls.remove(key(update.chatId, it)) }; publish()
                }
                is TdApi.UpdateAuthorizationState -> if (update.authorizationState is TdApi.AuthorizationStateClosed) {
                    calls.clear(); nextOffset = ""; loading = false; complete = false; started = false
                    _state.value = TelegramCallsState()
                }
            }
        }
    }

    /** Loads the next page; the first call also starts listening for new calls. */
    fun loadMore() = client.post {
        started = true
        if (loading || complete) return@post
        loading = true
        publish()
        client.send(TdApi.SearchCallMessages(nextOffset, PAGE_SIZE, false)) { result ->
            loading = false
            when (result) {
                is TdApi.FoundMessages -> {
                    result.messages.filterNotNull().forEach { calls[key(it)] = it }
                    nextOffset = result.nextOffset.orEmpty()
                    if (nextOffset.isEmpty() || result.messages.isEmpty()) complete = true
                }
                is TdApi.Error -> complete = true
            }
            publish()
        }
    }

    private fun publish() {
        _state.value = TelegramCallsState(
            calls.values.mapNotNull { message ->
                val chat = chats.chat(message.chatId)
                toCall(message, chat?.title.orEmpty(), chat?.photo?.small?.toRef())
            }.sortedByDescending { it.date },
            loading, complete
        )
    }

    private fun key(message: TdApi.Message) = key(message.chatId, message.id)
    private fun key(chatId: Long, messageId: Long) = chatId to messageId

    private companion object { const val PAGE_SIZE = 50 }
}

internal fun toCall(message: TdApi.Message, peerName: String, photo: TelegramFileRef?): TelegramCall? {
    val call = message.content as? TdApi.MessageCall ?: return null
    val notAnswered = call.discardReason is TdApi.CallDiscardReasonMissed || call.discardReason is TdApi.CallDiscardReasonDeclined
    return TelegramCall(
        messageId = message.id,
        chatId = message.chatId,
        peerName = peerName,
        photo = photo,
        kind = when {
            message.isOutgoing && notAnswered -> TelegramCallKind.Unanswered
            message.isOutgoing -> TelegramCallKind.Outgoing
            notAnswered -> TelegramCallKind.Missed
            else -> TelegramCallKind.Incoming
        },
        isVideo = call.isVideo,
        durationSeconds = call.duration,
        date = message.date
    )
}
