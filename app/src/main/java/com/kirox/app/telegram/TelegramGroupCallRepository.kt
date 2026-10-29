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

    @Volatile private var engine: NativeGroupCall? = null
    // Dispatcher-confined.
    private val participants = LinkedHashMap<String, TdApi.GroupCallParticipant>()
    private val speakingSources = HashSet<Int>()
    private var mySource = 0
    /** Last streams requested from the engine, so it is told only about changes. */
    private var requestedVideo: List<NativeGroupCall.VideoChannel> = emptyList()

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
                is TdApi.UpdateGroupCall -> if (update.groupCall.id == current.groupCallId) {
                    val group = update.groupCall
                    if (!group.isActive) { end("Видеочат завершён"); return@addUpdateHandler }
                    if (group.needRejoin && current.phase == GroupCallPhase.Connected) rejoin()
                    _call.value = current.copy(title = group.title.ifBlank { current.title }, count = group.participantCount, canEnableVideo = group.canEnableVideo)
                }
                is TdApi.UpdateAuthorizationState -> if (update.authorizationState is TdApi.AuthorizationStateClosed) end(null)
            }
        }
    }

    /** Joins the video chat of [chatId]; creates one first when the chat has none (needs admin rights). */
    suspend fun join(chatId: Long) {
        if (_call.value != null) return
        val chat = runCatching { client.query(TdApi.GetChat(chatId)) }.getOrNull()
        _call.value = TelegramGroupCall(chatId, 0, chat?.title.orEmpty(), GroupCallPhase.Joining, muted = true, canUnmute = true, members = emptyList(), count = 0)
        val existing = chat?.videoChat?.groupCallId ?: 0
        val groupCallId = existing.takeIf { it != 0 }
            ?: runCatching { client.query(TdApi.CreateVideoChat(chatId, "", 0, false)).id }.getOrElse {
                _call.value = null
                throw IllegalStateException("В чате нет видеочата, а начать его могут только администраторы")
            }
        client.post {
            _call.value = _call.value?.copy(groupCallId = groupCallId)
            participants.clear(); speakingSources.clear()
            startEngine(groupCallId)
        }
    }

    private fun startEngine(groupCallId: Int) {
        engine?.stop()
        engine = NativeGroupCall.start(object : NativeGroupCall.Listener {
            override fun onJoinPayload(audioSourceId: Int, payload: String) = client.post { sendJoin(groupCallId, audioSourceId, payload) }
            override fun onNetwork(connected: Boolean) = client.post {
                val current = _call.value ?: return@post
                _call.value = current.copy(phase = if (connected) GroupCallPhase.Connected else if (current.phase == GroupCallPhase.Joining) GroupCallPhase.Joining else GroupCallPhase.Reconnecting)
            }
            override fun onLevels(sources: IntArray, levels: FloatArray) = client.post {
                val now = sources.indices.filter { levels[it] > SPEAKING_LEVEL }.map { if (sources[it] == 0) mySource else sources[it] }.toHashSet()
                if (now != speakingSources) { speakingSources.clear(); speakingSources += now; publish() }
            }
        })
        if (engine == null) end("Звонки недоступны на этом устройстве")
    }

    private fun sendJoin(groupCallId: Int, audioSourceId: Int, payload: String) {
        mySource = audioSourceId
        val muted = _call.value?.muted ?: true
        val camera = _call.value?.cameraOn ?: false
        client.send(TdApi.JoinVideoChat(groupCallId, null, TdApi.GroupCallJoinParameters(audioSourceId, payload, muted, camera), "")) { result ->
            when (result) {
                is TdApi.Text -> {
                    engine?.setJoinResponse(result.text)
                    engine?.setMuted(muted)
                    // After a rejoin the camera keeps going.
                    if (camera) _call.value?.let { engine?.setVideo(true, it.frontCamera) }
                    requestedVideo = emptyList()
                    client.send(TdApi.LoadGroupCallParticipants(groupCallId, PARTICIPANTS_LIMIT))
                }
                is TdApi.Error -> end(humanError(result))
            }
        }
    }

    private fun rejoin() {
        val current = _call.value ?: return
        _call.value = current.copy(phase = GroupCallPhase.Reconnecting)
        startEngine(current.groupCallId)
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
        _call.value = current.copy(cameraOn = on)
        engine?.setVideo(on, current.frontCamera)
        if (current.groupCallId != 0) client.send(TdApi.ToggleGroupCallIsMyVideoEnabled(current.groupCallId, on))
    }

    fun switchCamera() = client.post {
        val current = _call.value ?: return@post
        val front = !current.frontCamera
        _call.value = current.copy(frontCamera = front)
        if (current.cameraOn) engine?.setVideo(true, front)
    }

    /** Renderer of our own camera; null detaches it. */
    fun setLocalSink(sink: org.webrtc.VideoSink?) { engine?.setLocalSink(sink) }

    /** Renderer of a participant's camera by its endpoint; null detaches it. */
    fun setRemoteSink(endpoint: String, sink: org.webrtc.VideoSink?) { engine?.setRemoteSink(endpoint, sink) }

    fun leave() = client.post {
        val current = _call.value ?: return@post
        if (current.groupCallId != 0) client.send(TdApi.LeaveGroupCall(current.groupCallId))
        end(null)
    }

    /** The screen closed after a failure or the end. */
    fun dismiss() = client.post { if (_call.value?.phase is GroupCallPhase.Failed) _call.value = null }

    private fun end(message: String?) {
        engine?.stop()
        engine = null
        participants.clear(); speakingSources.clear(); requestedVideo = emptyList()
        val current = _call.value
        _call.value = if (message != null && current != null) current.copy(phase = GroupCallPhase.Failed(message), members = emptyList()) else null
    }

    private fun publish() {
        val current = _call.value ?: return
        val self = participants.values.firstOrNull { it.isCurrentUser }
        val members = participants.values.sortedBy { it.order }.reversed().map { p ->
            GroupCallMember(
                key = keyOf(p.participantId),
                name = if (p.isCurrentUser) "Вы" else nameOf(p.participantId),
                photo = photoOf(p.participantId),
                isSelf = p.isCurrentUser,
                speaking = p.isSpeaking || (p.audioSourceId != 0 && p.audioSourceId in speakingSources),
                muted = p.isMutedForAllUsers || p.isMutedForCurrentUser || (p.isCurrentUser && current.muted),
                handRaised = p.isHandRaised,
                videoEndpoint = p.videoInfo?.takeIf { !it.isPaused && !p.isCurrentUser }?.endpointId?.takeIf { it.isNotBlank() }
            )
        }
        // Receive at most MAX_VIDEOS cameras: each stream costs bandwidth and a decoder.
        val video = participants.values.asSequence()
            .filter { !it.isCurrentUser }
            .mapNotNull { p -> p.videoInfo?.takeIf { !it.isPaused }?.let { info -> NativeGroupCall.VideoChannel(p.audioSourceId, info.endpointId, encodeSourceGroups(info.sourceGroups)) } }
            .take(MAX_VIDEOS)
            .toList()
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
    }
}
