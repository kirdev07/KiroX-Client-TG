package com.kirox.app.ui.splash

import android.content.Context
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import com.kirox.app.ui.components.SeasonalLayer
import com.kirox.app.ui.components.SeasonalMode
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

private const val SplashDurationMs = 3_500
private const val DesignTimelineMs = 2_500f

private data class SplashParticle(
    val x: Float,
    val y: Float,
    val radius: Float,
    val alpha: Float,
    val phase: Float,
    val speed: Float
)

@Composable
fun AnimatedSplashScreen2(
    contentReady: Boolean,
    playFullAnimation: Boolean,
    onFinished: () -> Unit
) {
    val context = LocalContext.current
    val effectsEnabled = remember { systemAnimationsEnabled(context) }
    val shouldAnimate = playFullAnimation && effectsEnabled
    val elapsed = remember { Animatable(if (shouldAnimate) 0f else SplashDurationMs.toFloat()) }
    var timelineFinished by remember { mutableStateOf(!shouldAnimate) }

    LaunchedEffect(shouldAnimate) {
        if (shouldAnimate) {
            elapsed.animateTo(
                targetValue = SplashDurationMs.toFloat(),
                animationSpec = tween(SplashDurationMs, easing = LinearEasing)
            )
        }
        timelineFinished = true
    }
    LaunchedEffect(contentReady, timelineFinished) {
        if (contentReady && timelineFinished) onFinished()
    }

    val ms = elapsed.value
    val timelineMs = ms * DesignTimelineMs / SplashDurationMs
    val particleIntro = segment(timelineMs, 0f, 300f)
    val logoReveal = segment(timelineMs, 300f, 1_000f)
    val accentWindow = segment(timelineMs, 1_000f, 1_500f)
    val indicatorIntro = segment(timelineMs, 1_000f, 1_500f)
    val copyIntro = segment(timelineMs, 1_500f, 2_100f)
    val exitAlpha = 1f - segment(timelineMs, 2_100f, 2_500f)
    val accent = sin(accentWindow * PI).toFloat().coerceAtLeast(0f)
    val particles = remember {
        val random = Random(0x4B49524F)
        List(28) {
            SplashParticle(
                x = random.nextFloat(),
                y = random.nextFloat(),
                radius = .55f + random.nextFloat() * 1.15f,
                alpha = .2f + random.nextFloat() * .65f,
                phase = random.nextFloat(),
                speed = .45f + random.nextFloat() * 1.25f
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .graphicsLayer { alpha = exitAlpha }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val seconds = ms / 1_000f
            particles.forEach { particle ->
                val px = ((particle.x + seconds * particle.speed * .012f) % 1f) * size.width
                val drift = sin((seconds * particle.speed + particle.phase) * PI * 2).toFloat()
                val py = particle.y * size.height + drift * 10.dp.toPx()
                val twinkle = .72f + .28f * sin((seconds * particle.speed + particle.phase) * PI * 3).toFloat()
                drawCircle(
                    color = Color.White.copy(alpha = particle.alpha * twinkle.coerceIn(.3f, 1f) * particleIntro),
                    radius = particle.radius.dp.toPx(),
                    center = Offset(px, py)
                )
            }
        }

        SeasonalLayer(
            Modifier.fillMaxSize().graphicsLayer { alpha = particleIntro },
            mode = SeasonalMode.Splash
        )

        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = buildAnnotatedString {
                    append("kiro")
                    withStyle(
                        SpanStyle(
                            color = Color(
                                red = .45f + .55f * accent,
                                green = .45f + .55f * accent,
                                blue = .47f + .53f * accent
                            ),
                            fontWeight = FontWeight.Light
                        )
                    ) { append("X") }
                },
                color = Color.White,
                fontSize = 58.sp,
                fontWeight = FontWeight.Light,
                modifier = Modifier
                    .graphicsLayer {
                        scaleX = .94f + .06f * logoReveal
                        scaleY = .94f + .06f * logoReveal
                        alpha = logoReveal
                    }
                    .drawWithContent {
                        clipRect(right = size.width * logoReveal) { this@drawWithContent.drawContent() }
                    }
            )
            Spacer(Modifier.height(22.dp))
            Canvas(Modifier.width(144.dp).height(18.dp).graphicsLayer { alpha = indicatorIntro }) {
                val path = Path()
                val phase = ms / 135f
                val amplitude = 2.3.dp.toPx()
                val centerY = size.height / 2f
                val steps = 48
                for (step in 0..steps) {
                    val x = size.width * step / steps
                    val y = centerY + sin(step * .42f - phase).toFloat() * amplitude
                    if (step == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(
                    path = path,
                    color = Color.White.copy(alpha = .35f + .65f * indicatorIntro),
                    style = Stroke(width = 1.15.dp.toPx())
                )
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 28.dp, vertical = 46.dp)
                .graphicsLayer {
                    alpha = copyIntro
                    translationY = (1f - copyIntro) * 10.dp.toPx()
                },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Больше, чем мессенджер.",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Скорость, удобство и свобода общения в одном приложении.",
                color = Color(0xFF8E8E93),
                fontSize = 14.sp,
                lineHeight = 20.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

private fun segment(value: Float, start: Float, end: Float): Float =
    ((value - start) / (end - start)).coerceIn(0f, 1f)

internal fun systemAnimationsEnabled(context: Context): Boolean {
    val powerSave = (context.getSystemService(Context.POWER_SERVICE) as? PowerManager)?.isPowerSaveMode == true
    val scale = runCatching {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
    }.getOrDefault(1f)
    return !powerSave && scale > 0f
}
