package com.antares.customtflite.ver2.segmentor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
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

    fun runInference(bitmap: Bitmap): Triple<List<List<List<PointF>>>, FloatArray?, List<YoloObject>> {
        val inputBuffer = preprocessBitmap(bitmap)
        val output0 = Array(1) { Array(37) { FloatArray(8400) } }
        val output1 = Array(1) { Array(320) { Array(320) { FloatArray(32) } } }
        val outputs = mapOf(0 to output0, 1 to output1)

        synchronized(interpreterLock) {
            interpreter.runForMultipleInputsOutputs(arrayOf(inputBuffer), outputs)
        }

        val videoWidth = bitmap.width.toFloat()
        val videoHeight = bitmap.height.toFloat()
        val confidenceThreshold = 0.35f

        val detectedObjects = mutableListOf<YoloObject>()
        val filteredMaskCoeffs = mutableListOf<FloatArray>()
        val numMaskCoeffs = 31

        for (i in 0 until 8400) {
            val obj = sigmoid(output0[0][4][i])
            val cls = sigmoid(output0[0][5][i])
            val conf = obj * cls
            if (conf <= confidenceThreshold) continue

            val cx = output0[0][0][i]
            val cy = output0[0][1][i]
            val w = output0[0][2][i]
            val h = output0[0][3][i]
            if (cx !in 0f..1f || cy !in 0f..1f || w <= 0f || h <= 0f || w > 1f || h > 1f) continue

            val absCx = cx * videoWidth
            val absCy = cy * videoHeight
            val absW = w * videoWidth
            val absH = h * videoHeight

            val left = (absCx - absW / 2f).coerceIn(0f, videoWidth)
            val top = (absCy - absH / 2f).coerceIn(0f, videoHeight)
            val right = (absCx + absW / 2f).coerceIn(0f, videoWidth)
            val bottom = (absCy + absH / 2f).coerceIn(0f, videoHeight)

            detectedObjects.add(YoloObject(PointF(left, top), PointF(right, bottom), conf))
            filteredMaskCoeffs.add(FloatArray(numMaskCoeffs) { j -> output0[0][6 + j][i] })
        }

        val protos = Array(32) { c -> Array(320) { y -> FloatArray(320) { x -> output1[0][y][x][c] } } }
        val masks = MaskUtils2.computeMasks(filteredMaskCoeffs.toTypedArray(), protos)

        if (masks.isEmpty()) {
            Log.w("YoloV8Segmentor", "No masks computed.")
            return Triple(emptyList(), null, detectedObjects)
        }

        val allContours = mutableListOf<List<List<PointF>>>()

        for (i in masks.indices) {
            val flatMask = masks[i]
            val reshapedMask = Array(320) { y -> FloatArray(320) { x -> flatMask[y * 320 + x] } }
            val bbox = detectedObjects.getOrNull(i) ?: continue

            // (2, 3, 4) — всё внутри
            val result = MaskUtils2.extractContoursFromMask(
                mask = reshapedMask,
                maskWidth = 320,
                maskHeight = 320,
                threshold = confidenceThreshold,
                bbox = bbox,
                displaySize = Size(bitmap.width, bitmap.height)
            )

            if (result.contours.isNotEmpty()) {
                allContours.add(result.contours)
            }
        }

        return Triple(allContours, masks.firstOrNull(), detectedObjects)
    }

    private fun sigmoid(x: Float): Float = 1f / (1f + exp(-x))
}