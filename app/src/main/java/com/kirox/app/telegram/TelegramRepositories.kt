package com.kirox.app.telegram

class TelegramAuthRepository(private val manager: TdlibClientManager) {
    val state = manager.authState
    val busy = manager.busy
    val error = manager.error
    fun phone(value: String) = manager.submitPhone(value)
    fun code(value: String) = manager.submitCode(value)
    fun password(value: String) = manager.submitPassword(value)
    fun email(value: String) = manager.submitEmail(value)
    fun emailCode(value: String) = manager.submitEmailCode(value)
    fun register(first: String, last: String) = manager.register(first, last)
    fun resend() = manager.resendCode()
    fun clearError() = manager.clearError()
}

class TelegramChatRepository(manager: TdlibClientManager) {
    val mainChats = manager.mainChats
    val archiveChats = manager.archiveChats
    val profile = manager.profile
}

