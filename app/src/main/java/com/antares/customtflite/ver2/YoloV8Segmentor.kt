package com.antares.customtflite.ver2

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PointF
import com.antares.customtflite.data.Detection
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

class YoloV8Segmentor(private val context: Context) {

    private val interpreter: Interpreter by lazy {
        Interpreter(loadModelFile("best_float32.tflite"))
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
        return buffer
    }

    fun runInference(bitmap: Bitmap): List<List<PointF>> {
        val input = preprocess(bitmap)

        // Выход YOLOv8-seg:
        val output0 = Array(1) { Array(8400) { FloatArray(32) } } // bboxes + mask coeffs
        val output1 = Array(1) { Array(32) { FloatArray(160 * 160) } } // protos

        val outputs = mapOf(
            0 to output0,
            1 to output1
        )

        interpreter.runForMultipleInputsOutputs(arrayOf(input), outputs)

        val masks = MaskUtils.computeMasks(output0[0], output1[0])
        return masks.mapNotNull { mask -> MaskUtils.extractContourFromMask(mask) }
    }

    fun preprocessBitmap(bitmap: Bitmap): ByteBuffer {
        val inputSize = 640
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

        return inputBuffer
    }

    fun createOutputBuffer(): Array<Array<FloatArray>> {
        return Array(1) { Array(5) { FloatArray(8400) } } // [1, 5, 8400]
    }

    fun runInferenceOnBitmap(bitmap: Bitmap): List<Detection> {
        val inputBuffer = preprocessBitmap(bitmap)
        val outputBuffer = createOutputBuffer()
        interpreter.run(inputBuffer, outputBuffer)
        return parseOutput(outputBuffer)
    }

    fun runContoursOnBitmap(bitmap: Bitmap): List<List<PointF>> {
        // Для упрощения пример возвращает квадратные контуры по bbox
        val detections = runInferenceOnBitmap(bitmap)

        val contours = mutableListOf<List<PointF>>()
        for (det in detections) {
            val x = det.x
            val y = det.y
            val w = det.w
            val h = det.h

            // Пример контуров — прямоугольник (нормализованные координаты 0..1)
            val contour = listOf(
                PointF(x - w / 2f, y - h / 2f), // левый верхний
                PointF(x + w / 2f, y - h / 2f), // правый верхний
                PointF(x + w / 2f, y + h / 2f), // правый нижний
                PointF(x - w / 2f, y + h / 2f)  // левый нижний
            )
            contours.add(contour)
        }
        return contours
    }

    private fun parseOutput(output: Array<Array<FloatArray>>, threshold: Float = 0.25f): List<Detection> {
        val detections = mutableListOf<Detection>()
        val channels = output[0] // shape: [5][8400]
        for (i in 0 until 8400) {
            val x = channels[0][i]
            val y = channels[1][i]
            val w = channels[2][i]
            val h = channels[3][i]
            val score = channels[4][i]
            if (score > threshold) {
                detections.add(Detection(x, y, w, h, score))
            }
        }
        return detections
    }
}
