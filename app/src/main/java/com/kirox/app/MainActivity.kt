package com.kirox.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.CompositionLocalProvider
import com.kirox.app.ui.KiroXApp
import com.kirox.app.ui.theme.LocalBubbleRadius
import com.kirox.app.ui.theme.LocalMotionFactor
import com.kirox.app.ui.theme.FusionTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val palette by (application as KiroXApplication).settings.palette.collectAsStateWithLifecycle()
            val settings = (application as KiroXApplication).settings
            val motion by settings.motion.collectAsStateWithLifecycle()
            val bubbles by settings.bubbleShape.collectAsStateWithLifecycle()
            FusionTheme(palette) {
                CompositionLocalProvider(LocalMotionFactor provides motion.factor, LocalBubbleRadius provides bubbles.radiusDp) {
                    KiroXApp()
                }
            }
        }
    }
}
