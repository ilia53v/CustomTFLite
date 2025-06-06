package com.antares.customtflite

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.PointF
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.view.TextureView
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.doOnLayout
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.antares.customtflite.ui.theme.CustomTFLiteTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import kotlin.concurrent.thread
import android.util.Log
import androidx.annotation.OptIn
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.sharp.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.runtime.State
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.res.painterResource
import androidx.media3.common.util.UnstableApi
import kotlinx.coroutines.withContext
/*
class MainActivity : ComponentActivity() {

    private lateinit var player: ExoPlayer
    private lateinit var segmentor: YoloV8Segmentor
    private lateinit var frameExtractor: VideoFrameExtractor


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        segmentor = YoloV8Segmentor(applicationContext)
        player = ExoPlayer.Builder(this).build()

        val videoFile = copyAssetToCache("test_video.mp4")

        setContent {
            Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                videoFile?.let {
                    VideoScreen(
                        modifier = Modifier.fillMaxSize(),
                        videoUri = Uri.fromFile(it)
                    )
                } ?: run {
                    Text("Не удалось загрузить видео.", modifier = Modifier.padding(16.dp))
                }
            }


        }
    }
    private fun copyAssetToCache(assetName: String): File? {
        return try {
            val file = File(cacheDir, assetName)
            if (!file.exists()) {
                assets.open(assetName).use { input ->
                    FileOutputStream(file).use { output ->
                        input.copyTo(output)
                    }
                }
            }
            file
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

}

*/


class MainActivity : ComponentActivity() {

    private var exoPlayer: ExoPlayer? = null
    private var segmentationJob: Job? = null

    @OptIn(UnstableApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val _contoursState = mutableStateOf<List<Contour>>(emptyList())
        val contoursState: State<List<Contour>> = _contoursState

        val pickVideoLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            uri?.let { videoUri ->
                exoPlayer?.setMediaItem(MediaItem.fromUri(videoUri))
                exoPlayer?.prepare()
                exoPlayer?.play()

                // Запускаем сегментацию
                segmentationJob?.cancel()
                segmentationJob = CoroutineScope(Dispatchers.Main).launch {
                    val segmentor = YoloV8Segmentor(applicationContext)
                    val extractor = VideoFrameExtractor(applicationContext, segmentor) { contours ->
                        // Обновим contoursState (через mutableStateOf)
                        _contoursState.value = contours
                    }
                    extractor.extractFramesAndSegment(videoUri)
                    segmentor.close()
                }
            }
        }

        val _videoDuration = mutableStateOf(0L)
        val _currentPosition = mutableStateOf(0L)
        val _playbackSpeed = mutableStateOf(1.0f)
        val _volume = mutableStateOf(1.0f)


        setContent {
            val context = LocalContext.current

            DisposableEffect(Unit) {
                exoPlayer = ExoPlayer.Builder(context).build()
                onDispose {
                    segmentationJob?.cancel()
                    exoPlayer?.release()
                    exoPlayer = null
                }
            }
            LaunchedEffect(exoPlayer) {
                while (true) {
                    delay(500)
                    exoPlayer?.let {
                        _currentPosition.value = it.currentPosition
                        _videoDuration.value = it.duration
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                Button(onClick = { pickVideoLauncher.launch(arrayOf("video/*")) }) {
                    Text("Выбрать видео из галереи")
                }

                Spacer(modifier = Modifier.height(16.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                ) {
                    AndroidView(
                        factory = { context ->
                            PlayerView(context).apply {
                                player = exoPlayer
                                useController = true

                                useArtwork = false
                                setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)

                                /*videoSurfaceView = null
                                videoTextureView = TextureView(context)*/
                            }
                        },
                        modifier = Modifier.matchParentSize()
                    )

                    Canvas(modifier = Modifier.matchParentSize()) {
                        val strokeWidth = 4f
                        val strokeColor = Color.Red

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
                                    style = androidx.compose.ui.graphics.drawscope.Stroke(
                                        width = strokeWidth,
                                        cap = StrokeCap.Round,
                                        join = StrokeJoin.Round
                                    )
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
                    IconButton(onClick = { exoPlayer?.seekBack() }) {
                        Icon(painterResource(R.drawable.back), contentDescription = "Back")
                    }
                    IconButton(onClick = { exoPlayer?.seekTo(0); exoPlayer?.play() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Refresh")
                    }
                    IconButton(onClick = { exoPlayer?.play() }) {
                        Icon(painterResource(R.drawable.play), contentDescription = "Play")
                    }
                    IconButton(onClick = { exoPlayer?.pause() }) {
                        Icon(painterResource(R.drawable.pause), contentDescription = "Pause")
                    }
                    IconButton(onClick = { exoPlayer?.seekForward() }) {
                        Icon(painterResource(R.drawable.next), contentDescription = "Next")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Slider(
                    value = _currentPosition.value.toFloat(),
                    onValueChange = { pos -> exoPlayer?.seekTo(pos.toLong()) },
                    valueRange = 0f..(_videoDuration.value.toFloat().coerceAtLeast(1f)),
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "Позиция: ${_currentPosition.value / 1000}s / ${_videoDuration.value / 1000}s",
                    style = MaterialTheme.typography.bodyMedium
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Volume
                Text("Громкость: %.1f".format(_volume.value))
                Slider(
                    value = _volume.value,
                    onValueChange = {
                        _volume.value = it
                        exoPlayer?.volume = it
                    },
                    valueRange = 0f..1f
                )

                // Speed
                Text("Скорость: %.1fx".format(_playbackSpeed.value))
                Slider(
                    value = _playbackSpeed.value,
                    onValueChange = {
                        _playbackSpeed.value = it
                        exoPlayer?.setPlaybackSpeed(it)
                    },
                    valueRange = 0.25f..2.0f
                )
            }
        }
    }
}