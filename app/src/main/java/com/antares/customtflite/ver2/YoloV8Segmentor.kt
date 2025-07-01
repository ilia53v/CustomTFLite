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

    private val inputSize = 640
    private val interpreter: Interpreter

    init {
        val options = Interpreter.Options().apply {
            setUseXNNPACK(false)  // отключено для стабильности
            setNumThreads(1)      // ограничиваем до 1 потока для предотвращения гонок
        }
        interpreter = Interpreter(loadModelFile("best_segment_float32.tflite"), options)
    }

    private fun loadModelFile(modelName: String): ByteBuffer {
        val fileDescriptor = context.assets.openFd(modelName)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    private fun preprocessBitmap(bitmap: Bitmap): ByteBuffer {
        val resized = Bitmap.createScaledBitmap(bitmap, inputSize, inputSize, true)
        val inputBuffer = ByteBuffer.allocateDirect(1 * inputSize * inputSize * 3 * 4)
        inputBuffer.order(ByteOrder.nativeOrder())

        val pixels = IntArray(inputSize * inputSize)
        resized.getPixels(pixels, 0, inputSize, 0, 0, inputSize, inputSize)

        for (pixel in pixels) {
            val r = ((pixel shr 16) and 0xFF) / 255.0f
            val g = ((pixel shr 8) and 0xFF) / 255.0f
            val b = (pixel and 0xFF) / 255.0f
            inputBuffer.putFloat(r)
            inputBuffer.putFloat(g)
            inputBuffer.putFloat(b)
        }

        inputBuffer.rewind()
        return inputBuffer
    }

    private val interpreterLock = Any()

    /**
     * Запускает инференс и возвращает список контуров
     */
    fun runInference(bitmap: Bitmap): List<List<PointF>> {
        val inputBuffer = preprocessBitmap(bitmap)
        val output0 = Array(1) { Array(38) { FloatArray(8400) } }
        val output1 = Array(1) { Array(160) { Array(160) { FloatArray(32) } } }
        val outputs = mapOf(0 to output0, 1 to output1)

        synchronized(interpreterLock) {
            interpreter.runForMultipleInputsOutputs(arrayOf(inputBuffer), outputs)
        }

        // Извлекаем маски
        val maskCoeffs = Array(8400) { FloatArray(32) }
        for (i in 0 until 8400) {
            for (j in 0 until 32) {
                maskCoeffs[i][j] = output0[0][j + 6][i]  // сдвиг на 6 — из документации модели
            }
        }

        // Преобразуем прототипы: [32][160][160]
        val protos = Array(32) { i ->
            Array(160) { y ->
                FloatArray(160) { x ->
                    output1[0][y][x][i]
                }
            }
        }

        // Вычисляем маски (логика в твоем MaskUtils)
        val masks = MaskUtils.computeMasks(maskCoeffs, protos)

        // Преобразуем маски в контуры (списки точек с нормализованными координатами)
        return masks.mapNotNull { MaskUtils.extractContourFromMask(it) }
    }
}