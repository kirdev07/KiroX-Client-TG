package com.kirox.app.telegram

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.drinkless.tdlib.TdApi

/** Stage of a one-to-one call as the UI shows it. */
sealed interface CallPhase {
    /** Outgoing: waiting for the other side; [ringing] once their device has received the call. */
    data class Calling(val ringing: Boolean) : CallPhase
    data object Incoming : CallPhase
    data object Connecting : CallPhase
    /** Connected; [sinceMillis] is the local time the call became ready. */
    data class Active(val sinceMillis: Long) : CallPhase
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
    val phase: CallPhase
)

/**
 * One-to-one calls over TDLib: CreateCall / AcceptCall / DiscardCall and UpdateCall.
 * TDLib only negotiates the call; audio and video need the tgcalls engine, which is not linked yet,
 * so [mediaAvailable] is false and the UI marks calls as a signaling test.
 */
class TelegramVoipRepository(private val client: TelegramClient, private val users: TelegramUserRepository) {
    private val _call = MutableStateFlow<TelegramActiveCall?>(null)
    val call: StateFlow<TelegramActiveCall?> = _call.asStateFlow()

    private var activeSince = 0L

    init {
        client.addUpdateHandler { update ->
            when (update) {
                is TdApi.UpdateCall -> onCall(update.call)
                is TdApi.UpdateAuthorizationState -> if (update.authorizationState is TdApi.AuthorizationStateClosed) _call.value = null
            }
        }
    }

    fun start(userId: Long, isVideo: Boolean) = client.post {
        if (_call.value?.phase.isLive()) return@post
        val user = users.user(userId)
        // Placeholder until TDLib reports the call id.
        _call.value = TelegramActiveCall(0, userId, user?.displayName().orEmpty(), user?.profilePhoto?.small?.toRef(), true, isVideo, CallPhase.Calling(false))
        client.send(TdApi.CreateCall(userId, protocol(), isVideo)) { result ->
            if (result is TdApi.Error) _call.value = _call.value?.copy(phase = CallPhase.Failed(errorText(result)))
        }
    }

    fun accept() = client.post {
        val current = _call.value ?: return@post
        if (current.phase != CallPhase.Incoming) return@post
        _call.value = current.copy(phase = CallPhase.Connecting)
        client.send(TdApi.AcceptCall(current.id, protocol()))
    }

    /** Declines an incoming call or hangs up any other. */
    fun hangUp() = client.post {
        val current = _call.value ?: return@post
        if (current.id == 0 || !current.phase.isLive()) { _call.value = null; return@post }
        val duration = (current.phase as? CallPhase.Active)?.let { ((System.currentTimeMillis() - it.sinceMillis) / 1000).toInt() } ?: 0
        client.send(TdApi.DiscardCall(current.id, false, "", duration, current.isVideo, 0))
    }

    /** Hides the finished-call screen. */
    fun dismiss() = client.post { if (!_call.value?.phase.isLive()) _call.value = null }

    private fun onCall(call: TdApi.Call) {
        val current = _call.value
        // A second call while one is live: decline it, TDLib marks it busy for the caller.
        if (current != null && current.id != 0 && current.id != call.id && current.phase.isLive()) {
            if (call.state is TdApi.CallStatePending && !call.isOutgoing) client.send(TdApi.DiscardCall(call.id, false, "", 0, call.isVideo, 0))
            return
        }
        val user = users.user(call.userId)
        val phase = when (val state = call.state) {
            is TdApi.CallStatePending -> if (call.isOutgoing) CallPhase.Calling(state.isReceived) else CallPhase.Incoming
            is TdApi.CallStateExchangingKeys -> CallPhase.Connecting
            is TdApi.CallStateReady -> {
                if (current?.phase !is CallPhase.Active) activeSince = System.currentTimeMillis()
                CallPhase.Active(activeSince)
            }
            is TdApi.CallStateHangingUp -> current?.phase ?: CallPhase.Connecting
            is TdApi.CallStateDiscarded -> CallPhase.Ended(
                when (state.reason) {
                    is TdApi.CallDiscardReasonDeclined -> CallEndReason.Declined
                    is TdApi.CallDiscardReasonMissed -> CallEndReason.Missed
                    is TdApi.CallDiscardReasonDisconnected -> CallEndReason.Disconnected
                    else -> CallEndReason.HungUp
                },
                (current?.phase as? CallPhase.Active)?.let { ((System.currentTimeMillis() - it.sinceMillis) / 1000).toInt() } ?: 0
            )
            is TdApi.CallStateError -> CallPhase.Failed(errorText(state.error))
            else -> return
        }
        _call.value = TelegramActiveCall(
            call.id, call.userId, user?.displayName() ?: current?.name.orEmpty(), user?.profilePhoto?.small?.toRef() ?: current?.photo,
            call.isOutgoing, call.isVideo, phase
        )
        // The finished screen goes away by itself after a moment.
        if (phase is CallPhase.Ended || phase is CallPhase.Failed) client.post(ENDED_SCREEN_MS) {
            if (_call.value?.id == call.id && !_call.value?.phase.isLive()) _call.value = null
        }
    }

    private fun protocol() = TdApi.CallProtocol(true, true, MIN_LAYER, MAX_LAYER, LIBRARY_VERSIONS)

    private fun errorText(error: TdApi.Error) = when {
        error.message.contains("PRIVACY", ignoreCase = true) -> "Пользователь запретил звонки"
        error.message.contains("USER_IS_BLOCKED", ignoreCase = true) -> "Пользователь недоступен"
        else -> "Не удалось позвонить"
    }

    companion object {
        /** Audio and video engine (tgcalls) is not linked yet. */
        const val mediaAvailable = false
        private const val MIN_LAYER = 65
        private const val MAX_LAYER = 92
        private val LIBRARY_VERSIONS = arrayOf("4.0.0", "3.0.0", "2.7.7", "2.4.4")
        private const val ENDED_SCREEN_MS = 1800L
    }
}

internal fun CallPhase?.isLive() = this != null && this !is CallPhase.Ended && this !is CallPhase.Failed
