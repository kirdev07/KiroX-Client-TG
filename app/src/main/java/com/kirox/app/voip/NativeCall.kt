package com.kirox.app.voip

import android.content.Context
import org.webrtc.ContextUtils
import org.webrtc.VideoSink

/** One call in the tgcalls engine (libkiroxvoip). Callbacks arrive on engine threads. */
class NativeCall private constructor(private val listener: Listener) {

    interface Listener {
        fun onEngineState(state: EngineState)
        fun onSignalingData(data: ByteArray)
        fun onRemoteMedia(audioMuted: Boolean, videoActive: Boolean)
        fun onSignalBars(bars: Int)
    }

    enum class EngineState { Connecting, Established, Failed, Reconnecting }

    /** A TDLib call server, already mapped to what the engine needs. */
    class Server(
        val id: Long,
        val ipv4: String,
        val ipv6: String,
        val port: Int,
        val isWebrtc: Boolean,
        val turn: Boolean,
        val tcp: Boolean,
        val username: String,
        val password: String,
        val reflectorId: Int,
        val peerTag: ByteArray
    )

    @Volatile private var handle = 0L

    fun receiveSignaling(data: ByteArray) { handle.takeIf { it != 0L }?.let { nativeReceiveSignaling(it, data) } }
    fun setMuted(muted: Boolean) { handle.takeIf { it != 0L }?.let { nativeSetMuted(it, muted) } }
    fun setVideo(enabled: Boolean) { handle.takeIf { it != 0L }?.let { nativeSetVideo(it, enabled) } }
    fun switchCamera(front: Boolean) { handle.takeIf { it != 0L }?.let { nativeSwitchCamera(it, front) } }
    fun setSinks(remote: VideoSink?, local: VideoSink?) { handle.takeIf { it != 0L }?.let { nativeSetSinks(it, remote, local) } }

    fun stop() {
        val current = handle
        handle = 0L
        if (current != 0L) nativeStop(current)
    }

    // Called from native code.
    @Suppress("unused") private fun onEngineState(state: Int) = listener.onEngineState(EngineState.entries.getOrElse(state) { EngineState.Failed })
    @Suppress("unused") private fun onSignalingData(data: ByteArray) = listener.onSignalingData(data)
    @Suppress("unused") private fun onRemoteMedia(audioMuted: Boolean, videoState: Int) = listener.onRemoteMedia(audioMuted, videoState == VIDEO_ACTIVE)
    @Suppress("unused") private fun onSignalBars(bars: Int) = listener.onSignalBars(bars)
    @Suppress("unused") private fun onStopped() {}

    private external fun nativeStart(
        version: String, key: ByteArray, isOutgoing: Boolean, allowP2p: Boolean, customParameters: String,
        initTimeout: Double, receiveTimeout: Double, maxLayer: Int,
        ids: LongArray, ipv4s: Array<String>, ipv6s: Array<String>, ports: IntArray, isRtcs: BooleanArray,
        turns: BooleanArray, tcps: BooleanArray, usernames: Array<String>, passwords: Array<String>,
        reflectorIds: IntArray, peerTags: Array<ByteArray>,
        video: Boolean, frontCamera: Boolean
    ): Long
    private external fun nativeReceiveSignaling(handle: Long, data: ByteArray)
    private external fun nativeSetMuted(handle: Long, muted: Boolean)
    private external fun nativeSetVideo(handle: Long, enabled: Boolean)
    private external fun nativeSwitchCamera(handle: Long, front: Boolean)
    private external fun nativeSetSinks(handle: Long, remote: VideoSink?, local: VideoSink?)
    private external fun nativeStop(handle: Long)

    companion object {
        private const val VIDEO_ACTIVE = 2

        /** False when the engine library is missing or failed to load; calls then stay signaling-only. */
        @Volatile var available = false
            private set

        private val loaded = java.util.concurrent.CountDownLatch(1)

        fun load(context: Context) {
            if (available) return
            available = runCatching {
                ContextUtils.initialize(context.applicationContext)
                System.loadLibrary("kiroxvoip")
            }.isSuccess
            loaded.countDown()
        }

        /**
         * Waits for [load] (it runs on a background thread at app start), so a call placed or
         * answered right after a cold start still gets the engine. Returns [available].
         */
        fun awaitLoaded(timeoutMs: Long = LOAD_TIMEOUT_MS): Boolean {
            loaded.await(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)
            return available
        }

        /** Engine versions for TDLib's CallProtocol, newest first. */
        fun versions(): Array<String> = if (available) nativeVersions() else emptyArray()
        fun maxLayer(): Int = if (available) nativeMaxLayer() else 92

        fun start(
            listener: Listener, version: String, key: ByteArray, isOutgoing: Boolean, allowP2p: Boolean,
            customParameters: String, maxLayer: Int, servers: List<Server>, video: Boolean
        ): NativeCall? {
            if (!available) return null
            val call = NativeCall(listener)
            call.handle = call.nativeStart(
                version, key, isOutgoing, allowP2p, customParameters, INIT_TIMEOUT_S, RECEIVE_TIMEOUT_S, maxLayer,
                servers.map { it.id }.toLongArray(), servers.map { it.ipv4 }.toTypedArray(), servers.map { it.ipv6 }.toTypedArray(),
                servers.map { it.port }.toIntArray(), servers.map { it.isWebrtc }.toBooleanArray(), servers.map { it.turn }.toBooleanArray(),
                servers.map { it.tcp }.toBooleanArray(), servers.map { it.username }.toTypedArray(), servers.map { it.password }.toTypedArray(),
                servers.map { it.reflectorId }.toIntArray(), servers.map { it.peerTag }.toTypedArray(),
                video, true
            )
            return call.takeIf { it.handle != 0L }
        }

        @JvmStatic private external fun nativeVersions(): Array<String>
        @JvmStatic private external fun nativeMaxLayer(): Int

        private const val LOAD_TIMEOUT_MS = 5_000L
        private const val INIT_TIMEOUT_S = 30.0
        private const val RECEIVE_TIMEOUT_S = 10.0
    }
}
