package com.antares.customtflite.ver2

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PointF
import android.graphics.RectF
import android.util.Log
import com.antares.customtflite.data.Detection
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import kotlin.math.exp


/*
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
        val confidenceThreshold = 0.2f

        if (isOutput0Shape8400x38) {
            val out0 = output0 as Array<Array<FloatArray>>
            for (i in 0 until 8400) {
                val rawObj = out0[0][i][4]
                val rawCls = out0[0][i][5]
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
*/

/*
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

        val output0 = Array(1) { Array(38) { FloatArray(8400) } }
        val output1 = Array(1) { Array(160) { Array(160) { FloatArray(32) } } }
        val outputs = mapOf(0 to output0, 1 to output1)

        synchronized(interpreterLock) {
            interpreter.runForMultipleInputsOutputs(arrayOf(inputBuffer), outputs)
        }

        val transposedOutput0 = Array(1) { Array(8400) { FloatArray(38) } }
        for (i in 0 until 8400) {
            for (j in 0 until 38) {
                transposedOutput0[0][i][j] = output0[0][j][i]
            }
        }

        val protos = Array(32) { i ->
            Array(160) { y ->
                FloatArray(160) { x ->
                    output1[0][y][x][i]
                }
            }
        }

        val confidenceThreshold = 0.2f
        val maskCoeffs = mutableListOf<FloatArray>()
        val bboxes = mutableListOf<RectF>()

        for (i in 0 until 8400) {
            val raw = transposedOutput0[0][i]

            val obj = sigmoid(raw[4])
            val cls = sigmoid(raw[5])
            val conf = obj * cls

            if (conf > confidenceThreshold) {
                val cx = sigmoid(raw[0])
                val cy = sigmoid(raw[1])
                val w = sigmoid(raw[2])
                val h = sigmoid(raw[3])

                val left = cx - w / 2f
                val top = cy - h / 2f
                val right = cx + w / 2f
                val bottom = cy + h / 2f

                val rect = RectF(
                    left * bitmap.width,
                    top * bitmap.height,
                    right * bitmap.width,
                    bottom * bitmap.height
                )

                val coeffs = FloatArray(32) { j -> raw[6 + j] }
                bboxes.add(rect)
                maskCoeffs.add(coeffs)
            }
        }

        Log.d("YoloV8Segmentor", "Filtered ${maskCoeffs.size} masks")

        val masks = MaskUtils.computeMasks(maskCoeffs.toTypedArray(), protos)

        val contours = mutableListOf<List<PointF>>()
        for (i in masks.indices) {
            val rawContour = MaskUtils.extractContourFromMask(masks[i]) ?: continue
            val box = bboxes[i]
            val scaledContour = rawContour.map {
                val x = box.left + box.width() * it.x
                val y = box.top + box.height() * it.y
                PointF(x, y)
            }
            contours.add(scaledContour)
        }

        Log.d("YoloV8Segmentor", "Extracted ${contours.size} contours")

        return contours
    }

    private fun sigmoid(x: Float): Float = (1f / (1f + exp(-x)))
}
*/

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

    private val interpreterLock = Any()

    fun runInference(bitmap: Bitmap): List<List<PointF>> {
        val inputBuffer = preprocessBitmap(bitmap)
        val output0Shape = interpreter.getOutputTensor(0).shape()
        val output1Shape = interpreter.getOutputTensor(1).shape()

        val output0: Any
        val isOutput0Shape8400x38 = output0Shape contentEquals intArrayOf(1, 8400, 38)

        output0 = if (isOutput0Shape8400x38) {
            Array(1) { Array(8400) { FloatArray(38) } }
        } else if (output0Shape contentEquals intArrayOf(1, 38, 8400)) {
            Array(1) { Array(38) { FloatArray(8400) } }
        } else {
            throw IllegalStateException("Unsupported output0 shape: ${output0Shape.joinToString()}")
        }

        val output1 = Array(1) { Array(160) { Array(160) { FloatArray(32) } } }
        val outputs = mapOf(0 to output0, 1 to output1)

        synchronized(interpreterLock) {
            interpreter.runForMultipleInputsOutputs(arrayOf(inputBuffer), outputs)
        }

        val filteredMaskCoeffs = mutableListOf<FloatArray>()
        val confidenceThreshold = 0.2f

        if (isOutput0Shape8400x38) {
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
        } else {
            val out0 = output0 as Array<Array<FloatArray>>
            for (i in 0 until 8400) {
                val obj = sigmoid(out0[0][4][i])
                val cls = sigmoid(out0[0][5][i])
                val conf = obj * cls
                if (conf > confidenceThreshold) {
                    val coeffs = FloatArray(32) { j -> out0[0][j + 6][i] }
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

        val masks = MaskUtils.computeMasks(filteredMaskCoeffs.toTypedArray(), protos)

        // Преобразование контуров из нормализованных координат [0..1] в пиксельные координаты исходного bitmap
        val scaleX = bitmap.width.toFloat()
        val scaleY = bitmap.height.toFloat()

        val contours = masks.mapNotNull { mask ->
            MaskUtils.extractContourFromMask(mask)
        }
        Log.d("YoloV8Segmentor", "First contour first point: ${contours.firstOrNull()?.firstOrNull()}")

        return contours
    }

    private fun sigmoid(x: Float): Float = (1f / (1f + exp(-x)))
}
