package com.kirox.app.telegram

import com.kirox.app.voip.NativeCall
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.drinkless.tdlib.TdApi
import org.webrtc.VideoSink

/** Stage of a one-to-one call as the UI shows it. */
sealed interface CallPhase {
    /** Outgoing: waiting for the other side; [ringing] once their device has received the call. */
    data class Calling(val ringing: Boolean) : CallPhase
    data object Incoming : CallPhase
    data object Connecting : CallPhase
    /** Connected; [sinceMillis] is the local time media started flowing. */
    data class Active(val sinceMillis: Long, val reconnecting: Boolean = false) : CallPhase
    data class Ended(val reason: CallEndReason, val durationSeconds: Int) : CallPhase
    data class Failed(val message: String) : CallPhase
}

enum class CallEndReason { HungUp, Declined, Missed, Disconnected }

data class TelegramActiveCall(
    val id: Int,
    val userId: Long,
    val name: String,
    val photo: TelegramFileRef?,
    val isOutgoing: Boolean,
    val isVideo: Boolean,
    val phase: CallPhase,
    /** Four emojis both sides see when the key exchange is done. */
    val emojis: List<String> = emptyList(),
    val micMuted: Boolean = false,
    val cameraOn: Boolean = false,
    val frontCamera: Boolean = true,
    val remoteVideo: Boolean = false,
    val remoteMuted: Boolean = false,
    val signalBars: Int = 4
)

/**
 * One-to-one calls: TDLib negotiates the call (CreateCall / AcceptCall / DiscardCall, UpdateCall)
 * and relays engine signaling; the tgcalls engine ([NativeCall]) carries audio and video.
 * Without the engine library calls stay signaling-only ([mediaAvailable] is false).
 */
class TelegramVoipRepository(private val client: TelegramClient, private val users: TelegramUserRepository) {
    private val _call = MutableStateFlow<TelegramActiveCall?>(null)
    val call: StateFlow<TelegramActiveCall?> = _call.asStateFlow()

    private var engine: NativeCall? = null
    private var engineCallId = 0
    private var activeSince = 0L
    private var remoteSink: VideoSink? = null
    private var localSink: VideoSink? = null
    /** Hang up was pressed before TDLib gave the outgoing call an id: discard it as soon as it has one. */
    private var cancelOutgoing = false
    /** Signaling that arrived before the engine of that call started; fed to it on start. */
    private val earlySignaling = ArrayList<Pair<Int, ByteArray>>()

    init {
        client.addUpdateHandler { update ->
            when (update) {
                is TdApi.UpdateCall -> onCall(update.call)
                is TdApi.UpdateNewCallSignalingData -> {
                    val running = engine
                    if (update.callId == engineCallId && running != null) running.receiveSignaling(update.data)
                    else if (earlySignaling.size < 64) earlySignaling += update.callId to update.data
                }
                is TdApi.UpdateAuthorizationState -> if (update.authorizationState is TdApi.AuthorizationStateClosed) {
                    stopEngine(); _call.value = null; cancelOutgoing = false; earlySignaling.clear()
                }
            }
        }
    }

    fun start(userId: Long, isVideo: Boolean) = client.post {
        if (_call.value?.phase.isLive()) return@post
        cancelOutgoing = false
        NativeCall.awaitLoaded()
        val user = users.user(userId)
        // Placeholder until TDLib reports the call id.
        _call.value = TelegramActiveCall(0, userId, user?.displayName().orEmpty(), user?.profilePhoto?.small?.toRef(), true, isVideo, CallPhase.Calling(false), cameraOn = isVideo)
        client.send(TdApi.CreateCall(userId, protocol(), isVideo)) { result ->
            if (result is TdApi.Error && com.kirox.app.BuildConfig.DEBUG) android.util.Log.d(TAG, "CreateCall: $result")
            if (result is TdApi.Error) _call.update { it?.copy(phase = CallPhase.Failed(errorText(result))) }
            if (result is TdApi.CallId && cancelOutgoing) discardCancelled(result.id, isVideo)
        }
    }

    fun accept() = client.post {
        val current = _call.value ?: return@post
        if (current.phase != CallPhase.Incoming) return@post
        NativeCall.awaitLoaded()
        _call.value = current.copy(phase = CallPhase.Connecting)
        client.send(TdApi.AcceptCall(current.id, protocol()))
    }

    /** Declines an incoming call or hangs up any other. */
    fun hangUp() = client.post {
        val current = _call.value ?: return@post
        if (current.id == 0 && current.phase.isLive()) {
            // CreateCall is still in flight: the call must not ring the other side anyway.
            cancelOutgoing = true
            _call.value = null
            return@post
        }
        if (current.id == 0 || !current.phase.isLive()) { _call.value = null; return@post }
        val duration = (current.phase as? CallPhase.Active)?.let { ((System.currentTimeMillis() - it.sinceMillis) / 1000).toInt() } ?: 0
        client.send(TdApi.DiscardCall(current.id, false, "", duration, current.isVideo, 0))
    }

