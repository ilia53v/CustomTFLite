package com.antares.customtflite.ver2.player

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
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
    private var videoUri: Uri? = null

    private var isFrameBeingProcessed = false
    private var frameRequestCallback: (() -> Unit)? = null

    var onSurfaceReady: (() -> Unit)? = null

    init {
        surfaceTextureListener = this
    }

    fun setVideoUri(uri: Uri) {
        videoUri = uri
        if (isSurfaceAvailable) {
            prepareMediaPlayer()
        }
    }

    fun play() {
        if (isPrepared) {
            mediaPlayer?.start()
        }
    }

    fun pause() {
        mediaPlayer?.pause()
    }

    fun resume() {
        if (!mediaPlayer?.isPlaying.orFalse() && isPrepared) {
            mediaPlayer?.start()
        }
    }

    fun isPlaying(): Boolean = mediaPlayer?.isPlaying ?: false

    fun setVolume(v: Float) {
        mediaPlayer?.setVolume(v, v)
    }

    fun seekTo(positionMs: Int) {
        if (isPrepared) {
            mediaPlayer?.seekTo(positionMs)
        }
    }

    fun getDuration(): Int = mediaPlayer?.duration ?: 0
    fun getCurrentPosition(): Int = mediaPlayer?.currentPosition ?: 0

    fun setPlaybackSpeed(speed: Float) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            try {
                mediaPlayer?.let {
                    val params = it.playbackParams ?: return
                    it.playbackParams = params.setSpeed(speed)
                }
            } catch (e: Exception) {
                Log.e(TAG, "setPlaybackSpeed error: ${e.message}", e)
            }
        }
    }

    fun setOnFrameRequested(callback: () -> Unit) {
        frameRequestCallback = callback
    }

    fun markFrameProcessed() {
        isFrameBeingProcessed = false
    }

    fun captureFrame(): Bitmap? {
        return if (isAvailable) {
            try {
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                getBitmap(bitmap)
                bitmap
            } catch (e: Exception) {
                Log.e(TAG, "captureFrame error: ${e.message}", e)
                null
            }
        } else null
    }

    private fun prepareMediaPlayer() {
        if (videoUri == null || !isSurfaceAvailable) return

        val st = surfaceTexture ?: return

        mediaPlayer?.release()
        mediaPlayer = MediaPlayer().apply {
            setSurface(Surface(st))
            setOnPreparedListener {
                isPrepared = true
                start()
            }
            setOnErrorListener { _, what, extra ->
                Log.e(TAG, "MediaPlayer error: what=$what, extra=$extra")
                true
            }
            try {
                setDataSource(context, videoUri!!)
                prepareAsync()
            } catch (e: Exception) {
                Log.e(TAG, "prepareMediaPlayer failed", e)
            }
        }
    }

    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
        isSurfaceAvailable = true
        prepareMediaPlayer()
        onSurfaceReady?.invoke()
    }

    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {}

    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
        isSurfaceAvailable = false
        mediaPlayer?.release()
        mediaPlayer = null
        isPrepared = false
        return true
    }

    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {
        if (!isFrameBeingProcessed) {
            isFrameBeingProcessed = true
            frameRequestCallback?.invoke()
        }
    }

    private fun Boolean?.orFalse(): Boolean = this ?: false

    companion object {
        private const val TAG = "VideoGLTextureView"
    }
}