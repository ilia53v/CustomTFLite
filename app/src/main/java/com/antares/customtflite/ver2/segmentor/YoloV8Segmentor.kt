package com.antares.customtflite.ver2.segmentor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PointF
import android.util.Log
import com.antares.customtflite.data.YoloObject
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
        interpreter = Interpreter(loadModelFile("best_float_segment_last.tflite"), options)
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

    fun runInference(bitmap: Bitmap): Triple<List<List<PointF>>, FloatArray?, List<YoloObject>> {
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

        val confidenceThreshold = 0.3f
        val filteredMaskCoeffs = mutableListOf<FloatArray>()
        val detectedObjects = mutableListOf<YoloObject>()

        if (isShape1x8400x38) {
            val out0 = output0 as Array<Array<FloatArray>>
            for (i in 0 until 8400) {
                val obj = sigmoid(out0[0][i][4])
                val cls = sigmoid(out0[0][i][5])
                val conf = obj * cls
                if (conf > confidenceThreshold) {
                    val cx = out0[0][i][0] * inputSize
                    val cy = out0[0][i][1] * inputSize
                    val w = out0[0][i][2] * inputSize
                    val h = out0[0][i][3] * inputSize

                    val left = cx - w / 2f
                    val top = cy - h / 2f
                    val right = cx + w / 2f
                    val bottom = cy + h / 2f

                    Log.d("BBox_isShape1x8400x38", "raw topLeft=PointF($left, $top), bottomRight=PointF($right, $bottom)")

                    detectedObjects.add(YoloObject(PointF(left, top), PointF(right, bottom), conf))

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
                    val cx = out0[0][0][i] // [0..1]
                    val cy = out0[0][1][i]
                    val w  = out0[0][2][i]
                    val h  = out0[0][3][i]

                    // если вам нужно в пикселях — используйте размеры inferenceSize
                    val absCx = cx * 640
                    val absCy = cy * 640
                    val absW  = w * 640
                    val absH  = h * 640

                    val left = absCx - absW / 2
                    val top = absCy - absH / 2
                    val right = absCx + absW / 2
                    val bottom = absCy + absH / 2

                    val topLeft = PointF(left, top)
                    val bottomRight = PointF(right, bottom)

                    Log.d("BBox_isShape1x38x8400", "raw topLeft=PointF($left, $top), bottomRight=PointF($right, $bottom)")

                    detectedObjects.add(YoloObject(PointF(left, top), PointF(right, bottom), conf))

                    val coeffs = FloatArray(32) { j -> out0[0][6 + j][i] }
                    filteredMaskCoeffs.add(coeffs)
                }
            }
        }

        val protos = Array(32) { c ->
            Array(160) { y ->
                FloatArray(160) { x ->
                    output1[0][y][x][c]
                }
            }
        }

        val masks = MaskUtils2.computeMasks(filteredMaskCoeffs.toTypedArray(), protos)
        val contours = masks.mapNotNull { mask ->
            MaskUtils2.extractContourFromMask(mask, 160, 160, 0.5f)
        }

        Log.d("YoloV8Segmentor", "Contours found: ${contours.size}")

        return Triple(contours, masks.firstOrNull(), detectedObjects)
    }

    private fun sigmoid(x: Float): Float = 1f / (1f + exp(-x))
}