    /** Hides the finished-call screen. */
    fun dismiss() = client.post { if (!_call.value?.phase.isLive()) _call.value = null }

    fun setMuted(muted: Boolean) = client.post {
        engine?.setMuted(muted)
        _call.update { it?.copy(micMuted = muted) }
    }

    fun setCamera(on: Boolean) = client.post {
        engine?.setVideo(on)
        _call.update { it?.copy(cameraOn = on) }
    }

    fun switchCamera() = client.post {
        val front = !(_call.value?.frontCamera ?: true)
        engine?.switchCamera(front)
        _call.update { it?.copy(frontCamera = front) }
    }

    /** Video renderers from the call screen; null when it goes away. */
    fun setVideoSinks(remote: VideoSink?, local: VideoSink?) = client.post {
        remoteSink = remote
        localSink = local
        engine?.setSinks(remote, local)
    }

    private fun discardCancelled(callId: Int, isVideo: Boolean) {
        cancelOutgoing = false
        client.send(TdApi.DiscardCall(callId, false, "", 0, isVideo, 0))
    }

    private fun onCall(call: TdApi.Call) {
        val current = _call.value
        // Our outgoing call that was hung up before it had an id.
        if (cancelOutgoing && call.isOutgoing && current == null) {
            if (call.state is TdApi.CallStatePending) discardCancelled(call.id, call.isVideo)
            return
        }
        // While dialing (no id yet) an incoming call is declined like during any other call.
        val dialing = current != null && current.id == 0 && current.phase.isLive() && !call.isOutgoing
        // A second call while one is live: decline it, the caller sees "busy".
        if (dialing || (current != null && current.id != 0 && current.id != call.id && current.phase.isLive())) {
            if (call.state is TdApi.CallStatePending && !call.isOutgoing) client.send(TdApi.DiscardCall(call.id, false, "", 0, call.isVideo, 0))
            return
        }
        if (com.kirox.app.BuildConfig.DEBUG) android.util.Log.d(TAG, "call ${call.id} out=${call.isOutgoing} state=${call.state}")
        val user = users.user(call.userId)
        var emojis = current?.emojis.orEmpty()
        val phase = when (val state = call.state) {
            is TdApi.CallStatePending -> if (call.isOutgoing) CallPhase.Calling(state.isReceived) else CallPhase.Incoming
            is TdApi.CallStateExchangingKeys -> CallPhase.Connecting
            is TdApi.CallStateReady -> {
                emojis = state.emojis.toList()
                if (!NativeCall.awaitLoaded()) {
                    if (current?.phase !is CallPhase.Active) activeSince = System.currentTimeMillis()
                    CallPhase.Active(activeSince)
                } else {
                    if (engineCallId != call.id) startEngine(call, state, current?.cameraOn ?: call.isVideo)
                    current?.phase as? CallPhase.Active ?: CallPhase.Connecting
                }
            }
            is TdApi.CallStateHangingUp -> current?.phase ?: CallPhase.Connecting
            is TdApi.CallStateDiscarded -> {
                stopEngine()
                earlySignaling.removeAll { it.first == call.id }
                CallPhase.Ended(
                    when (state.reason) {
                        is TdApi.CallDiscardReasonDeclined -> CallEndReason.Declined
                        is TdApi.CallDiscardReasonMissed -> CallEndReason.Missed
                        is TdApi.CallDiscardReasonDisconnected -> CallEndReason.Disconnected
                        else -> CallEndReason.HungUp
                    },
                    (current?.phase as? CallPhase.Active)?.let { ((System.currentTimeMillis() - it.sinceMillis) / 1000).toInt() } ?: 0
                )
            }
            is TdApi.CallStateError -> { stopEngine(); earlySignaling.removeAll { it.first == call.id }; CallPhase.Failed(errorText(state.error)) }
            else -> return
        }
        val base = current?.takeIf { it.id == call.id || it.id == 0 }
        _call.value = (base ?: TelegramActiveCall(call.id, call.userId, "", null, call.isOutgoing, call.isVideo, phase, cameraOn = call.isVideo)).copy(
            id = call.id,
            userId = call.userId,
            name = user?.displayName() ?: base?.name.orEmpty(),
            photo = user?.profilePhoto?.small?.toRef() ?: base?.photo,
            isOutgoing = call.isOutgoing,
            isVideo = call.isVideo,
            phase = phase,
            emojis = emojis
        )
        // The finished screen goes away by itself after a moment.
        if (phase is CallPhase.Ended || phase is CallPhase.Failed) client.post(ENDED_SCREEN_MS) {
            if (_call.value?.id == call.id && !_call.value?.phase.isLive()) _call.value = null
        }
    }

