package com.kirox.app.telegram

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.drinkless.tdlib.TdApi

/**
 * Chat lists (main, archive, folders) built from TDLib updates. All state is touched only on the
 * TDLib dispatcher; bursts of updates are coalesced into one publish per frame-ish window.
 */
class TelegramChatRepository(private val client: TelegramClient) {
    private val chatsById = HashMap<Long, TdApi.Chat>()
    private var folders: List<TelegramFolder> = emptyList()
    private val loading = HashSet<TelegramChatListKey>()
    private val complete = HashSet<TelegramChatListKey>()
    private var publishScheduled = false
    private var myId = 0L

    private val _lists = MutableStateFlow(TelegramChatLists())
    val lists: StateFlow<TelegramChatLists> = _lists.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    /** Last failed chat action, for a toast; cleared by [clearError]. */
    val error: StateFlow<String?> = _error.asStateFlow()

    init { client.addUpdateHandler(::handle) }

    /** Dispatcher-only lookup for other repositories. */
    internal fun chat(id: Long): TdApi.Chat? = chatsById[id]

    /** Id of the private chat with [userId], creating it locally if needed. */
    suspend fun privateChatId(userId: Long): Long = client.query(TdApi.CreatePrivateChat(userId, false)).id

    fun setArchived(chatId: Long, archived: Boolean) =
        act(TdApi.AddChatToList(chatId, if (archived) TdApi.ChatListArchive() else TdApi.ChatListMain()))

    fun setPinned(key: TelegramChatListKey, chatId: Long, pinned: Boolean) = act(TdApi.ToggleChatIsPinned(key.toTd(), chatId, pinned))

    fun markUnread(chatId: Long) = act(TdApi.ToggleChatIsMarkedAsUnread(chatId, true))

    /** End-to-end encrypted chat with [userId]; works only between this device and the peer's device. */
    suspend fun createSecretChat(userId: Long): Long = client.query(TdApi.CreateNewSecretChat(userId)).id

    /** New folder with the given chats; returns its id. */
    suspend fun createFolder(name: String, chatIds: List<Long>): Int {
        val folder = TdApi.ChatFolder(
            TdApi.ChatFolderName(TdApi.FormattedText(name, emptyArray()), false), null, -1, false,
            LongArray(0), chatIds.toLongArray(), LongArray(0), false, false, false, false, false, false, false, false
        )
        return client.query(TdApi.CreateChatFolder(folder)).id
    }

    /** Removes the folder only; its chats stay in the main list. */
    suspend fun deleteFolder(id: Int) { client.query(TdApi.DeleteChatFolder(id, LongArray(0))) }

    /** New basic group with [userIds]; returns its chat id. */
    suspend fun createGroup(title: String, userIds: List<Long>): Long =
        client.query(TdApi.CreateNewBasicGroupChat(userIds.toLongArray(), title, 0)).chatId

    /** New broadcast channel; returns its chat id. */
    suspend fun createChannel(title: String, description: String): Long =
        client.query(TdApi.CreateNewSupergroupChat(title, false, true, description, null, 0, false)).id

    suspend fun isMuted(chatId: Long): Boolean =
        client.query(TdApi.GetChat(chatId)).notificationSettings.let { !it.useDefaultMuteFor && it.muteFor > 0 }

    /** Mutes "forever" (Telegram treats a year and more as forever) or unmutes the chat. */
    suspend fun setMuted(chatId: Long, muted: Boolean) {
        val settings = client.query(TdApi.GetChat(chatId)).notificationSettings
        settings.useDefaultMuteFor = false
        settings.muteFor = if (muted) MUTE_FOREVER_S else 0
        client.query(TdApi.SetChatNotificationSettings(chatId, settings))
    }

    /** Deletes the chat history for this account only; the chat stays in the list. */
    suspend fun clearHistory(chatId: Long) { client.query(TdApi.DeleteChatHistory(chatId, false, false)) }

    /** Private chats: delete for this account and remove from the list. Groups and channels: leave. */
    suspend fun deleteChat(chatId: Long) {
        when (client.query(TdApi.GetChat(chatId)).type) {
            is TdApi.ChatTypePrivate, is TdApi.ChatTypeSecret -> client.query(TdApi.DeleteChatHistory(chatId, true, false))
            else -> client.query(TdApi.LeaveChat(chatId))
        }
    }

    fun markRead(chatId: Long) = client.post {
        val chat = chatsById[chatId] ?: return@post
        chat.lastMessage?.let { act(TdApi.ViewMessages(chatId, longArrayOf(it.id), TdApi.MessageSourceChatList(), true)) }
        if (chat.isMarkedAsUnread) act(TdApi.ToggleChatIsMarkedAsUnread(chatId, false))
    }

