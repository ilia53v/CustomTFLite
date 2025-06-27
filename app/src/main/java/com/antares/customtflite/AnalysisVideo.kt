package com.antares.customtflite

import android.net.Uri
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.antares.customtflite.data.Contour

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