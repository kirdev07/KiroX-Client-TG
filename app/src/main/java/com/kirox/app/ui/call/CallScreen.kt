package com.kirox.app.ui.call

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kirox.app.BuildConfig
import com.kirox.app.telegram.CallEndReason
import com.kirox.app.telegram.CallPhase
import com.kirox.app.telegram.TelegramActiveCall
import com.kirox.app.telegram.TelegramVoipRepository
import com.kirox.app.telegram.isLive
import com.kirox.app.ui.components.TelegramAvatar
import com.kirox.app.ui.icons.KiroXIcon
import com.kirox.app.voip.CallAudioRoute
import com.kirox.app.voip.CallAudioRouter
import com.kirox.app.ui.icons.KiroXIcon as IconGlyph
import com.kirox.app.ui.theme.Fusion
import kotlinx.coroutines.delay

private val CallGreen = Color(0xFF34C759)
private val CallRed = Color(0xFFFF453A)
private val ButtonGlass = Color(0x26FFFFFF)

/** Whether this build may place and answer calls: with the media engine, or in debug as a signaling test. */
val callsEnabled: Boolean get() = TelegramVoipRepository.mediaAvailable || BuildConfig.DEBUG

interface CallActions {
    fun accept()
    fun hangUp()
    fun dismiss()
    fun setMuted(muted: Boolean)
    fun setCamera(on: Boolean)
    fun switchCamera()
    fun setVideoSinks(remote: org.webrtc.VideoSink?, local: org.webrtc.VideoSink?)
}

/** Full-screen call: outgoing, incoming, connected and finished states for voice and video. */
@Composable
fun CallScreen(call: TelegramActiveCall, actions: CallActions) {
    val live = call.phase.isLive()
    BackHandler { if (!live) actions.dismiss() }
    IncomingAlert(call.phase == CallPhase.Incoming)
    val talking = live && call.phase != CallPhase.Incoming
    // Switch audio to call mode only once connected: during ringing other apps on the phone must not see a busy line.
    val router = callAudioRouter(call.id, active = call.phase is CallPhase.Active || call.phase == CallPhase.Connecting, video = call.isVideo)
    val route by router.route.collectAsState()
    val routes by router.available.collectAsState()
    CallWindow(live = live, earMode = talking && route == CallAudioRoute.Earpiece && !call.cameraOn && !call.remoteVideo)
    val showVideo = TelegramVoipRepository.mediaAvailable && talking && (call.remoteVideo || call.cameraOn)

    Box(
        Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF1C2633), Color(0xFF10151C), Color(0xFF050608))))
            .clickable(enabled = false) {}
    ) {
        if (showVideo) CallVideo(call, actions)
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(16.dp))
            TopLabel(call)
            if (call.remoteVideo && talking) {
                Spacer(Modifier.height(12.dp))
                Text(call.name, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                StatusLine(call)
                Spacer(Modifier.weight(1f))
            } else {
                Spacer(Modifier.weight(0.6f))
                PulsingAvatar(call, pulsing = call.phase is CallPhase.Calling || call.phase == CallPhase.Incoming || call.phase == CallPhase.Connecting)
                Spacer(Modifier.height(28.dp))
                Text(call.name, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                Spacer(Modifier.height(8.dp))
                StatusLine(call)
                Spacer(Modifier.weight(1f))
            }
            when {
                call.phase == CallPhase.Incoming -> IncomingControls(call, actions)
                live -> ActiveControls(
                    call.isVideo || call.cameraOn, call.micMuted, route, routes.size > 2, call.cameraOn,
                    { actions.setMuted(!call.micMuted) }, router::toggle, { actions.setCamera(!call.cameraOn) }, actions::switchCamera, actions::hangUp
                )
                else -> RoundButton(KiroXIcon.Check, "Закрыть", Color.White, Color(0xFF10151C), size = 64.dp, onClick = actions::dismiss)
            }
            Spacer(Modifier.height(36.dp))
        }
    }
}

