package com.kirox.app.voip

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.kirox.app.KiroXApplication
import com.kirox.app.MainActivity
import com.kirox.app.telegram.CallPhase
import com.kirox.app.telegram.TelegramActiveCall
import com.kirox.app.telegram.isLive
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Keeps a call alive while the app is in the background or the screen is off, with an
 * ongoing "call in progress" notification. Started when the user places or answers a call
 * (the app is in the foreground then) and stops itself when the call ends.
 */
class CallService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var started = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val voip = (application as KiroXApplication).telegram.voip
        val call = voip.call.value
        if (call == null || !call.phase.isLive()) { stopSelf(); return START_NOT_STICKY }
        if (!started) {
            started = true
            startInForeground(call)
            scope.launch {
                voip.call.collect { current ->
                    if (current == null || !current.phase.isLive()) {
                        ServiceCompat.stopForeground(this@CallService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    } else {
                        CallNotifications.manager(this@CallService).notify(CallNotifications.ONGOING_ID, CallNotifications.ongoing(this@CallService, current))
                    }
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun startInForeground(call: TelegramActiveCall) {
        var type = 0
        if (Build.VERSION.SDK_INT >= 30) {
            type = ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            if (call.cameraOn) type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
        }
        ServiceCompat.startForeground(this, CallNotifications.ONGOING_ID, CallNotifications.ongoing(this, call), type)
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        fun start(context: Context) {
            runCatching { androidx.core.content.ContextCompat.startForegroundService(context, Intent(context, CallService::class.java)) }
        }
    }
}

/** "Decline" and "Hang up" buttons in call notifications. */
class CallActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_HANG_UP) (context.applicationContext as KiroXApplication).telegram.voip.hangUp()
    }

    companion object { const val ACTION_HANG_UP = "com.kirox.app.voip.HANG_UP" }
}

object CallNotifications {
    const val ONGOING_ID = 7001
    const val INCOMING_ID = 7002
    private const val CHANNEL_INCOMING = "calls_incoming"
    private const val CHANNEL_ONGOING = "calls_ongoing"

    fun manager(context: Context): NotificationManager = context.getSystemService(NotificationManager::class.java)

    fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val incoming = NotificationChannel(CHANNEL_INCOMING, "Входящие звонки", NotificationManager.IMPORTANCE_HIGH).apply {
            setSound(
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE),
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE).build()
            )
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 600, 900, 600, 900)
        }
        val ongoing = NotificationChannel(CHANNEL_ONGOING, "Текущий звонок", NotificationManager.IMPORTANCE_LOW)
        manager(context).createNotificationChannels(listOf(incoming, ongoing))
    }

    fun ongoing(context: Context, call: TelegramActiveCall): Notification {
        val text = when (call.phase) {
            is CallPhase.Active -> if (call.isVideo) "Идёт видеозвонок" else "Идёт звонок"
            is CallPhase.Calling -> "Вызов…"
            else -> "Соединение…"
        }
        return NotificationCompat.Builder(context, CHANNEL_ONGOING)
            .setSmallIcon(android.R.drawable.sym_call_outgoing)
            .setContentTitle(call.name)
            .setContentText(text)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setContentIntent(openApp(context))
            .apply { (call.phase as? CallPhase.Active)?.let { setWhen(it.sinceMillis).setUsesChronometer(true) } }
            .addAction(0, "Завершить", hangUp(context))
            .build()
    }

    /** Full-screen incoming call when the app is not on screen. */
    fun incoming(context: Context, call: TelegramActiveCall): Notification =
        NotificationCompat.Builder(context, CHANNEL_INCOMING)
            .setSmallIcon(android.R.drawable.sym_call_incoming)
            .setContentTitle(call.name)
            .setContentText(if (call.isVideo) "Входящий видеозвонок kiroX" else "Входящий звонок kiroX")
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setOngoing(true)
            .setAutoCancel(false)
            .setContentIntent(openApp(context))
            .setFullScreenIntent(openApp(context), true)
            .addAction(0, "Отклонить", hangUp(context))
            .addAction(0, "Открыть", openApp(context))
            .build()

    private fun openApp(context: Context) = PendingIntent.getActivity(
        context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun hangUp(context: Context) = PendingIntent.getBroadcast(
        context, 1, Intent(context, CallActionReceiver::class.java).setAction(CallActionReceiver.ACTION_HANG_UP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
}
