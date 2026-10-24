package com.kirox.app.ui.splash

import android.content.Context
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kirox.app.R
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

private data class Particle(val x: Float, val y: Float, val radius: Float, val alpha: Float, val phase: Float, val speed: Float)

@Composable
fun AnimatedSplashScreen(contentReady: Boolean, onFinished: () -> Unit) {
    val context = LocalContext.current
    val motionEnabled = remember { motionEnabled(context) }
    var introduced by remember { mutableStateOf(!motionEnabled) }
    val intro by animateFloatAsState(if (introduced) 1f else 0f, tween(if (motionEnabled) 520 else 0, easing = FastOutSlowInEasing), label = "logo_intro")
    val motion = if (motionEnabled) {
        val loop = rememberInfiniteTransition(label = "splash_motion")
        val time by loop.animateFloat(0f, 1f, infiniteRepeatable(tween(9000, easing = LinearEasing)), label = "particles")
        val wave by loop.animateFloat(0f, (PI * 2).toFloat(), infiniteRepeatable(tween(1100, easing = LinearEasing)), label = "wave")
        time to wave
    } else {
        0f to 0f
    }
    val time = motion.first
    val wavePhase = motion.second
    val particles = remember {
        val random = Random(0x4B49524F)
        List(20) {
            Particle(random.nextFloat(), random.nextFloat(), .6f + random.nextFloat() * .9f, .35f + random.nextFloat() * .65f, random.nextFloat(), .35f + random.nextFloat() * .65f)
        }
    }
    LaunchedEffect(Unit) { introduced = true }
    LaunchedEffect(contentReady) { if (contentReady) { withFrameNanos { }; onFinished() } }

    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black)
    ) {
        Canvas(Modifier.fillMaxSize()) {
            particles.forEach { particle ->
                val drift = ((particle.phase + time * particle.speed) % 1f)
                val px = (particle.x + sin((time + particle.phase) * PI * 2).toFloat() * .025f) * size.width
                val py = ((particle.y + drift * .16f) % 1f) * size.height
                val logoZone = px in size.width * .24f..size.width * .76f && py in size.height * .40f..size.height * .62f
                val twinkle = .65f + .35f * sin((time * particle.speed + particle.phase) * PI * 4).toFloat()
                drawCircle(
                    color = Color.White.copy(alpha = particle.alpha * twinkle * intro * if (logoZone) .12f else .8f),
                    radius = particle.radius.dp.toPx(),
                    center = Offset(px, py)
                )
            }
            val arcColor = Color.White.copy(alpha = .11f * intro)
            drawArc(
                arcColor, 205f + time * 22f, 72f, false,
                topLeft = Offset(-size.width * .28f, size.height * .16f),
                size = Size(size.width * .8f, size.height * .42f),
                style = Stroke(1.dp.toPx())
            )
            drawArc(
                arcColor, 18f - time * 18f, 88f, false,
                topLeft = Offset(size.width * .56f, size.height * .48f),
                size = Size(size.width * .72f, size.height * .4f),
                style = Stroke(1.dp.toPx())
            )
        }

        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            androidx.compose.material3.Text(
                text = buildAnnotatedString {
                    append("kiro")
                    withStyle(SpanStyle(color = Color(0xFF77777C), fontWeight = androidx.compose.ui.text.font.FontWeight.Light)) { append("X") }
                },
                color = Color.White.copy(alpha = intro),
                fontSize = 58.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Light,
                modifier = Modifier.graphicsLayer(scaleX = .92f + .08f * intro, scaleY = .92f + .08f * intro)
            )
            Spacer(Modifier.height(24.dp))
            Canvas(Modifier.width(132.dp).height(12.dp)) {
                drawRoundRect(
                    Color(0xFF4A4A4E), topLeft = Offset(0f, size.height * .3f),
                    size = Size(size.width, size.height * .4f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height), style = Stroke(1.dp.toPx())
                )
                val travel = (wavePhase / (PI * 2).toFloat()).coerceIn(0f, 1f)
                val start = (travel * 1.35f - .35f).coerceIn(0f, .72f)
                drawRoundRect(
                    Color.White.copy(alpha = intro), topLeft = Offset(size.width * start, size.height * .32f),
                    size = Size(size.width * .3f, size.height * .36f),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height)
                )
            }
        }
        Column(
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(horizontal = 28.dp, vertical = 46.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            androidx.compose.material3.Text(
                "Больше, чем мессенджер.", color = Color.White, fontSize = 20.sp,
                fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            androidx.compose.material3.Text(
                "Скорость, удобство и свобода общения в одном приложении.",
                color = Color(0xFF8E8E93), fontSize = 14.sp, lineHeight = 20.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

private fun motionEnabled(context: Context): Boolean {
    val powerSave = (context.getSystemService(Context.POWER_SERVICE) as? PowerManager)?.isPowerSaveMode == true
    val scale = runCatching { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) }.getOrDefault(1f)
    return !powerSave && scale > 0f
}
