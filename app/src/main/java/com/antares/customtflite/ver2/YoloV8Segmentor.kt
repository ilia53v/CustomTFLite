package com.antares.customtflite.ver2

import android.content.Context
import android.graphics.Bitmap
import android.graphics.PointF
import com.antares.customtflite.check_model.createOutputBuffer
import com.antares.customtflite.check_model.preprocessBitmap
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

class YoloV8Segmentor(private val context: Context) {

    private val interpreter: Interpreter by lazy {
        Interpreter(loadModelFile("yolov8n-seg.tflite"))
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

// Класс для хранения детекций
data class Detection(
    val x: Float, // центр по X (0..1)
    val y: Float, // центр по Y (0..1)
    val w: Float, // ширина (0..1)
    val h: Float, // высота (0..1)
    val score: Float
)