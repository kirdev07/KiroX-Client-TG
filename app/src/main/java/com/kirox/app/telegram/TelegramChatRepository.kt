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
    private val wanted = HashSet<TelegramChatListKey>()
    private val failures = HashMap<TelegramChatListKey, Int>()
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

    /** Chats added to folder [id] by hand (not the ones it takes by type). */
    suspend fun folderChatIds(id: Int): List<Long> = client.query(TdApi.GetChatFolder(id)).includedChatIds.toList()

    /** Renames folder [id] and sets its hand-picked chats; type rules and pinned chats stay as they are. */
    suspend fun editFolder(id: Int, name: String, chatIds: List<Long>) {
        val folder = client.query(TdApi.GetChatFolder(id))
        folder.name = TdApi.ChatFolderName(TdApi.FormattedText(name, emptyArray()), false)
        folder.includedChatIds = chatIds.toLongArray()
        folder.pinnedChatIds = folder.pinnedChatIds.filter { it in chatIds }.toLongArray()
        client.query(TdApi.EditChatFolder(id, folder))
    }

    /** New basic group with [userIds]; returns its chat id. */
    suspend fun createGroup(title: String, userIds: List<Long>, description: String = ""): Long {
        val id = client.query(TdApi.CreateNewBasicGroupChat(userIds.toLongArray(), title, 0)).chatId
        if (description.isNotBlank()) runCatching { client.query(TdApi.SetChatDescription(id, description)) }
        return id
    }

    /** Why [username] cannot be a new public link, or null when it is free. */
    suspend fun publicLinkProblem(username: String): String? =
        when (runCatching { client.query(TdApi.CheckChatUsername(0, username)) }.getOrNull()) {
            is TdApi.CheckChatUsernameResultOk -> null
            is TdApi.CheckChatUsernameResultUsernameInvalid -> "Ссылка может содержать латиницу, цифры и «_», от 5 символов"
            is TdApi.CheckChatUsernameResultUsernameOccupied, is TdApi.CheckChatUsernameResultUsernamePurchasable -> "Эта ссылка уже занята"
            is TdApi.CheckChatUsernameResultPublicChatsTooMany -> "Слишком много публичных каналов и групп"
            else -> "Не удалось проверить ссылку"
        }

    /** New broadcast channel, public under t.me/[username] when it is given; returns its chat id. */
    suspend fun createChannel(title: String, description: String, username: String? = null): Long {
        val chat = client.query(TdApi.CreateNewSupergroupChat(title, false, true, description, null, 0, false))
        val supergroupId = (chat.type as? TdApi.ChatTypeSupergroup)?.supergroupId
        if (!username.isNullOrBlank() && supergroupId != null) client.query(TdApi.SetSupergroupUsername(supergroupId, username))
        return chat.id
    }

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
    /** New messages disappear [seconds] after they are read (secret chats) or sent; 0 turns it off. */
    suspend fun setAutoDelete(chatId: Long, seconds: Int) { client.query(TdApi.SetChatMessageAutoDeleteTime(chatId, seconds)) }

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

    /** Supergroup ids that are forums (topics). Dispatcher-confined. */
    private val forumSupergroups = HashSet<Long>()

    /** Whether [chatId] is a forum supergroup; call on the TDLib dispatcher. */
    internal fun isForum(chatId: Long): Boolean = (chatsById[chatId]?.type as? TdApi.ChatTypeSupergroup)?.supergroupId?.let { it in forumSupergroups } == true

    /**
     * Chats matching [query]: the user's own chats first (title and username), then chats found on
     * the server and public channels, groups and bots by username. Each appears once.
     */
    suspend fun search(query: String): List<TelegramChatItem> {
        val local = runCatching { client.query(TdApi.SearchChats(query, null, SEARCH_LIMIT)).chatIds.toList() }.getOrDefault(emptyList())
        val server = runCatching { client.query(TdApi.SearchChatsOnServer(query, null, SEARCH_LIMIT)).chatIds.toList() }.getOrDefault(emptyList())
        // Public search needs a few letters; Telegram rejects very short queries.
        val public = if (query.trim().length >= 4) runCatching { client.query(TdApi.SearchPublicChats(query, null)).chatIds.toList() }.getOrDefault(emptyList()) else emptyList()
        return items((local + server + public).distinct())
    }

    /** Username, about text and phone shown on a chat's profile; empty strings when there are none. */
    suspend fun profileInfo(chatId: Long): TelegramProfileInfo {
        val type = onDispatcher { chatsById[chatId]?.type }
        return runCatching {
            when (type) {
                is TdApi.ChatTypePrivate, is TdApi.ChatTypeSecret -> {
                    val userId = (type as? TdApi.ChatTypePrivate)?.userId ?: (type as TdApi.ChatTypeSecret).userId
                    val user = client.query(TdApi.GetUser(userId))
                    val full = runCatching { client.query(TdApi.GetUserFullInfo(userId)) }.getOrNull()
                    TelegramProfileInfo(user.usernames?.activeUsernames?.firstOrNull().orEmpty(), full?.bio?.text.orEmpty(), user.phoneNumber.orEmpty())
                }
                is TdApi.ChatTypeSupergroup -> {
                    val group = client.query(TdApi.GetSupergroup(type.supergroupId))
                    val full = runCatching { client.query(TdApi.GetSupergroupFullInfo(type.supergroupId)) }.getOrNull()
                    TelegramProfileInfo(group.usernames?.activeUsernames?.firstOrNull().orEmpty(), full?.description.orEmpty(), "")
                }
                is TdApi.ChatTypeBasicGroup -> TelegramProfileInfo("", runCatching { client.query(TdApi.GetBasicGroupFullInfo(type.basicGroupId)).description }.getOrNull().orEmpty(), "")
                else -> TelegramProfileInfo()
            }
        }.getOrDefault(TelegramProfileInfo())
    }

    /** Members of a group with their role; null for chats without a member list (users, channels you do not run). */
    suspend fun members(chatId: Long): List<TelegramMember>? {
        val type = onDispatcher { chatsById[chatId]?.type }
        val raw: List<TdApi.ChatMember> = runCatching {
            when (type) {
                is TdApi.ChatTypeBasicGroup -> client.query(TdApi.GetBasicGroupFullInfo(type.basicGroupId)).members.toList()
                is TdApi.ChatTypeSupergroup -> if (type.isChannel) return null else client.query(TdApi.GetSupergroupMembers(type.supergroupId, TdApi.SupergroupMembersFilterRecent(), 0, MEMBERS_LIMIT)).members.toList()
                else -> return null
            }
        }.getOrElse { return null }
        return raw.mapNotNull { member ->
            val userId = (member.memberId as? TdApi.MessageSenderUser)?.userId ?: return@mapNotNull null
            val user = runCatching { client.query(TdApi.GetUser(userId)) }.getOrNull() ?: return@mapNotNull null
            val role = when (member.status) {
                is TdApi.ChatMemberStatusCreator -> "Создатель"
                is TdApi.ChatMemberStatusAdministrator -> "Администратор"
                else -> ""
            }
            TelegramMember(userId, listOf(user.firstName, user.lastName).filter { it.isNotBlank() }.joinToString(" ").ifBlank { "Удалённый аккаунт" }, user.profilePhoto?.small?.toRef(), user.status.toStatus(), role)
        }.sortedWith(compareBy({ if (it.role.isBlank()) 1 else 0 }, { it.name }))
    }

    /** Newest photos/videos, files, voice notes or links of a chat, for the profile tabs. */
    suspend fun sharedMedia(chatId: Long, kind: SharedMediaKind): List<TelegramSharedItem> {
        val filter: TdApi.SearchMessagesFilter = when (kind) {
            SharedMediaKind.Media -> TdApi.SearchMessagesFilterPhotoAndVideo()
            SharedMediaKind.Files -> TdApi.SearchMessagesFilterDocument()
            SharedMediaKind.Voice -> TdApi.SearchMessagesFilterVoiceNote()
            SharedMediaKind.Links -> TdApi.SearchMessagesFilterUrl()
        }
        val found = runCatching { client.query(TdApi.SearchChatMessages(chatId, null, "", null, 0, 0, SHARED_LIMIT, filter)) }.getOrNull() ?: return emptyList()
        return found.messages.filterNotNull().map { message ->
            when (val content = message.content) {
                is TdApi.MessagePhoto -> TelegramSharedItem(message.id, message.date, thumb = content.photo.sizes.minByOrNull { kotlin.math.abs(it.width - 320) }?.photo?.toRef())
                is TdApi.MessageVideo -> TelegramSharedItem(message.id, message.date, thumb = content.video.thumbnail?.file?.toRef(), subtitle = durationText(content.video.duration), isVideo = true)
                is TdApi.MessageDocument -> TelegramSharedItem(message.id, message.date, title = content.document.fileName.ifBlank { "Файл" }, subtitle = sizeText(content.document.document.size))
                is TdApi.MessageVoiceNote -> TelegramSharedItem(message.id, message.date, title = "Голосовое сообщение", subtitle = durationText(content.voiceNote.duration))
                is TdApi.MessageText -> TelegramSharedItem(message.id, message.date, title = Regex("https?://\\S+|t\\.me/\\S+|[\\w.-]+\\.[a-z]{2,}/\\S*").find(content.text.text)?.value ?: content.text.text.take(80), subtitle = content.text.text.take(120))
                else -> TelegramSharedItem(message.id, message.date, title = messagePreview(message))
            }
        }
    }

    private fun durationText(seconds: Int) = "%d:%02d".format(seconds / 60, seconds % 60)
    private fun sizeText(bytes: Long) = when { bytes >= 1_048_576 -> "%.1f МБ".format(bytes / 1_048_576.0); bytes >= 1024 -> "${bytes / 1024} КБ"; else -> "$bytes Б" }

    /** Messages matching [query] in all chats of the main list, newest first. */
    suspend fun searchMessages(query: String): List<TelegramFoundMessage> {
        val found = runCatching { client.query(TdApi.SearchMessages(TdApi.ChatListMain(), query, "", SEARCH_LIMIT, null, null, 0, 0)) }.getOrNull() ?: return emptyList()
        return onDispatcher {
            found.messages.filterNotNull().mapNotNull { message ->
                val chat = chatsById[message.chatId] ?: return@mapNotNull null
                TelegramFoundMessage(message.chatId, message.id, chat.title, messagePreview(message), message.date, chat.photo?.small?.toRef())
            }
        }
    }

    /** Chat list items for [ids], in that order; TDLib reports every chat before returning its id. */
    private suspend fun items(ids: List<Long>): List<TelegramChatItem> = onDispatcher {
        ids.mapNotNull { id ->
            chatsById[id]?.let { chat ->
                val position = chat.positions.firstOrNull { it.list is TdApi.ChatListMain } ?: chat.positions.firstOrNull() ?: TdApi.ChatPosition(TdApi.ChatListMain(), 0, false, null)
                toChatItem(chat, position, archived = position.list is TdApi.ChatListArchive)
            }
        }
    }

    /** Runs [block] on the TDLib dispatcher, where chatsById may be read. */
    private suspend fun <T> onDispatcher(block: () -> T): T = kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
        client.post { continuation.resumeWith(runCatching(block)) }
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
                    // A background pause keeps the list on screen; it is refreshed on Ready.
                    // Logout and account switch start from nothing.
                    if (!client.paused) { chatsById.clear(); folders = emptyList(); forumSupergroups.clear() }
                    loading.clear(); complete.clear(); wanted.clear(); failures.clear()
                    schedulePublish()
                }
            }
            is TdApi.UpdateNewChat -> { chatsById[update.chat.id] = update.chat; schedulePublish() }
            is TdApi.UpdateSupergroup -> if (update.supergroup.isForum) forumSupergroups += update.supergroup.id else forumSupergroups -= update.supergroup.id
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
        if (key in complete) return
        // Asked again while a page is loading: load the next one right after it.
        if (key in loading) { wanted += key; return }
        loading += key
        schedulePublish()
        val sent = client.send(TdApi.LoadChats(key.toTd(), PAGE_SIZE)) { result ->
            loading -= key
            val again = wanted.remove(key)
            when {
                // 404: every chat of the list is already known.
                result is TdApi.Error && result.code == 404 -> complete += key
                result is TdApi.Error -> {
                    // Network or flood errors: try again a little later, a few times.
                    val tries = (failures[key] ?: 0) + 1
                    failures[key] = tries
                    if (tries <= 3) client.post(2_000L * tries) { requestPage(key) }
                }
                else -> {
                    failures.remove(key)
                    // TDLib may answer with a short page (only pinned chats): fill the screen anyway.
                    if (again || countIn(key) < MIN_FILL) client.post { requestPage(key) }
                }
            }
            schedulePublish()
        }
        if (!sent) loading -= key
    }

    private fun countIn(key: TelegramChatListKey): Int {
        val list = key.toTd()
        return chatsById.values.count { chat -> chat.positions.any { sameChatList(it.list, list) && it.order != 0L } }
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
        const val MIN_FILL = 30
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
        isSaved = saved,
        kind = when (val type = chat.type) {
            is TdApi.ChatTypeSupergroup -> if (type.isChannel) TelegramChatKind.Channel else TelegramChatKind.Group
            is TdApi.ChatTypeBasicGroup -> TelegramChatKind.Group
            else -> TelegramChatKind.Private
        }
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

data class TelegramProfileInfo(val username: String = "", val about: String = "", val phone: String = "")

enum class SharedMediaKind(val title: String) { Media("Медиа"), Files("Файлы"), Voice("Голосовые"), Links("Ссылки") }

data class TelegramSharedItem(
    val messageId: Long,
    val date: Int,
    val thumb: TelegramFileRef? = null,
    val title: String = "",
    val subtitle: String = "",
    val isVideo: Boolean = false
)

private const val SHARED_LIMIT = 60

data class TelegramMember(val userId: Long, val name: String, val photo: TelegramFileRef?, val status: TelegramUserStatus, val role: String)

private const val MEMBERS_LIMIT = 200
