package com.antares.customtflite

import android.content.Context
import android.opengl.GLSurfaceView
import android.util.AttributeSet

class SegmentationGLSurfaceView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : GLSurfaceView(context, attrs) {

    val renderer: SegmentationGLRenderer

    init {
        setEGLContextClientVersion(2)
        renderer = SegmentationGLRenderer()
        setRenderer(renderer)
        renderMode = RENDERMODE_WHEN_DIRTY
    }

    fun updateContours(contours: List<List<android.graphics.PointF>>) {
        renderer.contours = contours
        requestRender()
    }
}