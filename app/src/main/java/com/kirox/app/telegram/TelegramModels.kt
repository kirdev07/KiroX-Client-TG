package com.kirox.app.telegram

sealed interface TelegramAuthState {
    data object Initializing : TelegramAuthState
    data object WaitPhoneNumber : TelegramAuthState
    data class WaitCode(val phoneNumber: String, val length: Int, val canResend: Boolean, val timeout: Int) : TelegramAuthState
    data class WaitPassword(val hint: String, val hasRecoveryEmail: Boolean, val recoveryEmailPattern: String) : TelegramAuthState
    data object WaitEmailAddress : TelegramAuthState
    data class WaitEmailCode(val length: Int) : TelegramAuthState
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
    val archived: Boolean
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
    val photo: TelegramFileRef?
)

data class TelegramUserProfile(
    val id: Long,
    val name: String,
    val username: String?,
    val phoneNumber: String,
    val bio: String = "",
    val photo: TelegramFileRef?
)
