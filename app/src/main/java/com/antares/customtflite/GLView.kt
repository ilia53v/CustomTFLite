package com.antares.customtflite

import android.content.Context
import android.graphics.PointF
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.GLU
import android.opengl.Matrix
import android.util.AttributeSet
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class GLView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : GLSurfaceView(context, attrs) {

    private val renderer: ContourRenderer

    init {
        setEGLContextClientVersion(2) // OpenGL ES 2.0
        renderer = ContourRenderer()
        setRenderer(renderer)
        renderMode = RENDERMODE_WHEN_DIRTY
    }

    fun updateContours(newContours: List<Contour>) {
        renderer.setContours(newContours)
        requestRender()
    }

    private class ContourRenderer : Renderer {
        private var contours: List<Contour> = emptyList()

        override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
            GLES20.glClearColor(0f, 0f, 0f, 1f)
            ShaderUtils.init()
        }

        override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
            GLES20.glViewport(0, 0, width, height)
        }

        override fun onDrawFrame(gl: GL10?) {
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
            ShaderUtils.drawContours(contours)
        }

        fun setContours(contours: List<Contour>) {
            this.contours = contours
        }
    }
}