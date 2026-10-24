package com.kirox.app

import android.app.Application
import com.kirox.app.telegram.TelegramServices
import java.io.File

class KiroXApplication : Application() {
    /** The only owner of the TDLib client: one instance per process, never per screen. */
    val telegram: TelegramServices by lazy { TelegramServices(this) }

    override fun onCreate() {
        super.onCreate()
        removeLegacyDemoData()
    }

    /** DEMO mode (≤ 0.1.x) kept fake data locally; it is gone, so drop its files. Telegram data is untouched. */
    private fun removeLegacyDemoData() = Thread {
        deleteDatabase("kirox_demo.db")
        File(filesDir, "datastore/kirox_demo_settings.preferences_pb").delete()
    }.start()
}
