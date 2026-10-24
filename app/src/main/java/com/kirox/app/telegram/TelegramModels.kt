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

data class TelegramChatItem(
    val id: Long,
    val title: String,
    val preview: String,
    val timestamp: Int,
    val unreadCount: Int,
    val isPinned: Boolean,
    val photoPath: String?,
    val archived: Boolean
)

data class TelegramUserProfile(
    val id: Long,
    val name: String,
    val username: String?,
    val phoneNumber: String,
    val photoPath: String?
)

