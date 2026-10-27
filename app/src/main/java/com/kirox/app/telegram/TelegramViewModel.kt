package com.kirox.app.telegram

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.kirox.app.KiroXApplication

class TelegramViewModel(application: Application) : AndroidViewModel(application) {
    private val services = (application as KiroXApplication).telegram
    val auth = services.auth
    val chats = services.chats
    val users = services.users
    val files = services.files
    val messages = services.messages
    val calls = services.calls
    val voip = services.voip
    val stories = services.stories
    val connection = services.connection
    val activity = services.activity
    init { services.client.start() }
}
