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

    var onSurfaceReady: (() -> Unit)? = null
    var onFrameCaptured: ((Bitmap) -> Unit)? = null

    init {
        surfaceTextureListener = this


    }

    fun setVideoUri(uri: Uri) {
        Log.d(TAG, "setVideoUri: $uri")
        videoUri = uri
        if (isSurfaceAvailable) {
            Log.d(TAG, "Surface доступен, запускаем prepareMediaPlayer()")
            prepareMediaPlayer()
        } else {
            Log.d(TAG, "Surface ещё не доступен, подождём onSurfaceTextureAvailable")
        }
    }

    fun play() {
        Log.d(TAG, "play() called")
        if (isPrepared) {
            mediaPlayer?.start()
            Log.d(TAG, "MediaPlayer started")
        } else {
            Log.w(TAG, "play() вызван, но MediaPlayer не готов")
        }
    }

    fun pause() {
        Log.d(TAG, "pause() called")
        mediaPlayer?.pause()
    }

    fun resume() {
        Log.d(TAG, "resume() called")
        if (!mediaPlayer?.isPlaying.orFalse() && isPrepared) {
            mediaPlayer?.start()
        }
    }

    fun isPlaying(): Boolean = mediaPlayer?.isPlaying ?: false

    fun setVolume(v: Float) {
        Log.d(TAG, "setVolume: $v")
        mediaPlayer?.setVolume(v, v)
    }

    fun seekTo(positionMs: Int) {
        if (isPrepared) {
            mediaPlayer?.seekTo(positionMs)
            Log.d(TAG, "seekTo: $positionMs")
        } else {
            Log.w(TAG, "seekTo вызван, но MediaPlayer не готов")
        }
    }

    fun getDuration(): Int = mediaPlayer?.duration ?: 0

    fun getCurrentPosition(): Int = mediaPlayer?.currentPosition ?: 0

    fun setPlaybackSpeed(speed: Float) {
        Log.d(TAG, "setPlaybackSpeed: $speed")
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            try {
                mediaPlayer?.let {
                    val params = it.playbackParams ?: return
                    it.playbackParams = params.setSpeed(speed)
                    Log.d(TAG, "Playback speed установлен")
                }
            } catch (e: Exception) {
                Log.e(TAG, "setPlaybackSpeed error: ${e.message}", e)
            }
        }
    }

    private fun prepareMediaPlayer() {
        if (videoUri == null) {
            Log.w(TAG, "videoUri is null, не могу подготовить MediaPlayer")
            return
        }
        if (!isSurfaceAvailable) {
            Log.w(TAG, "Surface не доступен, не могу подготовить MediaPlayer")
            return
        }

        val st = surfaceTexture
        if (st == null) {
            Log.w(TAG, "surfaceTexture равен null")
            return
        }

        Log.d(TAG, "prepareMediaPlayer: uri=$videoUri, surfaceTexture=$st")

        mediaPlayer?.release()
        mediaPlayer = MediaPlayer().apply {
            setSurface(Surface(st))
            setOnPreparedListener {
                isPrepared = true
                Log.d(TAG, "MediaPlayer подготовлен, стартуем воспроизведение")
                start()
            }
            setOnErrorListener { mp, what, extra ->
                Log.e(TAG, "MediaPlayer ошибка: what=$what, extra=$extra")
                true
            }
            setOnVideoSizeChangedListener { mp, width, height ->
                Log.d(TAG, "Видео размер изменён: $width x $height")
            }
            setOnInfoListener { mp, what, extra ->
                Log.d(TAG, "MediaPlayer info: what=$what, extra=$extra")
                false
            }
            try {
                setDataSource(context, videoUri!!)
                prepareAsync()
                Log.d(TAG, "prepareAsync() вызван")
            } catch (e: Exception) {
                Log.e(TAG, "Не удалось установить источник данных", e)
            }
        }
    }

    fun captureFrame(): Bitmap? {
        return if (isAvailable) {
            try {
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                val gotBitmap = getBitmap(bitmap)
                Log.d(TAG, "captureFrame: bitmap size ${bitmap.width}x${bitmap.height}, getBitmap вернул $gotBitmap")
                bitmap
            } catch (e: Exception) {
                Log.e(TAG, "captureFrame error: ${e.message}", e)
                null
            }
        } else {
            Log.w(TAG, "captureFrame вызван, но TextureView не доступен")
            null
        }
    }

    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
        Log.d(TAG, "onSurfaceTextureAvailable: $width x $height")
        isSurfaceAvailable = true
        prepareMediaPlayer()
        onSurfaceReady?.invoke()
    }

    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
        Log.d(TAG, "onSurfaceTextureSizeChanged: $width x $height")
    }

    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
        Log.d(TAG, "onSurfaceTextureDestroyed")
        isSurfaceAvailable = false
        mediaPlayer?.release()
        mediaPlayer = null
        isPrepared = false
        return true
    }

    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {
        // Логировать каждый кадр может быть очень шумно, оставлю комментарий
        // Log.d(TAG, "onSurfaceTextureUpdated")
        captureFrame()?.let { bitmap ->
            onFrameCaptured?.invoke(bitmap)
        }
    }

    private fun Boolean?.orFalse(): Boolean = this ?: false

    companion object {
        private const val TAG = "VideoGLTextureView"
    }
}