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

enum class TelegramMessageKind { Text, Photo, Video, Animation, VideoNote, Voice, Audio, Document, Sticker, Other }

/** A message's main file and what is known about it before it is downloaded. */
data class TelegramMedia(
    val file: TelegramFileRef,
    /** Bytes; 0 when unknown. */
    val size: Long = 0,
    /** Seconds. */
    val duration: Int = 0,
    val width: Int = 0,
    val height: Int = 0,
    val mimeType: String = "",
    /** Music title, or the file name for documents. */
    val title: String = "",
    val performer: String = "",
    /** Sticker formats other than static WEBP are animated and drawn from their thumbnail for now. */
    val animatedSticker: Boolean = false,
    /** Voice note loudness, 0–31 per bar, as Telegram sends it; empty when unknown. */
    val waveform: List<Int> = emptyList(),
    /** TGS (Lottie) sticker; WEBM ones are video stickers. */
    val lottieSticker: Boolean = false
)

/** Formatting of a message text; offsets and lengths are in UTF-16 units, as in Kotlin strings. */
data class TelegramTextEntity(val offset: Int, val length: Int, val type: TelegramEntityType, val url: String? = null)

enum class TelegramEntityType { Bold, Italic, Underline, Strike, Code, Pre, Spoiler, Url, TextUrl, Mention, MentionName, Hashtag, Email, Phone, BotCommand, Quote }
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
    val canEdit: Boolean = false,
    /** Emoji reactions: emoji, count, whether this account chose it. */
    val reactions: List<Triple<String, Int, Boolean>> = emptyList(),
    /** File of a video, GIF, video note, music, document or sticker. */
    val media: TelegramMedia? = null,
    /** Preview image of [media], when Telegram provides one. */
    val thumbnail: TelegramFileRef? = null,
    val entities: List<TelegramTextEntity> = emptyList(),
    /** Messages of one album share this id; 0 when not in an album. */
    val albumId: Long = 0,
    /** Comments under a channel post; null when the post has no comments. */
    val commentCount: Int? = null,
    /** Message this one answers, in the same chat; 0 when none. */
    val replyToMessageId: Long = 0
)

/** A message found by the global search, with its chat for the result row. */
data class TelegramFoundMessage(val chatId: Long, val messageId: Long, val chatTitle: String, val text: String, val date: Int, val photo: TelegramFileRef?)

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
    val reachedStart: Boolean = false,
    /** End-to-end encrypted secret chat; its chat id is not the peer's user id. */
    val isSecret: Boolean = false,
    /** Unsent text saved in Telegram for this chat (synced with other devices), empty if none. */
    val draft: String = "",
    /** Newest pinned message, for the bar above the history. */
    val pinned: TelegramMessage? = null,
    val pinnedIds: Set<Long> = emptySet(),
    /** Loaded message the screen should scroll to; 0 when none (see TelegramMessageRepository.jumpTo). */
    val jumpTo: Long = 0,
    /** Set for a comment thread or forum topic: shown as the header title instead of the chat. */
    val threadTitle: String? = null,
    /** Forum supergroup opened as a whole: the screen shows its topics. */
    val isForum: Boolean = false
)

/** A topic of a forum supergroup. */
data class TelegramForumTopic(
    val id: Int,
    val title: String,
    val isClosed: Boolean,
    val isPinned: Boolean,
    val unreadCount: Int,
    val preview: String,
    val date: Int,
    val isGeneral: Boolean
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
    val caption: String,
    /** Video file of a video story. */
    val video: TelegramFileRef? = null,
    /** Seconds; shown time of a video story. */
    val durationS: Double = 0.0,
    val canReply: Boolean = false,
    val canDelete: Boolean = false,
    /** Own story: views (and who viewed) can be shown. */
    val canSeeViews: Boolean = false,
    val viewCount: Int = 0,
    /** Emoji reaction this account set, or null. */
    val chosenReaction: String? = null
)

/** Someone who viewed an own story, with the reaction they left. */
data class TelegramStoryViewer(val name: String, val photo: TelegramFileRef?, val date: Int, val reaction: String?)

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
    val birthday: String = "",
    val firstName: String = "",
    val lastName: String = ""
)

/** Where Telegram sent the login code. */
enum class CodeDestination { App, Sms, Call, Fragment, Other }
