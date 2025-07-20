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
import com.antares.customtflite.data.OverlayFrame
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
    val overlayBitmapRef = remember { mutableStateOf<OverlayFrame?>(null) }
    val drawerRef = remember { mutableStateOf<YoloContourDrawer?>(null) }
    val lastSize = remember { mutableStateOf<Pair<Int, Int>?>(null) }
    val scope = rememberCoroutineScope()
    val lastInferenceTime = remember { mutableStateOf(0L) }
    val isRunning = remember { mutableStateOf(false) }
    val inferenceIntervalMs = 1000L

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
                            setPlaybackSpeed(0.5f)
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
                            val inferenceSize = 640

                            if (lastSize.value != videoW to videoH) {
                                drawerRef.value = YoloContourDrawer(
                                    displaySize = Size(videoW, videoH)
                                )
                                lastSize.value = videoW to videoH
                            }

                            val frozenFrame = frame.copy(Bitmap.Config.ARGB_8888, false)
                            val displaySize = drawerRef.value?.displaySize
                            Log.i("Video", "Frame=${frozenFrame.width}x${frozenFrame.height}, Display=${displaySize?.width}x${displaySize?.height}")

                            scope.launch {
                                try {
                                    val (contours, rawMask, objects) = withContext(Dispatchers.Default) {
                                        yolo.runInference(frozenFrame)
                                    }

                                    val drawer = drawerRef.value
                                    if (drawer != null) {
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

                // Наложение маски и bbox
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