package com.antares.customtflite.ver2

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PointF
import android.util.Log
import com.antares.customtflite.data.Detection
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

class YoloV8Segmentor(private val context: Context) {

    private val lock = Any()

    private val interpreter: Interpreter by lazy {
        val options = Interpreter.Options().apply {
            setUseXNNPACK(false)  // отключаем XNNPACK для стабильности
            setUseNNAPI(false)
            setNumThreads(1)
        }
        Interpreter(loadModelFile("best_segment_float32.tflite"), options)
    }

    fun runInferenceSafe(input: ByteBuffer, outputs: Map<Int, Any>): Boolean {
        return try {
            synchronized(lock) {
                interpreter.runForMultipleInputsOutputs(arrayOf(input), outputs)
            }
            true
        } catch (e: Exception) {
            Log.e("YoloV8Segmentor", "Inference error", e)
            false
        }
    }


    private fun loadModelFile(modelName: String): ByteBuffer {
        val fileDescriptor = context.assets.openFd(modelName)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    private fun preprocess(bitmap: Bitmap): ByteBuffer {
        val resized = Bitmap.createScaledBitmap(bitmap, 640, 640, true)
        val buffer = ByteBuffer.allocateDirect(1 * 640 * 640 * 3 * 4)
        buffer.order(ByteOrder.nativeOrder())
        val pixels = IntArray(640 * 640)
        resized.getPixels(pixels, 0, 640, 0, 0, 640, 640)
        for (pixel in pixels) {
            buffer.putFloat(((pixel shr 16) and 0xFF) / 255f)
            buffer.putFloat(((pixel shr 8) and 0xFF) / 255f)
            buffer.putFloat((pixel and 0xFF) / 255f)
        }
        buffer.rewind()
        return buffer
    }

    fun runInference(bitmap: Bitmap): List<List<PointF>> {
        val input = preprocess(bitmap) // [1,640,640,3]

        // Выходы модели согласно форме
        val output0 = Array(1) { Array(38) { FloatArray(8400) } }               // [1, 38, 8400]
        val output1 = Array(1) { Array(160) { Array(160) { FloatArray(32) } } } // [1, 160, 160, 32]

        val outputs = mapOf(
            0 to output0,
            1 to output1
        )

        /*try {
            interpreter.runForMultipleInputsOutputs(arrayOf(input), outputs)
        } catch (e: Exception) {
            Log.e("YoloV8Segmentor", "Inference error", e)
            return emptyList()
        }*/

        runInferenceSafe(input, outputs)

        // Транспонируем output0 в [8400][38]
        val anchorsCount = 8400
        val channels = 38
        val outputTransposed = Array(anchorsCount) { FloatArray(channels) }
        for (c in 0 until channels) {
            for (i in 0 until anchorsCount) {
                outputTransposed[i][c] = output0[0][c][i]
            }
        }

        // Преобразуем protos из output1 в [32][160*160]
        val protos2d = Array(32) { FloatArray(160 * 160) }
        for (p in 0 until 32) {
            var idx = 0
            for (y in 0 until 160) {
                for (x in 0 until 160) {
                    protos2d[p][idx++] = output1[0][y][x][p]
                }
            }
        }

        val masks = MaskUtils.computeMasks(outputTransposed, protos2d)
        return masks.mapNotNull { mask ->
            MaskUtils.extractContourFromMask(mask, 160)
        }
    }
}