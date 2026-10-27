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
    val stickers = services.stickers
    val autoDownload = services.autoDownload
    /** Chat id for a t.me / tg: link, or null when Telegram does not resolve it. */
    suspend fun resolveLink(link: TelegramLink): Long? = services.client.resolveLink(link)
    init { services.client.start() }
}
