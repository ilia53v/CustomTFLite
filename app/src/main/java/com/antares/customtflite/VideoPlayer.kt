package com.antares.customtflite

import android.media.MediaPlayer
import android.media.PlaybackParams
import android.net.Uri
import android.os.Build
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView

@Composable
fun VideoPlayer(uri: Uri, speed: Float) {
    val context = LocalContext.current
    val mediaPlayer = remember { MediaPlayer() }
    var surfaceReady by remember { mutableStateOf(false) }

    AndroidView(
        factory = { ctx ->
            val surfaceView = SurfaceView(ctx)
            val holder = surfaceView.holder

            holder.addCallback(object : SurfaceHolder.Callback {
                override fun surfaceCreated(holder: SurfaceHolder) {
                    surfaceReady = true
                    try {
                        mediaPlayer.setDataSource(ctx, uri)
                        mediaPlayer.setDisplay(holder)
                        mediaPlayer.setOnPreparedListener {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                mediaPlayer.playbackParams = PlaybackParams().setSpeed(speed)
                            }
                            mediaPlayer.start()
                        }
                        mediaPlayer.prepareAsync()
                    } catch (e: Exception) {
                        Log.e("VideoPlayer", "Ошибка инициализации: ${e.message}")
                    }
                }

                override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {}
                override fun surfaceDestroyed(holder: SurfaceHolder) {
                    mediaPlayer.release()
                }
            })
            surfaceView
        },
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16 / 9f)
    )
}