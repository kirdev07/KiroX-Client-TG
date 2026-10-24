package com.kirox.app.telegram

import android.content.Context
import java.io.File

class TelegramSessionManager(context: Context) {
    private val root = File(context.filesDir, "tdlib/session-primary")
    val databaseDirectory = File(root, "database").apply { mkdirs() }.absolutePath
    val filesDirectory = File(root, "files").apply { mkdirs() }.absolutePath
}

