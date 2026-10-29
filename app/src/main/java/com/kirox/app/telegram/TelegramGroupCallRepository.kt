package com.kirox.app.telegram

import com.kirox.app.voip.NativeGroupCall
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.drinkless.tdlib.TdApi

/** A person in the video chat. */
data class GroupCallMember(
    val key: String,
    val name: String,
    val photo: TelegramFileRef?,
    val isSelf: Boolean,
    val speaking: Boolean,
    val muted: Boolean,
    val handRaised: Boolean,
    /** Endpoint of their camera stream while it is on, for the renderer. */
    val videoEndpoint: String? = null
)

/** TDLib source groups → "SIM:1,2,3;FID:1,4" for the engine. */
internal fun encodeSourceGroups(groups: Array<TdApi.GroupCallVideoSourceGroup>?): String =
    groups.orEmpty().filter { it.sourceIds.isNotEmpty() }.joinToString(";") { group -> group.semantics + ":" + group.sourceIds.joinToString(",") }

sealed interface GroupCallPhase {
    data object Joining : GroupCallPhase
    data object Connected : GroupCallPhase
    data object Reconnecting : GroupCallPhase
    data class Failed(val message: String) : GroupCallPhase
}

data class TelegramGroupCall(
    val chatId: Long,
    val groupCallId: Int,
    val title: String,
    val phase: GroupCallPhase,
    val muted: Boolean,
    /** Whether Telegram lets us speak (admins can mute everyone). */
    val canUnmute: Boolean,
    val members: List<GroupCallMember>,
    val count: Int,
    val cameraOn: Boolean = false,
    val frontCamera: Boolean = true,
    /** Whether Telegram allows this account to turn its camera on here. */
    val canEnableVideo: Boolean = true
)

/**
 * Voice chats of groups and channels: joins the chat's active video chat (or starts one when allowed),
 * keeps the member list from TDLib updates and drives the tgcalls group engine. Video and screen
 * sharing are not sent; others' voices are heard. Only one video chat at a time.
 */
class TelegramGroupCallRepository(private val client: TelegramClient, private val chats: TelegramChatRepository, private val users: TelegramUserRepository) {
    private val _call = MutableStateFlow<TelegramGroupCall?>(null)
    val call: StateFlow<TelegramGroupCall?> = _call.asStateFlow()

    // Everything below is confined to the TDLib dispatcher: engine calls included, so the native
    // object is never touched from two threads (its stop and delete happen there too).
    private var engine: NativeGroupCall? = null
    /** Bumped on every join, rejoin and end; late callbacks of an older engine are dropped. */
    private var session = 0
    private var rejoining = false
    private val participants = LinkedHashMap<String, TdApi.GroupCallParticipant>()
    private val speakingSources = HashSet<Int>()
    private var mySource = 0
    /** Last streams requested from the engine, so it is told only about changes. */
    private var requestedVideo: List<NativeGroupCall.VideoChannel> = emptyList()
    /** Renderers on screen; re-attached to every new engine (rejoin), so video survives it. */
    private var localSink: org.webrtc.VideoSink? = null
    private val remoteSinks = HashMap<String, org.webrtc.VideoSink>()

    init {
        client.addUpdateHandler { update ->
            val current = _call.value ?: return@addUpdateHandler
            when (update) {
                is TdApi.UpdateGroupCallParticipant -> if (update.groupCallId == current.groupCallId) {
                    val participant = update.participant
                    val key = keyOf(participant.participantId)
                    // An empty order means the participant left.
                    if (participant.order.isNullOrEmpty()) {
                        participants.remove(key)?.let { gone -> if (gone.audioSourceId != 0) engine?.removeSources(intArrayOf(gone.audioSourceId)) }
                    } else participants[key] = participant
                    publish()
                }
                is TdApi.UpdateGroupCall -> if (update.groupCall.id == current.groupCallId && current.phase !is GroupCallPhase.Failed) {
                    val group = update.groupCall
                    if (!group.isActive) { end("Видеочат завершён"); return@addUpdateHandler }
                    _call.value = current.copy(title = group.title.ifBlank { current.title }, count = group.participantCount, canEnableVideo = group.canEnableVideo)
                    if (group.needRejoin && !rejoining && current.phase == GroupCallPhase.Connected) rejoin()
                }
                is TdApi.UpdateAuthorizationState -> if (update.authorizationState is TdApi.AuthorizationStateClosed) end(null)
            }
        }
    }

