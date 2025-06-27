package com.antares.customtflite

import android.opengl.GLES20
import android.opengl.GLSurfaceView
import com.antares.customtflite.data.Detection
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.microedition.khronos.opengles.GL10
import javax.microedition.khronos.egl.EGLConfig

class DetectionRenderer : GLSurfaceView.Renderer {

    private var boxes: List<Detection> = emptyList()

    fun setDetections(newBoxes: List<Detection>) {
        boxes = newBoxes
    }

    override fun onSurfaceCreated(p0: GL10?, p1: EGLConfig?) {
        GLES20.glClearColor(0f, 0f, 0f, 0f)
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)

        // рисуем боксы
        for (box in boxes) {
            drawBox(box)
        }
    }

    private fun drawBox(d: Detection) {
        val x1 = d.x - d.w / 2
        val y1 = d.y - d.h / 2
        val x2 = d.x + d.w / 2
        val y2 = d.y + d.h / 2

        val coords = floatArrayOf(
            x1, y1,
            x2, y1,
            x2, y2,
            x1, y2
        )

        // преобразование в [-1, 1] координаты и отрисовка
        val ndc = coords.mapIndexed { i, v ->
            if (i % 2 == 0) (v / 640f) * 2f - 1f // X (в предположении ширины 640)
            else 1f - (v / 640f) * 2f // Y
        }.toFloatArray()

        drawRect(ndc)
    }

    private fun drawRect(ndcCoords: FloatArray) {
        val vertexBuffer = ByteBuffer
            .allocateDirect(ndcCoords.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
                put(ndcCoords)
                position(0)
            }

        val vertexShader = """
            attribute vec2 aPosition;
            void main() {
                gl_Position = vec4(aPosition, 0.0, 1.0);
            }
        """.trimIndent()

        val fragmentShader = """
            precision mediump float;
            void main() {
                gl_FragColor = vec4(1.0, 0.2, 0.2, 1.0); // красный
            }
        """.trimIndent()

        val program = GLES20.glCreateProgram().also {
            val vShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexShader)
            val fShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentShader)
            GLES20.glAttachShader(it, vShader)
            GLES20.glAttachShader(it, fShader)
            GLES20.glLinkProgram(it)
        }

        GLES20.glUseProgram(program)
        val posHandle = GLES20.glGetAttribLocation(program, "aPosition")
        GLES20.glEnableVertexAttribArray(posHandle)
        GLES20.glVertexAttribPointer(posHandle, 2, GLES20.GL_FLOAT, false, 0, vertexBuffer)
        GLES20.glDrawArrays(GLES20.GL_LINE_LOOP, 0, 4)
        GLES20.glDisableVertexAttribArray(posHandle)
    }

    private fun loadShader(type: Int, code: String): Int {
        return GLES20.glCreateShader(type).also {
            GLES20.glShaderSource(it, code)
            GLES20.glCompileShader(it)
        }
    }
}