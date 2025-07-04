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
import java.nio.FloatBuffer

/*
class EGLHelper {

    private var display: EGLDisplay? = null
    private var context: EGLContext? = null
    private var eglSurface: EGLSurface? = null

    private var contours: List<List<PointF>> = emptyList()
    private var intersections: List<PointF> = emptyList()

    private var programLine: Int = 0
    private var programPoint: Int = 0
    private var programFill: Int = 0
    private var positionHandleLine: Int = 0
    private var positionHandlePoint: Int = 0
    private var positionHandleFill: Int = 0
    private var colorHandleLine: Int = 0
    private var colorHandlePoint: Int = 0
    private var colorHandleFill: Int = 0

    fun setContours(contours: List<List<PointF>>) {
        Log.d("EGLHelper", "setContours called with ${contours.size} contours")
        this.contours = contours
    }

    fun setIntersections(points: List<PointF>) {
        Log.d("EGLHelper", "setIntersections called with ${points.size} points")
        this.intersections = points
    }

    fun init(surfaceTexture: SurfaceTexture, width: Int, height: Int) {
        Log.d("EGLHelper", "Initializing EGL")

        display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        if (display == EGL14.EGL_NO_DISPLAY) throw RuntimeException("Unable to get EGL14 display")

        val version = IntArray(2)
        if (!EGL14.eglInitialize(display, version, 0, version, 1)) {
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
        if (!EGL14.eglChooseConfig(display, attribList, 0, configs, 0, 1, numConfigs, 0) || numConfigs[0] == 0) {
            throw RuntimeException("No suitable EGLConfig found")
        }
        val config = configs[0]!!

        val attribListContext = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE)
        context = EGL14.eglCreateContext(display, config, EGL14.EGL_NO_CONTEXT, attribListContext, 0)
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
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        GLES20.glClearColor(0f, 0f, 0f, 0f)

        val vertexShaderCode = """
            attribute vec4 a_Position;
            void main() {
                gl_Position = a_Position;
                gl_PointSize = 15.0;
            }
        """.trimIndent()

        val fragmentShaderCode = """
            precision mediump float;
            uniform vec4 u_Color;
            void main() {
                gl_FragColor = u_Color;
            }
        """.trimIndent()

        val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexShaderCode)
        val fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderCode)

        programLine = GLES20.glCreateProgram().also {
            GLES20.glAttachShader(it, vertexShader)
            GLES20.glAttachShader(it, fragmentShader)
            GLES20.glLinkProgram(it)
            checkProgramLink(it)
        }

        programPoint = GLES20.glCreateProgram().also {
            GLES20.glAttachShader(it, vertexShader)
            GLES20.glAttachShader(it, fragmentShader)
            GLES20.glLinkProgram(it)
            checkProgramLink(it)
        }

        programFill = GLES20.glCreateProgram().also {
            GLES20.glAttachShader(it, vertexShader)
            GLES20.glAttachShader(it, fragmentShader)
            GLES20.glLinkProgram(it)
            checkProgramLink(it)
        }

        positionHandleLine = GLES20.glGetAttribLocation(programLine, "a_Position")
        colorHandleLine = GLES20.glGetUniformLocation(programLine, "u_Color")

        positionHandlePoint = GLES20.glGetAttribLocation(programPoint, "a_Position")
        colorHandlePoint = GLES20.glGetUniformLocation(programPoint, "u_Color")

        positionHandleFill = GLES20.glGetAttribLocation(programFill, "a_Position")
        colorHandleFill = GLES20.glGetUniformLocation(programFill, "u_Color")

        Log.d("EGLHelper", "EGL initialized successfully")
    }

    private fun checkProgramLink(program: Int) {
        val linkStatus = IntArray(1)
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0)
        if (linkStatus[0] == 0) {
            val msg = GLES20.glGetProgramInfoLog(program)
            GLES20.glDeleteProgram(program)
            throw RuntimeException("Program link failed: $msg")
        }
    }

    fun drawFrame() {
        val disp = display ?: return
        val surf = eglSurface ?: return
        val ctx = context ?: return

        if (!EGL14.eglMakeCurrent(disp, surf, surf, ctx)) {
            Log.e("EGLHelper", "eglMakeCurrent failed in drawFrame")
            return
        }

        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)

        val baseColors = listOf(
            floatArrayOf(1f, 0f, 0f), // red
            floatArrayOf(0f, 1f, 0f), // green
            floatArrayOf(0f, 0f, 1f), // blue
            floatArrayOf(1f, 1f, 0f), // yellow
            floatArrayOf(1f, 0f, 1f), // magenta
            floatArrayOf(0f, 1f, 1f)  // cyan
        )

        contours.forEachIndexed { index, contour ->
            if (contour.size < 3) return@forEachIndexed

            val vertexData = FloatArray(contour.size * 2)
            for ((i, point) in contour.withIndex()) {
                */
