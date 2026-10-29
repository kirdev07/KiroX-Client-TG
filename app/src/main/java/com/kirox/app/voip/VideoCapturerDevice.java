package com.kirox.app.voip;

import android.content.Intent;
import android.media.projection.MediaProjection;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.DisplayMetrics;

import org.webrtc.Camera2Enumerator;
import org.webrtc.CameraEnumerator;
import org.webrtc.CameraVideoCapturer;
import org.webrtc.CapturerObserver;
import org.webrtc.ContextUtils;
import org.webrtc.EglBase;
import org.webrtc.ScreenCapturerAndroid;
import org.webrtc.SurfaceTextureHelper;
import org.webrtc.VideoCapturer;

/**
 * Camera or screen source for the call engine (screen: video chat screen sharing, through
 * MediaProjection with the consent from {@link ScreenShare}). tgcalls creates one per capture interface and drives it through
 * {@link #init}, {@link #onStateChanged} and {@link #onDestroy}; frames go to the native observer.
 * Every camera call runs on one background thread.
 */
@SuppressWarnings("unused") // Called from native code.
public final class VideoCapturerDevice {
    private static final int CAPTURE_WIDTH = 1280;
    private static final int CAPTURE_HEIGHT = 720;
    private static final int CAPTURE_FPS = 30;
    private static final int STATE_ACTIVE = 2;
    /** Screen sharing: long side of the sent picture and its frame rate. */
    private static final int SCREEN_MAX_SIDE = 1280;
    private static final int SCREEN_FPS = 15;

    private static EglBase sharedEgl;

    private final HandlerThread thread = new HandlerThread("KiroxCamera");
    private final Handler handler;
    private final boolean screencast;
    private VideoCapturer capturer;
    private SurfaceTextureHelper textureHelper;
    private long nativePtr;
    private String deviceId = "front";
    private boolean capturing;

    public VideoCapturerDevice(boolean screencast) {
        this.screencast = screencast;
        thread.start();
        handler = new Handler(thread.getLooper());
    }

    /** Shared EGL context for the camera and the hardware codecs. */
    public static synchronized EglBase.Context getSharedEGLContext() {
        if (sharedEgl == null) sharedEgl = EglBase.create();
        return sharedEgl.getEglBaseContext();
    }

    /** Screen sharing is not supported. */
    public static MediaProjection getMediaProjection() {
        return null;
    }

    private void init(long ptr, String id) {
        handler.post(() -> {
            nativePtr = ptr;
            boolean switchCamera = !screencast && capturer instanceof CameraVideoCapturer && !id.equals(deviceId);
            deviceId = id;
            if (switchCamera) ((CameraVideoCapturer) capturer).switchCamera(null, cameraName());
        });
    }

    private void onStateChanged(long ptr, int state) {
        handler.post(() -> {
            if (ptr != nativePtr) return;
            if (state == STATE_ACTIVE) start(); else stop();
        });
    }

    private void onAspectRatioRequested(float aspectRatio) {
    }

    private void onDestroy() {
        handler.post(() -> {
            stop();
            if (capturer != null) { capturer.dispose(); capturer = null; }
            if (textureHelper != null) { textureHelper.dispose(); textureHelper = null; }
            nativePtr = 0;
            thread.quitSafely();
        });
    }

    private void start() {
        if (capturing || nativePtr == 0) return;
        if (screencast) { startScreen(); return; }
        if (capturer == null) {
            String name = cameraName();
            if (name == null) return;
            CameraEnumerator enumerator = new Camera2Enumerator(ContextUtils.getApplicationContext());
            capturer = enumerator.createCapturer(name, null);
            textureHelper = SurfaceTextureHelper.create("KiroxCameraTexture", getSharedEGLContext());
            CapturerObserver observer = (CapturerObserver) nativeGetJavaVideoCapturerObserver(nativePtr);
            capturer.initialize(textureHelper, ContextUtils.getApplicationContext(), observer);
        }
        capturer.startCapture(CAPTURE_WIDTH, CAPTURE_HEIGHT, CAPTURE_FPS);
        capturing = true;
    }

    /** Screen capture with the user's consent; the system or the user may stop it at any time. */
    private void startScreen() {
        if (capturer == null) {
            Intent consent = ScreenShare.INSTANCE.getPermissionData();
            if (consent == null) return;
            capturer = new ScreenCapturerAndroid(consent, new MediaProjection.Callback() {
                @Override public void onStop() { ScreenShare.INSTANCE.onSystemStopped(); }
            });
            textureHelper = SurfaceTextureHelper.create("KiroxScreenTexture", getSharedEGLContext());
            CapturerObserver observer = (CapturerObserver) nativeGetJavaVideoCapturerObserver(nativePtr);
            capturer.initialize(textureHelper, ContextUtils.getApplicationContext(), observer);
        }
        DisplayMetrics metrics = ContextUtils.getApplicationContext().getResources().getDisplayMetrics();
        int width = metrics.widthPixels;
        int height = metrics.heightPixels;
        float scale = Math.min(1f, (float) SCREEN_MAX_SIDE / Math.max(width, height));
        // Even sizes: video encoders need them.
        int w = Math.max(2, Math.round(width * scale) & ~1);
        int h = Math.max(2, Math.round(height * scale) & ~1);
        try {
            capturer.startCapture(w, h, SCREEN_FPS);
            capturing = true;
        } catch (RuntimeException error) {
            // Consent already used or revoked (each one is good for a single capture on Android 14+).
            ScreenShare.INSTANCE.onSystemStopped();
        }
    }

    private void stop() {
        if (!capturing || capturer == null) return;
        try {
            capturer.stopCapture();
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
        capturing = false;
    }

    private String cameraName() {
        CameraEnumerator enumerator = new Camera2Enumerator(ContextUtils.getApplicationContext());
        boolean front = !"back".equals(deviceId);
        String fallback = null;
        for (String name : enumerator.getDeviceNames()) {
            if (enumerator.isFrontFacing(name) == front) return name;
            if (fallback == null) fallback = name;
        }
        return fallback;
    }

    private static native Object nativeGetJavaVideoCapturerObserver(long ptr);
}
