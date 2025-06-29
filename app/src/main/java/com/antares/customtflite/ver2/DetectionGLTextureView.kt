package com.antares.customtflite.ver2

import android.content.Context
import android.graphics.PointF
import android.graphics.SurfaceTexture
import android.opengl.GLES20
import android.util.Log
import android.view.TextureView
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.concurrent.thread

class DetectionGLTextureView(context: Context) : TextureView(context), TextureView.SurfaceTextureListener {

    private var contours: List<List<PointF>> = emptyList()
    private var renderThread: Thread? = null
    private var egl: EGLUtils.EGLData? = null
    private var program = 0
    private var aPosition = 0
    private var uColor = 0


    init {
        surfaceTextureListener = this
        isOpaque = false
    }

    fun setContours(newContours: List<List<PointF>>) {
        contours = newContours
    }

    private fun createProgram(): Int {
        val vertexShader = compileShader(GLES20.GL_VERTEX_SHADER, VERTEX_SHADER)
        val fragmentShader = compileShader(GLES20.GL_FRAGMENT_SHADER, FRAGMENT_SHADER)

        val program = GLES20.glCreateProgram()
        GLES20.glAttachShader(program, vertexShader)
        GLES20.glAttachShader(program, fragmentShader)
        GLES20.glLinkProgram(program)
        return program
    }

    private fun compileShader(type: Int, source: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)
        return shader
    }

    private fun renderLoop(surface: SurfaceTexture) {
        egl = EGLUtils.initGL(surface, width, height)
        program = createProgram()
        aPosition = GLES20.glGetAttribLocation(program, "a_Position")
        uColor = GLES20.glGetUniformLocation(program, "u_Color")

        GLES20.glClearColor(0f, 0f, 0f, 0f)

        while (!Thread.interrupted()) {
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
            GLES20.glUseProgram(program)

            for (contour in contours) {
                val vertices = FloatArray(contour.size * 2)
                contour.forEachIndexed { i, point ->
                    vertices[i * 2] = point.x * 2f - 1f
                    vertices[i * 2 + 1] = 1f - point.y * 2f
                }
                val buffer = ByteBuffer.allocateDirect(vertices.size * 4)
                    .order(ByteOrder.nativeOrder()).asFloatBuffer()
                buffer.put(vertices).position(0)
                GLES20.glLineWidth(2.0f)
                GLES20.glEnableVertexAttribArray(aPosition)
                GLES20.glVertexAttribPointer(aPosition, 2, GLES20.GL_FLOAT, false, 0, buffer)
                GLES20.glUniform4f(uColor, 1f, 0f, 0f, 1f)
                GLES20.glDrawArrays(GLES20.GL_LINE_LOOP, 0, vertices.size / 2)
                GLES20.glDisableVertexAttribArray(aPosition)
            }

            egl?.let { EGLUtils.swapBuffers(it) }
        }

        egl?.let { EGLUtils.release(it) }
    }

    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
        Log.d("VideoGLTextureView", "Surface is ready: $width x $height")
        renderThread = thread {
            renderLoop(surface)
        }
    }

    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {}

    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
        renderThread?.interrupt()
        return true
    }

    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}

    companion object {
        private const val VERTEX_SHADER = """
            attribute vec4 a_Position;
            void main() {
                gl_Position = a_Position;
            }
        """

        private const val FRAGMENT_SHADER = """
            precision mediump float;
            uniform vec4 u_Color;
            void main() {
                gl_FragColor = u_Color;
            }
        """
    }
}