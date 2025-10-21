package com.antares.customtflite

import android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
import android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Modifier

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestedOrientation = SCREEN_ORIENTATION_SENSOR_LANDSCAPE

        setContent {
                Surface(modifier = Modifier.fillMaxSize()) {
                    VideoScreen()
                }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Можно вернуть в норму (если есть другие Activity)
        requestedOrientation = SCREEN_ORIENTATION_UNSPECIFIED
    }
}