/** Remote video full screen and the own camera in a corner, rendered by WebRTC. */
@Composable
private fun CallVideo(call: TelegramActiveCall, actions: CallActions) {
    val context = LocalContext.current
    val remote = remember { org.webrtc.TextureViewRenderer(context) }
    val local = remember { org.webrtc.TextureViewRenderer(context) }
    DisposableEffect(Unit) {
        val egl = com.kirox.app.voip.VideoCapturerDevice.getSharedEGLContext()
        remote.init(egl, null)
        local.init(egl, null)
        remote.setScalingType(org.webrtc.RendererCommon.ScalingType.SCALE_ASPECT_FILL)
        local.setScalingType(org.webrtc.RendererCommon.ScalingType.SCALE_ASPECT_FILL)
        actions.setVideoSinks(remote, local)
        onDispose {
            actions.setVideoSinks(null, null)
            remote.release()
            local.release()
        }
    }
    LaunchedEffect(call.frontCamera) { local.setMirror(call.frontCamera) }
    Box(Modifier.fillMaxSize()) {
        if (call.remoteVideo) androidx.compose.ui.viewinterop.AndroidView({ remote }, Modifier.fillMaxSize())
        if (call.cameraOn) Box(
            Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(top = 56.dp, end = 16.dp)
                .size(104.dp, 148.dp).clip(RoundedCornerShape(18.dp)).border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(18.dp))
        ) { androidx.compose.ui.viewinterop.AndroidView({ local }, Modifier.fillMaxSize()) }
    }
}

/** Shows the call over the lock screen, and turns the screen off at the ear like a phone call. */
@Composable
private fun CallWindow(live: Boolean, earMode: Boolean) {
    val context = LocalContext.current
    val activity = context as? android.app.Activity
    DisposableEffect(live) {
        if (live && activity != null) { activity.setShowWhenLocked(true); activity.setTurnScreenOn(true) }
        onDispose {
            if (activity != null) { activity.setShowWhenLocked(false); activity.setTurnScreenOn(false) }
        }
    }
    // Held by CallMedia, which also lets it go when the call ends while this screen is not updating.
    DisposableEffect(earMode) {
        com.kirox.app.voip.CallMedia.setProximity(context, earMode)
        onDispose { com.kirox.app.voip.CallMedia.setProximity(context, false) }
    }
}

/**
 * Call audio routing for [callId]: switched on while [active] (connected), back to normal after.
 * Asks for the Bluetooth permission once on Android 12+, so a connected headset can be used.
 */
@Composable
private fun callAudioRouter(callId: Int, active: Boolean, video: Boolean): CallAudioRouter {
    val context = LocalContext.current
    // Owned by CallMedia for the whole call: started and stopped with the call, not with this screen.
    val router = remember(callId) { com.kirox.app.voip.CallMedia.router(context, callId) }
    val bluetoothPermission = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted && active) { router.stop(); router.start(video) } }
    LaunchedEffect(router, active) {
        if (active) {
            if (!router.isRunning) router.start(video)
            if (android.os.Build.VERSION.SDK_INT >= 31 &&
                context.checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT) != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) runCatching { bluetoothPermission.launch(android.Manifest.permission.BLUETOOTH_CONNECT) }
        }
    }
    return router
}