    fun clearError() { _error.value = null }

    /** Chats matching [query] among all chats TDLib knows (title and username), main-list style items. */
    suspend fun search(query: String): List<TelegramChatItem> = kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
        val sent = client.send(TdApi.SearchChats(query, null, SEARCH_LIMIT)) { result ->
            // Runs on the TDLib dispatcher, where chatsById may be read.
            val items = (result as? TdApi.Chats)?.chatIds?.toList()?.mapNotNull { id ->
                chatsById[id]?.let { chat ->
                    val position = chat.positions.firstOrNull { it.list is TdApi.ChatListMain } ?: chat.positions.firstOrNull() ?: TdApi.ChatPosition(TdApi.ChatListMain(), 0, false, null)
                    toChatItem(chat, position, archived = position.list is TdApi.ChatListArchive)
                }
            }.orEmpty()
            continuation.resumeWith(Result.success(items))
        }
        if (!sent) continuation.resumeWith(Result.success(emptyList()))
    }

    private fun act(function: TdApi.Function<*>) {
        client.send(function) { result -> if (result is TdApi.Error) _error.value = humanError(result) }
    }

    /** Requests the next page of [key]; safe to call repeatedly from UI (e.g. on scroll to the end). */
    fun loadMore(key: TelegramChatListKey) = client.post { requestPage(key) }

    private fun handle(update: TdApi.Object) {
        when (update) {
            is TdApi.UpdateAuthorizationState -> when (update.authorizationState) {
                is TdApi.AuthorizationStateReady -> {
                    requestPage(TelegramChatListKey.Main)
                    requestPage(TelegramChatListKey.Archive)
                }
                is TdApi.AuthorizationStateClosed -> {
                    chatsById.clear(); folders = emptyList(); loading.clear(); complete.clear()
                    schedulePublish()
                }
            }
            is TdApi.UpdateNewChat -> { chatsById[update.chat.id] = update.chat; schedulePublish() }
            is TdApi.UpdateOption -> if (update.name == "my_id") { myId = (update.value as? TdApi.OptionValueInteger)?.value ?: 0L; schedulePublish() }
            is TdApi.UpdateChatFolders -> {
                folders = update.chatFolders.map { TelegramFolder(it.id, it.name?.text?.text.orEmpty()) }
                schedulePublish()
            }
            is TdApi.UpdateChatTitle -> mutate(update.chatId) { it.title = update.title }
            is TdApi.UpdateChatPhoto -> mutate(update.chatId) { it.photo = update.photo }
            is TdApi.UpdateChatLastMessage -> mutate(update.chatId) { chat ->
                chat.lastMessage = update.lastMessage
                update.positions.forEach { chat.positions = applyChatPosition(chat.positions, it) }
            }
            is TdApi.UpdateChatPosition -> mutate(update.chatId) { it.positions = applyChatPosition(it.positions, update.position) }
            is TdApi.UpdateChatDraftMessage -> mutate(update.chatId) { chat ->
                chat.draftMessage = update.draftMessage
                update.positions.forEach { chat.positions = applyChatPosition(chat.positions, it) }
            }
            is TdApi.UpdateChatReadInbox -> mutate(update.chatId) { it.unreadCount = update.unreadCount; it.lastReadInboxMessageId = update.lastReadInboxMessageId }
            is TdApi.UpdateChatReadOutbox -> mutate(update.chatId) { it.lastReadOutboxMessageId = update.lastReadOutboxMessageId }
            is TdApi.UpdateChatUnreadMentionCount -> mutate(update.chatId) { it.unreadMentionCount = update.unreadMentionCount }
            is TdApi.UpdateMessageMentionRead -> mutate(update.chatId) { it.unreadMentionCount = update.unreadMentionCount }
            is TdApi.UpdateChatIsMarkedAsUnread -> mutate(update.chatId) { it.isMarkedAsUnread = update.isMarkedAsUnread }
            is TdApi.UpdateChatNotificationSettings -> mutate(update.chatId) { it.notificationSettings = update.notificationSettings }
        }
    }

    private fun requestPage(key: TelegramChatListKey) {
        if (key in loading || key in complete) return
        loading += key
        schedulePublish()
        client.send(TdApi.LoadChats(key.toTd(), PAGE_SIZE)) { result ->
            loading -= key
            // 404: every chat of the list is already known.
            if (result is TdApi.Error && result.code == 404) complete += key
            schedulePublish()
        }
    }

    private fun mutate(id: Long, block: (TdApi.Chat) -> Unit) {
        chatsById[id]?.let { block(it); schedulePublish() }
    }

    private fun schedulePublish() {
        if (publishScheduled) return
        publishScheduled = true
        client.post(PUBLISH_DELAY_MS) {
            publishScheduled = false
            publish()
        }
    }

    private fun publish() {
        _lists.value = TelegramChatLists(
            main = state(TelegramChatListKey.Main),
            archive = state(TelegramChatListKey.Archive),
            folders = folders,
            folderChats = folders.associate { it.id to state(TelegramChatListKey.Folder(it.id)) }
        )
    }

    private fun state(key: TelegramChatListKey): TelegramChatListState {
        val list = key.toTd()
        val chats = chatsById.values.mapNotNull { chat ->
            val position = chat.positions.firstOrNull { sameChatList(it.list, list) }?.takeIf { it.order != 0L } ?: return@mapNotNull null
            toChatItem(chat, position, archived = key == TelegramChatListKey.Archive, myId = myId) to position.order
        }.sortedWith(compareByDescending<Pair<TelegramChatItem, Long>> { it.second }.thenByDescending { it.first.id }).map { it.first }
        return TelegramChatListState(chats, loading = key in loading, complete = key in complete)
    }

    private companion object {
        const val PAGE_SIZE = 50
        const val PUBLISH_DELAY_MS = 32L
        const val SEARCH_LIMIT = 50
    }
}

