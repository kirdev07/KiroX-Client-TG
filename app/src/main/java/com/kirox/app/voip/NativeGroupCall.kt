package com.kirox.app.voip

/**
 * Voice part of a Telegram video chat in the tgcalls engine (libkiroxvoip, kirox_group_jni.cpp).
 * Callbacks arrive on engine threads.
 */
class NativeGroupCall private constructor(private val listener: Listener) {

    interface Listener {
        /** Our audio source and the payload to send with JoinVideoChat. */
        fun onJoinPayload(audioSourceId: Int, payload: String)
        fun onNetwork(connected: Boolean)
        /** Audio sources speaking now (0 is this device) and their levels 0–1. */
        fun onLevels(sources: IntArray, levels: FloatArray)
    }

    @Volatile private var handle = 0L

    fun setJoinResponse(payload: String) { handle.takeIf { it != 0L }?.let { nativeSetJoinResponse(it, payload) } }
    fun setMuted(muted: Boolean) { handle.takeIf { it != 0L }?.let { nativeSetMuted(it, muted) } }
    fun removeSources(sources: IntArray) { handle.takeIf { it != 0L }?.let { nativeRemoveSsrcs(it, sources) } }

    fun stop() {
        val current = handle
        handle = 0L
        if (current != 0L) nativeStop(current)
    }

    // Called from native code.
    @Suppress("unused") private fun onJoinPayload(audioSourceId: Int, payload: String) = listener.onJoinPayload(audioSourceId, payload)
    @Suppress("unused") private fun onNetwork(connected: Boolean) = listener.onNetwork(connected)
    @Suppress("unused") private fun onLevels(sources: IntArray, levels: FloatArray) = listener.onLevels(sources, levels)
    @Suppress("unused") private fun onStopped() {}

    private external fun nativeStart(): Long
    private external fun nativeSetJoinResponse(handle: Long, payload: String)
    private external fun nativeSetMuted(handle: Long, muted: Boolean)
    private external fun nativeRemoveSsrcs(handle: Long, ssrcs: IntArray)
    private external fun nativeStop(handle: Long)

    companion object {
        /** Starts the engine; null when the call library is not available on this device. */
        fun start(listener: Listener): NativeGroupCall? {
            if (!NativeCall.awaitLoaded()) return null
            val call = NativeGroupCall(listener)
            call.handle = runCatching { call.nativeStart() }.getOrDefault(0L)
            return call.takeIf { it.handle != 0L }
        }
    }
}
