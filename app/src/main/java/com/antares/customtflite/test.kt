package com.antares.customtflite

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.antares.customtflite.check_model.YoloV8VideoTester
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoScreen() {
    val context = LocalContext.current

    val contoursState = remember { mutableStateOf<List<Contour>>(emptyList()) }
    val isPlaying = remember { mutableStateOf(false) }
    val isProcessing = remember { mutableStateOf(false) }
    val volume = remember { mutableFloatStateOf(1f) }
    val speed = remember { mutableFloatStateOf(1f) }

    val textureViewRef = remember { mutableStateOf<VideoGLTextureView?>(null) }
    val videoUri = remember { mutableStateOf<Uri?>(null) }
    val isTextureReady = remember { mutableStateOf(false) }

    val segmentor = remember { YoloV8Segmentor(context) }

    val videoPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            videoUri.value = it
            textureViewRef.value?.setVideoUri(it)
        }
    }

    /*LaunchedEffect(textureViewRef.value) {
        textureViewRef.value?.onFrameCaptured = { bitmap ->
            if (!isProcessing.value) {
                isProcessing.value = true
                CoroutineScope(Dispatchers.Default).launch {
                    val contours = try {
                        segmentor.runSegmentation(bitmap, bitmap.width, bitmap.height)
                    } catch (e: Exception) {
                        Log.e("Segmentation", "runSegmentation error", e)
                        emptyList()
                    }
                    withContext(Dispatchers.Main) {
                        contoursState.value = contours
                        isProcessing.value = false
                    }
                }
            }
        }
    }*/
    testingModel(context)
    /*startAnalysisVideo(
        textureViewRef,
        videoUri,
        isTextureReady,
        contoursState,
        isProcessing,
        videoPickerLauncher,
        isPlaying,
        volume,
        speed
    )*/

}

@Composable
fun startAnalysisVideo(textureViewRef: MutableState<VideoGLTextureView?>,
                       videoUri: MutableState<Uri?>,
                       isTextureReady:MutableState<Boolean>,
                       contoursState: MutableState<List<Contour>>,
                       isProcessing: MutableState<Boolean>,
                       videoPickerLauncher: ManagedActivityResultLauncher<String, Uri?>,
                       isPlaying: MutableState<Boolean>,
                       volume: MutableFloatState,
                       speed: MutableFloatState){
    Column(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {

            AndroidView(
                factory = { ctx ->
                    VideoGLTextureView(ctx).also { view ->
                        textureViewRef.value = view
                        videoUri.value?.let { view.setVideoUri(it) }

                        view.onSurfaceReady = {
                            isTextureReady.value = true
                        }
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            Canvas(modifier = Modifier.fillMaxSize()) {
                val strokeColor = Color.Red
                val strokeWidth = 3f
                contoursState.value.forEach { contour ->
                    if (contour.points.size > 1) {
                        val path = Path().apply {
                            moveTo(contour.points[0].first, contour.points[0].second)
                            for (i in 1 until contour.points.size) {
                                lineTo(contour.points[i].first, contour.points[i].second)
                            }
                            close()
                        }
                        drawPath(
                            path = path,
                            color = strokeColor,
                            style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round)
                        )
                    }
                }
            }

            if (isProcessing.value) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp),
                    color = Color.Red
                )
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
                textureViewRef.value?.let { view ->
                    if (isPlaying.value) view.pause() else view.play()
                    isPlaying.value = !isPlaying.value
                }
            }) {
                Text(if (isPlaying.value) "Пауза" else "Воспроизвести")
            }
        }

        VolumeSlider(volume, textureViewRef)
        SpeedSlider(speed, textureViewRef)
    }
}

fun copyVideoFromAssets(context: Context, assetFileName: String): String {
    val file = File(context.cacheDir, assetFileName)
    if (!file.exists()) {
        context.assets.open(assetFileName).use { inputStream ->
            FileOutputStream(file).use { outputStream ->
                inputStream.copyTo(outputStream)
            }
        }
    }
    return file.absolutePath
}

@Composable
fun testingModel(context: Context){

    val tester = YoloV8VideoTester(context)
    val allAssets = context.assets.list("")?.toList()
    Log.d("YOLO", "Доступные файлы в assets: $allAssets")
    tester.runInferenceOnVideoAsset("test_video_2.mp4")

}

