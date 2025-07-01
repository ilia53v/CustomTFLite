package com.antares.customtflite.ver2

import android.content.Context
import android.graphics.PointF
import android.graphics.SurfaceTexture
import android.util.AttributeSet
import android.util.Log
import android.view.TextureView

class DetectionGLTextureView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : TextureView(context, attrs), TextureView.SurfaceTextureListener {

    private var eglHelper: EGLHelper? = null
    private var contours: List<List<PointF>> = emptyList()

    init {
        surfaceTextureListener = this
        isOpaque = false
    }

    fun setContours(contours: List<List<PointF>>) {
        Log.d("DetectionGLTextureView", "setContours called with ${contours.size} contours")
        this.contours = contours
        eglHelper?.setContours(contours)
    }

    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
        eglHelper = EGLHelper()
        eglHelper?.init(surface, width, height)
        eglHelper?.setContours(contours)
    }

    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {}
    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
        eglHelper?.release()
        return true
    }
    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {
        eglHelper?.drawFrame()
    }
}
