package com.antares.customtflite.ver2.opengl

import android.graphics.SurfaceTexture
import android.opengl.*

object EGLUtils {

    data class EGLData(
        val eglDisplay: EGLDisplay,
        val eglSurface: EGLSurface,
        val eglContext: EGLContext
    )

    fun initGL(surfaceTexture: SurfaceTexture, width: Int, height: Int): EGLData {
        val eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        if (eglDisplay == EGL14.EGL_NO_DISPLAY) {
            throw RuntimeException("Unable to get EGL14 display")
        }

        val version = IntArray(2)
        if (!EGL14.eglInitialize(eglDisplay, version, 0, version, 1)) {
            throw RuntimeException("Unable to initialize EGL14")
        }

        val attribList = intArrayOf(
            EGL14.EGL_RED_SIZE, 8,
            EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            EGL14.EGL_NONE
        )

        val configs = arrayOfNulls<EGLConfig>(1)
        val numConfigs = IntArray(1)
        EGL14.eglChooseConfig(eglDisplay, attribList, 0, configs, 0, configs.size, numConfigs, 0)

        val attrib_list = intArrayOf(
            EGL14.EGL_CONTEXT_CLIENT_VERSION, 2,
            EGL14.EGL_NONE
        )

        val eglContext = EGL14.eglCreateContext(
            eglDisplay,
            configs[0],
            EGL14.EGL_NO_CONTEXT,
            attrib_list, 0
        )

        val eglSurface = EGL14.eglCreateWindowSurface(
            eglDisplay,
            configs[0],
            surfaceTexture,
            intArrayOf(EGL14.EGL_NONE), 0
        )

        if (!EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) {
            throw RuntimeException("eglMakeCurrent failed")
        }

        GLES20.glViewport(0, 0, width, height)

        return EGLData(eglDisplay, eglSurface, eglContext)
    }

    fun swapBuffers(eglData: EGLData) {
        EGL14.eglSwapBuffers(eglData.eglDisplay, eglData.eglSurface)
    }

    fun release(eglData: EGLData) {
        EGL14.eglMakeCurrent(eglData.eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
        EGL14.eglDestroySurface(eglData.eglDisplay, eglData.eglSurface)
        EGL14.eglDestroyContext(eglData.eglDisplay, eglData.eglContext)
        EGL14.eglReleaseThread()
        EGL14.eglTerminate(eglData.eglDisplay)
    }
}