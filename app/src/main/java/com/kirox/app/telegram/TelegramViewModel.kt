package com.kirox.app.telegram

import android.app.Application
import androidx.lifecycle.AndroidViewModel

class TelegramViewModel(application: Application) : AndroidViewModel(application) {
    private val manager = TdlibClientManager(application)
    val auth = TelegramAuthRepository(manager)
    val chats = TelegramChatRepository(manager)
    init { manager.start() }
}
