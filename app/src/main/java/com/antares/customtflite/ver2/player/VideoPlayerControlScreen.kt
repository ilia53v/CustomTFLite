package com.antares.customtflite.ver2.player

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.Button
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Composable
fun VideoPlayerControlScreen(
    videoUri: Uri?,
    videoViewRef: MutableState<VideoGLTextureView?>,
    isPlaying: Boolean,
    speed: Float,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var value by remember { mutableStateOf(0f) }

    var volume by remember { mutableStateOf(1.0f) }
    var duration by remember { mutableStateOf(0) }
    var position by remember { mutableStateOf(0) }

    // Автообновление позиции видео
    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            videoViewRef.value?.let {
                position = it.getCurrentPosition()
                duration = it.getDuration()
            }
            delay(300L)
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(200.dp) // высота блока с элементами управления
            .padding(start = 8.dp, end = 8.dp)
    ) {
        // Вертикальный слайдер громкости
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxHeight()
                .width(60.dp)
        ) {
            VerticalSlider(
                value = volume,
                onValueChange = {
                    volume = it
                    videoViewRef.value?.setVolume(volume)
                },
                modifier = Modifier
                    .width(200.dp)
                    .height(50.dp)
                    .background(Color(0xffdedede))
            )

            Text(
                text = "${(volume * 100).toInt()}%",
                color = Color.Black,
                fontSize = 12.sp,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 4.dp)
            )
        }

        // Горизонтальные элементы управления (позиция видео)
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(0.8f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Позиция: ${position / 1000}s / ${duration / 1000}s")
            Slider(
                value = position.toFloat(),
                onValueChange = {
                    videoViewRef.value?.seekTo(it.toInt())
                    position = it.toInt()
                },
                valueRange = 0f..(duration.toFloat().coerceAtLeast(1f)),
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = Color(0xFF4CAF50),
                    inactiveTrackColor = Color.Gray.copy(alpha = 0.4f),
                )
            )
        }
    }
}