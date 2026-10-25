package com.kirox.app.telegram

sealed interface TelegramAuthState {
    data object Initializing : TelegramAuthState
    data object WaitPhoneNumber : TelegramAuthState
    data class WaitCode(val phoneNumber: String, val length: Int, val canResend: Boolean, val timeout: Int, val sentTo: CodeDestination = CodeDestination.Other) : TelegramAuthState
    data class WaitPassword(val hint: String, val hasRecoveryEmail: Boolean, val recoveryEmailPattern: String) : TelegramAuthState
    data object WaitEmailAddress : TelegramAuthState
    data class WaitEmailCode(val length: Int, val emailPattern: String = "") : TelegramAuthState
    data class WaitOtherDevice(val link: String) : TelegramAuthState
    data class WaitRegistration(val termsOfService: String, val minUserAge: Int) : TelegramAuthState
    data object Ready : TelegramAuthState
    data object Closing : TelegramAuthState
    data class Blocked(val message: String) : TelegramAuthState
}

enum class TelegramConnection { WaitingForNetwork, ConnectingToProxy, Connecting, Updating, Ready }

/** A Telegram file reference for lazy download; [localPath] is set when already on disk. */
data class TelegramFileRef(val id: Int, val localPath: String?)

enum class LastMessageState { None, Sent, Read }

data class TelegramChatItem(
    val id: Long,
    val title: String,
    val preview: String,
    val timestamp: Int,
    val unreadCount: Int,
    val unreadMentionCount: Int = 0,
    val isMarkedUnread: Boolean = false,
    val isPinned: Boolean,
    val isMuted: Boolean = false,
    val draft: String? = null,
    val outgoingState: LastMessageState = LastMessageState.None,
    val photo: TelegramFileRef?,
    val archived: Boolean,
    /** The user's own chat (Saved Messages). */
    val isSaved: Boolean = false
)

sealed interface TelegramChatListKey {
    data object Main : TelegramChatListKey
    data object Archive : TelegramChatListKey
    data class Folder(val id: Int) : TelegramChatListKey
}

data class TelegramFolder(val id: Int, val title: String)

data class TelegramChatListState(
    val chats: List<TelegramChatItem> = emptyList(),
    val loading: Boolean = false,
    val complete: Boolean = false
)

data class TelegramChatLists(
    val main: TelegramChatListState = TelegramChatListState(),
    val archive: TelegramChatListState = TelegramChatListState(),
    val folders: List<TelegramFolder> = emptyList(),
    val folderChats: Map<Int, TelegramChatListState> = emptyMap()
) {
    operator fun get(key: TelegramChatListKey): TelegramChatListState = when (key) {
        TelegramChatListKey.Main -> main
        TelegramChatListKey.Archive -> archive
        is TelegramChatListKey.Folder -> folderChats[key.id] ?: TelegramChatListState()
    }
}

sealed interface TelegramUserStatus {
    data object Online : TelegramUserStatus
    data class Offline(val wasOnline: Int) : TelegramUserStatus
    data object Recently : TelegramUserStatus
    data object LastWeek : TelegramUserStatus
    data object LastMonth : TelegramUserStatus
    data object LongAgo : TelegramUserStatus
}

data class TelegramContact(
    val id: Long,
    val name: String,
    val username: String?,
    val status: TelegramUserStatus,
    val photo: TelegramFileRef?,
    /** Digits only, without "+"; empty when hidden. */
    val phone: String = ""
)

enum class TelegramMessageKind { Text, Photo, Video, Voice, Document, Other }
enum class TelegramDelivery { None, Sending, Sent, Read, Failed }

data class TelegramMessage(
    val id: Long,
    val chatId: Long,
    val senderName: String,
    val isOutgoing: Boolean,
    val kind: TelegramMessageKind,
    val text: String,
    val date: Int,
    val edited: Boolean,
    val delivery: TelegramDelivery,
    val replyAuthor: String? = null,
    val replyText: String? = null,
    val forwardedFrom: String? = null,
    val photo: TelegramFileRef? = null,
    val photoAspect: Float = 0f,
    val fileName: String? = null,
    val canEdit: Boolean = false
)

enum class TelegramPeerKind { User, Bot, Group, Channel, Saved, Unknown }

data class TelegramChatHistory(
    val chatId: Long = 0,
    val title: String = "",
    val photo: TelegramFileRef? = null,
    val peerKind: TelegramPeerKind = TelegramPeerKind.Unknown,
    val peerStatus: TelegramUserStatus? = null,
    val memberCount: Int = 0,
    /** Oldest first. */
    val messages: List<TelegramMessage> = emptyList(),
    val loadingOlder: Boolean = false,
    val reachedStart: Boolean = false
)

enum class TelegramCallKind { Incoming, Outgoing, Missed, Unanswered }

data class TelegramCall(
    val messageId: Long,
    val chatId: Long,
    val peerName: String,
    val photo: TelegramFileRef?,
    val kind: TelegramCallKind,
    val isVideo: Boolean,
    val durationSeconds: Int,
    val date: Int
)

data class TelegramCallsState(
    val calls: List<TelegramCall> = emptyList(),
    val loading: Boolean = false,
    val complete: Boolean = false
)

data class TelegramStoryChat(
    val chatId: Long,
    val title: String,
    val photo: TelegramFileRef?,
    val storyIds: List<Int>,
    val hasUnread: Boolean,
    val isSelf: Boolean
)

data class TelegramStory(
    val id: Int,
    val chatId: Long,
    val date: Int,
    val image: TelegramFileRef?,
    val aspect: Float,
    val isVideo: Boolean,
    val caption: String
)

data class TelegramUserProfile(
    val id: Long,
    val name: String,
    val username: String?,
    val phoneNumber: String,
    val bio: String = "",
    val photo: TelegramFileRef?,
    val isPremium: Boolean = false,
    val isVerified: Boolean = false,
    /** "d.MM" or "d.MM.yyyy", empty when hidden or not set. */
    val birthday: String = ""
)

/** Where Telegram sent the login code. */
enum class CodeDestination { App, Sms, Call, Fragment, Other }
