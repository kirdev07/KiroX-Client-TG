package com.kirox.app.voip

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import com.kirox.app.KiroXApplication
import com.kirox.app.telegram.GroupCallPhase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Keeps a video chat going with KiroX in the background: Android 11+ gives the microphone to a
 * backgrounded app only from a foreground service of the microphone type. Started when the user
 * joins (KiroX is on screen then); stops itself when the video chat ends or is left.
 */
class GroupCallService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var started = false
    private var withCamera = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val calls = (application as KiroXApplication).telegram.groupCalls
        val call = calls.call.value
        if (call == null || call.phase is GroupCallPhase.Failed) { stopSelf(); return START_NOT_STICKY }
        foreground(call)
        if (!started) {
            started = true
            scope.launch {
                calls.call.collect { current ->
                    if (current == null || current.phase is GroupCallPhase.Failed) {
                        ServiceCompat.stopForeground(this@GroupCallService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    } else if (current.cameraOn != withCamera) {
                        foreground(current)
                    } else {
                        CallNotifications.manager(this@GroupCallService).notify(CallNotifications.GROUP_ID, CallNotifications.groupOngoing(this@GroupCallService, current))
                    }
                }
            }
        }
        return START_NOT_STICKY
    }

    /**
     * Enters (or updates) the foreground state; the camera type is added while the camera is on.
     * Turning the camera on happens with KiroX on screen, when Android allows the camera type.
     */
    private fun foreground(call: com.kirox.app.telegram.TelegramGroupCall) {
        withCamera = call.cameraOn
        var type = 0
        if (Build.VERSION.SDK_INT >= 30) {
            type = ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            if (withCamera) type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
        }
        runCatching { ServiceCompat.startForeground(this, CallNotifications.GROUP_ID, CallNotifications.groupOngoing(this, call), type) }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        fun start(context: Context) {
            runCatching { androidx.core.content.ContextCompat.startForegroundService(context, Intent(context, GroupCallService::class.java)) }
        }
    }
}