/*vertexData[i * 2] = point.x * 2f - 1f
                vertexData[i * 2 + 1] = 1f - point.y * 2f*//*

                vertexData[i * 2] = point.x / width * 2f - 1f
                vertexData[i * 2 + 1] = 1f - point.y / height * 2f
            }

            val buffer = ByteBuffer.allocateDirect(vertexData.size * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
                .put(vertexData)
            buffer.position(0)

            val color = baseColors[index % baseColors.size]

            // Fill
            GLES20.glUseProgram(programFill)
            GLES20.glUniform4f(colorHandleFill, color[0], color[1], color[2], 0.3f)
            GLES20.glEnableVertexAttribArray(positionHandleFill)
            GLES20.glVertexAttribPointer(positionHandleFill, 2, GLES20.GL_FLOAT, false, 0, buffer)
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_FAN, 0, contour.size)
            GLES20.glDisableVertexAttribArray(positionHandleFill)

            // Outline
            GLES20.glUseProgram(programLine)
            GLES20.glUniform4f(colorHandleLine, color[0], color[1], color[2], 1f)
            GLES20.glEnableVertexAttribArray(positionHandleLine)
            GLES20.glVertexAttribPointer(positionHandleLine, 2, GLES20.GL_FLOAT, false, 0, buffer)
            GLES20.glDrawArrays(GLES20.GL_LINE_LOOP, 0, contour.size)
            GLES20.glDisableVertexAttribArray(positionHandleLine)
        }

        // Intersections
        if (intersections.isNotEmpty()) {
            GLES20.glUseProgram(programPoint)
            GLES20.glUniform4f(colorHandlePoint, 1f, 0f, 0f, 1f)

            val vertexData = FloatArray(intersections.size * 2)
            for ((i, point) in intersections.withIndex()) {
                vertexData[i * 2] = point.x * 2f - 1f
                vertexData[i * 2 + 1] = 1f - point.y * 2f
            }

            val buffer = ByteBuffer.allocateDirect(vertexData.size * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
                .put(vertexData)
            buffer.position(0)

            GLES20.glEnableVertexAttribArray(positionHandlePoint)
            GLES20.glVertexAttribPointer(positionHandlePoint, 2, GLES20.GL_FLOAT, false, 0, buffer)
            GLES20.glDrawArrays(GLES20.GL_POINTS, 0, intersections.size)
            GLES20.glDisableVertexAttribArray(positionHandlePoint)
        }

        GLES20.glDisable(GLES20.GL_BLEND)

        if (!EGL14.eglSwapBuffers(disp, surf)) {
            Log.e("EGLHelper", "eglSwapBuffers failed")
        } else {
            Log.d("EGLHelper", "eglSwapBuffers called")
        }
    }

    private fun loadShader(type: Int, shaderCode: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, shaderCode)
        GLES20.glCompileShader(shader)

        val compiled = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0)
        if (compiled[0] == 0) {
            Log.e("EGLHelper", "Could not compile shader $type: ${GLES20.glGetShaderInfoLog(shader)}")
            GLES20.glDeleteShader(shader)
            throw RuntimeException("Shader compile failed")
        }

        return shader
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
*/


class EGLHelper {

    private var display: EGLDisplay? = null
    private var context: EGLContext? = null
    private var eglSurface: EGLSurface? = null

    private var contours: List<List<PointF>> = emptyList()
    private var intersections: List<PointF> = emptyList()


    private var program: Int = 0
    private var positionHandle: Int = 0
    private var colorHandle: Int = 0
    private var surfaceWidth: Int = 0
    private var surfaceHeight: Int = 0

    fun setContours(contours: List<List<PointF>>) {
        Log.d("EGLHelper", "setContours called with ${contours.size} contours")
        this.contours = contours
    }

    fun setIntersections(points: List<PointF>) {
        Log.d("EGLHelper", "setIntersections called with ${points.size} points")
        this.intersections = points
    }

