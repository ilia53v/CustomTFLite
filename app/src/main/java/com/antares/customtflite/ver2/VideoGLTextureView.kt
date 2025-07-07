package com.antares.customtflite.ver2

import android.content.Context
import android.graphics.Bitmap
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.util.AttributeSet
import android.util.Log
import android.view.Surface
import android.view.TextureView

class VideoGLTextureView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : TextureView(context, attrs), TextureView.SurfaceTextureListener {

    private var mediaPlayer: MediaPlayer? = null
    private var isPrepared = false
    private var isSurfaceAvailable = false
    var onSurfaceReady: (() -> Unit)? = null
    private var videoUri: Uri? = null

    // Callback для передачи захваченного кадра
    var onFrameCaptured: ((frame: Bitmap, timestampMs: Long) -> Unit)? = null

    init {
        surfaceTextureListener = this
    }

    fun setVideoUri(uri: Uri) {
        Log.d("VideoGLTextureView", "setVideoUri: $uri")
        videoUri = uri
        if (isSurfaceAvailable) {
            prepareMediaPlayer()
        } else {
            Log.d("VideoGLTextureView", "Surface not yet available")
        }
    }

    fun play() {
        Log.d("VideoGLTextureView", "play() called")
        mediaPlayer?.takeIf { isPrepared }?.start()
    }

    fun pause() {
        Log.d("VideoGLTextureView", "pause() called")
        mediaPlayer?.pause()
    }

    fun setVolume(v: Float) {
        Log.d("VideoGLTextureView", "setVolume: $v")
        mediaPlayer?.setVolume(v, v)
    }

    fun seekTo(positionMs: Int) {
        mediaPlayer?.seekTo(positionMs)
    }

    fun getDuration(): Int = mediaPlayer?.duration ?: 0

    fun getCurrentPosition(): Int = mediaPlayer?.currentPosition ?: 0

    fun setPlaybackSpeed(speed: Float) {
        Log.d("VideoGLTextureView", "setPlaybackSpeed: $speed")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            mediaPlayer?.playbackParams = mediaPlayer?.playbackParams?.setSpeed(speed)!!
        }
    }

    private fun prepareMediaPlayer() {
        if (videoUri == null || !isSurfaceAvailable) {
            Log.w("VideoGLTextureView", "Cannot prepare MediaPlayer — surfaceAvailable=$isSurfaceAvailable, uri=$videoUri")
            return
        }

        Log.d("VideoGLTextureView", "Preparing MediaPlayer with URI: $videoUri")

        mediaPlayer?.release()
        mediaPlayer = MediaPlayer().apply {
            setSurface(Surface(surfaceTexture))
            setOnPreparedListener {
                isPrepared = true
                Log.d("VideoGLTextureView", "MediaPlayer prepared, starting playback")
                start()
            }
            setOnErrorListener { _, what, extra ->
                Log.e("VideoGLTextureView", "MediaPlayer error what=$what extra=$extra")
                true
            }
            try {
                setDataSource(context, videoUri!!)
                prepareAsync()
                Log.d("VideoGLTextureView", "prepareAsync() called")
            } catch (e: Exception) {
                Log.e("VideoGLSurfaceView", "Failed to setDataSource", e)
            }
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
            Log.w("VideoGLTextureView", "captureFrame called but TextureView not available")
            null
        }
    }

    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
        Log.d("VideoGLTextureView", "SurfaceTexture available: $width x $height")
        isSurfaceAvailable = true
        prepareMediaPlayer()
        onSurfaceReady?.invoke()
    }

    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
        Log.d("VideoGLTextureView", "SurfaceTexture size changed: $width x $height")
    }

    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
        Log.d("VideoGLTextureView", "SurfaceTexture destroyed")
        isSurfaceAvailable = false
        mediaPlayer?.release()
        mediaPlayer = null
        isPrepared = false
        return true
    }

    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {
        // Автоматический вызов captureFrame после отрисовки кадра
        Log.d("VideoGLTextureView", "SurfaceTexture updated")
        captureFrame()?.let { bitmap ->
            val timestamp = mediaPlayer?.currentPosition?.toLong() ?: System.currentTimeMillis()
            onFrameCaptured?.invoke(bitmap, timestamp)
        }
    }
}
