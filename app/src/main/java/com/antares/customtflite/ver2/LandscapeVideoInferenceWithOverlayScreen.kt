package com.antares.customtflite.ver2

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.antares.customtflite.data.OverlayFrame
import com.antares.customtflite.ver2.player.SpeedDropdownMenuSingle
import com.antares.customtflite.ver2.player.VideoGLTextureView
import com.antares.customtflite.ver2.player.VideoPlayerControlScreen
import com.antares.customtflite.ver2.segmentor.YoloV8Segmentor
import com.antares.customtflite.ver2.segmentor.YoloV8SegmentorV2

@Composable
fun LandscapeVideoInferenceWithOverlayScreen(yolo: YoloV8SegmentorV2) {
    var videoUri by remember { mutableStateOf<Uri?>(null) }
    val videoViewRef = remember { mutableStateOf<VideoGLTextureView?>(null) }
    val overlayBitmapRef = remember { mutableStateOf<OverlayFrame?>(null) }
    val drawerRef = remember { mutableStateOf<YoloContourDrawer?>(null) }
    val scope = rememberCoroutineScope()
    val lastInferenceTime = remember { mutableLongStateOf(0L) }
    val inferenceIntervalMs = 0L
    val lastSize = remember { mutableStateOf<Pair<Int, Int>?>(null) }
    val isProcessing = remember { java.util.concurrent.atomic.AtomicBoolean(false) }
    val confidenceThreshold = 0.35f

    var selectedSpeed by remember { mutableStateOf<Float?>(0.1f) }

    var isPlaying by remember { mutableStateOf(false) }
    val speed by remember { mutableFloatStateOf(0.15f) }

    val videoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri -> videoUri = uri }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        // Отображение видео с оверлеем
        videoUri?.let {
            VideoWithInference(
                it,
                videoViewRef,
                isProcessing,
                lastInferenceTime,
                inferenceIntervalMs,
                scope,
                lastSize,
                drawerRef,
                yolo,
                confidenceThreshold,
                overlayBitmapRef,
                speed,
                isPlayingState = remember { mutableStateOf(isPlaying) })
        }

        // Кнопка выбора видео
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(6.dp)
        ) {
            Row(horizontalArrangement = Arrangement.SpaceBetween){
                Button(modifier = Modifier.weight(1f),
                    onClick = {
                        videoLauncher.launch("video/*")
                    }
                )
                {
                    Text("Открыть видео")
                }
                Spacer(modifier = Modifier.weight(1f))
                Row(modifier = Modifier.weight(1.5f),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Button(onClick = {
                        if (isPlaying) {
                            videoViewRef.value?.pause()
                        } else {
                            videoViewRef.value?.setPlaybackSpeed(speed)
                            videoViewRef.value?.play()
                        }
                        isPlaying = !isPlaying
                    }) {
                        Text(if (isPlaying) "Пауза" else "Воспроизвести")
                    }
                    SpeedDropdownMenuSingle(
                        selectedSpeed = selectedSpeed,
                        onSpeedSelected = { selectedSpeed = it },
                        videoViewRef = videoViewRef
                    )
                }
            }
        }

        // Управление видео
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 6.dp)
        ) {
            VideoPlayerControlScreen(
                videoUri = videoUri,
                videoViewRef = videoViewRef,
                isPlaying = isPlaying,
                speed = speed
            )
        }
    }
}