package com.kirox.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kirox.app.ui.KiroXApp
import com.kirox.app.ui.theme.FusionTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val palette by (application as KiroXApplication).settings.palette.collectAsStateWithLifecycle()
            FusionTheme(palette) {
                KiroXApp()
            }
        }
    }
}
