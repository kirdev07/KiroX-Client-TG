package com.kirox.app.voip

import android.content.Intent

/**
 * Consent for screen capture (the result of MediaProjectionManager.createScreenCaptureIntent) handed
 * from the video chat screen to the capturer, and the way back when the system ends the capture
 * (the user stops it from the status bar, or the screen locks on some devices).
 */
object ScreenShare {
    /** Read once by the capturer; each consent is good for one capture on Android 14+. */
    @Volatile var permissionData: Intent? = null

    /** Set by the video chat repository: stops sharing when the system ends the capture. */
    @Volatile var onStopped: (() -> Unit)? = null

    fun onSystemStopped() {
        permissionData = null
        onStopped?.invoke()
    }
}
