package com.antares.customtflite.ver2

import android.graphics.Bitmap
import android.graphics.PointF
import android.net.Uri
import android.util.Size
import android.widget.FrameLayout
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.viewinterop.AndroidView
import com.antares.customtflite.data.OverlayFrame
import com.antares.customtflite.ver2.player.VideoGLTextureView
import com.antares.customtflite.ver2.segmentor.YoloV8Segmentor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

@Composable
fun VideoWithInference(videoUri: Uri,
                       videoViewRef: MutableState<VideoGLTextureView?>,
                       isProcessing: AtomicBoolean,
                       lastInferenceTime: MutableState<Long>,
                       inferenceIntervalMs: Long,
                       scope: CoroutineScope,
                       lastSize: MutableState<Pair<Int, Int>?>,
                       drawerRef: MutableState<YoloContourDrawer?>,
                       yolo: YoloV8Segmentor,
                       confidenceThreshold: Float,
                       overlayBitmapRef: MutableState<OverlayFrame?>,
                       speed: Float
){
    videoUri?.let { uri ->
        AndroidView(factory = { context ->
            FrameLayout(context).apply {
                val videoView = VideoGLTextureView(context).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                    )
                    setVideoUri(uri)
                    setPlaybackSpeed(speed)
                    videoViewRef.value = this
                }

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
                        val (contours, _, objects) = yolo.runInference(resizedFrame, confidenceThreshold)

                        val scaleX = frozenFrame.width / 320f
                        val scaleY = frozenFrame.height / 320f

                        val bboxList = objects.map {
                            val tl = PointF(it.topLeft.x * scaleX, it.topLeft.y * scaleY)
                            val br = PointF(it.bottomRight.x * scaleX, it.bottomRight.y * scaleY)
                            Triple(tl, br, it.confidence)
                        }

                        val scaledContours = contours.map { group ->
                            group.map { contour ->
                                contour.map { pt -> PointF(pt.x * scaleX, pt.y * scaleY) }
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
                            confidenceThreshold = confidenceThreshold,
                            minAreaAbs = minAreaAbs // = 0.01f
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
        }, modifier = Modifier.fillMaxSize())

        overlayBitmapRef.value?.let { overlay ->
            Image(
                bitmap = overlay.image.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}