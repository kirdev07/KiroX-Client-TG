package com.kirox.app.voip

import org.webrtc.VideoSink

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
    fun setVideo(enabled: Boolean, frontCamera: Boolean) { handle.takeIf { it != 0L }?.let { nativeSetVideo(it, enabled, frontCamera) } }
    fun setLocalSink(sink: VideoSink?) { handle.takeIf { it != 0L }?.let { nativeSetLocalSink(it, sink) } }
    fun setRemoteSink(endpoint: String, sink: VideoSink?) { handle.takeIf { it != 0L }?.let { nativeSetRemoteSink(it, endpoint, sink) } }

    /** Video streams to receive; [full] asks for full quality (one stream on screen). */
    fun setVideoChannels(channels: List<VideoChannel>, full: Boolean) {
        val current = handle.takeIf { it != 0L } ?: return
        nativeSetVideoChannels(
            current, channels.map { it.audioSource }.toIntArray(), channels.map { it.endpoint }.toTypedArray(),
            channels.map { it.encodedGroups }.toTypedArray(), full
        )
    }

    /** One participant's video: audio source, endpoint and source groups encoded as "SIM:1,2;FID:1,3". */
    data class VideoChannel(val audioSource: Int, val endpoint: String, val encodedGroups: String)

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
    private external fun nativeSetVideo(handle: Long, enabled: Boolean, front: Boolean)
    private external fun nativeSetLocalSink(handle: Long, sink: VideoSink?)
    private external fun nativeSetRemoteSink(handle: Long, endpoint: String, sink: VideoSink?)
    private external fun nativeSetVideoChannels(handle: Long, audioSsrcs: IntArray, endpoints: Array<String>, groups: Array<String>, full: Boolean)
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