internal fun TelegramChatListKey.toTd(): TdApi.ChatList = when (this) {
    TelegramChatListKey.Main -> TdApi.ChatListMain()
    TelegramChatListKey.Archive -> TdApi.ChatListArchive()
    is TelegramChatListKey.Folder -> TdApi.ChatListFolder(id)
}

internal fun toChatItem(chat: TdApi.Chat, position: TdApi.ChatPosition, archived: Boolean, myId: Long = 0L): TelegramChatItem {
    val saved = myId != 0L && (chat.type as? TdApi.ChatTypePrivate)?.userId == myId
    val last = chat.lastMessage
    val draft = (chat.draftMessage?.content as? TdApi.DraftMessageContentText)?.text?.text?.takeIf { it.isNotBlank() }
    val notifications = chat.notificationSettings
    return TelegramChatItem(
        id = chat.id,
        title = if (saved) "Избранное" else chat.title.orEmpty(),
        preview = messagePreview(last),
        timestamp = maxOf(last?.date ?: 0, if (draft != null) chat.draftMessage?.date ?: 0 else 0),
        unreadCount = chat.unreadCount,
        unreadMentionCount = chat.unreadMentionCount,
        isMarkedUnread = chat.isMarkedAsUnread,
        isPinned = position.isPinned,
        // Chats on the scope default are shown unmuted; scope defaults arrive with notifications work.
        isMuted = notifications != null && !notifications.useDefaultMuteFor && notifications.muteFor > 0,
        draft = draft,
        outgoingState = when {
            saved || last == null || !last.isOutgoing -> LastMessageState.None
            last.id <= chat.lastReadOutboxMessageId -> LastMessageState.Read
            else -> LastMessageState.Sent
        },
        photo = if (saved) null else chat.photo?.small?.toRef(),
        archived = archived,
        isSaved = saved
    )
}

internal fun TdApi.File.toRef() = TelegramFileRef(id, local?.path?.takeIf { local.isDownloadingCompleted && it.isNotBlank() })

internal fun messagePreview(message: TdApi.Message?): String = when (val content = message?.content) {
    is TdApi.MessageText -> content.text.text
    is TdApi.MessagePhoto -> content.caption.text.ifBlank { "Фотография" }
    is TdApi.MessageVideo -> content.caption.text.ifBlank { "Видео" }
    is TdApi.MessageDocument -> content.caption.text.ifBlank { "Документ" }
    is TdApi.MessageVoiceNote -> content.caption.text.ifBlank { "Голосовое сообщение" }
    is TdApi.MessageVideoNote -> "Видеосообщение"
    is TdApi.MessageSticker -> "Стикер ${content.sticker.emoji}".trim()
    is TdApi.MessageAnimation -> content.caption.text.ifBlank { "GIF" }
    is TdApi.MessageAudio -> content.caption.text.ifBlank { "Аудио" }
    is TdApi.MessageCall -> "Звонок"
    is TdApi.MessageContact -> "Контакт"
    is TdApi.MessageLocation -> "Геопозиция"
    is TdApi.MessagePoll -> "Опрос"
    null -> ""
    else -> "Сообщение"
}

private const val MUTE_FOREVER_S = 400 * 24 * 60 * 60
