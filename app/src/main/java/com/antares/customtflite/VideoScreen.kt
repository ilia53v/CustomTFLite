package com.antares.customtflite

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PointF
import android.net.Uri
import android.util.Log
import android.util.Size
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
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.antares.customtflite.data.OverlayFrame
import com.antares.customtflite.ver2.FileUtil
import com.antares.customtflite.ver2.YoloContourDrawer
import com.antares.customtflite.ver2.segmentor.YoloV8Segmentor
import com.antares.customtflite.ver2.player.VideoGLTextureView
import com.antares.customtflite.ver2.player.VideoPlayerControlScreen
import com.antares.customtflite.ver2.saveVideoWithInferenceCanvasOptimized
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

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
    val overlayBitmapRef = remember { mutableStateOf<OverlayFrame?>(null) }
    val drawerRef = remember { mutableStateOf<YoloContourDrawer?>(null) }
    val lastSize = remember { mutableStateOf<Pair<Int, Int>?>(null) }
    val scope = rememberCoroutineScope()
    val lastInferenceTime = remember { mutableStateOf(0L) }
    val isRunning = remember { mutableStateOf(false) }
    val inferenceIntervalMs = 1000L
    val context = LocalContext.current

    val progressPercent = remember { mutableStateOf(0) }
    var saving by remember { mutableStateOf(false) }
    var saveResult by remember { mutableStateOf<String?>(null) }

    val videoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri -> uri?.let { videoUri = it } }

    val saveLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("video/mp4")
    ) { uri ->
        uri?.let { selectedUri ->
            val drawer = drawerRef.value ?: return@let
            scope.launch {
                saving = true
                saveResult = null
                progressPercent.value = 0

                try {
                    val outputFile = FileUtil.fromUri(context, selectedUri) // <-- Utility method

                    saveVideoWithInferenceCanvasOptimized(
                        context = context,
                        inputUri = videoUri!!,
                        outputFile = outputFile,
                        yolo = yolo,
                        drawer = drawer,
                        onProgress = { percent -> progressPercent.value = percent }
                    )

                    saveResult = "Сохранено: ${selectedUri.lastPathSegment}"
                } catch (e: Exception) {
                    saveResult = "Ошибка: ${e.message}"
                    e.printStackTrace()
                } finally {
                    saving = false
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Button(onClick = { videoLauncher.launch("video/*") }) {
            Text("Выбрать видео из галереи")
        }

        if (videoUri != null) {
            Button(
                onClick = {
                    saveLauncher.launch("output_with_overlay.mp4")
                },
                enabled = !saving
            ) {
                Text(if (saving) "Сохраняется..." else "Сохранить видео с масками")
            }

            if (saving) {
                LinearProgressIndicator(
                    progress = progressPercent.value / 100f,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                )
                Text("Прогресс: ${progressPercent.value}%", fontSize = 12.sp)
            }

            saveResult?.let {
                Text(it, fontSize = 12.sp)
            }
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
                            setPlaybackSpeed(0.25f)
                            videoViewRef.value = this
                        }

                        videoView.onFrameCaptured = label@{ frame ->
                            val now = System.currentTimeMillis()
                            if (now - lastInferenceTime.value < inferenceIntervalMs) return@label
                            if (isRunning.value) return@label

                            lastInferenceTime.value = now
                            isRunning.value = true

                            val videoW = frame.width
                            val videoH = frame.height

                            if (lastSize.value != videoW to videoH) {
                                drawerRef.value = YoloContourDrawer(
                                    displaySize = Size(videoW, videoH)
                                )
                                lastSize.value = videoW to videoH
                            }

                            val frozenFrame = frame.copy(Bitmap.Config.ARGB_8888, false)

                            scope.launch {
                                try {
                                    val (contours, _, objects) = withContext(Dispatchers.Default) {
                                        yolo.runInference(frozenFrame)
                                    }

                                    drawerRef.value?.let { drawer ->
                                        val filteredObjects = objects.filter { it.confidence >= 0.3f }

                                        val bboxList = filteredObjects.map { obj ->
                                            Triple(obj.topLeft, obj.bottomRight, obj.confidence)
                                        }

                                        val overlayImage = drawer.drawDetections(
                                            bboxList = bboxList,
                                            allContours = contours,
                                            baseFrame = frozenFrame
                                        )

                                        overlayBitmapRef.value = OverlayFrame(
                                            image = overlayImage,
                                            sourceFrame = frozenFrame
                                        )
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

                overlayBitmapRef.value?.let { overlayFrame ->
                    Image(
                        bitmap = overlayFrame.image.asImageBitmap(),
                        contentDescription = "Overlay Image",
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