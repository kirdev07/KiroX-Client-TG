package com.kirox.app.ui.chat

import androidx.compose.runtime.Immutable

enum class MessageDirection { Incoming, Outgoing, System }
enum class MessageKind { Text, Photo, Video, Voice, Document }
enum class DeliveryState { Sending, Sent, Delivered, Read, Failed }
enum class ChatLoadState { Loading, Ready, Empty, Error, Offline }

@Immutable
data class QuoteUi(val author: String, val text: String)

@Immutable
data class ReactionUi(val emoji: String, val count: Int, val selected: Boolean = false)

@Immutable
data class MessageUi(
    val id: Long,
    val author: String,
    val direction: MessageDirection,
    val kind: MessageKind = MessageKind.Text,
    val text: String,
    val time: String,
    val delivery: DeliveryState = DeliveryState.Read,
    val quote: QuoteUi? = null,
    val forwardedFrom: String? = null,
    val reactions: List<ReactionUi> = emptyList(),
    val canEdit: Boolean = direction == MessageDirection.Outgoing,
    val canDelete: Boolean = true
)

@Immutable
data class ChatPeerUi(
    val id: Long,
    val name: String,
    val status: String,
    val initial: String,
    val hasAvatar: Boolean = false,
    val canVoiceCall: Boolean = false,
    val canVideoCall: Boolean = false
)

data class ChatAppearance(
    val incomingColor: Long = 0xFF1D1D1F,
    val outgoingColor: Long = 0xFF37383B,
    val textScale: Float = 1f,
    val bubbleRadiusDp: Float = 18f,
    val messageSpacingDp: Float = 4f,
    val inputColor: Long = 0xFF171719,
    val backgroundColor: Long = 0xFF000000,
    val animationSpeed: Float = 1f
)

/** Boundary for the future real implementation. Mapping TDLib objects belongs here, not in Compose UI. */
interface TdLibChatAdapter {
    suspend fun loadHistory(chatId: Long, beforeMessageId: Long?, limit: Int): Result<List<MessageUi>>
    suspend fun sendText(chatId: Long, text: String, replyToMessageId: Long?): Result<MessageUi>
}
