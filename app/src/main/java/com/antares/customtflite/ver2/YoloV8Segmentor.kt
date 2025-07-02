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
import kotlin.math.exp


class YoloV8Segmentor(private val context: Context) {

    private val inputSize = 640
    private val interpreter: Interpreter

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

    fun runInference(bitmap: Bitmap): List<List<PointF>> {
        val inputBuffer = preprocessBitmap(bitmap)

        Log.d("YoloV8Segmentor", "runInference started")

        // Проверка формы выходного тензора
        val output0Shape = interpreter.getOutputTensor(0).shape()
        val output1Shape = interpreter.getOutputTensor(1).shape()
        Log.d("YoloV8Segmentor", "Output0 shape = ${output0Shape.joinToString()}")
        Log.d("YoloV8Segmentor", "Output1 shape = ${output1Shape.joinToString()}")

        // Инициализация выходов на основе формы
        val output0: Any
        val isOutput0Shape8400x38 = output0Shape contentEquals intArrayOf(1, 8400, 38)

        if (isOutput0Shape8400x38) {
            output0 = Array(1) { Array(8400) { FloatArray(38) } }
        } else if (output0Shape contentEquals intArrayOf(1, 38, 8400)) {
            output0 = Array(1) { Array(38) { FloatArray(8400) } }
        } else {
            throw IllegalStateException("Unsupported output0 shape: ${output0Shape.joinToString()}")
        }

        val output1 = Array(1) { Array(160) { Array(160) { FloatArray(32) } } }
        val outputs = mapOf(0 to output0, 1 to output1)

        synchronized(interpreterLock) {
            interpreter.runForMultipleInputsOutputs(arrayOf(inputBuffer), outputs)
        }

        val filteredMaskCoeffs = mutableListOf<FloatArray>()
        val confidenceThreshold = 0.1f

        if (isOutput0Shape8400x38) {
            val out0 = output0 as Array<Array<FloatArray>>
            for (i in 0 until 8400) {
                val rawObj = out0[0][4][i]
                val rawCls = out0[0][5][i]
                val obj = sigmoid(rawObj)
                val cls = sigmoid(rawCls)
                val conf = obj * cls
                Log.d("YoloV8Segmentor", "Index $i: raw_obj=$rawObj, raw_cls=$rawCls, obj=$obj, cls=$cls, conf=$conf")
                if (conf > confidenceThreshold) {
                    val coeffs = FloatArray(32) { j -> out0[0][i][6 + j] }
                    filteredMaskCoeffs.add(coeffs)
                }
            }
        } else {
            val out0 = output0 as Array<Array<FloatArray>>
            for (i in 0 until 8400) {
                val rawObj = out0[0][4][i]
                val rawCls = out0[0][5][i]
                val obj = sigmoid(rawObj)
                val cls = sigmoid(rawCls)
                val conf = obj * cls
                Log.d("YoloV8Segmentor", "Index $i: raw_obj=$rawObj, raw_cls=$rawCls, obj=$obj, cls=$cls, conf=$conf")
                if (conf > confidenceThreshold) {
                    val coeffs = FloatArray(32) { j -> out0[0][j + 6][i] }
                    filteredMaskCoeffs.add(coeffs)
                }
            }
        }

        Log.d("YoloV8Segmentor", "Filtered mask count: ${filteredMaskCoeffs.size}")

        val protos = Array(32) { i ->
            Array(160) { y ->
                FloatArray(160) { x ->
                    output1[0][y][x][i]
                }
            }
        }

        val masks = MaskUtils.computeMasks(filteredMaskCoeffs.toTypedArray(), protos)
        Log.d("YoloV8Segmentor", "computeMasks returned ${masks.size} masks")

        val contours = masks.mapNotNull { MaskUtils.extractContourFromMask(it) }
        Log.d("YoloV8Segmentor", "Extracted ${contours.size} contours from masks")

        if (contours.isNotEmpty()) {
            Log.d("YoloV8Segmentor", "First contour size: ${contours[0].size}, first point: ${contours[0].firstOrNull()}")
        }

        return contours
    }

    private fun sigmoid(x: Float): Float = (1f / (1f + exp(-x)))
}

