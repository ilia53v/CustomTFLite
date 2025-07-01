package com.antares.customtflite

import android.net.Uri
import android.util.Log
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.antares.customtflite.ver2.YoloV8Segmentor
import com.antares.customtflite.ver2.DetectionGLTextureView
import com.antares.customtflite.ver2.VideoGLTextureView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoScreen() {
    val context = LocalContext.current

    var selectedVideoUri by remember { mutableStateOf<Uri?>(null) }
    val yolo = YoloV8Segmentor(context)

    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        selectedVideoUri = uri
    }

    Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(16.dp))

        Button(onClick = {
            videoPickerLauncher.launch("video/*")
        }) {
            Text("Выбрать видео из галереи")
        }

        Spacer(Modifier.height(16.dp))

        selectedVideoUri?.let { uri ->
            VideoInferenceWithOverlayScreen(videoUri = uri, yolo)
        }
    }
}


@Composable
fun VideoInferenceWithOverlayScreen(
    videoUri: Uri,
    yoloSegmentor: YoloV8Segmentor,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    // ref для управления VideoGLTextureView
    val videoViewRef = remember { mutableStateOf<VideoGLTextureView?>(null) }
    // ref для управления DetectionGLTextureView
    val detectionViewRef = remember { mutableStateOf<DetectionGLTextureView?>(null) }

    // Состояния управления воспроизведением
    var isPlaying by remember { mutableStateOf(false) }
    var videoDuration by remember { mutableStateOf(0L) }
    var currentPosition by remember { mutableStateOf(0L) }

    LaunchedEffect(videoUri) {
        videoViewRef.value?.setVideoUri(videoUri)
        videoViewRef.value?.start()
        isPlaying = true

        while (true) {
            delay(200L)
            val pos = videoViewRef.value?.getCurrentPosition() ?: 0
            val dur = videoViewRef.value?.getDuration() ?: 0
            currentPosition = pos.toLong()
            videoDuration = dur.toLong()
            if (!isPlaying) break
        }
    }

    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            val bitmap = videoViewRef.value?.captureFrame() ?: continue
            val detections = withContext(Dispatchers.Default) {
                yoloSegmentor.runInference(bitmap)
            }

            val contours = detections.mapNotNull { it.takeIf { it.isNotEmpty() } }

            Log.d("VideoScreen", "Contours count: ${contours.size}")
            contours.forEachIndexed { index, contour ->
                Log.d("VideoScreen", "Contour #$index points count: ${contour.size}")
            }

            detectionViewRef.value?.setContours(contours)

            delay(500L)
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                VideoGLTextureView(ctx).apply {
                    videoViewRef.value = this
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        AndroidView(
            factory = { ctx ->
                DetectionGLTextureView(ctx).apply {
                    detectionViewRef.value = this
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    alpha = 1.0f
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(Color(0xAA000000))
                .padding(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth()
            ) {
                IconButton(onClick = {
                    if (isPlaying) {
                        videoViewRef.value?.pause()
                    } else {
                        videoViewRef.value?.start()
                    }
                    isPlaying = !isPlaying
                }) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Close else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = Color.White
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Text(
                    text = "${formatTime(currentPosition)} / ${formatTime(videoDuration)}",
                    color = Color.White
                )
            }
        }
    }
}


private fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%02d:%02d", minutes, seconds)
}