package com.antares.customtflite

import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.graphics.PointF
import javax.microedition.khronos.opengles.GL10
import javax.microedition.khronos.egl.EGLConfig

class SegmentationGLRenderer : GLSurfaceView.Renderer {

    var contours: List<List<PointF>> = emptyList()

    private val projectionMatrix = FloatArray(16)
    private val vertexShaderCode = """
        uniform mat4 uMVPMatrix;
        attribute vec4 vPosition;
        void main() {
            gl_Position = uMVPMatrix * vPosition;
        }
    """

    private val fragmentShaderCode = """
        precision mediump float;
        uniform vec4 vColor;
        void main() {
            gl_FragColor = vColor;
        }
    """

    private var program = 0

    override fun onSurfaceCreated(unused: GL10, config: EGLConfig) {
        GLES20.glClearColor(0f, 0f, 0f, 0f)
        program = createProgram(vertexShaderCode, fragmentShaderCode)
    }

    override fun onSurfaceChanged(unused: GL10, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        Matrix.orthoM(projectionMatrix, 0, 0f, width.toFloat(), height.toFloat(), 0f, -1f, 1f)
    }

    override fun onDrawFrame(unused: GL10) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

        GLES20.glUseProgram(program)
        val positionHandle = GLES20.glGetAttribLocation(program, "vPosition")
        val colorHandle = GLES20.glGetUniformLocation(program, "vColor")
        val mvpMatrixHandle = GLES20.glGetUniformLocation(program, "uMVPMatrix")

        GLES20.glUniform4f(colorHandle, 1f, 0f, 0f, 0.5f)
        GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false, projectionMatrix, 0)

        for (contour in contours) {
            val vertexBuffer = OpenGLUtils.createFloatBuffer(contour.flatMap { listOf(it.x, it.y) }.toFloatArray())
            GLES20.glEnableVertexAttribArray(positionHandle)
            GLES20.glVertexAttribPointer(positionHandle, 2, GLES20.GL_FLOAT, false, 0, vertexBuffer)
            GLES20.glDrawArrays(GLES20.GL_LINE_LOOP, 0, contour.size)
            GLES20.glDisableVertexAttribArray(positionHandle)
        }
    }

    private fun createProgram(vertexCode: String, fragmentCode: String): Int {
        val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexCode)
        val fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentCode)
        return GLES20.glCreateProgram().also {
            GLES20.glAttachShader(it, vertexShader)
            GLES20.glAttachShader(it, fragmentShader)
            GLES20.glLinkProgram(it)
        }
    }

    private fun loadShader(type: Int, shaderCode: String): Int {
        return GLES20.glCreateShader(type).also { shader ->
            GLES20.glShaderSource(shader, shaderCode)
            GLES20.glCompileShader(shader)
        }
    }
}