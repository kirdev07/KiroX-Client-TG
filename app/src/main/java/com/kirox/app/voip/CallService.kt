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
    private var withCamera = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val voip = (application as KiroXApplication).telegram.voip
        val call = voip.call.value
        if (call == null || !call.phase.isLive()) {
            // Started with startForegroundService: it must go foreground once even to stop at once,
            // or Android reports a crash. The call can end between the start and this point.
            if (!started) runCatching { startForeground(CallNotifications.ONGOING_ID, CallNotifications.ending(this)) }
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        if (!started) {
            started = true
            if (!startInForeground(call)) { stopSelf(); return START_NOT_STICKY }
            scope.launch {
                voip.call.collect { current ->
                    if (current == null || !current.phase.isLive()) {
                        ServiceCompat.stopForeground(this@CallService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    } else {
                        // Camera turned on mid-call: the service needs the camera type to keep it in the background.
                        if (current.cameraOn && !withCamera && cameraGranted()) startInForeground(current)
                        else runCatching { CallNotifications.manager(this@CallService).notify(CallNotifications.ONGOING_ID, CallNotifications.ongoing(this@CallService, current)) }
                    }
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun cameraGranted() = androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA) ==
        android.content.pm.PackageManager.PERMISSION_GRANTED

    /**
     * The camera type only with the camera permission: Android 14 throws otherwise (a video call whose
     * camera prompt was denied). Falls back to a call without the camera type before giving up.
     */
    private fun startInForeground(call: TelegramActiveCall): Boolean {
        val notification = CallNotifications.ongoing(this, call)
        if (Build.VERSION.SDK_INT < 30) return runCatching { startForeground(CallNotifications.ONGOING_ID, notification) }.isSuccess
        val base = ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        if (call.cameraOn && cameraGranted() &&
            runCatching { ServiceCompat.startForeground(this, CallNotifications.ONGOING_ID, notification, base or ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA) }.isSuccess
        ) { withCamera = true; return true }
        return runCatching { ServiceCompat.startForeground(this, CallNotifications.ONGOING_ID, notification, base) }.isSuccess
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
        val telegram = (context.applicationContext as KiroXApplication).telegram
        when (intent.action) {
            ACTION_HANG_UP -> telegram.voip.hangUp()
            ACTION_LEAVE_GROUP -> telegram.groupCalls.leave()
        }
    }

    companion object {
        const val ACTION_HANG_UP = "com.kirox.app.voip.HANG_UP"
        const val ACTION_LEAVE_GROUP = "com.kirox.app.voip.LEAVE_GROUP"
    }
}

object CallNotifications {
    const val ONGOING_ID = 7001
    const val INCOMING_ID = 7002
    const val GROUP_ID = 7003
    private const val CHANNEL_INCOMING = "calls_incoming"
    private const val CHANNEL_ONGOING = "calls_ongoing"

    fun manager(context: Context): NotificationManager = context.getSystemService(NotificationManager::class.java)

    fun ensureChannels(context: Context) {
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

    /** Shown for an instant when the call ended before the service started. */
    fun ending(context: Context): Notification =
        NotificationCompat.Builder(context, CHANNEL_ONGOING)
            .setSmallIcon(android.R.drawable.sym_call_outgoing)
            .setContentTitle("Звонок завершён")
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .build()

    /** Placeholder while a video chat is being joined. */
    fun groupConnecting(context: Context): Notification =
        NotificationCompat.Builder(context, CHANNEL_ONGOING)
            .setSmallIcon(android.R.drawable.sym_call_outgoing)
            .setContentTitle("Видеочат")
            .setContentText("Подключение…")
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setContentIntent(openApp(context))
            .build()

    /** Ongoing video chat with a "Leave" button. */
    fun groupOngoing(context: Context, call: com.kirox.app.telegram.TelegramGroupCall): Notification =
        NotificationCompat.Builder(context, CHANNEL_ONGOING)
            .setSmallIcon(android.R.drawable.sym_call_outgoing)
            .setContentTitle(call.title.ifBlank { "Видеочат" })
            .setContentText(when (call.phase) {
                com.kirox.app.telegram.GroupCallPhase.Connected -> if (call.muted) "Видеочат · микрофон выключен" else "Видеочат · вы в эфире"
                else -> "Подключение к видеочату…"
            })
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setContentIntent(openApp(context))
            .addAction(0, "Выйти", PendingIntent.getBroadcast(
                context, 2, Intent(context, CallActionReceiver::class.java).setAction(CallActionReceiver.ACTION_LEAVE_GROUP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            ))
            .build()

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
            // Rings until answered or declined, not just once.
            .apply { flags = flags or Notification.FLAG_INSISTENT }

    private fun openApp(context: Context) = PendingIntent.getActivity(
        context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun hangUp(context: Context) = PendingIntent.getBroadcast(
        context, 1, Intent(context, CallActionReceiver::class.java).setAction(CallActionReceiver.ACTION_HANG_UP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
}
