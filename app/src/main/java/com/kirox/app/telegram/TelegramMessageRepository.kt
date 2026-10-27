package com.kirox.app.telegram

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.drinkless.tdlib.TdApi

/**
 * History and actions for the one chat that is open on screen. Dispatcher-confined like the other
 * repositories; message objects are kept raw and mapped on (coalesced) publish.
 */
class TelegramMessageRepository(
    private val client: TelegramClient,
    private val chats: TelegramChatRepository,
    private val users: TelegramUserRepository
) {
    private var chatId = 0L
    private val messages = sortedMapOf<Long, TdApi.Message>()
    private var loadingOlder = false
    private var reachedStart = false
    private var lastReadOutbox = 0L
    private var publishScheduled = false
    private val viewed = HashSet<Long>()
    private var savedChat = false

    private val _history = MutableStateFlow(TelegramChatHistory())
    val history: StateFlow<TelegramChatHistory> = _history.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    /** Last failed action, for a toast; cleared by [clearError]. */
    val error: StateFlow<String?> = _error.asStateFlow()

    init { client.addUpdateHandler(::handle) }

    private var resumeChatId = 0L

    fun open(id: Long) = client.post {
        if (chatId == id) return@post
        if (chatId != 0L) client.send(TdApi.CloseChat(chatId))
        chatId = id
        messages.clear(); viewed.clear()
        loadingOlder = false; reachedStart = false
        lastReadOutbox = chats.chat(id)?.lastReadOutboxMessageId ?: 0
        _history.value = TelegramChatHistory(chatId = id)
        client.send(TdApi.OpenChat(id))
        requestOlder()
        publish()
    }

    fun close(id: Long) = client.post {
        if (resumeChatId == id) { resumeChatId = 0; _history.value = TelegramChatHistory() }
        if (chatId != id) return@post
        client.send(TdApi.CloseChat(id))
        chatId = 0; messages.clear(); viewed.clear()
        _history.value = TelegramChatHistory()
    }

    fun loadOlder() = client.post { requestOlder() }

    fun sendText(text: String, replyToMessageId: Long?) = act { id ->
        TdApi.SendMessage(id, null, replyToMessageId?.let { TdApi.InputMessageReplyToMessage(it, null, 0, null) }, null, null, inputText(text))
    }

    fun sendPhoto(path: String, caption: String) = act { id ->
        TdApi.SendMessage(id, null, null, null, null, TdApi.InputMessagePhoto().apply {
            photo = TdApi.InputPhoto().apply { photo = TdApi.InputFileLocal(path); addedStickerFileIds = IntArray(0) }
            this.caption = TdApi.FormattedText(caption, emptyArray())
        })
    }

    fun sendVoice(path: String, durationSeconds: Int, waveform: ByteArray) = act { id ->
        TdApi.SendMessage(id, null, null, null, null, TdApi.InputMessageVoiceNote(
            TdApi.InputVoiceNote(TdApi.InputFileLocal(path), durationSeconds, waveform), TdApi.FormattedText("", emptyArray()), null
        ))
    }

    fun sendVideo(path: String, caption: String, durationSeconds: Int, width: Int, height: Int) = act { id ->
        TdApi.SendMessage(id, null, null, null, null, TdApi.InputMessageVideo(
            TdApi.InputVideo(TdApi.InputFileLocal(path), null, null, 0, IntArray(0), durationSeconds, width, height, true),
            TdApi.FormattedText(caption, emptyArray()), false, null, false
        ))
    }

    fun sendDocument(path: String, caption: String) = act { id ->
        TdApi.SendMessage(id, null, null, null, null, TdApi.InputMessageDocument().apply {
            document = TdApi.InputDocument().apply { document = TdApi.InputFileLocal(path) }
            this.caption = TdApi.FormattedText(caption, emptyArray())
        })
    }

    /** Sends a sticker or saved GIF from the panel by its file id (no upload). */
    fun sendPanelItem(item: TelegramPanelItem, replyToMessageId: Long?) = act { id ->
        val file = TdApi.InputFileId(item.fileId)
        val media = item.media
        val content: TdApi.InputMessageContent = if (item.isGif) TdApi.InputMessageAnimation(
            TdApi.InputAnimation(file, null, IntArray(0), media.duration, media.width, media.height), TdApi.FormattedText("", emptyArray()), false, false
        ) else TdApi.InputMessageSticker(TdApi.InputSticker(file, null, media.width, media.height), media.title)
        TdApi.SendMessage(id, null, replyToMessageId?.let { TdApi.InputMessageReplyToMessage(it, null, 0, null) }, null, null, content)
    }

    /** Sends a recorded video note (round video); [length] is the side of the square video. */
    fun sendVideoNote(path: String, durationSeconds: Int, length: Int) = act { id ->
        TdApi.SendMessage(id, null, null, null, null, TdApi.InputMessageVideoNote(TdApi.InputVideoNote(TdApi.InputFileLocal(path), null, durationSeconds, length), null))
    }

    /** Edits a text message, or the caption of a media message. */
    fun edit(messageId: Long, text: String) = act { id ->
        if (messages[messageId]?.content is TdApi.MessageText) TdApi.EditMessageText(id, messageId, null, inputText(text))
        else TdApi.EditMessageCaption(id, messageId, null, TdApi.FormattedText(text, emptyArray()), false)
    }

    /** Sends 2–10 photos and videos as one album; [items] are local paths with video metadata or null for photos. */
    fun sendAlbum(items: List<Pair<String, VideoMeta?>>, caption: String) = act { id ->
        val contents = items.mapIndexed { index, (path, video) ->
            // Telegram shows the album caption from its first item.
            val text = TdApi.FormattedText(if (index == 0) caption else "", emptyArray())
            if (video == null) TdApi.InputMessagePhoto().apply {
                photo = TdApi.InputPhoto().apply { photo = TdApi.InputFileLocal(path); addedStickerFileIds = IntArray(0) }
                this.caption = text
            } else TdApi.InputMessageVideo(
                TdApi.InputVideo(TdApi.InputFileLocal(path), null, null, 0, IntArray(0), video.durationSeconds, video.width, video.height, true),
                text, false, null, false
            )
        }
        TdApi.SendMessageAlbum(id, null, null, null, contents.toTypedArray())
    }

    /** Adds the emoji reaction, or removes it when this account already chose it. */
    fun toggleReaction(messageId: Long, emoji: String) = act { id ->
        val chosen = messages[messageId]?.interactionInfo?.reactions?.reactions
            ?.any { (it.type as? TdApi.ReactionTypeEmoji)?.emoji == emoji && it.isChosen } == true
        if (chosen) TdApi.RemoveMessageReaction(id, messageId, TdApi.ReactionTypeEmoji(emoji))
        else TdApi.AddMessageReaction(id, messageId, TdApi.ReactionTypeEmoji(emoji), false, true)
    }

    fun delete(messageIds: List<Long>, forEveryone: Boolean) = act { id -> TdApi.DeleteMessages(id, messageIds.toLongArray(), forEveryone) }

    fun forward(toChatId: Long, messageIds: List<Long>) = act { id -> TdApi.ForwardMessages(toChatId, null, id, messageIds.toLongArray(), null, false, false) }

    /**
     * Saves [text] as the Telegram draft of [chatId] (any chat, not only the open one), or clears it
     * when blank. Skipped when the draft already holds this text, so leaving a chat is not a request.
     */
    fun saveDraft(chatId: Long, text: String) = client.post {
        val chat = chats.chat(chatId) ?: return@post
        if (text.isBlank() && chat.draftMessage == null) return@post
        if (draftText(chat) == text) return@post
        val draft = text.takeIf { it.isNotBlank() }?.let {
            TdApi.DraftMessage(null, (System.currentTimeMillis() / 1000).toInt(), TdApi.DraftMessageContentText(TdApi.FormattedText(it, emptyArray()), null), 0, null)
        }
        client.send(TdApi.SetChatDraftMessage(chatId, null, draft)) { result -> if (result is TdApi.Error) _error.value = humanError(result) }
    }

    fun clearError() { _error.value = null }

    private fun act(build: (Long) -> TdApi.Function<*>) = client.post {
        val id = chatId.takeIf { it != 0L } ?: return@post
        client.send(build(id)) { result -> if (result is TdApi.Error) _error.value = humanError(result) }
    }

    private fun inputText(text: String) = TdApi.InputMessageText(TdApi.FormattedText(text, emptyArray()), null, true)

    private fun requestOlder() {
        val id = chatId
        if (id == 0L || loadingOlder || reachedStart) return
        loadingOlder = true
        schedulePublish()
        client.send(TdApi.GetChatHistory(id, messages.keys.firstOrNull() ?: 0L, 0, PAGE_SIZE, false)) { result ->
            if (id != chatId) return@send
            loadingOlder = false
            when (result) {
                is TdApi.Messages -> {
                    val page = result.messages.filterNotNull()
                    if (page.isEmpty()) reachedStart = true
                    page.forEach { messages[it.id] = it }
                }
                is TdApi.Error -> _error.value = humanError(result)
            }
            schedulePublish()
        }
    }

    private fun handle(update: TdApi.Object) {
        when (update) {
            is TdApi.UpdateNewMessage -> if (update.message.chatId == chatId) { messages[update.message.id] = update.message; schedulePublish() }
            is TdApi.UpdateMessageSendSucceeded -> if (update.message.chatId == chatId) {
                messages.remove(update.oldMessageId); messages[update.message.id] = update.message; schedulePublish()
            }
            is TdApi.UpdateMessageSendFailed -> if (update.message.chatId == chatId) {
                messages.remove(update.oldMessageId); messages[update.message.id] = update.message
                _error.value = humanError(update.error)
                schedulePublish()
            }
            is TdApi.UpdateMessageContent -> if (update.chatId == chatId) messages[update.messageId]?.let { it.content = update.newContent; schedulePublish() }
            is TdApi.UpdateMessageEdited -> if (update.chatId == chatId) messages[update.messageId]?.let { it.editDate = update.editDate; schedulePublish() }
            is TdApi.UpdateMessageInteractionInfo -> if (update.chatId == chatId) messages[update.messageId]?.let { it.interactionInfo = update.interactionInfo; schedulePublish() }
            is TdApi.UpdateDeleteMessages -> if (update.chatId == chatId && update.isPermanent) {
                update.messageIds.forEach { messages.remove(it) }; schedulePublish()
            }
            is TdApi.UpdateChatReadOutbox -> if (update.chatId == chatId) { lastReadOutbox = update.lastReadOutboxMessageId; schedulePublish() }
            is TdApi.UpdateUser, is TdApi.UpdateUserStatus -> if (chatId != 0L) schedulePublish()
            is TdApi.UpdateAuthorizationState -> when (update.authorizationState) {
                // After a background pause the open chat loads again; logout and account switch drop it.
                is TdApi.AuthorizationStateClosed -> {
                    resumeChatId = if (client.paused) chatId else 0
                    chatId = 0; messages.clear(); viewed.clear()
                    if (resumeChatId == 0L) _history.value = TelegramChatHistory()
                }
                is TdApi.AuthorizationStateReady -> if (resumeChatId != 0L) { open(resumeChatId); resumeChatId = 0 }
            }
        }
    }

    private fun schedulePublish() {
        if (publishScheduled) return
        publishScheduled = true
        client.post(PUBLISH_DELAY_MS) { publishScheduled = false; publish() }
    }

    private fun publish() {
        val id = chatId.takeIf { it != 0L } ?: return
        val chat = chats.chat(id)
        val type = chat?.type
        val peerUser = (type as? TdApi.ChatTypePrivate)?.let { users.user(it.userId) } ?: (type as? TdApi.ChatTypeSecret)?.let { users.user(it.userId) }
        savedChat = peerUser != null && peerUser.id == users.myId()
        val mapped = messages.values.map { it.toModel() }
        _history.value = TelegramChatHistory(
            chatId = id,
            title = chat?.title.orEmpty(),
            photo = chat?.photo?.small?.toRef(),
            peerKind = when {
                peerUser != null && peerUser.id == users.myId() -> TelegramPeerKind.Saved
                peerUser?.type is TdApi.UserTypeBot -> TelegramPeerKind.Bot
                type is TdApi.ChatTypePrivate || type is TdApi.ChatTypeSecret -> TelegramPeerKind.User
                type is TdApi.ChatTypeSupergroup && type.isChannel -> TelegramPeerKind.Channel
                type is TdApi.ChatTypeBasicGroup || type is TdApi.ChatTypeSupergroup -> TelegramPeerKind.Group
                else -> TelegramPeerKind.Unknown
            },
            peerStatus = peerUser?.status?.toStatus(),
            messages = mapped,
            loadingOlder = loadingOlder,
            reachedStart = reachedStart,
            isSecret = type is TdApi.ChatTypeSecret,
            draft = chat?.let(::draftText).orEmpty()
        )
        markViewed(id)
    }

    /** Opening a chat reads it: report newly shown incoming messages once. */
    private fun markViewed(id: Long) {
        val fresh = messages.values.filter { !it.isOutgoing && viewed.add(it.id) }.map { it.id }
        if (fresh.isNotEmpty()) client.send(TdApi.ViewMessages(id, fresh.toLongArray(), TdApi.MessageSourceChatHistory(), true))
    }

    private fun TdApi.Message.toModel(): TelegramMessage {
        val content = content
        val reply = replyTo as? TdApi.MessageReplyToMessage
        val replied = reply?.takeIf { it.chatId == 0L || it.chatId == chatId }?.let { messages[it.messageId] }
        val photo = (content as? TdApi.MessagePhoto)?.photo?.let(::bestPhoto)
        val formatted = formattedTextOf(content).takeIf { content !is TdApi.MessageVoiceNote }
        val mediaInfo = mediaOf(content)
        return TelegramMessage(
            id = id,
            chatId = chatId,
            senderName = senderName(senderId),
            isOutgoing = isOutgoing,
            kind = kindOf(content),
            text = when (content) {
                is TdApi.MessageVoiceNote -> "%d:%02d".format(content.voiceNote.duration / 60, content.voiceNote.duration % 60)
                // Media without a caption shows nothing under it; other contents a text preview.
                is TdApi.MessageVideo, is TdApi.MessageAnimation, is TdApi.MessageAudio, is TdApi.MessageVideoNote, is TdApi.MessageSticker -> formatted?.text.orEmpty()
                else -> formatted?.text ?: messagePreview(this)
            },
            entities = entitiesOf(formatted),
            media = mediaInfo.first,
            thumbnail = mediaInfo.second,
            albumId = mediaAlbumId,
            date = date,
            edited = editDate > 0,
            delivery = when {
                !isOutgoing || savedChat -> TelegramDelivery.None
                sendingState is TdApi.MessageSendingStatePending -> TelegramDelivery.Sending
                sendingState is TdApi.MessageSendingStateFailed -> TelegramDelivery.Failed
                id <= lastReadOutbox -> TelegramDelivery.Read
                else -> TelegramDelivery.Sent
            },
            replyAuthor = replied?.let { senderName(it.senderId) } ?: reply?.let { "Ответ" },
            replyText = replied?.let(::messagePreview) ?: reply?.content?.let { "Сообщение" },
            forwardedFrom = forwardInfo?.origin?.let(::originName),
            photo = photo?.photo?.toRef() ?: (content as? TdApi.MessageVoiceNote)?.voiceNote?.voice?.toRef(),
            reactions = interactionInfo?.reactions?.reactions.orEmpty().mapNotNull { reaction ->
                (reaction.type as? TdApi.ReactionTypeEmoji)?.let { Triple(it.emoji, reaction.totalCount, reaction.isChosen) }
            },
            photoAspect = photo?.let { if (it.height > 0) it.width.toFloat() / it.height else 0f } ?: 0f,
            fileName = (content as? TdApi.MessageDocument)?.document?.fileName ?: (content as? TdApi.MessageAudio)?.audio?.fileName,
            // Rough client-side hint; TDLib enforces the real rules and reports errors.
            canEdit = isOutgoing && sendingState == null && (content is TdApi.MessageText || content is TdApi.MessagePhoto || content is TdApi.MessageDocument || content is TdApi.MessageVideo || content is TdApi.MessageAnimation || content is TdApi.MessageAudio)
        )
    }

    private fun senderName(sender: TdApi.MessageSender?): String = when (sender) {
        is TdApi.MessageSenderUser -> if (sender.userId == users.myId()) "Вы" else users.user(sender.userId)?.displayName() ?: "Пользователь"
        is TdApi.MessageSenderChat -> chats.chat(sender.chatId)?.title ?: "Чат"
        else -> ""
    }

    private fun originName(origin: TdApi.MessageOrigin): String = when (origin) {
        is TdApi.MessageOriginUser -> users.user(origin.senderUserId)?.displayName() ?: "Пользователь"
        is TdApi.MessageOriginHiddenUser -> origin.senderName
        is TdApi.MessageOriginChat -> chats.chat(origin.senderChatId)?.title ?: "Чат"
        is TdApi.MessageOriginChannel -> chats.chat(origin.chatId)?.title ?: "Канал"
        else -> "—"
    }

    private companion object {
        const val PAGE_SIZE = 50
        const val PUBLISH_DELAY_MS = 32L
    }
}

/** Duration and size of a picked video, needed to send it as a video. */
data class VideoMeta(val durationSeconds: Int, val width: Int, val height: Int)

/** Text of the chat's draft, empty when there is none. */
internal fun draftText(chat: TdApi.Chat): String =
    (chat.draftMessage?.content as? TdApi.DraftMessageContentText)?.text?.text.orEmpty()

/** Largest photo size not wider than ~1280 px, falling back to the smallest available. */
internal fun bestPhoto(photo: TdApi.Photo): TdApi.PhotoSize? =
    photo.sizes.filter { it.width <= 1280 }.maxByOrNull { it.width } ?: photo.sizes.minByOrNull { it.width }
