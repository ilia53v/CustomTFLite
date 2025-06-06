package com.antares.customtflite

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.util.Log
import android.view.PixelCopy
import android.view.Surface
import android.view.TextureView
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import java.io.IOException

class VideoGLTextureView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : TextureView(context, attrs), TextureView.SurfaceTextureListener {

    private var mediaPlayer: MediaPlayer? = null
    private var isPrepared = false
    var onSurfaceReady: (() -> Unit)? = null

    // Callback для передачи захваченного кадра
    var onFrameCaptured: ((Bitmap) -> Unit)? = null

    init {
        surfaceTextureListener = this
    }

    fun setVideoUri(uri: Uri) {
        if (surfaceTexture == null) {
            Log.w("VideoGLTextureView", "SurfaceTexture not ready yet")
            return
        }

        if (mediaPlayer == null) {
            mediaPlayer = MediaPlayer().apply {
                setSurface(Surface(surfaceTexture))
                setOnPreparedListener {
                    isPrepared = true
                    start()
                }
            }
        } else {
            mediaPlayer?.reset()
        }

        try {
            mediaPlayer?.apply {
                setDataSource(context, uri)
                prepareAsync()
            }
        } catch (e: Exception) {
            Log.e("VideoGLTextureView", "Failed to setDataSource", e)
        }
    }

    fun play() = mediaPlayer?.takeIf { isPrepared }?.start()
    fun pause() = mediaPlayer?.pause()
    fun setVolume(v: Float) = mediaPlayer?.setVolume(v, v)
    fun setPlaybackSpeed(speed: Float) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            mediaPlayer?.playbackParams = mediaPlayer?.playbackParams?.setSpeed(speed)!!
        }
    }

    fun captureFrame(): Bitmap? {
        return if (isAvailable) {
            try {
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                val result = getBitmap(bitmap)
                Log.d("captureFrame", "Bitmap size: ${bitmap.width}x${bitmap.height}, getBitmap returned $result")
                bitmap
            } catch (e: Exception) {
                Log.e("VideoGLTextureView", "captureFrame error: ${e.message}")
                null
            }
        } else {
            null
        }
    }

    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
        onSurfaceReady?.invoke()
    }

    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {}

    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
        mediaPlayer?.release()
        mediaPlayer = null
        isPrepared = false
        return true
    }

    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {
        // Автоматический вызов captureFrame после отрисовки кадра
        captureFrame()?.let { bitmap ->
            onFrameCaptured?.invoke(bitmap)
        }
    }
}