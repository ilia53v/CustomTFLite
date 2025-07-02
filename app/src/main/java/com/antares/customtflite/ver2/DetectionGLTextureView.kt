package com.antares.customtflite.ver2

import android.content.Context
import android.graphics.PointF
import android.graphics.SurfaceTexture
import android.util.AttributeSet
import android.util.Log
import android.view.TextureView
import com.antares.customtflite.intersection_point.findAllIntersectionsInContours

class DetectionGLTextureView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : TextureView(context, attrs), TextureView.SurfaceTextureListener {

    private var eglHelper: EGLHelper? = null
    private var contours: List<List<PointF>> = emptyList()
    private var surfaceReady = false

    init {
        surfaceTextureListener = this
        isOpaque = false
    }

    fun setContours(contours: List<List<PointF>>) {
        Log.d("DetectionGLTextureView", "setContours called with ${contours.size} contours")
        this.contours = contours

        if (surfaceReady && eglHelper != null) {
            eglHelper?.setContours(contours)
            eglHelper?.drawFrame()
        }
    }

    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
        eglHelper = EGLHelper()
        eglHelper?.init(surface, width, height)
        eglHelper?.setContours(contours) // <- ВАЖНО!
        val intersections = findAllIntersectionsInContours(contours)
        eglHelper!!.setIntersections(intersections)
        eglHelper?.drawFrame()
        surfaceReady = true
    }

    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {}
    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
        eglHelper?.release()
        eglHelper = null
        surfaceReady = false
        return true
    }

    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {
        eglHelper?.drawFrame()
    }
}