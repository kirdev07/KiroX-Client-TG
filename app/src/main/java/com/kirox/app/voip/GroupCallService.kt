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
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Keeps a video chat going with KiroX in the background: Android 11+ gives the microphone to a
 * backgrounded app only from a foreground service of the microphone type. Started when the user
 * joins (KiroX is on screen then); stops itself when the video chat ends or is left. While the screen
 * is shared it also carries the media projection type.
 */
class GroupCallService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var started = false
    private var withCamera = false
    private var withScreen = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val calls = (application as KiroXApplication).telegram.groupCalls
        // startForeground must follow startForegroundService within seconds, even before the join
        // is published; a placeholder notification covers that moment.
        if (!enterForeground(calls.call.value)) {
            calls.leave()
            stopSelf()
            return START_NOT_STICKY
        }
        if (!started) {
            started = true
            scope.launch {
                var seen = calls.call.value != null
                launch {
                    // A join that never shows up (failed before it was published) must not keep the service.
                    kotlinx.coroutines.delay(NO_CALL_GRACE_MS)
                    if (!seen) finish()
                }
                // Only what the notification shows: speaking levels change many times a second.
                calls.call.map { it?.let { c -> Shown(c.title, c.phase, c.muted, c.cameraOn, c.screenSharing) } }.distinctUntilChanged().collect { shown ->
                    val current = calls.call.value
                    when {
                        shown == null || current == null -> if (seen) finish()
                        shown.phase is GroupCallPhase.Failed -> finish()
                        else -> {
                            seen = true
                            if (current.screenSharing != withScreen) {
                                // Android 14+: the media projection type must be in place before capture starts.
                                val ok = enterForeground(current)
                                if (current.screenSharing) { if (ok) calls.screenForegroundReady() else calls.stopScreenShare() }
                            } else if (current.cameraOn != withCamera) {
                                if (!enterForeground(current)) { calls.setCamera(false) }
                            } else {
                                CallNotifications.manager(this@GroupCallService).notify(CallNotifications.GROUP_ID, CallNotifications.groupOngoing(this@GroupCallService, current))
                            }
                        }
                    }
                }
            }
        }
        return START_NOT_STICKY
    }

    private data class Shown(val title: String, val phase: GroupCallPhase, val muted: Boolean, val camera: Boolean, val screen: Boolean)

    private fun finish() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    /**
     * Enters (or updates) the foreground state; the camera type is added while the camera is on.
     * Returns false when Android refuses (e.g. started from the background).
     */
    private fun enterForeground(call: com.kirox.app.telegram.TelegramGroupCall?): Boolean {
        withCamera = call?.cameraOn == true
        withScreen = call?.screenSharing == true
        var type = 0
        if (Build.VERSION.SDK_INT >= 30) {
            type = ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            if (withCamera) type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
        }
        if (withScreen) type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
        val notification = if (call != null) CallNotifications.groupOngoing(this, call) else CallNotifications.groupConnecting(this)
        return runCatching { ServiceCompat.startForeground(this, CallNotifications.GROUP_ID, notification, type) }.isSuccess
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val NO_CALL_GRACE_MS = 10_000L

        fun start(context: Context) {
            runCatching { androidx.core.content.ContextCompat.startForegroundService(context, Intent(context, GroupCallService::class.java)) }
        }
    }
}
