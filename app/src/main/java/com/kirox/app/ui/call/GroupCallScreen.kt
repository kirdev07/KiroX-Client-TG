package com.kirox.app.ui.call

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kirox.app.telegram.GroupCallMember
import com.kirox.app.telegram.GroupCallPhase
import com.kirox.app.telegram.TelegramGroupCall
import com.kirox.app.ui.components.TelegramAvatar
import com.kirox.app.voip.CallAudioRoute
import com.kirox.app.voip.CallAudioRouter

/** What the video chat screen can do. */
interface GroupCallActions {
    fun setMuted(muted: Boolean)
    fun leave()
    fun dismiss()
    /** Turning the camera on asks for the CAMERA permission first. */
    fun setCamera(on: Boolean) {}
    fun switchCamera() {}
    fun setLocalSink(sink: org.webrtc.VideoSink?) {}
    fun setRemoteSink(endpoint: String, sink: org.webrtc.VideoSink?) {}
}

/**
 * Video chat of a group or channel: members in a grid with a ring around whoever speaks; cameras that
 * are on (ours and up to four others) replace the avatar. Microphone, camera, sound route and leave.
 * Fusion style on a black stage, like the 1:1 call.
 */
@Composable
fun GroupCallScreen(call: TelegramGroupCall, actions: GroupCallActions) {
    val failed = call.phase as? GroupCallPhase.Failed
    BackHandler { if (failed != null) actions.dismiss() else actions.leave() }
    val context = LocalContext.current
    val router = androidx.compose.runtime.remember(call.groupCallId) { CallAudioRouter(context) }
    androidx.compose.runtime.DisposableEffect(router, failed == null) {
        // Voice chats start on the speaker, like a conference.
        if (failed == null) { router.start(isVideo = true) }
        onDispose { router.stop() }
    }
    val route by router.route.collectAsState()
    Column(Modifier.fillMaxSize().background(Color(0xFF050608)).statusBarsPadding().navigationBarsPadding()) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Видеочат", color = Color(0xB3FFFFFF), fontSize = 13.sp)
            Text(call.title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                when (call.phase) {
                    GroupCallPhase.Joining -> "Подключение…"
                    GroupCallPhase.Reconnecting -> "Переподключение…"
                    GroupCallPhase.Connected -> participantsLabel(call.count)
                    is GroupCallPhase.Failed -> call.phase.message
                },
                color = if (failed != null) Color(0xFFFF6B6B) else Color(0xB3FFFFFF), fontSize = 14.sp, textAlign = TextAlign.Center
            )
        }
        Box(Modifier.fillMaxWidth().weight(1f)) {
            if (call.members.isEmpty() && failed == null) CircularProgressIndicator(Modifier.align(Alignment.Center).size(28.dp), color = Color.White, strokeWidth = 2.dp)
            val anyVideo = call.cameraOn || call.members.any { it.videoEndpoint != null }
            LazyVerticalGrid(
                GridCells.Adaptive(if (anyVideo) 150.dp else 96.dp), contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(call.members, key = { it.key }) { member ->
                    when {
                        member.isSelf && call.cameraOn -> VideoTile(member, mirror = call.frontCamera, attach = actions::setLocalSink)
                        member.videoEndpoint != null -> {
                            val endpoint = member.videoEndpoint
                            VideoTile(member, mirror = false) { sink -> actions.setRemoteSink(endpoint, sink) }
                        }
                        else -> MemberTile(member)
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 20.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.Top) {
            if (failed == null) {
                GroupButton(route.label, active = route != CallAudioRoute.Earpiece) { router.toggle() }
                if (call.canEnableVideo || call.cameraOn) GroupButton(if (call.cameraOn) "Камера" else "Включить камеру", active = call.cameraOn, icon = "📷") { actions.setCamera(!call.cameraOn) }
                if (call.cameraOn) GroupButton("Повернуть", active = false, icon = "🔄") { actions.switchCamera() }
                GroupButton(
                    if (!call.canUnmute) "Микрофон выключен админом" else if (call.muted) "Включить микрофон" else "Микрофон",
                    active = !call.muted, enabled = call.canUnmute
                ) { actions.setMuted(!call.muted) }
                GroupButton("Выйти", active = true, danger = true) { actions.leave() }
            } else GroupButton("Закрыть", active = true) { actions.dismiss() }
        }
    }
}

@Composable
private fun MemberTile(member: GroupCallMember) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(76.dp).border(if (member.speaking) 3.dp else 0.dp, if (member.speaking) Color.White else Color.Transparent, CircleShape).padding(4.dp),
            contentAlignment = Alignment.Center
        ) { TelegramAvatar(member.name, member.photo, 68.dp) }
        Spacer(Modifier.height(6.dp))
        Text(member.name, color = Color.White, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        Text(
            when { member.handRaised -> "✋ хочет сказать"; member.speaking -> "говорит"; member.muted -> "без звука"; else -> "слушает" },
            color = Color(0x99FFFFFF), fontSize = 11.sp
        )
    }
}

