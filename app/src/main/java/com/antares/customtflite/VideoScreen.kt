package com.antares.customtflite

import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import android.util.Log
import android.util.SizeF
import android.widget.FrameLayout
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.antares.customtflite.ver2.YoloContourDrawer
import com.antares.customtflite.ver2.segmentor.YoloV8Segmentor
import com.antares.customtflite.ver2.player.VideoGLTextureView
import com.antares.customtflite.ver2.player.VideoPlayerControlScreen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
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
    val videoViewRef = remember { mutableStateOf<VideoGLTextureView?>(null) }

    // Кэш отрисовки
    val overlayBitmapRef = remember { mutableStateOf<Bitmap?>(null) }
    val drawerRef = remember { mutableStateOf<YoloContourDrawer?>(null) }
    val lastSize = remember { mutableStateOf<Pair<Int, Int>?>(null) }
    val scope = rememberCoroutineScope()
    // throttle + флаг
    val lastInferenceTime = remember { mutableStateOf(0L) }
    val isRunning = remember { mutableStateOf(false) }
    val inferenceIntervalMs = 1000L


    // Поток для передачи кадров с дебаунсом
    val frameFlow = remember { MutableSharedFlow<Bitmap>(extraBufferCapacity = 1) }

    // Лаунчим обработку кадров с ограничением частоты
    LaunchedEffect(frameFlow) {
        frameFlow
            .debounce(500)
            .collectLatest { frame ->
                val contours = withContext(Dispatchers.Default) {
                    yolo.runInference(frame)
                }
                Log.d("VideoInference", "Contours count: ${contours.size}")
                withContext(Dispatchers.Main) {
                    val overlay = overlayBitmapRef.value
                    val drawer = drawerRef.value
                    if (overlay != null && drawer != null) {
                        drawer.drawContours(contours, overlay)
                    }
                }
            }
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri -> uri?.let { videoUri = it } }

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
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
            ) {
                AndroidView(factory = { context ->
                    FrameLayout(context).apply {
                        val videoView = VideoGLTextureView(context).apply {
                            layoutParams = FrameLayout.LayoutParams(
                                FrameLayout.LayoutParams.MATCH_PARENT,
                                FrameLayout.LayoutParams.MATCH_PARENT
                            )
                            setVideoUri(uri)
                            setPlaybackSpeed(1.0f)
                            videoViewRef.value = this
                        }

                        videoView.onFrameCaptured = label@{ frame ->
                            val now = System.currentTimeMillis()
                            if (now - lastInferenceTime.value < inferenceIntervalMs) return@label
                            if (isRunning.value) return@label

                            lastInferenceTime.value = now
                            isRunning.value = true

                            val width = frame.width
                            val height = frame.height

                            if (lastSize.value != width to height) {
                                overlayBitmapRef.value = Bitmap.createBitmap(
                                    width, height, Bitmap.Config.ARGB_8888
                                )
                                drawerRef.value = YoloContourDrawer(SizeF(width.toFloat(), height.toFloat()))
                                lastSize.value = width to height
                            }

                            scope.launch {
                                try {
                                    val contours = withContext(Dispatchers.Default) {
                                        yolo.runInference(frame)
                                    }

                                    val overlay = overlayBitmapRef.value
                                    val drawer = drawerRef.value
                                    if (overlay != null && drawer != null) {
                                        drawer.drawContours(contours, overlay)
                                    }
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                } finally {
                                    isRunning.value = false
                                }
                            }
                        }
                        addView(videoView)
                    }
                }, modifier = Modifier.matchParentSize())

                overlayBitmapRef.value?.let { bitmap ->
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "Contours",
                        modifier = Modifier.matchParentSize()
                    )
                }
            }
        }

        VideoPlayerControlScreen(
            videoUri = videoUri,
            videoViewRef = videoViewRef
        )
    }
}