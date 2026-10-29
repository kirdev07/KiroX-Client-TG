package com.kirox.app

import android.app.Application
import com.kirox.app.telegram.TelegramServices
import com.kirox.app.settings.KiroXSettingsRepository
import java.io.File

class KiroXApplication : Application() {
    /** The only owner of the TDLib client: one instance per process, never per screen. */
    val telegram: TelegramServices by lazy { TelegramServices(this) }
    val settings: KiroXSettingsRepository by lazy { KiroXSettingsRepository(this) }
    val updates: com.kirox.app.update.UpdateCenter by lazy { com.kirox.app.update.UpdateCenter(this) }
    val push: com.kirox.app.push.KiroxPush by lazy { com.kirox.app.push.KiroxPush(this, telegram.client) }

    override fun onCreate() {
        super.onCreate()
        CrashLog.install(this)
        removeLegacyDemoData()
        removeOldOutgoing()
        // The call engine is large: load it off the main thread, calls check NativeCall.available.
        Thread { com.kirox.app.voip.NativeCall.load(this) }.start()
        com.kirox.app.voip.CallController(this, telegram.voip, telegram.client).start()
        push // registers push channels once Telegram is signed in
        telegram.notifications // new-message notifications
        updates.checkDaily()
    }

    /**
     * Copies made for sending (picked files, recorded voice and video notes). TDLib uploads from
     * these paths, so they stay for three days to cover sending without network; then they go.
     */
    private fun removeOldOutgoing() = Thread {
        val dayAgo = System.currentTimeMillis() - 3 * 24 * 60 * 60 * 1000L
        listOf("outgoing", "voice", "notes", "shared").forEach { name ->
            File(cacheDir, name).listFiles()?.filter { it.lastModified() < dayAgo }?.forEach { it.deleteRecursively() }
        }
    }.start()

    /** DEMO mode (≤ 0.1.x) kept fake data locally; it is gone, so drop its files. Telegram data is untouched. */
    private fun removeLegacyDemoData() = Thread {
        deleteDatabase("kirox_demo.db")
        File(filesDir, "datastore/kirox_demo_settings.preferences_pb").delete()
    }.start()
}
