package com.antares.customtflite

import android.net.Uri
import android.widget.FrameLayout
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.antares.customtflite.ver2.YoloV8Segmentor
import com.antares.customtflite.ver2.DetectionGLTextureView
import com.antares.customtflite.ver2.VideoGLTextureView
import com.antares.customtflite.ver2.VideoPlayerControlScreen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    val videoViewRef = remember { mutableStateOf<VideoGLTextureView?>(null) }


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

        videoUri?.let { uri ->
            AndroidView(factory = {
                FrameLayout(it).apply {
                    val videoView = VideoGLTextureView(it).apply {
                        layoutParams = FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.MATCH_PARENT,
                            FrameLayout.LayoutParams.MATCH_PARENT
                        )
                        setVideoUri(uri)
                        setPlaybackSpeed(1.0f)
                        videoViewRef.value = this
                    }

                    val glOverlay = DetectionGLTextureView(it).apply {
                        layoutParams = FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.MATCH_PARENT,
                            FrameLayout.LayoutParams.MATCH_PARENT
                        )
                    }

                    videoView.onFrameCaptured = { frame ->
                        CoroutineScope(Dispatchers.Default).launch {
                            val contours = yolo.runInference(frame)
                            withContext(Dispatchers.Main) {
                                glOverlay.setContours(contours)
                            }
                        }
                    }
                    addView(videoView)
                    addView(glOverlay)
                }
            }, modifier = Modifier
                .fillMaxWidth()
                .height(300.dp)
            )
        }
        // Управление воспроизведением
        VideoPlayerControlScreen(
            videoUri = videoUri,
            videoViewRef = videoViewRef
        )
    }
}