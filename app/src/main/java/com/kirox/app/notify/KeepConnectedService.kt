package com.kirox.app.notify

import androidx.core.content.edit
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import com.kirox.app.KiroXApplication
import com.kirox.app.MainActivity

/**
 * Optional "background connection": a quiet foreground service keeps the process, and so TDLib,
 * alive while KiroX is off screen. Messages then arrive over TDLib's own connection, without
 * waiting for a push; the price is a permanent low-priority notification.
 */
class KeepConnectedService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // startForeground comes first even when stopping: a service started with
        // startForegroundService that stops without it is reported as a crash.
        val promoted = promote()
        if (!isEnabled(this) || !promoted) {
            running = false
            if (promoted) stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        running = true
        (application as KiroXApplication).telegram.client.start()
        return START_STICKY
    }

    override fun onDestroy() {
        running = false
        super.onDestroy()
    }

    private fun promote(): Boolean {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Фоновое соединение", NotificationManager.IMPORTANCE_MIN).apply { setShowBadge(false) })
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK), PendingIntent.FLAG_IMMUTABLE)
        val notification = Notification.Builder(this, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle("KiroX на связи")
            .setContentText("Сообщения приходят сразу")
            .setOngoing(true)
            .setShowWhen(false)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setContentIntent(open)
            .build()
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                // Some vendor builds reject the explicit type; the manifest type then applies.
                runCatching { startForeground(ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE) }
                    .getOrElse { startForeground(ID, notification) }
            } else startForeground(ID, notification)
        }.isSuccess
    }

    companion object {
        private const val CHANNEL = "kirox_connection"
        private const val ID = 7301
        private const val PREFS = "kirox_push"
        private const val KEY = "keep_connected"

        /** True while the service really runs in the foreground; the setting alone does not keep TDLib alive. */
        @Volatile var running = false
            private set

        fun isEnabled(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY, false)

        fun setEnabled(context: Context, enabled: Boolean) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putBoolean(KEY, enabled) }
            if (enabled) start(context) else context.stopService(Intent(context, KeepConnectedService::class.java))
        }

        /** Allowed while KiroX is on screen and after boot; a refused start waits for the next launch. */
        fun start(context: Context) {
            if (!isEnabled(context)) return
            runCatching { context.startForegroundService(Intent(context, KeepConnectedService::class.java)) }
        }
    }
}

/** Brings the background connection back after a reboot or an app update. */
class KeepConnectedBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) KeepConnectedService.start(context)
    }
}
