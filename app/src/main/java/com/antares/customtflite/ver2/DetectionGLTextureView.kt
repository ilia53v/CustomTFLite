package com.antares.customtflite.ver2

import android.content.Context
import android.graphics.PointF
import android.graphics.SurfaceTexture
import android.util.AttributeSet
import android.util.Log
import android.view.TextureView
import com.antares.customtflite.intersection_point.findAllIntersectionsInContours
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/*
class DetectionGLTextureView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : TextureView(context, attrs), TextureView.SurfaceTextureListener {

    private var eglHelper: EGLHelper? = null
    private var renderThread: Thread? = null
    @Volatile private var running = false

    private val contoursLock = ReentrantLock()
    private var contours: List<List<PointF>> = emptyList()

    init {
        surfaceTextureListener = this
    }

    fun setContours(newContours: List<List<PointF>>) {
        contoursLock.withLock {
            contours = newContours
        }
    }

    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
        Log.d("DetectionGLTextureView", "Surface available: $width x $height")

        eglHelper = EGLHelper().apply {
            init(surface, width, height)
        }

        running = true
        renderThread = Thread {
            while (running) {
                // Получаем копию текущих контуров с блокировкой
                val currentContours = contoursLock.withLock {
                    contours
                }
                // Передаём их в EGLHelper и отрисовываем
                eglHelper?.setContours(currentContours)
                eglHelper?.drawFrame()
                Log.d("EGLHelperDetection", "drawFrame called with ${contours.size} contours")

                Thread.sleep(16) // ~60 FPS
            }
        }
        renderThread?.start()
    }

    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
        eglHelper?.onSurfaceChanged(width, height)
    }

    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
        running = false
        renderThread?.join()
        renderThread = null

        eglHelper?.release()
        eglHelper = null

        return true
    }

    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {
        // Не используется
    }
}*/


class DetectionGLTextureView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : TextureView(context, attrs), TextureView.SurfaceTextureListener {

    private var renderThread: Thread? = null
    @Volatile private var running = false
    private var surfaceWidth = 0
    private var surfaceHeight = 0

    private val contoursLock = ReentrantLock()
    private var contours: List<List<PointF>> = emptyList()

    private var surface: SurfaceTexture? = null

    init {
        surfaceTextureListener = this
    }

    fun setContours(newContours: List<List<PointF>>) {
        contoursLock.lock()
        try {
            contours = newContours
        } finally {
            contoursLock.unlock()
        }
    }

    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
        this.surface = surface
        this.surfaceWidth = width
        this.surfaceHeight = height

        startRenderThread()
    }

    private fun startRenderThread() {
        running = true
        renderThread = Thread {
            val eglHelper = EGLHelper()
            surface?.let { eglHelper.init(it, surfaceWidth, surfaceHeight) }

            while (running) {
                val currentContours = contoursLock.withLock { contours }
                eglHelper.setContours(currentContours)
                eglHelper.drawFrame()
                Thread.sleep(16) // ~60 FPS
            }

            eglHelper.release()
        }
        renderThread?.start()
    }

    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
        surfaceWidth = width
        surfaceHeight = height
    }

    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
        running = false
        renderThread?.join()
        renderThread = null
        return true
    }

    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {
        // No-op
    }
}