@Composable
private fun TopLabel(call: TelegramActiveCall) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Canvas(Modifier.size(12.dp)) {
                // Small lock: end-to-end encrypted call.
                val w = size.width
                drawRoundRect(Color(0xB3FFFFFF), topLeft = androidx.compose.ui.geometry.Offset(w * 0.1f, w * 0.45f), size = androidx.compose.ui.geometry.Size(w * 0.8f, w * 0.55f), cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.12f))
                drawArc(Color(0xB3FFFFFF), 180f, 180f, false, topLeft = androidx.compose.ui.geometry.Offset(w * 0.25f, w * 0.08f), size = androidx.compose.ui.geometry.Size(w * 0.5f, w * 0.7f), style = androidx.compose.ui.graphics.drawscope.Stroke(w * 0.12f))
            }
            Spacer(Modifier.width(6.dp))
            Text(if (call.isVideo) "Видеозвонок kiroX" else "Звонок kiroX", color = Color(0xB3FFFFFF), fontSize = 13.sp)
        }
        if (!TelegramVoipRepository.mediaAvailable) {
            Spacer(Modifier.height(8.dp))
            Text(
                "Тест сигналинга · звук и видео пока не подключены", color = Color(0xFFFFD60A), fontSize = 11.sp,
                modifier = Modifier.clip(RoundedCornerShape(50)).background(Color(0x22FFD60A)).padding(horizontal = 10.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
private fun PulsingAvatar(call: TelegramActiveCall, pulsing: Boolean) {
    val transition = rememberInfiniteTransition(label = "call_pulse")
    val wave by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(1800, easing = LinearEasing), RepeatMode.Restart), label = "wave")
    Box(Modifier.size(220.dp), contentAlignment = Alignment.Center) {
        if (pulsing) Canvas(Modifier.fillMaxSize()) {
            repeat(2) { i ->
                val p = (wave + i * 0.5f) % 1f
                drawCircle(Color.White.copy(alpha = 0.16f * (1f - p)), radius = size.minDimension * (0.3f + 0.2f * p))
            }
        } else Canvas(Modifier.fillMaxSize()) {
            drawCircle(Brush.radialGradient(listOf(Color(0x33FFFFFF), Color.Transparent)), radius = size.minDimension * 0.5f)
        }
        TelegramAvatar(call.name, call.photo, 128.dp)
    }
}

@Composable
private fun StatusLine(call: TelegramActiveCall) {
    val text = when (val phase = call.phase) {
        is CallPhase.Calling -> if (phase.ringing) "Звонок идёт…" else "Вызов…"
        CallPhase.Incoming -> if (call.isVideo) "Входящий видеозвонок" else "Входящий звонок"
        CallPhase.Connecting -> "Соединение…"
        is CallPhase.Active -> {
            var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
            LaunchedEffect(phase.sinceMillis) { while (true) { now = System.currentTimeMillis(); delay(500) } }
            when {
                phase.reconnecting -> "Переподключение…"
                call.signalBars <= 1 -> formatDuration(((now - phase.sinceMillis) / 1000).toInt()) + " · плохая связь"
                else -> formatDuration(((now - phase.sinceMillis) / 1000).toInt())
            }
        }
        is CallPhase.Ended -> when (phase.reason) {
            CallEndReason.Declined -> if (call.isOutgoing) "Звонок отклонён" else "Вы отклонили звонок"
            CallEndReason.Missed -> if (call.isOutgoing) "Нет ответа" else "Пропущенный звонок"
            CallEndReason.Disconnected -> "Связь прервалась"
            CallEndReason.HungUp -> if (phase.durationSeconds > 0) "Звонок завершён · ${formatDuration(phase.durationSeconds)}" else "Звонок завершён"
        }
        is CallPhase.Failed -> phase.message
    }
    val muted = call.phase is CallPhase.Ended || call.phase is CallPhase.Failed
    Text(text, color = if (call.phase is CallPhase.Failed) CallRed else if (muted) Color(0x99FFFFFF) else Color(0xCCFFFFFF), fontSize = 16.sp, textAlign = TextAlign.Center)
    if (call.phase is CallPhase.Active && call.remoteMuted) {
        Spacer(Modifier.height(6.dp))
        Text("У собеседника выключен микрофон", color = Color(0x99FFFFFF), fontSize = 13.sp, textAlign = TextAlign.Center)
    }
}

@Composable
private fun IncomingControls(call: TelegramActiveCall, actions: CallActions) {
    // After a cold start from a push the engine may still be loading: the Accept button appears then.
    val engineReady by com.kirox.app.voip.NativeCall.ready.collectAsState()
    val canAnswer = engineReady || callsEnabled
    val transition = rememberInfiniteTransition(label = "accept")
    val bounce by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "bounce")
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.Bottom) {
        LabeledButton("Отклонить") { RoundButton(KiroXIcon.CallEnd, "Отклонить", CallRed, Color.White, size = 72.dp, onClick = actions::hangUp) }
        if (canAnswer) LabeledButton(if (call.isVideo) "С видео" else "Принять") {
            Box(Modifier.padding(bottom = (6 * bounce).dp)) {
                RoundButton(if (call.isVideo) KiroXIcon.Video else KiroXIcon.Call, "Принять", CallGreen, Color.White, size = 72.dp, onClick = actions::accept)
            }
        } else Text("Ответьте на другом устройстве", color = Color(0x99FFFFFF), fontSize = 13.sp, modifier = Modifier.width(140.dp), textAlign = TextAlign.Center)
    }
}

