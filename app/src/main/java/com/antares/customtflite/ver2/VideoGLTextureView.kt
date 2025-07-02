package com.antares.customtflite.ver2

import android.content.Context
import android.content.res.AssetFileDescriptor
import android.graphics.Bitmap
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.media.PlaybackParams
import android.net.Uri
import android.os.Build
import android.util.AttributeSet
import android.util.Log
import android.view.Surface
import android.view.TextureView
import androidx.annotation.RequiresApi

///////////////////////////////////////////////////////
/////   класс с методами для работы видеоплеера   /////
///////////////////////////////////////////////////////
class VideoGLTextureView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : TextureView(context, attrs), TextureView.SurfaceTextureListener {

    private var mediaPlayer: MediaPlayer? = null
    private var surface: Surface? = null
    private var isPrepared = false

    init {
        surfaceTextureListener = this
    }

    fun setVideoUri(uri: Uri) {
        surfaceTexture?.let {
            initMediaPlayer(uri, it)
        }
    }

    private fun initMediaPlayer(uri: Uri, surfaceTexture: SurfaceTexture) {
        mediaPlayer?.release()
        mediaPlayer = MediaPlayer()
        surface = Surface(surfaceTexture)

        try {
            mediaPlayer?.apply {
                setSurface(surface)
                setDataSource(context, uri)
                setOnPreparedListener {
                    isPrepared = true
                    start()
                }
                setOnErrorListener { _, what, extra ->
                    Log.e("VideoGLTextureView", "MediaPlayer error: $what, $extra")
                    true
                }
                prepareAsync()
            }
        } catch (e: Exception) {
            Log.e("VideoGLTextureView", "MediaPlayer init error", e)
        }
    }

    fun start() = mediaPlayer?.start()
    fun pause() = mediaPlayer?.pause()
    fun getCurrentPosition(): Int = mediaPlayer?.currentPosition ?: 0
    fun getDuration(): Int = mediaPlayer?.duration ?: 0
    fun captureFrame(): Bitmap? = bitmap
    fun seekTo(positionMs: Int) {
        if (isPrepared) {
            mediaPlayer?.seekTo(positionMs)
        }
    }

    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {}
    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {}
    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
        mediaPlayer?.release()
        mediaPlayer = null
        this.surface?.release()
        return true
    }
    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
}