/**
 * A participant's camera (or ours): a WebRTC renderer handed to the engine through [attach] while
 * on screen, released when it leaves.
 */
@Composable
private fun VideoTile(member: GroupCallMember, mirror: Boolean, attach: (org.webrtc.VideoSink?) -> Unit) {
    val context = LocalContext.current
    val renderer = androidx.compose.runtime.remember { org.webrtc.TextureViewRenderer(context) }
    androidx.compose.runtime.DisposableEffect(renderer) {
        renderer.init(com.kirox.app.voip.VideoCapturerDevice.getSharedEGLContext(), null)
        renderer.setScalingType(org.webrtc.RendererCommon.ScalingType.SCALE_ASPECT_FILL)
        attach(renderer)
        onDispose { attach(null); renderer.release() }
    }
    androidx.compose.runtime.LaunchedEffect(mirror) { renderer.setMirror(mirror) }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.fillMaxWidth().height(200.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp)).background(Color(0xFF15171B))
                .border(if (member.speaking) 3.dp else 0.dp, if (member.speaking) Color.White else Color.Transparent, androidx.compose.foundation.shape.RoundedCornerShape(16.dp))
        ) {
            androidx.compose.ui.viewinterop.AndroidView({ renderer }, Modifier.fillMaxSize())
            Text(
                member.name + if (member.muted) " · 🔇" else "", color = Color.White, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.BottomStart).padding(8.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp)).background(Color(0x99000000)).padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
    }
}

@Composable
private fun GroupButton(label: String, active: Boolean, enabled: Boolean = true, danger: Boolean = false, icon: String? = null, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 4.dp)) {
        Box(
            Modifier.size(64.dp).clip(CircleShape)
                .background(when { danger -> Color(0xFFE5484D); active -> Color.White; else -> Color(0x33FFFFFF) })
                .clickable(enabled = enabled, onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Text(
                icon ?: when { danger -> "✕"; label.startsWith("Микрофон") || label.startsWith("Включить микрофон") -> if (active) "🎙" else "🔇"; else -> "🔊" },
                fontSize = 22.sp, color = if (active && !danger) Color.Black else Color.White
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(label, color = if (enabled) Color.White else Color(0x66FFFFFF), fontSize = 12.sp, textAlign = TextAlign.Center, modifier = Modifier.size(width = 96.dp, height = 32.dp))
    }
}

/** "1 участник", "3 участника", "5 участников". */
internal fun participantsLabel(count: Int): String {
    val mod100 = count % 100
    val mod10 = count % 10
    val word = when {
        mod100 in 11..14 -> "участников"
        mod10 == 1 -> "участник"
        mod10 in 2..4 -> "участника"
        else -> "участников"
    }
    return "$count $word"
}
