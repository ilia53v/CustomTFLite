package com.antares.customtflite

import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.util.Log
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.gpu.GpuDelegate
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.model.Model
import org.tensorflow.lite.DataType
import org.tensorflow.lite.support.common.FileUtil
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.MappedByteBuffer

class YoloV8Segmentor(private val context: Context) {

    companion object {
        private const val TAG = "YoloV8Segmentor"
        private const val MODEL_FILE = "best.tflite"

        // Размер входного изображения модели — подстрой под свою модель
        private const val MODEL_INPUT_WIDTH = 640
        private const val MODEL_INPUT_HEIGHT = 640
    }

    private var interpreter: Interpreter? = null
    private var gpuDelegate: GpuDelegate? = null

    init {
        try {
            val modelBuffer = loadModelFile()
            interpreter = createInterpreterWithFallback(modelBuffer)
            Log.i(TAG, "Interpreter initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize interpreter", e)
            throw e
        }
    }

    private fun loadModelFile(): MappedByteBuffer {
        return try {
            FileUtil.loadMappedFile(context, MODEL_FILE)
        } catch (e: IOException) {
            Log.e(TAG, "Failed to load model file: $MODEL_FILE", e)
            throw e
        }
    }

    private fun createInterpreterWithFallback(modelBuffer: MappedByteBuffer): Interpreter {
        val options = Interpreter.Options()

        if (isGpuDelegateSupported()) {
            try {
                gpuDelegate = GpuDelegate()
                options.addDelegate(gpuDelegate)
                Log.i(TAG, "Trying to create interpreter with GPU delegate")
                return Interpreter(modelBuffer, options)
            } catch (e: Exception) {
                Log.w(TAG, "GPU delegate initialization failed, fallback to CPU. Error: ${e.localizedMessage}")
                gpuDelegate?.close()
                gpuDelegate = null
            }
        } else {
            Log.i(TAG, "GPU delegate not supported on this device, using CPU")
        }

        return Interpreter(modelBuffer, Interpreter.Options())
    }

    private fun isGpuDelegateSupported(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            Log.i(TAG, "GPU delegate requires Android 8.0+ (Oreo), current API: ${Build.VERSION.SDK_INT}")
            return false
        }
        return true
    }

    /** Преобразует Bitmap в FloatArray с нормализацией [0,1] и размером под модель */
    private fun preprocessBitmap(bitmap: Bitmap): FloatArray {
        // Ресайз bitmap к MODEL_INPUT_WIDTH x MODEL_INPUT_HEIGHT
        val resizedBitmap = Bitmap.createScaledBitmap(bitmap, MODEL_INPUT_WIDTH, MODEL_INPUT_HEIGHT, true)

        // Модель ожидает float32 с нормализацией (0..1), порядок RGB
        val input = FloatArray(MODEL_INPUT_WIDTH * MODEL_INPUT_HEIGHT * 3)

        var idx = 0
        for (y in 0 until MODEL_INPUT_HEIGHT) {
            for (x in 0 until MODEL_INPUT_WIDTH) {
                val pixel = resizedBitmap.getPixel(x, y)
                // Получаем R, G, B как float от 0 до 1
                input[idx++] = ((pixel shr 16 and 0xFF) / 255.0f)
                input[idx++] = ((pixel shr 8 and 0xFF) / 255.0f)
                input[idx++] = ((pixel and 0xFF) / 255.0f)
            }
        }
        return input
    }

    /**
     * Запускает сегментацию по Bitmap, возвращает контуры объектов
     */
    fun runSegmentation(bitmap: Bitmap): List<Contour> {
        val inputData = preprocessBitmap(bitmap)
        return segment(inputData)
    }

    fun segment(inputData: FloatArray): List<Contour> {
        if (interpreter == null) {
            Log.e(TAG, "Interpreter is not initialized")
            return emptyList()
        }

        try {
            // Примерные выходы (подстрой под свою модель)
            val maskOutput = Array(1) { Array(128) { FloatArray(128) } }
            val scoresOutput = Array(1) { FloatArray(100) }
            val classesOutput = Array(1) { IntArray(100) }
            val coordsOutput = Array(1) { Array(100) { FloatArray(4) } }

            val outputs = mapOf(
                0 to maskOutput,
                1 to scoresOutput,
                2 to classesOutput,
                3 to coordsOutput
            )

            interpreter!!.runForMultipleInputsOutputs(arrayOf(inputData), outputs)

            val contours = mutableListOf<Contour>()
            val threshold = 0.5f

            for (i in scoresOutput[0].indices) {
                if (scoresOutput[0][i] > threshold) {
                    val bbox = coordsOutput[0][i]
                    val points = listOf(
                        Pair(bbox[0], bbox[1]),
                        Pair(bbox[0] + bbox[2], bbox[1]),
                        Pair(bbox[0] + bbox[2], bbox[1] + bbox[3]),
                        Pair(bbox[0], bbox[1] + bbox[3])
                    )
                    contours.add(Contour(points, scoresOutput[0][i], classesOutput[0][i]))
                }
            }

            return contours

        } catch (e: Exception) {
            Log.e(TAG, "Error during model inference", e)
            return emptyList()
        }
    }

    fun close() {
        interpreter?.close()
        gpuDelegate?.close()
    }
}