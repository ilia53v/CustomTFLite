package com.antares.customtflite.check_model

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.util.Log
import org.tensorflow.lite.Interpreter
import java.io.File
import java.io.FileOutputStream
import java.io.FileInputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

class YoloV8VideoTester(private val context: Context) {

    private val interpreter: Interpreter by lazy {
        Interpreter(loadModelFile("best.tflite"))
    }

    // Загружаем модель из assets
    private fun loadModelFile(modelName: String): ByteBuffer {
        val fileDescriptor = context.assets.openFd(modelName)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    // Копируем видео из assets в кэш
    private fun copyAssetToCache(assetFileName: String): String {
        val outFile = File(context.cacheDir, assetFileName)
        if (!outFile.exists()) {
            try {
                context.assets.open(assetFileName).use { input ->
                    FileOutputStream(outFile).use { output ->
                        input.copyTo(output)
                    }
                }
                Log.d("YoloV8", "Файл скопирован в кэш: ${outFile.absolutePath}")
            } catch (e: IOException) {
                Log.e("YoloV8", "Ошибка копирования файла: $e")
                throw e
            }
        }
        return outFile.absolutePath
    }

    // Предобработка Bitmap в ByteBuffer [1, 640, 640, 3]
    private fun preprocessBitmap(bitmap: Bitmap): ByteBuffer {
        val inputSize = 640
        val resized = Bitmap.createScaledBitmap(bitmap, inputSize, inputSize, true)
        val byteBuffer = ByteBuffer.allocateDirect(1 * inputSize * inputSize * 3 * 4)
        byteBuffer.order(ByteOrder.nativeOrder())

        val pixels = IntArray(inputSize * inputSize)
        resized.getPixels(pixels, 0, inputSize, 0, 0, inputSize, inputSize)

        for (pixel in pixels) {
            val r = ((pixel shr 16) and 0xFF) / 255.0f
            val g = ((pixel shr 8) and 0xFF) / 255.0f
            val b = (pixel and 0xFF) / 255.0f

            byteBuffer.putFloat(r)
            byteBuffer.putFloat(g)
            byteBuffer.putFloat(b)
        }
        return byteBuffer
    }

    // Выходной буфер [1, 5, 8400]
    private fun createOutputBuffer(): Array<Array<FloatArray>> {
        return Array(1) { Array(5) { FloatArray(8400) } }
    }

    // Обработка видео из assets
    fun runInferenceOnVideoAsset(assetVideoFileName: String) {
        val videoPath = copyAssetToCache(assetVideoFileName)
        val retriever = MediaMetadataRetriever()
        retriever.setDataSource(videoPath)

        val frameIntervalUs = 1_000_000L / 5  // 5 FPS
        var timeUs = 0L

        Log.d("YoloV8", "Начинаем анализ видео: $videoPath")

        while (true) {
            val frame = retriever.getFrameAtTime(timeUs, MediaMetadataRetriever.OPTION_CLOSEST)
                ?: break

            val inputBuffer = preprocessBitmap(frame)
            val outputBuffer = createOutputBuffer()

            interpreter.run(inputBuffer, outputBuffer)

            val detections = parseOutput(outputBuffer)
            if(detections.size>0){
                Log.d("YoloV8", "Кадр ${timeUs / 1000} мс — объектов: ${detections.size}")
            }

            detections.forEach {
                Log.d("Detection", "x=${it.x}, y=${it.y}, w=${it.w}, h=${it.h}, score=${it.score}")
            }

            timeUs += frameIntervalUs
        }

        retriever.release()
    }

    data class Detection(val x: Float, val y: Float, val w: Float, val h: Float, val score: Float)

    /*private fun parseOutput(output: Array<Array<FloatArray>>, threshold: Float = 0.5f): List<Detection> {
        val detections = mutableListOf<Detection>()
        val channels = output[0]  // [5][8400]

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
    }*/
    private fun parseOutput(output: Array<Array<FloatArray>>, threshold: Float = 0.25f): List<Detection> {
        val detections = mutableListOf<Detection>()
        val channels = output[0] // shape: [5][8400]

        Log.d("TFLITE", "Sample output: ${output[0][0].joinToString(", ", limit = 10)}")
        Log.d("YOLOv5", "Output shape: " + interpreter.getOutputTensor(0).shape().joinToString())

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