    /**
     * Joins the video chat of [chatId], creating one when the chat has none and the user may.
     * The screen shows at once (Joining); failures turn it into Failed with the reason.
     */
    fun join(chatId: Long) = client.post {
        if (_call.value != null) return@post
        val id = ++session
        _call.value = TelegramGroupCall(chatId, 0, chats.chat(chatId)?.title.orEmpty(), GroupCallPhase.Joining, muted = true, canUnmute = true, members = emptyList(), count = 0)
        participants.clear(); speakingSources.clear(); requestedVideo = emptyList()
        client.send(TdApi.GetChat(chatId)) { chat ->
            if (id != session) return@send
            val existing = (chat as? TdApi.Chat)?.videoChat?.groupCallId ?: 0
            if (existing != 0) { connect(id, existing); return@send }
            client.send(TdApi.CreateVideoChat(chatId, "", 0, false)) { created ->
                if (id != session) return@send
                if (created is TdApi.GroupCallId) connect(id, created.id)
                else end("В чате нет видеочата, а начать его могут только администраторы")
            }
        }
    }

    private fun connect(id: Int, groupCallId: Int) {
        _call.value = _call.value?.copy(groupCallId = groupCallId) ?: return
        startEngine(id, groupCallId)
    }

    private fun startEngine(id: Int, groupCallId: Int) {
        engine?.stop()
        engine = null
        val created = NativeGroupCall.start(object : NativeGroupCall.Listener {
            // Engine threads: hop to the dispatcher and ignore anything from a replaced engine.
            override fun onJoinPayload(audioSourceId: Int, payload: String) = client.post { if (id == session) sendJoin(id, groupCallId, audioSourceId, payload) }
            override fun onNetwork(connected: Boolean) = client.post {
                if (id != session) return@post
                val current = _call.value ?: return@post
                if (current.phase is GroupCallPhase.Failed) return@post
                _call.value = current.copy(phase = when {
                    connected -> GroupCallPhase.Connected
                    current.phase == GroupCallPhase.Joining -> GroupCallPhase.Joining
                    else -> GroupCallPhase.Reconnecting
                })
                if (connected) rejoining = false else watchConnection(id)
            }
            override fun onLevels(sources: IntArray, levels: FloatArray) = client.post {
                if (id != session) return@post
                val now = sources.indices.filter { levels[it] > SPEAKING_LEVEL }.map { if (sources[it] == 0) mySource else sources[it] }.toHashSet()
                if (now != speakingSources) { speakingSources.clear(); speakingSources += now; publish() }
            }
        })
        if (created == null) { end("Звонки недоступны на этом устройстве"); return }
        engine = created
        localSink?.let { created.setLocalSink(it) }
        remoteSinks.forEach { (endpoint, sink) -> created.setRemoteSink(endpoint, sink) }
        watchConnection(id)
    }

    /** Gives up when the engine does not connect within [CONNECT_TIMEOUT_MS]. */
    private fun watchConnection(id: Int) = client.post(CONNECT_TIMEOUT_MS) {
        val phase = _call.value?.phase
        if (id == session && (phase == GroupCallPhase.Joining || phase == GroupCallPhase.Reconnecting)) {
            _call.value?.groupCallId?.takeIf { it != 0 }?.let { client.send(TdApi.LeaveGroupCall(it)) }
            end("Не удалось подключиться к видеочату. Проверьте соединение")
        }
    }

    private fun sendJoin(id: Int, groupCallId: Int, audioSourceId: Int, payload: String) {
        mySource = audioSourceId
        val muted = _call.value?.muted ?: true
        val camera = _call.value?.cameraOn ?: false
        client.send(TdApi.JoinVideoChat(groupCallId, null, TdApi.GroupCallJoinParameters(audioSourceId, payload, muted, camera), "")) { result ->
            if (id != session) {
                // The user left while joining: do not stay in the chat as an invisible participant.
                if (result is TdApi.Text) client.send(TdApi.LeaveGroupCall(groupCallId))
                return@send
            }
            when (result) {
                is TdApi.Text -> {
                    val current = engine ?: return@send
                    current.setJoinResponse(result.text)
                    current.setMuted(muted)
                    // After a rejoin the camera keeps going.
                    if (camera) _call.value?.let { current.setVideo(true, it.frontCamera) }
                    requestedVideo = emptyList()
                    publish()
                    client.send(TdApi.LoadGroupCallParticipants(groupCallId, PARTICIPANTS_LIMIT))
                }
                is TdApi.Error -> end(humanError(result))
            }
        }
    }

    private fun rejoin() {
        val current = _call.value ?: return
        rejoining = true
        _call.value = current.copy(phase = GroupCallPhase.Reconnecting)
        startEngine(++session, current.groupCallId)
    }

    fun setMuted(muted: Boolean) = client.post {
        val current = _call.value ?: return@post
        if (!muted && !current.canUnmute) return@post
        _call.value = current.copy(muted = muted)
        engine?.setMuted(muted)
        users.myId().takeIf { it != 0L }?.let { me ->
            client.send(TdApi.ToggleGroupCallParticipantIsMuted(current.groupCallId, TdApi.MessageSenderUser(me), muted))
        }
    }