    private fun startEngine(call: TdApi.Call, ready: TdApi.CallStateReady, video: Boolean) {
        stopEngine()
        engineCallId = call.id
        val callId = call.id
        // Reflector ids are their 1-based positions among reflectors sorted by server id.
        val reflectorIds = ready.servers.filter { it.type is TdApi.CallServerTypeTelegramReflector }.map { it.id }.sorted()
        val servers = ready.servers.map { server ->
            when (val type = server.type) {
                is TdApi.CallServerTypeWebrtc -> NativeCall.Server(
                    server.id, server.ipAddress, server.ipv6Address, server.port, true, type.supportsTurn, false,
                    type.username, type.password, 0, ByteArray(0)
                )
                is TdApi.CallServerTypeTelegramReflector -> NativeCall.Server(
                    server.id, server.ipAddress, server.ipv6Address, server.port, false, true, type.isTcp,
                    "reflector", type.peerTag.joinToString("") { "%02x".format(it) }, reflectorIds.indexOf(server.id) + 1, type.peerTag
                )
                else -> null
            }
        }.filterNotNull()
        val listener = object : NativeCall.Listener {
            override fun onEngineState(state: NativeCall.EngineState) = client.post {
                if (com.kirox.app.BuildConfig.DEBUG) android.util.Log.d(TAG, "engine $state")
                if (engineCallId != callId) return@post
                when (state) {
                    NativeCall.EngineState.Established -> _call.update { current ->
                        val since = (current?.phase as? CallPhase.Active)?.sinceMillis ?: System.currentTimeMillis()
                        current?.copy(phase = CallPhase.Active(since))
                    }
                    NativeCall.EngineState.Reconnecting -> _call.update { current ->
                        (current?.phase as? CallPhase.Active)?.let { current.copy(phase = it.copy(reconnecting = true)) } ?: current
                    }
                    NativeCall.EngineState.Failed -> {
                        val active = _call.value?.phase as? CallPhase.Active
                        val duration = active?.let { ((System.currentTimeMillis() - it.sinceMillis) / 1000).toInt() } ?: 0
                        client.send(TdApi.DiscardCall(callId, true, "", duration, call.isVideo, 0))
                    }
                    NativeCall.EngineState.Connecting -> Unit
                }
            }

            override fun onSignalingData(data: ByteArray) {
                client.send(TdApi.SendCallSignalingData(callId, data))
            }

            // Late callbacks of a stopped engine must not touch the next call.
            override fun onRemoteMedia(audioMuted: Boolean, videoActive: Boolean) = client.post {
                if (engineCallId == callId) _call.update { it?.copy(remoteMuted = audioMuted, remoteVideo = videoActive) }
            }

            override fun onSignalBars(bars: Int) = client.post { if (engineCallId == callId) _call.update { it?.copy(signalBars = bars) } }
        }
        val version = ready.protocol.libraryVersions.firstOrNull() ?: run {
            // No common engine version: end the call instead of hanging on "Соединение…".
            client.send(TdApi.DiscardCall(callId, true, "", 0, call.isVideo, 0))
            return
        }
        engine = NativeCall.start(
            listener, version, ready.encryptionKey, call.isOutgoing, ready.allowP2p, ready.customParameters,
            ready.protocol.maxLayer, servers, video
        )?.also { native ->
            native.setSinks(remoteSink, localSink)
            // Signaling that came before the engine existed.
            earlySignaling.filter { it.first == callId }.forEach { native.receiveSignaling(it.second) }
            earlySignaling.clear()
            _call.value?.micMuted?.let { if (it) native.setMuted(true) }
        }
        if (engine == null) client.send(TdApi.DiscardCall(callId, true, "", 0, call.isVideo, 0))
    }

    private fun stopEngine() {
        engine?.stop()
        engine = null
        engineCallId = 0
    }

    private fun protocol(): TdApi.CallProtocol {
        val versions = NativeCall.versions().takeIf { it.isNotEmpty() } ?: LIBRARY_VERSIONS
        return TdApi.CallProtocol(true, true, MIN_LAYER, NativeCall.maxLayer(), versions).also { if (com.kirox.app.BuildConfig.DEBUG) android.util.Log.d(TAG, "protocol $it") }
    }

    private fun errorText(error: TdApi.Error) = when {
        error.message.contains("PRIVACY", ignoreCase = true) -> "Пользователь запретил звонки"
        error.message.contains("USER_IS_BLOCKED", ignoreCase = true) -> "Пользователь недоступен"
        else -> "Не удалось позвонить"
    }

    companion object {
        /** Audio and video work only when the tgcalls engine library is loaded. */
        val mediaAvailable: Boolean get() = NativeCall.available
        private const val MIN_LAYER = 65
        private val LIBRARY_VERSIONS = arrayOf("4.0.0", "3.0.0", "2.7.7", "2.4.4")
        private const val ENDED_SCREEN_MS = 1800L
        private const val TAG = "KiroxVoip"
    }
}

internal fun CallPhase?.isLive() = this != null && this !is CallPhase.Ended && this !is CallPhase.Failed
