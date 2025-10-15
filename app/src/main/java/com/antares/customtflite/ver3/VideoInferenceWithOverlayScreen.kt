package com.antares.customtflite.ver3

/*
import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PointF
import android.net.Uri
import android.provider.MediaStore
import android.util.Size
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.antares.customtflite.ver2.YoloContourDrawer
import com.antares.customtflite.ver2.segmentor.YoloV8Segmentor
import androidx.compose.runtime.collectAsState
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

@Composable
fun VideoInferenceWithOverlayScreen(yolo: YoloV8Segmentor) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var videoUri by remember { mutableStateOf<Uri?>(null) }

    // Размер дисплея для overlay и отрисовки
    val displaySize = remember { Size(640, 480) } // Подстрой под видео, если нужно

    // View и pipeline
    val videoView = remember { VideoGLTextureView(context) }
    val inferencePipeline = remember {
        val contourDrawer = YoloContourDrawer(displaySize)
        InferencePipeline(
            segmentor = yolo,
            contourDrawer = contourDrawer,
            confidenceThreshold = 0.4f,
            minAreaAbs = 100f
        )
    }

    // Подписка на bitmap
    val overlayBitmap by inferencePipeline.overlayBitmapState.collectAsState()

    // Управление видео и инференсом
    DisposableEffect(Unit) {
        videoView.setOnFrameRequested {
            val frame = videoView.captureFrame()
            if (frame != null) {
                coroutineScope.launch {
                    inferencePipeline.processFrame(frame)
                    videoView.markFrameProcessed()
                }
            } else {
                videoView.markFrameProcessed()
            }
        }

        videoView.onSurfaceReady = {
            videoUri?.let {
                videoView.setVideoUri(it)
                videoView.setPlaybackSpeed(0.25f)
                videoView.setVolume(0f)
            }
        }

        onDispose {
            videoView.pause()
        }
    }

    // UI
    Column {
        Button(onClick = {
            val intent = Intent(Intent.ACTION_PICK, MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
            (context as? Activity)?.startActivityForResult(intent, 123)
        }) {
            Text("Выбрать видео")
        }

        Box(modifier = Modifier.fillMaxSize()) {
            AndroidView(factory = { videoView })

            overlayBitmap?.let { bitmap ->
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer(alpha = 0.6f) // для прозрачности оверлея
                )
            }
        }
    }
}
*/
