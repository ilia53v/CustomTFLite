package com.antares.customtflite

import android.opengl.GLES20
import android.opengl.GLES20.*
import android.util.Log
import com.antares.customtflite.Contour
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer



object ShaderUtils {

    private var program = 0
    private var positionHandle = 0
    private var colorHandle = 0

    private const val vertexShaderCode = """
        attribute vec4 vPosition;
        void main() {
            gl_Position = vPosition;
        }
    """

    private const val fragmentShaderCode = """
        precision mediump float;
        uniform vec4 vColor;
        void main() {
            gl_FragColor = vColor;
        }
    """

    private val color = floatArrayOf(0f, 1f, 0f, 1f) // Зелёный цвет

    fun init() {
        val vertexShader = loadShader(GL_VERTEX_SHADER, vertexShaderCode)
        val fragmentShader = loadShader(GL_FRAGMENT_SHADER, fragmentShaderCode)

        program = glCreateProgram().also {
            glAttachShader(it, vertexShader)
            glAttachShader(it, fragmentShader)
            glLinkProgram(it)
        }

        positionHandle = glGetAttribLocation(program, "vPosition")
        colorHandle = glGetUniformLocation(program, "vColor")
    }

    fun drawContours(contours: List<Contour>) {
        glUseProgram(program)
        glUniform4fv(colorHandle, 1, color, 0)

        for (contour in contours) {
            if (contour.points.size < 2) continue

            val coords = contour.points.flatMap { (x, y) ->
                listOf(2 * x - 1, 1 - 2 * y) // нормализация в координаты OpenGL
            }.toFloatArray()

            val vertexBuffer: FloatBuffer = ByteBuffer.allocateDirect(coords.size * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
                .put(coords)
                .apply { position(0) }

            glEnableVertexAttribArray(positionHandle)
            glVertexAttribPointer(positionHandle, 2, GL_FLOAT, false, 0, vertexBuffer)

            glDrawArrays(GL_LINE_LOOP, 0, coords.size / 2)

            glDisableVertexAttribArray(positionHandle)
        }
    }

    private fun loadShader(type: Int, shaderCode: String): Int {
        return glCreateShader(type).also { shader ->
            glShaderSource(shader, shaderCode)
            glCompileShader(shader)

            val compileStatus = IntArray(1)
            glGetShaderiv(shader, GL_COMPILE_STATUS, compileStatus, 0)
            if (compileStatus[0] == 0) {
                Log.e("ShaderUtils", "Shader compile error: ${glGetShaderInfoLog(shader)}")
                glDeleteShader(shader)
            }
        }
    }
}