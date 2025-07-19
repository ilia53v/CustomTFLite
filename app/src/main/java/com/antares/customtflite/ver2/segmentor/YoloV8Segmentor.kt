package com.antares.customtflite.ver2.segmentor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PointF
import android.util.Log
import android.util.Size
import com.antares.customtflite.data.Quadruple
import com.antares.customtflite.data.YoloObject
import com.antares.customtflite.ver2.YoloContourDrawer
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
        interpreter = Interpreter(loadModelFile("segment_17_07.tflite"), options)
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

    fun runInference(bitmap: Bitmap): Quadruple<List<List<PointF>>, FloatArray?, List<YoloObject>, Bitmap> {
        val inputBuffer = preprocessBitmap(bitmap)

        val output0Shape = interpreter.getOutputTensor(0).shape()
        val output1Shape = interpreter.getOutputTensor(1).shape()


        val isShape1x37x8400 = output0Shape contentEquals intArrayOf(1, 37, 8400)
        if (!isShape1x37x8400) {
            throw IllegalStateException("Unsupported output shape: ${output0Shape.joinToString()}")
        }

        val outChannels = output0Shape[1]              // 37
        val numMaskCoeffs = outChannels - 6

        val output0 = Array(1) { Array(37) { FloatArray(8400) } }
        val output1 = Array(1) { Array(320) { Array(320) { FloatArray(32) } } }
        val outputs = mapOf(0 to output0, 1 to output1)

        synchronized(interpreterLock) {
            interpreter.runForMultipleInputsOutputs(arrayOf(inputBuffer), outputs)
        }

        val confidenceThreshold = 0.32f
        val filteredMaskCoeffs = mutableListOf<FloatArray>()
        val detectedObjects = mutableListOf<YoloObject>()

        val videoWidth = bitmap.width.toFloat()
        val videoHeight = bitmap.height.toFloat()

        val out0 = output0 as Array<Array<FloatArray>>
        for (i in 0 until 8400) {
            val obj = sigmoid(out0[0][4][i])
            val cls = sigmoid(out0[0][5][i])
            val conf = obj * cls

            if (conf <= confidenceThreshold) continue

            val cx = out0[0][0][i]
            val cy = out0[0][1][i]
            val w = out0[0][2][i]
            val h = out0[0][3][i]

            if (cx !in 0f..1f || cy !in 0f..1f || w <= 0f || h <= 0f || w > 1f || h > 1f) {
                Log.d("BBox_SKIP", "cx=$cx, w=$w — skipped")
                continue
            }

            val absCx = cx * videoWidth
            val absCy = cy * videoHeight
            val absW = w * videoWidth
            val absH = h * videoHeight

            var left = absCx - absW / 2f
            var top = absCy - absH / 2f
            var right = absCx + absW / 2f
            var bottom = absCy + absH / 2f

            if (right <= 0f || bottom <= 0f || left >= videoWidth || top >= videoHeight) {
                Log.d("BBox_OUTSIDE", "Box is outside view: left=$left top=$top right=$right bottom=$bottom")
                continue
            }

            left = left.coerceIn(0f, videoWidth)
            top = top.coerceIn(0f, videoHeight)
            right = right.coerceIn(0f, videoWidth)
            bottom = bottom.coerceIn(0f, videoHeight)

            Log.d("BBox_OK", "topLeft=PointF($left, $top), bottomRight=PointF($right, $bottom)")
            detectedObjects.add(YoloObject(PointF(left, top), PointF(right, bottom), conf))

            val coeffs = FloatArray(numMaskCoeffs) { j -> out0[0][6 + j][i] }
            filteredMaskCoeffs.add(coeffs)
        }

        val protos = Array(32) { c ->
            Array(320) { y ->
                FloatArray(320) { x -> output1[0][y][x][c] }
            }
        }
        Log.d("DEBUG_MASK", "filteredMaskCoeffs=${filteredMaskCoeffs.size} firstLen=${filteredMaskCoeffs.firstOrNull()?.size}")

        val masks = MaskUtils2.computeMasks(filteredMaskCoeffs.toTypedArray(), protos)

        Log.d("masks", "masks = ${masks.size}")
        val contours = masks.mapNotNull { mask ->
            MaskUtils2.extractContourFromMask(mask, 320, 320, confidenceThreshold)?.map { pt ->
                PointF(pt.x * videoWidth, pt.y * videoHeight)
            }
        }

        Log.d("YoloV8Segmentor", "Contours found: ${contours.size}")

        val overlayBitmap = YoloContourDrawer(
            displaySize = Size(bitmap.width, bitmap.height)
        ).drawDetections(
            bboxList = detectedObjects.map { Triple(it.topLeft, it.bottomRight, it.confidence) },
            contours = contours,
            baseFrame = bitmap
        )

        return Quadruple(contours, masks.firstOrNull(), detectedObjects, overlayBitmap)
    }

    private fun sigmoid(x: Float): Float = 1f / (1f + exp(-x))
}