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
    /** Comment thread or forum topic shown instead of the whole chat; null for the chat itself. */
    private var topic: TdApi.MessageTopic? = null
    private var threadTitle: String? = null
    /** Root of a comment thread in the discussion group, for GetMessageThreadHistory. */
    private var threadRoot = 0L

    /** Pinned message ids of the open chat, newest last. */
    private val pinnedIds = sortedSetOf<Long>()
    /** Pinned messages not in the loaded history. */
    private val pinnedCache = HashMap<Long, TdApi.Message>()
    /** Message to scroll to once it is loaded, see [jumpTo]. */
    private var jumpTarget = 0L
    private var jumpPagesLeft = 0

    private val _history = MutableStateFlow(TelegramChatHistory())
    val history: StateFlow<TelegramChatHistory> = _history.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    /** Last failed action, for a toast; cleared by [clearError]. */
    val error: StateFlow<String?> = _error.asStateFlow()

    init { client.addUpdateHandler(::handle) }

    private var resumeChatId = 0L
    /** Draft of the open thread or topic (the chat's own draft is in Chat.draftMessage). */
    private var threadDraft = ""
    /** Bumped on every open/close, so a late GetMessageThread/GetForumTopic answer is ignored. */
    private var threadRequest = 0

    fun open(id: Long) = client.post {
        if (chatId == id && topic == null) return@post
        threadRequest++
        topic = null; threadTitle = null; threadRoot = 0; threadDraft = ""
        openLoaded(id)
    }

    /**
     * Opens the comments of channel post [postId]: they live in the channel's discussion group as a
     * thread, which TDLib resolves with GetMessageThread. Errors (comments off) go to [error].
     */
    fun openComments(channelChatId: Long, postId: Long) = client.post {
        // No chat id until the thread is resolved, so nothing (a draft, a read mark) lands in the channel.
        _history.value = TelegramChatHistory(threadTitle = "Комментарии")
        val request = ++threadRequest
        client.send(TdApi.GetMessageThread(channelChatId, postId)) { result ->
            if (request != threadRequest) return@send
            when (result) {
                is TdApi.MessageThreadInfo -> {
                    topic = TdApi.MessageTopicThread(result.messageThreadId)
                    threadTitle = "Комментарии"
                    threadRoot = result.messageThreadId
                    threadDraft = draftText(result.draftMessage)
                    openLoaded(result.chatId)
                }
                is TdApi.Error -> _error.value = humanError(result)
            }
        }
    }

    /** Opens one topic of a forum supergroup as its own conversation. */
    fun openTopic(chatId: Long, topicId: Int, title: String) = client.post {
        _history.value = TelegramChatHistory(threadTitle = title)
        val request = ++threadRequest
        // The topic's own draft comes with the topic; load it before the history is shown.
        client.send(TdApi.GetForumTopic(chatId, topicId)) { result ->
            if (request != threadRequest) return@send
            topic = TdApi.MessageTopicForum(topicId)
            threadTitle = title
            threadRoot = 0
            threadDraft = draftText((result as? TdApi.ForumTopic)?.draftMessage)
            openLoaded(chatId)
        }
    }

    /** Votes for options at [indices] (empty retracts the vote). */
    fun vote(messageId: Long, indices: List<Int>) = act { id -> TdApi.SetPollAnswer(id, messageId, indices.toIntArray()) }

    fun stopPoll(messageId: Long) = act { id -> TdApi.StopPoll(id, messageId, null) }

    /** Sends a poll; [correctOption] makes it a quiz with that right answer. */
    fun sendPoll(question: String, options: List<String>, anonymous: Boolean, multipleAnswers: Boolean, correctOption: Int?) = act { id ->
        val type: TdApi.InputPollType = if (correctOption != null) TdApi.InputPollTypeQuiz(intArrayOf(correctOption), TdApi.FormattedText("", emptyArray()), null)
        else TdApi.InputPollTypeRegular(false)
        TdApi.SendMessage(id, topic, null, null, null, TdApi.InputMessagePoll(
            TdApi.FormattedText(question, emptyArray()),
            options.map { TdApi.InputPollOption(TdApi.FormattedText(it, emptyArray()), null) }.toTypedArray(),
            null, null, anonymous, multipleAnswers && correctOption == null, true, false, emptyArray(), false, false, type, 0, 0, false
        ))
    }

    /** Presses a bot's inline button; returns the bot's answer (text to show, URL to open) or null. */
    suspend fun pressButton(messageId: Long, button: TelegramButton): Pair<String, String?>? {
        val id = chatId.takeIf { it != 0L } ?: return null
        val data = button.data ?: return null
        val answer = client.query(TdApi.GetCallbackQueryAnswer(id, messageId, TdApi.CallbackQueryPayloadData(data)))
        return answer.text.orEmpty() to answer.url?.takeIf { it.isNotBlank() }
    }

    /** Message text translated to [language] (ISO code, e.g. "ru") by Telegram. */
    suspend fun translate(messageId: Long, language: String): String? {
        val id = chatId.takeIf { it != 0L } ?: return null
        return runCatching { client.query(TdApi.TranslateMessageText(id, messageId, language, "")).text }.getOrNull()
    }

    /** Topics of a forum supergroup, pinned first as Telegram orders them. */
    suspend fun forumTopics(chatId: Long): List<TelegramForumTopic> = runCatching {
        client.query(TdApi.GetForumTopics(chatId, "", 0, 0, 0, FORUM_TOPICS_LIMIT)).topics.map { t ->
            TelegramForumTopic(
                id = t.info.forumTopicId, title = t.info.name, isClosed = t.info.isClosed, isPinned = t.isPinned,
                unreadCount = t.unreadCount, preview = messagePreview(t.lastMessage), date = t.lastMessage?.date ?: t.info.creationDate,
                isGeneral = t.info.isGeneral
            )
        }
    }.getOrDefault(emptyList())

    private fun openLoaded(id: Long) {
        if (chatId != 0L) client.send(TdApi.CloseChat(chatId))
        chatId = id
        messages.clear(); viewed.clear()
        loadingOlder = false; reachedStart = false
        pinnedIds.clear(); pinnedCache.clear(); jumpTarget = 0; jumpPagesLeft = 0
        lastReadOutbox = chats.chat(id)?.lastReadOutboxMessageId ?: 0
        _history.value = TelegramChatHistory(chatId = id)
        client.send(TdApi.OpenChat(id))
        requestOlder()
        loadPinned(id)
        publish()
    }

    /** All pinned messages of the chat (Telegram allows many); the newest one is shown in the bar. */
    private fun loadPinned(id: Long) {
        client.send(TdApi.SearchChatMessages(id, topic, "", null, 0, 0, PINNED_LIMIT, TdApi.SearchMessagesFilterPinned())) { result ->
            if (id != chatId || result !is TdApi.FoundChatMessages) return@send
            result.messages.filterNotNull().forEach { message ->
                pinnedIds += message.id
                // Keep the pinned message itself for the bar even when it is far up in the history.
                if (message.id !in messages) pinnedCache[message.id] = message
            }
            schedulePublish()
        }
    }
    /** Emoji reactions this chat allows on [messageId], without Premium-only ones; null on failure. */
    suspend fun availableReactions(messageId: Long): List<String>? {
        val id = chatId.takeIf { it != 0L } ?: return null
        return runCatching {
            val available = client.query(TdApi.GetMessageAvailableReactions(id, messageId, 8))
            (available.topReactions + available.recentReactions + available.popularReactions)
                .filter { !it.needsPremium }
                .mapNotNull { (it.type as? TdApi.ReactionTypeEmoji)?.emoji }
                .distinct()
                .take(8)
        }.getOrNull()
    }

    fun pin(messageId: Long) = act { id -> TdApi.PinChatMessage(id, messageId, false, false) }
    fun unpin(messageId: Long) = act { id -> TdApi.UnpinChatMessage(id, messageId) }

    /**
     * Scrolls the open chat to [messageId]: loads older pages until it is in the list (up to
     * [JUMP_PAGES] pages), then reports it in [TelegramChatHistory.jumpTo] for the screen to scroll.
     */
    fun jumpTo(messageId: Long) = client.post {
        if (chatId == 0L) return@post
        jumpTarget = messageId
        jumpPagesLeft = JUMP_PAGES
        continueJump()
    }

    /** The screen has scrolled to the target. */
    fun jumpDone() = client.post { jumpTarget = 0; schedulePublish() }

    private fun continueJump() {
        if (jumpTarget == 0L) return
        when {
            jumpTarget in messages -> schedulePublish()
            reachedStart || jumpPagesLeft <= 0 -> { jumpTarget = 0; _error.value = "Сообщение слишком далеко в истории"; schedulePublish() }
            else -> { jumpPagesLeft--; requestOlder() }
        }
    }

    /** Server-side search in the open chat, newest first; [fromMessageId] 0 for the first page. */
    suspend fun search(query: String, fromMessageId: Long = 0): Pair<List<TelegramMessage>, Long> {
        val id = chatId.takeIf { it != 0L } ?: return emptyList<TelegramMessage>() to 0L
        val found = client.query(TdApi.SearchChatMessages(id, topic, query, null, fromMessageId, 0, SEARCH_PAGE, null))
        val list = kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
            // Mapping reads repository state, so it runs on the TDLib dispatcher.
            client.post { continuation.resumeWith(Result.success(found.messages.filterNotNull().map { it.toModel() })) }
        }
        return list to found.nextFromMessageId
    }

    fun close(id: Long) = client.post {
        if (resumeChatId == id) { resumeChatId = 0; _history.value = TelegramChatHistory() }
        // A thread or topic of this chat belongs to its own screen, which leaves it with closeThread;
        // the chat screen going away during the transition must not close it.
        if (chatId != id || topic != null) return@post
        client.send(TdApi.CloseChat(id))
        chatId = 0; messages.clear(); viewed.clear()
        _history.value = TelegramChatHistory()
    }

    /** Leaves a comment thread or forum topic (its screen closed); a plain open chat is left alone. */
    fun closeThread() = client.post {
        threadRequest++
        if (topic == null || chatId == 0L) return@post
        client.send(TdApi.CloseChat(chatId))
        chatId = 0; topic = null; threadTitle = null; threadRoot = 0
        messages.clear(); viewed.clear(); pinnedIds.clear(); pinnedCache.clear()
        _history.value = TelegramChatHistory()
    }

    fun loadOlder() = client.post { requestOlder() }

    fun sendText(text: String, replyToMessageId: Long?) = act { id ->
        TdApi.SendMessage(id, topic, replyToMessageId?.let { TdApi.InputMessageReplyToMessage(it, null, 0, null) }, null, null, inputText(text))
    }

    fun sendPhoto(path: String, caption: String) = act { id ->
        TdApi.SendMessage(id, topic, null, null, null, TdApi.InputMessagePhoto().apply {
            photo = TdApi.InputPhoto().apply { photo = TdApi.InputFileLocal(path); addedStickerFileIds = IntArray(0) }
            this.caption = TdApi.FormattedText(caption, emptyArray())
        })
    }

    fun sendVoice(path: String, durationSeconds: Int, waveform: ByteArray) = act { id ->
        TdApi.SendMessage(id, topic, null, null, null, TdApi.InputMessageVoiceNote(
            TdApi.InputVoiceNote(TdApi.InputFileLocal(path), durationSeconds, waveform), TdApi.FormattedText("", emptyArray()), null
        ))
    }

    fun sendVideo(path: String, caption: String, durationSeconds: Int, width: Int, height: Int) = act { id ->
        TdApi.SendMessage(id, topic, null, null, null, TdApi.InputMessageVideo(
            TdApi.InputVideo(TdApi.InputFileLocal(path), null, null, 0, IntArray(0), durationSeconds, width, height, true),
            TdApi.FormattedText(caption, emptyArray()), false, null, false
        ))
    }

    fun sendDocument(path: String, caption: String) = act { id ->
        TdApi.SendMessage(id, topic, null, null, null, TdApi.InputMessageDocument().apply {
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
        TdApi.SendMessage(id, topic, replyToMessageId?.let { TdApi.InputMessageReplyToMessage(it, null, 0, null) }, null, null, content)
    }

    /** Sends a recorded video note (round video); [length] is the side of the square video. */
    fun sendVideoNote(path: String, durationSeconds: Int, length: Int) = act { id ->
        TdApi.SendMessage(id, topic, null, null, null, TdApi.InputMessageVideoNote(TdApi.InputVideoNote(TdApi.InputFileLocal(path), null, durationSeconds, length), null))
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
        TdApi.SendMessageAlbum(id, topic, null, null, contents.toTypedArray())
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
    fun saveDraft(chatId: Long, text: String, threadId: Long = 0, forumTopicId: Int = 0) = client.post {
        val target: TdApi.MessageTopic? = when {
            threadId != 0L -> TdApi.MessageTopicThread(threadId)
            forumTopicId != 0 -> TdApi.MessageTopicForum(forumTopicId)
            else -> null
        }
        if (target != null) {
            // The thread may already be closed (screen order on leaving): then its draft is not known
            // here, and the text is sent as is.
            val open = chatId == this.chatId && sameTopic(topic, target)
            if (open && threadDraft == text) return@post
            if (open) threadDraft = text
            client.send(TdApi.SetChatDraftMessage(chatId, target, draftOf(text))) { result -> if (result is TdApi.Error) _error.value = humanError(result) }
            return@post
        }
        val chat = chats.chat(chatId) ?: return@post
        if (text.isBlank() && chat.draftMessage == null) return@post
        if (draftText(chat) == text) return@post
        client.send(TdApi.SetChatDraftMessage(chatId, null, draftOf(text))) { result -> if (result is TdApi.Error) _error.value = humanError(result) }
    }

    private fun draftOf(text: String): TdApi.DraftMessage? = text.takeIf { it.isNotBlank() }?.let {
        TdApi.DraftMessage(null, (System.currentTimeMillis() / 1000).toInt(), TdApi.DraftMessageContentText(TdApi.FormattedText(it, emptyArray()), null), 0, null)
    }

    private fun sameTopic(a: TdApi.MessageTopic?, b: TdApi.MessageTopic): Boolean = when (a) {
        is TdApi.MessageTopicThread -> b is TdApi.MessageTopicThread && a.messageThreadId == b.messageThreadId
        is TdApi.MessageTopicForum -> b is TdApi.MessageTopicForum && a.forumTopicId == b.forumTopicId
        else -> false
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
        val from = messages.keys.firstOrNull() ?: 0L
        val request: TdApi.Function<TdApi.Messages> = when (val current = topic) {
            is TdApi.MessageTopicThread -> TdApi.GetMessageThreadHistory(id, threadRoot, from, 0, PAGE_SIZE)
            is TdApi.MessageTopicForum -> TdApi.GetForumTopicHistory(id, current.forumTopicId, from, 0, PAGE_SIZE)
            else -> TdApi.GetChatHistory(id, from, 0, PAGE_SIZE, false)
        }
        client.send(request) { result ->
            if (id != chatId) return@send
            loadingOlder = false
            when (result) {
                is TdApi.Messages -> {
                    val page = result.messages.filterNotNull()
                    if (page.isEmpty()) reachedStart = true
                    page.forEach { messages[it.id] = it; pinnedCache.remove(it.id) }
                }
                is TdApi.Error -> _error.value = humanError(result)
            }
            schedulePublish()
            continueJump()
        }
    }

    private fun handle(update: TdApi.Object) {
        when (update) {
            is TdApi.UpdateNewMessage -> if (update.message.chatId == chatId && inTopic(update.message)) { messages[update.message.id] = update.message; schedulePublish() }
            is TdApi.UpdateMessageSendSucceeded -> if (update.message.chatId == chatId) {
                messages.remove(update.oldMessageId); messages[update.message.id] = update.message; schedulePublish()
            }
            is TdApi.UpdateMessageSendFailed -> if (update.message.chatId == chatId) {
                messages.remove(update.oldMessageId); messages[update.message.id] = update.message
                _error.value = humanError(update.error)
                schedulePublish()
            }
            is TdApi.UpdateMessageContent -> if (update.chatId == chatId) messages[update.messageId]?.let { it.content = update.newContent; schedulePublish() }
            is TdApi.UpdateMessageEdited -> if (update.chatId == chatId) messages[update.messageId]?.let { it.editDate = update.editDate; it.replyMarkup = update.replyMarkup; schedulePublish() }
            is TdApi.UpdatePoll -> messages.values.firstOrNull { (it.content as? TdApi.MessagePoll)?.poll?.id == update.poll.id }?.let { message ->
                (message.content as TdApi.MessagePoll).poll = update.poll
                schedulePublish()
            }
            is TdApi.UpdateMessageInteractionInfo -> if (update.chatId == chatId) messages[update.messageId]?.let { it.interactionInfo = update.interactionInfo; schedulePublish() }
            is TdApi.UpdateDeleteMessages -> if (update.chatId == chatId && update.isPermanent) {
                update.messageIds.forEach { messages.remove(it) }; schedulePublish()
            }
            is TdApi.UpdateChatReadOutbox -> if (update.chatId == chatId) { lastReadOutbox = update.lastReadOutboxMessageId; schedulePublish() }
            // Keeps the open topic's draft current when it changes on another device.
            is TdApi.UpdateForumTopic -> if (update.chatId == chatId && (topic as? TdApi.MessageTopicForum)?.forumTopicId == update.forumTopicId) {
                threadDraft = draftText(update.draftMessage)
            }
            is TdApi.UpdateMessageIsPinned -> if (update.chatId == chatId) {
                if (update.isPinned) {
                    pinnedIds += update.messageId
                    if (update.messageId !in messages) client.send(TdApi.GetMessage(update.chatId, update.messageId)) { result ->
                        if (result is TdApi.Message && result.chatId == chatId) { pinnedCache[result.id] = result; schedulePublish() }
                    }
                } else { pinnedIds -= update.messageId; pinnedCache.remove(update.messageId) }
                schedulePublish()
            }
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

    /** Whether [message] belongs to the open thread or topic (always true for a whole chat). */
    private fun inTopic(message: TdApi.Message): Boolean = when (val current = topic) {
        null -> true
        is TdApi.MessageTopicThread -> (message.topicId as? TdApi.MessageTopicThread)?.messageThreadId == current.messageThreadId
        is TdApi.MessageTopicForum -> (message.topicId as? TdApi.MessageTopicForum)?.forumTopicId == current.forumTopicId
        else -> true
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
            draft = if (topic != null) threadDraft else chat?.let(::draftText).orEmpty(),
            threadId = (topic as? TdApi.MessageTopicThread)?.messageThreadId ?: 0L,
            forumTopicId = (topic as? TdApi.MessageTopicForum)?.forumTopicId ?: 0,
            pinned = pinnedIds.lastOrNull()?.let { messages[it] ?: pinnedCache[it] }?.toModel(),
            pinnedIds = pinnedIds.toSet(),
            jumpTo = jumpTarget.takeIf { it in messages } ?: 0L,
            threadTitle = threadTitle,
            // The newest keyboard a bot sent (or removed) in the open chat.
            replyKeyboard = messages.values.reversed().asSequence().filter { !it.isOutgoing }.mapNotNull { replyKeyboardOf(it.replyMarkup) }.firstOrNull().orEmpty(),
            isForum = topic == null && chats.isForum(id)
        )
        markViewed(id)
    }

    /** Opening a chat reads it: report newly shown incoming messages once. */
    private fun markViewed(id: Long) {
        val fresh = messages.values.filter { !it.isOutgoing && viewed.add(it.id) }.map { it.id }
        if (fresh.isNotEmpty()) client.send(TdApi.ViewMessages(id, fresh.toLongArray(), when (topic) {
            is TdApi.MessageTopicThread -> TdApi.MessageSourceMessageThreadHistory()
            is TdApi.MessageTopicForum -> TdApi.MessageSourceForumTopicHistory()
            else -> TdApi.MessageSourceChatHistory()
        }, true))
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
            poll = pollOf(content),
            buttons = inlineButtonsOf(replyMarkup),
            commentCount = interactionInfo?.replyInfo?.replyCount?.takeIf { isChannelPost },
            replyToMessageId = reply?.takeIf { it.chatId == 0L || it.chatId == chatId }?.messageId ?: 0L,
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
        const val PINNED_LIMIT = 100
        /** Up to 1000 messages back; further is rare and better reached by search. */
        const val JUMP_PAGES = 20
        const val SEARCH_PAGE = 50
        const val FORUM_TOPICS_LIMIT = 100
    }
}

/** Duration and size of a picked video, needed to send it as a video. */
data class VideoMeta(val durationSeconds: Int, val width: Int, val height: Int)

/** Text of the chat's draft, empty when there is none. */
internal fun draftText(chat: TdApi.Chat): String = draftText(chat.draftMessage)

internal fun draftText(draft: TdApi.DraftMessage?): String =
    (draft?.content as? TdApi.DraftMessageContentText)?.text?.text.orEmpty()

/** Largest photo size not wider than ~1280 px, falling back to the smallest available. */
internal fun bestPhoto(photo: TdApi.Photo): TdApi.PhotoSize? =
    photo.sizes.filter { it.width <= 1280 }.maxByOrNull { it.width } ?: photo.sizes.minByOrNull { it.width }
