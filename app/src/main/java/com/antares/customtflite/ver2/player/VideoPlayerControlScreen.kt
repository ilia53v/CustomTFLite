package com.antares.customtflite.ver2.player

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

@Composable
fun VideoPlayerControlScreen(
    videoUri: Uri?,
    videoViewRef: MutableState<VideoGLTextureView?>,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var isPlaying by remember { mutableStateOf(false) }
    var volume by remember { mutableStateOf(1.0f) }
    var speed by remember { mutableStateOf(1.0f) }
    var duration by remember { mutableStateOf(0) }
    var position by remember { mutableStateOf(0) }

    // Auto update position
    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            videoViewRef.value?.let {
                position = it.getCurrentPosition()
                duration = it.getDuration()
            }
            delay(500L)
        }
    }

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                if (isPlaying) {
                    videoViewRef.value?.pause()
                } else {
                    videoViewRef.value?.play()
                }
                isPlaying = !isPlaying
            }) {
                Text(if (isPlaying) "Пауза" else "Воспроизвести")
            }

            Button(onClick = {
                speed = when (speed) {
                    0.5f -> 1.0f
                    1.0f -> 1.5f
                    1.5f -> 2.0f
                    else -> 0.5f
                }
                videoViewRef.value?.setPlaybackSpeed(speed)
            }) {
                Text("Скорость x$speed")
            }
        }

        Text("Громкость: ${(volume * 100).toInt()}%")
        Slider(
            value = volume,
            onValueChange = {
                volume = it
                videoViewRef.value?.setVolume(volume)
            },
            valueRange = 0f..1f
        )

        Text("Позиция: ${position / 1000}s / ${duration / 1000}s")
        Slider(
            value = position.toFloat(),
            onValueChange = {
                videoViewRef.value?.seekTo(it.toInt())
                position = it.toInt()
            },
            valueRange = 0f..(duration.toFloat().coerceAtLeast(1f))
        )
    }
}
