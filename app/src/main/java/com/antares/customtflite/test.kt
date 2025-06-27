package com.antares.customtflite

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.net.Uri
import android.util.Log
import android.view.SurfaceView
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.widget.FrameLayout
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.antares.customtflite.check_model.YoloV8VideoTester
import com.antares.customtflite.data.Contour
import com.antares.customtflite.data.Detection
import com.antares.customtflite.ver2.YoloV8Segmentor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import com.antares.customtflite.ver2.DetectionGLTextureView

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoScreen() {
    val context = LocalContext.current
    val yolo = YoloV8Segmentor(context)
    VideoInferenceWithOverlayScreen(yolo)
}


@Composable
fun VideoInferenceWithOverlayScreen(yolo: YoloV8Segmentor) {

    var videoUri by remember { mutableStateOf<Uri?>(null) }
    var playbackSpeed by remember { mutableFloatStateOf(1.0f) }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { videoUri = it }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Button(onClick = { launcher.launch("video/*") }) {
            Text("Выбрать видео из галереи")
        }

        Button(onClick = {
            playbackSpeed = when (playbackSpeed) {
                0.5f -> 1.0f
                1.0f -> 1.5f
                1.5f -> 2.0f
                else -> 0.5f
            }
        }) {
            Text("Скорость x$playbackSpeed")
        }

        videoUri?.let { uri ->
            AndroidView(factory = {
                FrameLayout(it).apply {
                    val videoView = VideoGLTextureView(it).apply {
                        layoutParams = FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.MATCH_PARENT,
                            FrameLayout.LayoutParams.MATCH_PARENT
                        )
                        setVideoUri(uri)
                        setPlaybackSpeed(playbackSpeed)
                    }

                    val glOverlay = DetectionGLTextureView(it).apply {
                        layoutParams = FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.MATCH_PARENT,
                            FrameLayout.LayoutParams.MATCH_PARENT
                        )
                    }

                    videoView.onFrameCaptured = { frame ->
                        val contours = yolo.runContoursOnBitmap(frame)
                        glOverlay.setContours(contours)
                    }

                    addView(videoView)
                    addView(glOverlay)
                }
            }, modifier = Modifier.fillMaxSize())
        }
    }
}