    /** Turns our camera on or off (needs CAMERA permission, asked by the screen). */
    fun setCamera(on: Boolean) = client.post {
        val current = _call.value ?: return@post
        if (on && !current.canEnableVideo) return@post
        engine?.setVideo(on, current.frontCamera)
        _call.value = current.copy(cameraOn = on)
        if (current.groupCallId != 0) client.send(TdApi.ToggleGroupCallIsMyVideoEnabled(current.groupCallId, on))
    }

    fun switchCamera() = client.post {
        val current = _call.value ?: return@post
        val front = !current.frontCamera
        if (current.cameraOn) engine?.setVideo(true, front)
        _call.value = current.copy(frontCamera = front)
    }

    /** Renderer of our own camera; null detaches it. */
    fun setLocalSink(sink: org.webrtc.VideoSink?) = client.post {
        localSink = sink
        engine?.setLocalSink(sink)
    }

    /** Renderer of a participant's camera by its endpoint; null detaches it. */
    fun setRemoteSink(endpoint: String, sink: org.webrtc.VideoSink?) = client.post {
        if (sink == null) remoteSinks.remove(endpoint) else remoteSinks[endpoint] = sink
        engine?.setRemoteSink(endpoint, sink)
    }

    fun leave() = client.post {
        val current = _call.value ?: return@post
        if (current.groupCallId != 0) client.send(TdApi.LeaveGroupCall(current.groupCallId))
        end(null)
    }

    /** The screen closed after a failure or the end. */
    fun dismiss() = client.post { if (_call.value?.phase is GroupCallPhase.Failed) _call.value = null }

    private fun end(message: String?) {
        session++
        rejoining = false
        engine?.stop()
        engine = null
        participants.clear(); speakingSources.clear(); requestedVideo = emptyList()
        localSink = null; remoteSinks.clear()
        val current = _call.value
        _call.value = if (message != null && current != null) current.copy(phase = GroupCallPhase.Failed(message), members = emptyList(), cameraOn = false) else null
    }

    private fun publish() {
        val current = _call.value ?: return
        val self = participants.values.firstOrNull { it.isCurrentUser }
        val ordered = participants.values.sortedByDescending { it.order }
        // Receive at most MAX_VIDEOS cameras, the first ones in the shown order: each stream costs
        // bandwidth and a decoder, and only those get a video tile.
        val video = ordered.asSequence()
            .filter { !it.isCurrentUser }
            .mapNotNull { p -> p.videoInfo?.takeIf { !it.isPaused && it.endpointId.isNotBlank() }?.let { info -> NativeGroupCall.VideoChannel(p.audioSourceId, info.endpointId, encodeSourceGroups(info.sourceGroups)) } }
            .take(MAX_VIDEOS)
            .toList()
        val shownEndpoints = video.map { it.endpoint }.toSet()
        val members = ordered.map { p ->
            GroupCallMember(
                key = keyOf(p.participantId),
                name = if (p.isCurrentUser) "Вы" else nameOf(p.participantId),
                photo = photoOf(p.participantId),
                isSelf = p.isCurrentUser,
                speaking = p.isSpeaking || (p.audioSourceId != 0 && p.audioSourceId in speakingSources),
                muted = p.isMutedForAllUsers || p.isMutedForCurrentUser || (p.isCurrentUser && current.muted),
                handRaised = p.isHandRaised,
                videoEndpoint = p.videoInfo?.endpointId?.takeIf { !p.isCurrentUser && it in shownEndpoints }
            )
        }
        if (video != requestedVideo) {
            requestedVideo = video
            engine?.setVideoChannels(video, full = video.size == 1)
        }
        _call.value = current.copy(
            members = members,
            canUnmute = self?.let { it.canUnmuteSelf || !it.isMutedForAllUsers } ?: current.canUnmute,
            count = maxOf(current.count, members.size)
        )
    }

    private fun keyOf(sender: TdApi.MessageSender): String = when (sender) {
        is TdApi.MessageSenderUser -> "u${sender.userId}"
        is TdApi.MessageSenderChat -> "c${sender.chatId}"
        else -> "?"
    }

    private fun nameOf(sender: TdApi.MessageSender): String = when (sender) {
        is TdApi.MessageSenderUser -> users.user(sender.userId)?.displayName() ?: "Участник"
        is TdApi.MessageSenderChat -> chats.chat(sender.chatId)?.title ?: "Канал"
        else -> "Участник"
    }

    private fun photoOf(sender: TdApi.MessageSender): TelegramFileRef? = when (sender) {
        is TdApi.MessageSenderUser -> users.user(sender.userId)?.profilePhoto?.small?.toRef()
        is TdApi.MessageSenderChat -> chats.chat(sender.chatId)?.photo?.small?.toRef()
        else -> null
    }

    private companion object {
        const val PARTICIPANTS_LIMIT = 100
        const val SPEAKING_LEVEL = 0.05f
        const val MAX_VIDEOS = 4
        const val CONNECT_TIMEOUT_MS = 30_000L
    }
}
