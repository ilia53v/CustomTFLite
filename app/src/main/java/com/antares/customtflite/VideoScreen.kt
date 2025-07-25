package com.antares.customtflite

import android.graphics.Bitmap
import android.graphics.PointF
import android.net.Uri
import android.os.Build
import android.util.Size
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.antares.customtflite.data.OverlayFrame
import com.antares.customtflite.ver2.YoloContourDrawer
import com.antares.customtflite.ver2.segmentor.YoloV8Segmentor
import com.antares.customtflite.ver2.player.VideoGLTextureView
import com.antares.customtflite.ver2.player.VideoPlayerControlScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    val scope = rememberCoroutineScope()
    val lastInferenceTime = remember { mutableStateOf(0L) }
    val inferenceIntervalMs = 150L
    val lastSize = remember { mutableStateOf<Pair<Int, Int>?>(null) }
    val isProcessing = remember { java.util.concurrent.atomic.AtomicBoolean(false) }

    val videoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri -> videoUri = uri }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Button(onClick = { videoLauncher.launch("video/*") }) {
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
                            setPlaybackSpeed(0.25f)
                            videoViewRef.value = this
                        }

                        /*videoView.setOnFrameRequested {
                            if (isProcessing.get()) {
                                videoView.markFrameProcessed()
                                return@setOnFrameRequested
                            }
                            val now = System.currentTimeMillis()
                            if (now - lastInferenceTime.value < inferenceIntervalMs) {
                                videoView.markFrameProcessed()
                                return@setOnFrameRequested
                            }

                            isProcessing.set(true)
                            lastInferenceTime.value = now
                            videoView.pause() //  стоп видео

                            scope.launch(Dispatchers.Default) {
                                val bitmap = videoView.captureFrame() ?: run {
                                    videoView.markFrameProcessed()
                                    isProcessing.set(false)
                                    return@launch
                                }

                                if (lastSize.value != (bitmap.width to bitmap.height)) {
                                    drawerRef.value = YoloContourDrawer(Size(bitmap.width, bitmap.height))
                                    lastSize.value = bitmap.width to bitmap.height
                                }

                                val frozenFrame = bitmap.copy(Bitmap.Config.ARGB_8888, true)
                                val resizedFrame = Bitmap.createScaledBitmap(frozenFrame, 320, 320, true)

                                val (contours, _, objects) = yolo.runInference(resizedFrame)

                                val scaleX = frozenFrame.width / 320f
                                val scaleY = frozenFrame.height / 320f

                                val bboxList = objects.map {
                                    val tl = PointF(it.topLeft.x * scaleX, it.topLeft.y * scaleY)
                                    val br = PointF(it.bottomRight.x * scaleX, it.bottomRight.y * scaleY)
                                    Triple(tl, br, it.confidence)
                                }

                                val scaledContours = contours.map { contourGroup ->
                                    contourGroup.map { contour ->
                                        contour.map { point ->
                                            PointF(point.x * scaleX, point.y * scaleY)
                                        }
                                    }
                                }

                                val drawer = drawerRef.value ?: run {
                                    videoView.markFrameProcessed()
                                    isProcessing.set(false)
                                    return@launch
                                }

                                val minAreaAbs = 0.0002f * frozenFrame.width * frozenFrame.height

                                drawer.drawOverlay(
                                    bboxes = bboxList,
                                    contours = scaledContours,
                                    confidenceThreshold = 0.4f,
                                    minAreaAbs = minAreaAbs
                                )

                                withContext(Dispatchers.Main) {
                                    val overlay = drawer.getOverlayBitmap() // или drawer.overlayBitmap, если публичное
                                    overlayBitmapRef.value = OverlayFrame(overlay, frozenFrame)
                                    //videoView.play() // ️ верни воспроизведение
                                }

                                videoView.markFrameProcessed()
                                isProcessing.set(false)
                            }
                        }*/
                        videoView.setOnFrameRequested {
                            if (isProcessing.get()) {
                                videoView.markFrameProcessed()
                                return@setOnFrameRequested
                            }
                            val now = System.currentTimeMillis()
                            if (now - lastInferenceTime.value < inferenceIntervalMs) {
                                videoView.markFrameProcessed()
                                return@setOnFrameRequested
                            }

                            isProcessing.set(true)
                            lastInferenceTime.value = now

                            scope.launch(Dispatchers.Default) {
                                val bitmap = videoView.captureFrame() ?: run {
                                    videoView.markFrameProcessed()
                                    isProcessing.set(false)
                                    return@launch
                                }

                                if (lastSize.value != (bitmap.width to bitmap.height)) {
                                    drawerRef.value = YoloContourDrawer(Size(bitmap.width, bitmap.height))
                                    lastSize.value = bitmap.width to bitmap.height
                                }

                                val frozenFrame = bitmap.copy(Bitmap.Config.ARGB_8888, true)

                                val resizedFrame = Bitmap.createScaledBitmap(frozenFrame, 320, 320, true)

                                val (contours, _, objects) = yolo.runInference(resizedFrame)

                                val scaleX = frozenFrame.width / 320f
                                val scaleY = frozenFrame.height / 320f

                                val bboxList = objects.map {
                                    val tl = PointF(it.topLeft.x * scaleX, it.topLeft.y * scaleY)
                                    val br = PointF(it.bottomRight.x * scaleX, it.bottomRight.y * scaleY)
                                    Triple(tl, br, it.confidence)
                                }

                                val scaledContours = contours.map { contourGroup ->
                                    contourGroup.map { contour ->
                                        contour.map { point ->
                                            PointF(point.x * scaleX, point.y * scaleY)
                                        }
                                    }
                                }

                                val drawer = drawerRef.value ?: run {
                                    videoView.markFrameProcessed()
                                    isProcessing.set(false)
                                    return@launch
                                }

                                val minAreaAbs = 0.0002f * frozenFrame.width * frozenFrame.height

                                drawer.drawOverlay(
                                    bboxes = bboxList,
                                    contours = scaledContours,
                                    confidenceThreshold = 0.4f,
                                    minAreaAbs = minAreaAbs
                                )

                                withContext(Dispatchers.Main) {
                                    val overlay = drawer.getOverlayBitmap()
                                    overlayBitmapRef.value = OverlayFrame(overlay, frozenFrame)
                                }

                                videoView.markFrameProcessed()
                                isProcessing.set(false)
                            }
                        }

                        addView(videoView)
                    }
                }, modifier = Modifier.matchParentSize())

                overlayBitmapRef.value?.let { overlay ->
                    Image(
                        bitmap = overlay.image.asImageBitmap(),
                        contentDescription = null,
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
