package com.antares.customtflite

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.SurfaceTexture
import android.net.Uri
import android.provider.MediaStore
import android.util.Log
import android.view.TextureView
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/*
@Composable
fun VideoScreen() {
    val context = LocalContext.current

    val contoursState = remember { mutableStateOf<List<Contour>>(emptyList()) }
    val isPlaying = remember { mutableStateOf(false) }
    val volume = remember { mutableFloatStateOf(1f) }
    val speed = remember { mutableFloatStateOf(1f) }

    val textureViewRef = remember { mutableStateOf<VideoGLTextureView?>(null) }
    val videoUri = remember { mutableStateOf<Uri?>(null) }
    val isTextureReady = remember { mutableStateOf(false) }

    val isLoading = remember { mutableStateOf(true) }

    val videoPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            isLoading.value = true
            videoUri.value = it
            textureViewRef.value?.setVideoUri(it)
        }
    }

    val segmentor = remember { YoloV8Segmentor(context) }

    // ️ Частота сегментации (например, 5 кадров в секунду)
    val segmentationFps = 5
    val segmentationIntervalMs = (1000f / segmentationFps).toLong()

    //  Сегментация, пока доступен TextureView
    LaunchedEffect(isTextureReady.value) {
        if (isTextureReady.value) {
            isLoading.value = false
            while (isActive) {
                val textureView = textureViewRef.value ?: continue
                val bitmap = textureView.captureFrame() ?: continue
                try {
                    val contours = segmentor.runSegmentation(bitmap)
                    contoursState.value = contours
                } catch (e: Exception) {
                    Log.e("Segmentation", "runSegmentation error", e)
                }
                delay(segmentationIntervalMs)
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier
            .weight(1f)
            .fillMaxWidth()) {

            AndroidView(
                factory = { ctx ->
                    VideoGLTextureView(ctx).apply {
                        textureViewRef.value = this

                        viewTreeObserver.addOnGlobalLayoutListener {
                            if (width > 0 && height > 0 && isAvailable) {
                                isTextureReady.value = true
                            }
                        }

                        videoUri.value?.let { setVideoUri(it) }
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            // Canvas: отрисовка контуров
            Canvas(modifier = Modifier.fillMaxSize()) {
                val strokeColor = Color.Red
                val strokeWidth = 3f
                contoursState.value.forEach { contour ->
                    if (contour.points.isNotEmpty()) {
                        val path = Path().apply {
                            moveTo(contour.points[0].first, contour.points[0].second)
                            contour.points.drop(1).forEach { point ->
                                lineTo(point.first, point.second)
                            }
                            close()
                        }
                        drawPath(
                            path = path,
                            color = strokeColor,
                            style = Stroke(
                                width = strokeWidth,
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round
                            )
                        )
                    }
                }
            }

            // Индикатор загрузки
            if (isLoading.value) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.3f)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Color.White)
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Button(onClick = { videoPickerLauncher.launch("video/*") }) {
                Text("Выбрать видео")
            }

            Button(onClick = {
                val view = textureViewRef.value
                if (view != null) {
                    if (isPlaying.value) {
                        view.pause()
                    } else {
                        view.play()
                    }
                    isPlaying.value = !isPlaying.value
                }
            }) {
                Text(if (isPlaying.value) "Пауза" else "Воспроизвести")
            }
        }

        Row(modifier = Modifier.padding(8.dp)) {
            Text("Громкость", modifier = Modifier.width(80.dp))
            Slider(
                value = volume.floatValue,
                onValueChange = {
                    volume.floatValue = it
                    textureViewRef.value?.setVolume(it)
                },
                valueRange = 0f..1f
            )
        }

        Row(modifier = Modifier.padding(8.dp)) {
            Text("Скорость", modifier = Modifier.width(80.dp))
            Slider(
                value = speed.floatValue,
                onValueChange = {
                    speed.floatValue = it
                    textureViewRef.value?.setPlaybackSpeed(it)
                },
                valueRange = 0.25f..2f
            )
        }
    }
}*/

 */

