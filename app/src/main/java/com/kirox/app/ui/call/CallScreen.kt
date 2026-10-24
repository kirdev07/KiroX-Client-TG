package com.kirox.app.ui.call

import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.VibrationEffect
import android.os.Vibrator
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
}

/** Full-screen call: outgoing, incoming, connected and finished states for voice and video. */
@Composable
fun CallScreen(call: TelegramActiveCall, actions: CallActions) {
    val live = call.phase.isLive()
    BackHandler { if (!live) actions.dismiss() }
    IncomingAlert(call.phase == CallPhase.Incoming)

    var micOff by rememberSaveable(call.id) { mutableStateOf(false) }
    var speaker by rememberSaveable(call.id) { mutableStateOf(call.isVideo) }
    var camera by rememberSaveable(call.id) { mutableStateOf(call.isVideo) }

    Box(
        Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF1C2633), Color(0xFF10151C), Color(0xFF050608))))
            .clickable(enabled = false) {}
    ) {
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(16.dp))
            TopLabel(call)
            Spacer(Modifier.weight(0.6f))
            PulsingAvatar(call, pulsing = call.phase is CallPhase.Calling || call.phase == CallPhase.Incoming || call.phase == CallPhase.Connecting)
            Spacer(Modifier.height(28.dp))
            Text(call.name, color = Color.White, fontSize = 28.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
            Spacer(Modifier.height(8.dp))
            StatusLine(call)
            Spacer(Modifier.weight(1f))
            when {
                call.phase == CallPhase.Incoming -> IncomingControls(call, actions)
                live -> ActiveControls(call.isVideo, micOff, speaker, camera, { micOff = !micOff }, { speaker = !speaker }, { camera = !camera }, actions::hangUp)
                else -> RoundButton(KiroXIcon.Check, "Закрыть", Color.White, Color(0xFF10151C), size = 64.dp, onClick = actions::dismiss)
            }
            Spacer(Modifier.height(36.dp))
        }
        if (call.isVideo && camera && live && call.phase != CallPhase.Incoming) SelfPreview(Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(top = 56.dp, end = 16.dp))
    }
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
            formatDuration(((now - phase.sinceMillis) / 1000).toInt())
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
}

@Composable
private fun IncomingControls(call: TelegramActiveCall, actions: CallActions) {
    val transition = rememberInfiniteTransition(label = "accept")
    val bounce by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "bounce")
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.Bottom) {
        LabeledButton("Отклонить") { RoundButton(KiroXIcon.CallEnd, "Отклонить", CallRed, Color.White, size = 72.dp, onClick = actions::hangUp) }
        if (callsEnabled) LabeledButton(if (call.isVideo) "С видео" else "Принять") {
            Box(Modifier.padding(bottom = (6 * bounce).dp)) {
                RoundButton(if (call.isVideo) KiroXIcon.Video else KiroXIcon.Call, "Принять", CallGreen, Color.White, size = 72.dp, onClick = actions::accept)
            }
        } else Text("Ответьте на другом устройстве", color = Color(0x99FFFFFF), fontSize = 13.sp, modifier = Modifier.width(140.dp), textAlign = TextAlign.Center)
    }
}

@Composable
private fun ActiveControls(
    isVideo: Boolean, micOff: Boolean, speaker: Boolean, camera: Boolean,
    onMic: () -> Unit, onSpeaker: () -> Unit, onCamera: () -> Unit, onEnd: () -> Unit
) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.Top) {
        LabeledButton("Динамик") { ToggleButton(KiroXIcon.Speaker, "Динамик", speaker, onSpeaker) }
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
    Box(Modifier.size(size).clip(CircleShape).background(background).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        IconGlyph(icon, description, Modifier.size(size * 0.42f), tint)
        if (crossed) Canvas(Modifier.size(size * 0.5f)) {
            drawLine(tint, androidx.compose.ui.geometry.Offset(0f, 0f), androidx.compose.ui.geometry.Offset(this.size.width, this.size.height), 2.dp.toPx())
        }
    }
}

@Composable
private fun SelfPreview(modifier: Modifier) {
    Box(
        modifier.size(104.dp, 148.dp).clip(RoundedCornerShape(18.dp)).background(Color(0xFF232C38)).border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(18.dp)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            IconGlyph(KiroXIcon.Camera, null, Modifier.size(26.dp), Color(0x99FFFFFF))
            Spacer(Modifier.height(6.dp))
            Text("Вы", color = Color(0x99FFFFFF), fontSize = 12.sp)
        }
    }
}

/** Ringtone and vibration while an incoming call is waiting. */
@Composable
private fun IncomingAlert(active: Boolean) {
    val context = LocalContext.current
    DisposableEffect(active) {
        if (!active) return@DisposableEffect onDispose {}
        val ringtone = runCatching {
            RingtoneManager.getRingtone(context, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE))?.apply {
                audioAttributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE).build()
                if (android.os.Build.VERSION.SDK_INT >= 28) isLooping = true
                play()
            }
        }.getOrNull()
        @Suppress("DEPRECATION")
        val vibrator = context.getSystemService(Vibrator::class.java)
        val pattern = longArrayOf(0, 600, 900)
        runCatching {
            if (android.os.Build.VERSION.SDK_INT >= 26) vibrator?.vibrate(VibrationEffect.createWaveform(pattern, 0)) else vibrator?.vibrate(pattern, 0)
        }
        onDispose {
            runCatching { ringtone?.stop() }
            runCatching { vibrator?.cancel() }
        }
    }
}

internal fun formatDuration(seconds: Int): String {
    val s = seconds.coerceAtLeast(0)
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s / 60 % 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
}
