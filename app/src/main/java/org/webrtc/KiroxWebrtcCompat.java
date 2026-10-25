/*
 * Small helpers that replace app-specific utilities the Telegram build of the WebRTC Java
 * sources relied on (logging, main-thread posting).
 */
package org.webrtc;

import android.os.Handler;
import android.os.Looper;

final class KiroxWebrtcCompat {
    private static final String TAG = "KiroxWebrtc";
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private KiroxWebrtcCompat() {}

    static void e(Throwable error) { Logging.e(TAG, "", error); }
    static void d(String message) { Logging.d(TAG, message); }
    static void runOnUiThread(Runnable runnable) { MAIN.post(runnable); }
    static void cancelRunOnUiThread(Runnable runnable) { MAIN.removeCallbacks(runnable); }
}