    private fun convertPointsToFloatBuffer(points: List<PointF>): FloatBuffer {
        val buffer = ByteBuffer.allocateDirect(points.size * 2 * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()

        for (pt in points) {
            val xNdc = pt.x * 2f - 1f          // pt.x от 0 до 1 -> от -1 до 1
            val yNdc = 1f - pt.y * 2f          // pt.y от 0 до 1 -> от 1 до -1 (инвертируем Y)
            buffer.put(xNdc)
            buffer.put(yNdc)
        }
        buffer.position(0)
        return buffer
    }



    fun init(surfaceTexture: SurfaceTexture, width: Int, height: Int) {
        surfaceWidth = width
        surfaceHeight = height

        display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        if (display == EGL14.EGL_NO_DISPLAY) {
            Log.e("EGLHelper_Init", "Unable to get EGL display")
            return
        }

        val version = IntArray(2)
        if (!EGL14.eglInitialize(display, version, 0, version, 1)) {
            Log.e("EGLHelper_Init", "Unable to initialize EGL: ${EGL14.eglGetError()}")
            return
        }

        val configAttribs = intArrayOf(
            EGL14.EGL_RED_SIZE, 8,
            EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_ALPHA_SIZE, 8,
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            EGL14.EGL_NONE
        )
        val configs = arrayOfNulls<EGLConfig>(1)
        val numConfigs = IntArray(1)
        if (!EGL14.eglChooseConfig(display, configAttribs, 0, configs, 0, 1, numConfigs, 0)) {
            Log.e("EGLHelper_Init", "Unable to choose EGL config: ${EGL14.eglGetError()}")
            return
        }
        val config = configs[0] ?: run {
            Log.e("EGLHelper_Init", "No EGL config found")
            return
        }

        val contextAttribs = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE)
        context = EGL14.eglCreateContext(display, config, EGL14.EGL_NO_CONTEXT, contextAttribs, 0)
        if (context == EGL14.EGL_NO_CONTEXT) {
            Log.e("EGLHelper_Init", "Failed to create EGL context: ${EGL14.eglGetError()}")
            return
        }

        val surfaceAttribs = intArrayOf(EGL14.EGL_NONE)
        eglSurface = EGL14.eglCreateWindowSurface(display, config, surfaceTexture, surfaceAttribs, 0)
        if (eglSurface == null || eglSurface == EGL14.EGL_NO_SURFACE) {
            Log.e("EGLHelper_Init", "Failed to create EGL surface: ${EGL14.eglGetError()}")
            return
        }

        if (!EGL14.eglMakeCurrent(display, eglSurface, eglSurface, context)) {
            Log.e("EGLHelper_Init", "Failed to make EGL context current: ${EGL14.eglGetError()}")
            return
        }

        Log.d("EGLHelper_Init", "EGL context and surface successfully initialized")

        GLES20.glViewport(0, 0, width, height)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        GLES20.glClearColor(1f, 0f, 0f, 1f)

        val vertexShaderCode = """
        attribute vec4 a_Position;
        void main() {
            gl_Position = a_Position;
        }
    """.trimIndent()

        val fragmentShaderCode = """
        precision mediump float;
        uniform vec4 u_Color;
        void main() {
            gl_FragColor = u_Color;
        }
    """.trimIndent()

        val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexShaderCode)
        val fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderCode)

        program = GLES20.glCreateProgram().also {
            GLES20.glAttachShader(it, vertexShader)
            GLES20.glAttachShader(it, fragmentShader)
            GLES20.glLinkProgram(it)
            checkProgramLink(it)
        }

        positionHandle = GLES20.glGetAttribLocation(program, "a_Position")
        colorHandle = GLES20.glGetUniformLocation(program, "u_Color")
    }

    private fun checkProgramLink(program: Int) {
        val linkStatus = IntArray(1)
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0)
        if (linkStatus[0] == 0) {
            val errorMsg = GLES20.glGetProgramInfoLog(program)
            GLES20.glDeleteProgram(program)
            throw RuntimeException("Program link failed: $errorMsg")
        }
    }


    /*fun drawFrame() {
        val disp = display ?: return
        val surf = eglSurface ?: return
        val ctx = context ?: return

        if (!EGL14.eglMakeCurrent(disp, surf, surf, ctx)) return
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)

        GLES20.glUseProgram(program)

        for (contour in contours) {
            if (contour.size < 2) continue

            val vertexBuffer = convertPointsToFloatBuffer(contour)
            Log.d("EGLHelper", "Drawing ${contours.size} contours")
            for ((i, contour) in contours.withIndex()) {
                Log.d("EGLHelper", "Contour $i has ${contour.size} points")
            }
            GLES20.glEnableVertexAttribArray(positionHandle)
            GLES20.glVertexAttribPointer(positionHandle, 2, GLES20.GL_FLOAT, false, 0, vertexBuffer)

            GLES20.glUniform4f(colorHandle, 1f, 0f, 0f, 0.6f)
            GLES20.glDrawArrays(GLES20.GL_LINE_LOOP, 0, contour.size)

            GLES20.glDisableVertexAttribArray(positionHandle)
        }

        EGL14.eglSwapBuffers(disp, surf)
    }
*/

    fun drawFrame() {
        val disp = display ?: return
        val surf = eglSurface ?: return
        val ctx = context ?: return

        val madeCurrent = EGL14.eglMakeCurrent(disp, surf, surf, ctx)
        if (!madeCurrent) {
            Log.e("EGLHelperRed", "eglMakeCurrent failed: ${EGL14.eglGetError()}")
            return
        }

        GLES20.glClearColor(1f, 0f, 0f, 1f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)

        val success = EGL14.eglSwapBuffers(disp, surf)
        if (!success) {
            Log.e("EGLHelperRed", "eglSwapBuffers failed: ${EGL14.eglGetError()}")
        } else {
            Log.d("EGLHelperRed", "eglSwapBuffers successful")
        }
    }

    private fun loadShader(type: Int, code: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, code)
        GLES20.glCompileShader(shader)
        return shader
    }


    fun onSurfaceChanged(width: Int, height: Int) {
        surfaceWidth = width
        surfaceHeight = height
        GLES20.glViewport(0, 0, width, height)
    }


    fun release() {
        display?.let { disp ->
            eglSurface?.let { EGL14.eglDestroySurface(disp, it) }
            context?.let { EGL14.eglDestroyContext(disp, it) }
            EGL14.eglTerminate(disp)
        }
        display = null
        context = null
        eglSurface = null
    }
}
