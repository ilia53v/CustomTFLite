package com.antares.customtflite.ver2.segmentor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PointF
import android.util.Log
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import kotlin.math.exp

class YoloV8Segmentor(private val context: Context) {

    private val inputSize = 640
    private val interpreter: Interpreter
    private val interpreterLock = Any()

    init {
        val options = Interpreter.Options().apply {
            setUseXNNPACK(false)
            setNumThreads(1)
        }
        interpreter = Interpreter(loadModelFile("best_segment_float32.tflite"), options)
    }

    private fun loadModelFile(modelName: String): ByteBuffer {
        val fileDescriptor = context.assets.openFd(modelName)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        return fileChannel.map(
            FileChannel.MapMode.READ_ONLY,
            fileDescriptor.startOffset,
            fileDescriptor.declaredLength
        )
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

    /*fun runInference(bitmap: Bitmap): List<List<PointF>> {
        val inputBuffer = preprocessBitmap(bitmap)

        val output0Shape = interpreter.getOutputTensor(0).shape()
        val output1Shape = interpreter.getOutputTensor(1).shape()

        val isShape1x8400x38 = output0Shape contentEquals intArrayOf(1, 8400, 38)
        val isShape1x38x8400 = output0Shape contentEquals intArrayOf(1, 38, 8400)

        val output0: Any = when {
            isShape1x8400x38 -> Array(1) { Array(8400) { FloatArray(38) } }
            isShape1x38x8400 -> Array(1) { Array(38) { FloatArray(8400) } }
            else -> throw IllegalStateException("Unsupported output0 shape: ${output0Shape.joinToString()}")
        }

        val output1 = Array(1) { Array(160) { Array(160) { FloatArray(32) } } }
        val outputs = mapOf(0 to output0, 1 to output1)

        synchronized(interpreterLock) {
            interpreter.runForMultipleInputsOutputs(arrayOf(inputBuffer), outputs)
        }

        val confidenceThreshold = 0.5f
        val filteredMaskCoeffs = mutableListOf<FloatArray>()

        if (isShape1x8400x38) {
            val out0 = output0 as Array<Array<FloatArray>>
            for (i in 0 until 8400) {
                val obj = sigmoid(out0[0][i][4])
                val cls = sigmoid(out0[0][i][5])
                val conf = obj * cls
                if (conf > confidenceThreshold) {
                    val coeffs = FloatArray(32) { j -> out0[0][i][6 + j] }
                    filteredMaskCoeffs.add(coeffs)
                }
            }
        } else if (isShape1x38x8400) {
            val out0 = output0 as Array<Array<FloatArray>>
            for (i in 0 until 8400) {
                val obj = sigmoid(out0[0][4][i])
                val cls = sigmoid(out0[0][5][i])
                val conf = obj * cls
                if (conf > confidenceThreshold) {
                    val coeffs = FloatArray(32) { j -> out0[0][6 + j][i] }
                    filteredMaskCoeffs.add(coeffs)
                }
            }
        }

        val protos = Array(32) { i ->
            Array(160) { y ->
                FloatArray(160) { x ->
                    output1[0][y][x][i]
                }
            }
        }

        val masks = try {
            MaskUtils2.computeMasks(filteredMaskCoeffs.toTypedArray(), protos)
        } catch (e: OutOfMemoryError) {
            Log.e("YoloV8Segmentor", "OOM during mask computation", e)
            return emptyList()
        }

        val contours = mutableListOf<List<PointF>>()
        for (mask in masks) {
            val contour = MaskUtils2.extractContourFromMask(mask, 160, 160, threshold = 0.6f)
            if (contour != null && contour.size in 3..1000) {
                contours.add(contour)
            }
        }

        Log.d("YoloV8Segmentor", "Contours found: ${contours.size}")
        return contours
    }
*/

    fun runInference(bitmap: Bitmap): Pair<List<List<PointF>>, FloatArray?>
    {
        val inputBuffer = preprocessBitmap(bitmap)

        val output0Shape = interpreter.getOutputTensor(0).shape()
        val output1Shape = interpreter.getOutputTensor(1).shape()

        val isShape1x8400x38 = output0Shape contentEquals intArrayOf(1, 8400, 38)
        val isShape1x38x8400 = output0Shape contentEquals intArrayOf(1, 38, 8400)

        val output0: Any = when {
            isShape1x8400x38 -> Array(1) { Array(8400) { FloatArray(38) } }
            isShape1x38x8400 -> Array(1) { Array(38) { FloatArray(8400) } }
            else -> throw IllegalStateException("Unsupported output0 shape: ${output0Shape.joinToString()}")
        }

        val output1 = Array(1) { Array(160) { Array(160) { FloatArray(32) } } }
        val outputs = mapOf(0 to output0, 1 to output1)

        synchronized(interpreterLock) {
            interpreter.runForMultipleInputsOutputs(arrayOf(inputBuffer), outputs)
        }

        val confidenceThreshold = 0.1f
        val filteredMaskCoeffs = mutableListOf<FloatArray>()

        if (isShape1x8400x38) {
            val out0 = output0 as Array<Array<FloatArray>>
            for (i in 0 until 8400) {
                val obj = sigmoid(out0[0][i][4])
                val cls = sigmoid(out0[0][i][5]) // один класс
                val conf = obj * cls
                if (conf > confidenceThreshold) {
                    val coeffs = FloatArray(32) { j -> out0[0][i][6 + j] }
                    filteredMaskCoeffs.add(coeffs)
                }
            }
        } else if (isShape1x38x8400) {
            val out0 = output0 as Array<Array<FloatArray>>
            for (i in 0 until 8400) {
                val obj = sigmoid(out0[0][4][i])
                val cls = sigmoid(out0[0][5][i])
                val conf = obj * cls
                if (conf > confidenceThreshold) {
                    val coeffs = FloatArray(32) { j -> out0[0][6 + j][i] }
                    filteredMaskCoeffs.add(coeffs)
                }
            }
        }

        // Преобразуем output1 -> protos[channel][height][width]
        val protos = Array(32) { c ->
            Array(160) { y ->
                FloatArray(160) { x ->
                    output1[0][y][x][c]
                }
            }
        }

        // Вычисляем маски
        val masks = MaskUtils2.computeMasks(filteredMaskCoeffs.toTypedArray(), protos)

        // Извлекаем контуры
        val contours = masks.mapNotNull { mask ->
            MaskUtils2.extractContourFromMask(mask, 160, 160, 0.5f)
        }

        // Логгирование
        Log.d("YoloV8Segmentor", "Contours found: ${contours.size}")
        Log.d("MaskUtils2", "mask min=${masks.firstOrNull()?.minOrNull()} max=${masks.firstOrNull()?.maxOrNull()}")

// Вернуть контуры + первую маску для отрисовки
        return contours to masks.firstOrNull()
    }


    private fun sigmoid(x: Float): Float = 1f / (1f + exp(-x))
}