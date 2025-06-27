package com.antares.customtflite.check_model

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

fun runYolov5OnAssetImage(context: Context) {
    // Загружаем модель
    val interpreter = Interpreter(loadModelFile(context, "best_float16.tflite"))

    // Загружаем изображение из assets
    val bitmap = BitmapFactory.decodeStream(context.assets.open("img61.jpg"))

    // Предобработка (640x640, RGB, float)
    val input = preprocessBitmap(bitmap)

    // Буфер для вывода
    val output = createOutputBuffer()

    // Запускаем инференс
    interpreter.run(input, output)

    // Парсим результат
    val detections = parseOutput(output)

    // Выводим результат
    for (d in detections) {
        Log.d("YOLO-DETECT", "x=${d.x}, y=${d.y}, w=${d.w}, h=${d.h}, conf=${d.score}, class=${d.classId}")
    }
}

fun loadModelFile(context: Context, modelName: String): MappedByteBuffer {
    val fileDescriptor = context.assets.openFd(modelName)
    val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
    val fileChannel = inputStream.channel
    return fileChannel.map(FileChannel.MapMode.READ_ONLY, fileDescriptor.startOffset, fileDescriptor.declaredLength)
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
    //return Array(1) { Array(25200) { FloatArray(85) } } // [1,25200,85] для YOLOv5
    return Array(1) { Array(5) { FloatArray(8400) } } // [1, 5, 8400]
}

data class DetectionOnImage(val x: Float, val y: Float, val w: Float, val h: Float, val score: Float, val classId: Int)

fun parseOutput(output: Array<Array<FloatArray>>, objThreshold: Float = 0.4f, classThreshold: Float = 0.3f): List<DetectionOnImage> {
    val results = mutableListOf<DetectionOnImage>()
    val predictions = output[0]

    for (i in predictions.indices) {
        val row = predictions[i]
        val objectness = row[4]
        if (objectness < objThreshold) continue

        val classScores = row.copyOfRange(5, row.size)
        val (classId, classProb) = classScores.withIndex().maxByOrNull { it.value } ?: continue
        val confidence = objectness * classProb
        if (confidence > classThreshold) {
            results.add(DetectionOnImage(row[0], row[1], row[2], row[3], confidence, classId))
        }
    }

    return results
}