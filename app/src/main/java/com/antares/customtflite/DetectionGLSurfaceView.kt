package com.antares.customtflite

import android.content.Context
import android.graphics.PixelFormat
import android.graphics.SurfaceTexture
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.util.AttributeSet
import android.view.SurfaceView
import android.view.TextureView
import com.antares.customtflite.data.Detection
import java.nio.ByteBuffer
import java.nio.ByteOrder

// OpenGL view для отрисовки детекций
class DetectionGLTextureView(context: Context) : TextureView(context), TextureView.SurfaceTextureListener {

    private var renderThread: Thread? = null
    private var running = false
    private var surface: SurfaceTexture? = null
    private var detections: List<Detection> = emptyList()

    // Размеры видео
    var videoWidth: Int = 640
    var videoHeight: Int = 640

    init {
        surfaceTextureListener = this
        isOpaque = false
    }

    fun setDetections(d: List<Detection>) {
        detections = d
    }

    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
        this.surface = surface
        startRendering()
    }

    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {}

    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
        stopRendering()
        return true
    }

    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}

    private fun startRendering() {
        running = true
        renderThread = kotlin.concurrent.thread {
            val egl = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            EGL14.eglInitialize(egl, null, 0, null, 0)

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
            EGL14.eglChooseConfig(egl, attribList, 0, configs, 0, 1, numConfigs, 0)

            val attrib_list = intArrayOf(
                EGL14.EGL_CONTEXT_CLIENT_VERSION, 2,
                EGL14.EGL_NONE
            )
            val context = EGL14.eglCreateContext(egl, configs[0], EGL14.EGL_NO_CONTEXT, attrib_list, 0)
            val surfaceAttribs = intArrayOf(EGL14.EGL_NONE)
            val eglSurface = EGL14.eglCreateWindowSurface(egl, configs[0], this.surface, surfaceAttribs, 0)
            EGL14.eglMakeCurrent(egl, eglSurface, eglSurface, context)

            while (running) {
                GLES20.glViewport(0, 0, width, height)
                GLES20.glClearColor(0f, 0f, 0f, 0f)
                GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)

                drawDetections()

                EGL14.eglSwapBuffers(egl, eglSurface)
                Thread.sleep(1000 / 15) // ~15 FPS
            }

            EGL14.eglDestroySurface(egl, eglSurface)
            EGL14.eglDestroyContext(egl, context)
            EGL14.eglTerminate(egl)
        }
    }

    private fun stopRendering() {
        running = false
        renderThread?.join()
        renderThread = null
    }

    private fun drawDetections() {
        detections.forEach { det ->
            val x = det.x / videoWidth * 2f - 1f
            val y = 1f - det.y / videoHeight * 2f
            val w = det.w / videoWidth * 2f
            val h = det.h / videoHeight * 2f

            val left = x - w / 2f
            val right = x + w / 2f
            val top = y + h / 2f
            val bottom = y - h / 2f

            val coords = floatArrayOf(
                left, top,
                right, top,
                right, bottom,
                left, bottom
            )

            val buffer = ByteBuffer.allocateDirect(coords.size * 4)
                .order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
                    put(coords)
                    position(0)
                }

            val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, """
                attribute vec2 a_Position;
                void main() {
                    gl_Position = vec4(a_Position, 0.0, 1.0);
                }
            """.trimIndent())

            val fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, """
                precision mediump float;
                void main() {
                    gl_FragColor = vec4(1.0, 0.0, 0.0, 1.0); // Красный
                }
            """.trimIndent())

            val program = GLES20.glCreateProgram()
            GLES20.glAttachShader(program, vertexShader)
            GLES20.glAttachShader(program, fragmentShader)
            GLES20.glLinkProgram(program)
            GLES20.glUseProgram(program)

            val posHandle = GLES20.glGetAttribLocation(program, "a_Position")
            GLES20.glEnableVertexAttribArray(posHandle)
            GLES20.glVertexAttribPointer(posHandle, 2, GLES20.GL_FLOAT, false, 0, buffer)
            GLES20.glDrawArrays(GLES20.GL_LINE_LOOP, 0, 4)
            GLES20.glDisableVertexAttribArray(posHandle)
        }
    }

    private fun loadShader(type: Int, code: String): Int {
        return GLES20.glCreateShader(type).also {
            GLES20.glShaderSource(it, code)
            GLES20.glCompileShader(it)
        }
    }
}