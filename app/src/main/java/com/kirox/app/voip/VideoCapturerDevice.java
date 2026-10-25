package com.kirox.app.voip;

import android.media.projection.MediaProjection;
import android.os.Handler;
import android.os.HandlerThread;

import org.webrtc.Camera2Enumerator;
import org.webrtc.CameraEnumerator;
import org.webrtc.CameraVideoCapturer;
import org.webrtc.CapturerObserver;
import org.webrtc.ContextUtils;
import org.webrtc.EglBase;
import org.webrtc.SurfaceTextureHelper;

/**
 * Camera source for the call engine. tgcalls creates one per capture interface and drives it through
 * {@link #init}, {@link #onStateChanged} and {@link #onDestroy}; frames go to the native observer.
 * Every camera call runs on one background thread.
 */
@SuppressWarnings("unused") // Called from native code.
public final class VideoCapturerDevice {
    private static final int CAPTURE_WIDTH = 1280;
    private static final int CAPTURE_HEIGHT = 720;
    private static final int CAPTURE_FPS = 30;
    private static final int STATE_ACTIVE = 2;

    private static EglBase sharedEgl;

    private final HandlerThread thread = new HandlerThread("KiroxCamera");
    private final Handler handler;
    private CameraVideoCapturer capturer;
    private SurfaceTextureHelper textureHelper;
    private long nativePtr;
    private String deviceId = "front";
    private boolean capturing;

    public VideoCapturerDevice(boolean screencast) {
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
            boolean switchCamera = capturer != null && !id.equals(deviceId);
            deviceId = id;
            if (switchCamera) capturer.switchCamera(null, cameraName());
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
