package com.kirox.app

import android.app.Application
import com.kirox.app.telegram.TelegramServices

class KiroXApplication : Application() {
    /** The only owner of the TDLib client: one instance per process, never per screen. */
    val telegram: TelegramServices by lazy { TelegramServices(this) }
}
