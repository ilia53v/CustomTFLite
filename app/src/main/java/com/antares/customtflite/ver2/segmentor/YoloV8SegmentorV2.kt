package com.antares.customtflite.ver2.segmentor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.PointF
import android.util.Log
import android.util.Size
import com.antares.customtflite.data.Quadruple
import com.antares.customtflite.data.YoloObject
import com.antares.customtflite.ver2.YoloContourDrawer
import com.antares.customtflite.ver2.segmentor.MaskUtils2.computeMasks
import com.antares.customtflite.ver2.segmentor.MaskUtilsV2.extractContoursFromMask
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min


class YoloV8SegmentorV2(private val context: Context) {

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

    data class PreprocessResult(
        val inputBuffer: ByteBuffer,
        val scale: Float,
        val padX: Int,
        val padY: Int,
        val newWidth: Int,
        val newHeight: Int
    )

    private fun preprocessBitmap(bitmap: Bitmap): PreprocessResult {
        val inputWidth = inputSize
        val inputHeight = inputSize
        val srcWidth = bitmap.width
        val srcHeight = bitmap.height

        val scale = min(inputWidth.toFloat() / srcWidth, inputHeight.toFloat() / srcHeight)
        val newWidth = (srcWidth * scale).toInt()
        val newHeight = (srcHeight * scale).toInt()
        val padX = (inputWidth - newWidth) / 2
        val padY = (inputHeight - newHeight) / 2

        val letterboxed = Bitmap.createBitmap(inputWidth, inputHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(letterboxed)
        canvas.drawColor(Color.BLACK)
        val resized = Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
        canvas.drawBitmap(resized, padX.toFloat(), padY.toFloat(), null)

        val inputBuffer = ByteBuffer.allocateDirect(1 * inputWidth * inputHeight * 3 * 4)
        inputBuffer.order(ByteOrder.nativeOrder())

        val pixels = IntArray(inputWidth * inputHeight)
        letterboxed.getPixels(pixels, 0, inputWidth, 0, 0, inputWidth, inputHeight)

        for (pixel in pixels) {
            val r = ((pixel shr 16) and 0xFF) / 255.0f
            val g = ((pixel shr 8) and 0xFF) / 255.0f
            val b = (pixel and 0xFF) / 255.0f
            inputBuffer.putFloat(r)
            inputBuffer.putFloat(g)
            inputBuffer.putFloat(b)
        }

        inputBuffer.rewind()
        Log.d("YoloV8Segmentor", "Bitmap preprocessed: scale=$scale, padX=$padX, padY=$padY, newW=$newWidth, newH=$newHeight")
        return PreprocessResult(inputBuffer, scale, padX, padY, newWidth, newHeight)
    }

    fun runInference(
        bitmap: Bitmap,
        confidenceThreshold: Float
    ): Triple<List<List<List<PointF>>>, FloatArray?, List<YoloObject>> {

        val preprocess = preprocessBitmap(bitmap)
        val inputBuffer = preprocess.inputBuffer
        val scale = preprocess.scale
        val padX = preprocess.padX
        val padY = preprocess.padY
        val videoW = bitmap.width.toFloat()
        val videoH = bitmap.height.toFloat()

        val output0 = Array(1) { Array(37) { FloatArray(8400) } }
        val output1 = Array(1) { Array(320) { Array(320) { FloatArray(32) } } }
        val outputs = mapOf(0 to output0, 1 to output1)

        val startTime = System.currentTimeMillis()
        synchronized(interpreterLock) {
            interpreter.runForMultipleInputsOutputs(arrayOf(inputBuffer), outputs)
        }
        val endTime = System.currentTimeMillis()
        Log.i("YoloV8Segmentor", "Inference completed in ${endTime - startTime} ms")

        val strongObjects = mutableListOf<YoloObject>()
        val strongCoeffs = mutableListOf<FloatArray>()
        val numMaskCoeffs = 31

        for (i in 0 until 8400) {
            val obj = sigmoid(output0[0][4][i])
            val cls = sigmoid(output0[0][5][i])
            val conf = obj * cls
            if (conf < confidenceThreshold) continue

            val cx = output0[0][0][i]
            val cy = output0[0][1][i]
            val w = output0[0][2][i]
            val h = output0[0][3][i]

            val x1 = (cx - w / 2f) * inputSize
            val y1 = (cy - h / 2f) * inputSize
            val x2 = (cx + w / 2f) * inputSize
            val y2 = (cy + h / 2f) * inputSize

            val realLeft = ((x1 - padX) / scale).coerceIn(0f, videoW)
            val realTop = ((y1 - padY) / scale).coerceIn(0f, videoH)
            val realRight = ((x2 - padX) / scale).coerceIn(0f, videoW)
            val realBottom = ((y2 - padY) / scale).coerceIn(0f, videoH)

            val bbox = YoloObject(PointF(realLeft, realTop), PointF(realRight, realBottom), conf)
            val coeff = FloatArray(numMaskCoeffs) { j -> output0[0][6 + j][i] }

            strongObjects.add(bbox)
            strongCoeffs.add(coeff)
        }

        Log.i("YoloV8Segmentor", "Objects detected: ${strongObjects.size}")

        val masks = MaskUtils2.computeMasks(strongCoeffs.toTypedArray(), output1)
        if (masks.isEmpty()) {
            Log.w("YoloV8Segmentor", "No masks computed!")
            return Triple(emptyList(), null, strongObjects)
        }

        val contoursAll = mutableListOf<List<List<PointF>>>()
        for (i in masks.indices) {
            val flatMask = masks[i]
            val reshapedMask = Array(320) { y -> FloatArray(320) { x -> flatMask[y * 320 + x] } }
            val bbox = strongObjects.getOrNull(i) ?: continue

            val result = extractContoursFromMask(
                mask = reshapedMask,
                maskWidth = 320,
                maskHeight = 320,
                threshold = confidenceThreshold,
                bbox = bbox,
                displaySize = Size(bitmap.width, bitmap.height)
            )

            Log.d("YoloV8Segmentor", "Contours for object $i: ${result.contours.size}")
            if (result.contours.isNotEmpty()) contoursAll.add(result.contours)
        }

        Log.i("YoloV8Segmentor", "Total contours extracted: ${contoursAll.size}")
        return Triple(contoursAll, masks.firstOrNull(), strongObjects)
    }

    private fun sigmoid(x: Float): Float = 1f / (1f + exp(-x))

}