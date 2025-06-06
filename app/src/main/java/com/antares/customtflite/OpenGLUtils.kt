package com.antares.customtflite

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

object OpenGLUtils {
    fun createFloatBuffer(coords: FloatArray): FloatBuffer {
        val buffer = ByteBuffer.allocateDirect(coords.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
        buffer.put(coords)
        buffer.position(0)
        return buffer
    }
}