package com.kirox.app.voip

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.kirox.app.telegram.CallPhase
import com.kirox.app.telegram.TelegramVoipRepository
import com.kirox.app.telegram.isLive
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Ties calls to the rest of the system: an incoming call while the app is off screen becomes a
 * full-screen notification, and a call in progress runs in [CallService] so it survives leaving the app.
 */
class CallController(private val app: Application, private val voip: TelegramVoipRepository, private val client: com.kirox.app.telegram.TelegramClient) {
    private var pauseJob: kotlinx.coroutines.Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var startedActivities = 0
    private val appVisible get() = startedActivities > 0

    fun start() {
        // With the background connection on, TDLib stays connected and is never paused.
        client.isIdle = { !appVisible && voip.call.value == null && (app as com.kirox.app.KiroXApplication).telegram.groupCalls.call.value == null && !com.kirox.app.notify.KeepConnectedService.running }
        CallNotifications.ensureChannels(app)
        app.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityStarted(activity: Activity) { startedActivities++; com.kirox.app.notify.AppVisibility.visible = true; update() }
            override fun onActivityStopped(activity: Activity) { startedActivities--; com.kirox.app.notify.AppVisibility.visible = startedActivities > 0; update() }
            override fun onActivityCreated(activity: Activity, state: Bundle?) {}
            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
        scope.launch { voip.call.collect { update() } }
    }

    private fun update() {
        val call = voip.call.value
        CallMedia.sync(app, call)
        // Background without a call: close TDLib after a grace period so Telegram sends pushes.
        if (appVisible) { pauseJob?.cancel(); pauseJob = null; client.start(); com.kirox.app.notify.KeepConnectedService.start(app) }
        else if (call == null && pauseJob?.isActive != true) pauseJob = scope.launch { kotlinx.coroutines.delay(BACKGROUND_GRACE_MS); client.pauseIfIdle() }
        val notifications = CallNotifications.manager(app)
        if (call?.phase == CallPhase.Incoming && !appVisible) {
            runCatching { notifications.notify(CallNotifications.INCOMING_ID, CallNotifications.incoming(app, call)) }
        } else {
            notifications.cancel(CallNotifications.INCOMING_ID)
        }
        // A foreground service may only be started while the app is on screen.
        if (call != null && call.phase.isLive() && call.phase != CallPhase.Incoming && appVisible) CallService.start(app)
    }
}

/** How long TDLib stays connected after the app leaves the screen. */
private const val BACKGROUND_GRACE_MS = 30_000L
