package com.antares.customtflite.ver2

import android.graphics.PointF
import android.graphics.SurfaceTexture
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.opengl.GLES20
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder

class EGLHelper {

    private var display: EGLDisplay? = null
    private var context: EGLContext? = null
    private var eglSurface: EGLSurface? = null
    private var contours: List<List<PointF>> = emptyList()

    fun setContours(contours: List<List<PointF>>) {
        Log.d("EGLHelper", "setContours called with ${contours.size} contours")
        this.contours = contours
    }

    fun init(surfaceTexture: SurfaceTexture, width: Int, height: Int) {
        Log.d("EGLHelper", "Initializing EGL")

        display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        if (display == EGL14.EGL_NO_DISPLAY) {
            throw RuntimeException("Unable to get EGL14 display")
        }

        val version = IntArray(2)
        if (!EGL14.eglInitialize(display, version, 0, version, 1)) {
            throw RuntimeException("Unable to initialize EGL14")
        }

        val attribList = intArrayOf(
            EGL14.EGL_RED_SIZE, 8,
            EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            EGL14.EGL_NONE
        )
        val configs = arrayOfNulls<EGLConfig>(1)
        val numConfigs = IntArray(1)
        EGL14.eglChooseConfig(display, attribList, 0, configs, 0, 1, numConfigs, 0)
        val config = configs[0] ?: throw RuntimeException("No suitable EGLConfig found")

        val attrib_list = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE)
        context = EGL14.eglCreateContext(display, config, EGL14.EGL_NO_CONTEXT, attrib_list, 0)
        if (context == null || context == EGL14.EGL_NO_CONTEXT) {
            throw RuntimeException("Failed to create EGL context")
        }

        val surfaceAttribs = intArrayOf(EGL14.EGL_NONE)
        eglSurface = EGL14.eglCreateWindowSurface(display, config, surfaceTexture, surfaceAttribs, 0)
        if (eglSurface == null || eglSurface == EGL14.EGL_NO_SURFACE) {
            throw RuntimeException("Failed to create EGL surface")
        }

        if (!EGL14.eglMakeCurrent(display, eglSurface, eglSurface, context)) {
            throw RuntimeException("Failed to make EGL context current")
        }

        GLES20.glViewport(0, 0, width, height)
        GLES20.glClearColor(0f, 0f, 0f, 0f)

        Log.d("EGLHelper", "EGL initialized successfully")
    }

    fun drawFrame() {
        val disp = display
        val surf = eglSurface
        val ctx = context

        if (disp == null) {
            Log.e("EGLHelper", "drawFrame failed: display is null")
            return
        }
        if (surf == null) {
            Log.e("EGLHelper", "drawFrame failed: eglSurface is null")
            return
        }
        if (ctx == null) {
            Log.e("EGLHelper", "drawFrame failed: context is null")
            return
        }

        // Попытка сделать текущим EGL контекст
        if (!EGL14.eglMakeCurrent(disp, surf, surf, ctx)) {
            Log.e("EGLHelper", "eglMakeCurrent failed in drawFrame")
            return
        }

        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        GLES20.glLineWidth(4f)

        for (contour in contours) {
            val vertexData = FloatArray(contour.size * 2)
            for ((i, point) in contour.withIndex()) {
                vertexData[i * 2] = point.x
                vertexData[i * 2 + 1] = point.y
            }

            val buffer = ByteBuffer.allocateDirect(vertexData.size * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
                .put(vertexData)
            buffer.position(0)

            GLES20.glEnableVertexAttribArray(0)
            GLES20.glVertexAttribPointer(0, 2, GLES20.GL_FLOAT, false, 0, buffer)
            GLES20.glDrawArrays(GLES20.GL_LINE_STRIP, 0, contour.size)
            GLES20.glDisableVertexAttribArray(0)
        }

        GLES20.glDisable(GLES20.GL_BLEND)

        if (!EGL14.eglSwapBuffers(disp, surf)) {
            Log.e("EGLHelper", "eglSwapBuffers failed")
        }
    }

    fun release() {
        Log.d("EGLHelper", "Releasing EGL resources")

        display?.let { disp ->
            eglSurface?.let { surf ->
                EGL14.eglMakeCurrent(disp, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
                EGL14.eglDestroySurface(disp, surf)
            }

            context?.let { ctx ->
                EGL14.eglDestroyContext(disp, ctx)
            }

            EGL14.eglTerminate(disp)
        }

        display = null
        context = null
        eglSurface = null
    }
}