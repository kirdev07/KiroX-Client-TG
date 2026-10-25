package com.kirox.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.CompositionLocalProvider
import com.kirox.app.ui.KiroXApp
import com.kirox.app.ui.components.LocalSeasonalEffects
import com.kirox.app.ui.components.LocalEffectScene
import com.kirox.app.ui.theme.LocalLightEffects
import com.kirox.app.settings.EffectsQuality
import com.kirox.app.settings.prefersLightEffects
import androidx.compose.runtime.remember
import com.kirox.app.ui.theme.LocalBubbleRadius
import com.kirox.app.ui.theme.LocalMotionFactor
import com.kirox.app.ui.theme.FusionTheme

class MainActivity : ComponentActivity() {
    private val sceneClock = com.kirox.app.ui.components.SceneClock()

    // Any touch wakes header animations that went idle.
    override fun dispatchTouchEvent(event: android.view.MotionEvent): Boolean {
        if (event.actionMasked == android.view.MotionEvent.ACTION_DOWN) sceneClock.poke()
        return super.dispatchTouchEvent(event)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val palette by (application as KiroXApplication).settings.palette.collectAsStateWithLifecycle()
            val settings = (application as KiroXApplication).settings
            val motion by settings.motion.collectAsStateWithLifecycle()
            val bubbles by settings.bubbleShape.collectAsStateWithLifecycle()
            val seasonal by settings.seasonalEffects.collectAsStateWithLifecycle()
            val quality by settings.effectsQuality.collectAsStateWithLifecycle()
            val scene by settings.effectScene.collectAsStateWithLifecycle()
            val textSize by settings.chatTextSize.collectAsStateWithLifecycle()
            val wallpaper by settings.chatWallpaper.collectAsStateWithLifecycle()
            val density by settings.chatDensity.collectAsStateWithLifecycle()
            val chatStyle = com.kirox.app.settings.ChatStyle(bubbles.radiusDp, textSize.scale, wallpaper, density)
            val weakDevice = remember { prefersLightEffects(this@MainActivity) }
            val lightEffects = quality == EffectsQuality.Light || (quality == EffectsQuality.Auto && weakDevice)
            sceneClock.Drive(speed = motion.factor, light = lightEffects)
            FusionTheme(palette) {
                CompositionLocalProvider(LocalMotionFactor provides motion.factor, LocalBubbleRadius provides bubbles.radiusDp, LocalSeasonalEffects provides seasonal, LocalLightEffects provides lightEffects, LocalEffectScene provides scene, com.kirox.app.ui.theme.LocalChatStyle provides chatStyle, com.kirox.app.ui.components.LocalSceneClock provides sceneClock) {
                    KiroXApp()
                }
            }
        }
    }
}