@Composable
private fun ActiveControls(
    isVideo: Boolean, micOff: Boolean, route: CallAudioRoute, manyRoutes: Boolean, camera: Boolean,
    onMic: () -> Unit, onSpeaker: () -> Unit, onCamera: () -> Unit, onFlip: () -> Unit, onEnd: () -> Unit
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.Top) {
        if (isVideo && camera) LabeledButton("Повернуть") { ToggleButton(KiroXIcon.FlipCamera, "Сменить камеру", false, onFlip) }
        // With only the phone and the speaker the button is a speaker switch; with a headset it cycles routes.
        else LabeledButton(if (manyRoutes) route.label else "Динамик") {
            ToggleButton(KiroXIcon.Speaker, "Звук: ${route.label}", route != CallAudioRoute.Earpiece, onSpeaker)
        }
        LabeledButton(if (isVideo) "Камера" else "Видео") { ToggleButton(KiroXIcon.Video, "Камера", camera, onCamera, crossed = !camera) }
        LabeledButton(if (micOff) "Без звука" else "Микрофон") { ToggleButton(KiroXIcon.Voice, "Микрофон", micOff, onMic, crossed = micOff) }
        LabeledButton("Завершить") { RoundButton(KiroXIcon.CallEnd, "Завершить", CallRed, Color.White, size = 60.dp, onClick = onEnd) }
    }
}

@Composable
private fun LabeledButton(label: String, button: @Composable () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(84.dp)) {
        button()
        Spacer(Modifier.height(8.dp))
        Text(label, color = Color(0xCCFFFFFF), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun ToggleButton(icon: KiroXIcon, description: String, on: Boolean, onClick: () -> Unit, crossed: Boolean = false) {
    RoundButton(icon, description, if (on) Color.White else ButtonGlass, if (on) Color(0xFF10151C) else Color.White, size = 60.dp, crossed = crossed, onClick = onClick)
}

@Composable
private fun RoundButton(icon: KiroXIcon, description: String, background: Color, tint: Color, size: Dp, crossed: Boolean = false, onClick: () -> Unit) {
    // Material 3 filled icon button in the call screen's own colours.
    androidx.compose.material3.FilledIconButton(
        onClick = onClick,
        modifier = Modifier.size(size),
        colors = androidx.compose.material3.IconButtonDefaults.filledIconButtonColors(containerColor = background, contentColor = tint)
    ) {
        Box(contentAlignment = Alignment.Center) {
            IconGlyph(icon, description, Modifier.size(size * 0.42f), tint)
            if (crossed) Canvas(Modifier.size(size * 0.5f)) {
                drawLine(tint, androidx.compose.ui.geometry.Offset(0f, 0f), androidx.compose.ui.geometry.Offset(this.size.width, this.size.height), 2.dp.toPx())
            }
        }
    }
}

/** Ringtone and vibration while an incoming call is waiting. */
@Composable
private fun IncomingAlert(active: Boolean) {
    val context = LocalContext.current
    // CallMedia also stops it when the call stops ringing while this screen is in the background.
    DisposableEffect(active) {
        if (active) com.kirox.app.voip.CallMedia.startRinging(context)
        onDispose { com.kirox.app.voip.CallMedia.stopRinging() }
    }
}

internal fun formatDuration(seconds: Int): String {
    val s = seconds.coerceAtLeast(0)
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
}
