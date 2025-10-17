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
import com.antares.customtflite.ver2.segmentor.MaskUtils2.computeMasks
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

class YoloV8Segmentor(private val context: Context) {

    // Размер входного изображения для модели YOLOv8
    private val inputSize = 640

    private val interpreter: Interpreter

    private val interpreterLock = Any()

    init {
        val options = Interpreter.Options().apply {
            setUseXNNPACK(false)  // XNNPACK ускоряет инференс, но иногда даёт несовместимости
            setNumThreads(1)      // Используем один поток для стабильности
        }
        interpreter = Interpreter(loadModelFile("segment_17_07.tflite"), options)
    }

    /**
     * Загружает файл модели (.tflite) из assets и возвращает его как ByteBuffer.
     */
    private fun loadModelFile(modelName: String): ByteBuffer {
        val fileDescriptor = context.assets.openFd(modelName)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        // Отображаем файл в память для более быстрого доступа
        return fileChannel.map(
            FileChannel.MapMode.READ_ONLY,
            fileDescriptor.startOffset,
            fileDescriptor.declaredLength
        )
    }

    /**
     * Подготавливает Bitmap для подачи в модель:
     * - масштабирует до 640×640,
     * - нормализует RGB-каналы в диапазон [0, 1],
     * - сохраняет в ByteBuffer в порядке (R, G, B).
     */
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

    /**
     * Выполняет инференс YOLOv8 Segment на одном кадре.
     *
     * Возвращает Triple:
     *  - список контуров объектов (для визуализации),
     *  - первую рассчитанную маску,
     *  - список детектированных объектов (bbox + confidence).
     */
    fun runInference(
        bitmap: Bitmap,
        confidenceThreshold: Float
    ): Triple<List<List<List<PointF>>>, FloatArray?, List<YoloObject>> {

        // Предобработка изображения
        val inputBuffer = preprocessBitmap(bitmap)

        // Подготовка выходных массивов (результаты модели)
        val output0 = Array(1) { Array(37) { FloatArray(8400) } }
        val output1 = Array(1) { Array(320) { Array(320) { FloatArray(32) } } }
        val outputs = mapOf(0 to output0, 1 to output1)

        // Запуск модели с блокировкой
        synchronized(interpreterLock) {
            interpreter.runForMultipleInputsOutputs(arrayOf(inputBuffer), outputs)
        }

        // Размеры кадра (для обратного масштабирования координат)
        val videoWidth = bitmap.width.toFloat()
        val videoHeight = bitmap.height.toFloat()

        // Порог уверенности для фильтрации объектов
        val confidenceThresholdStrong = confidenceThreshold
        val confidenceThresholdWeak = 0.15f  // более слабые детекции, если нужно дополнительно

        val strongObjects = mutableListOf<YoloObject>()
        val weakObjects = mutableListOf<YoloObject>()
        val strongCoeffs = mutableListOf<FloatArray>()
        val weakCoeffs = mutableListOf<FloatArray>()
        val numMaskCoeffs = 31  // количество коэффициентов маски (обычно 32 - 1)

        // --- Разбор выходов YOLO ---
        for (i in 0 until 8400) {
            val obj = sigmoid(output0[0][4][i]) // вероятность объекта
            val cls = sigmoid(output0[0][5][i]) // вероятность класса
            val conf = obj * cls                // итоговая уверенность
            if (conf < confidenceThresholdWeak) continue

            // Координаты bbox (нормализованные)
            val cx = output0[0][0][i]
            val cy = output0[0][1][i]
            val w = output0[0][2][i]
            val h = output0[0][3][i]
            if (cx !in 0f..1f || cy !in 0f..1f || w <= 0f || h <= 0f) continue

            // Пересчёт в абсолютные пиксели
            val absCx = cx * videoWidth
            val absCy = cy * videoHeight
            val absW = w * videoWidth
            val absH = h * videoHeight

            // Ограничиваем bbox рамками кадра
            val left = (absCx - absW / 2f).coerceIn(0f, videoWidth)
            val top = (absCy - absH / 2f).coerceIn(0f, videoHeight)
            val right = (absCx + absW / 2f).coerceIn(0f, videoWidth)
            val bottom = (absCy + absH / 2f).coerceIn(0f, videoHeight)

            val bbox = YoloObject(PointF(left, top), PointF(right, bottom), conf)
            val coeff = FloatArray(numMaskCoeffs) { j -> output0[0][6 + j][i] }

            val aspectRatio = max(absW, absH) / max(1f, min(absW, absH))

            // Сильные и слабые объекты фильтруются по разным правилам
            if (conf >= confidenceThresholdStrong) {
                strongObjects.add(bbox)
                strongCoeffs.add(coeff)
            } else if (aspectRatio > 2.5f && absW > 20 && absH > 20) {
                weakObjects.add(bbox)
                weakCoeffs.add(coeff)
            }
        }

        val allObjects = strongObjects + weakObjects
        val allCoeffs = strongCoeffs + weakCoeffs
        // Вычисляем маски для всех объектов
        val masks = MaskUtils2.computeMasks(allCoeffs.toTypedArray(), output1)

        if (masks.isEmpty()) {
            Log.w("YoloV8Segmentor", "No masks computed.")
            return Triple(emptyList(), null, allObjects)
        }

        val allContours = mutableListOf<List<List<PointF>>>()

        // --- Извлечение контуров из масок ---
        for (i in masks.indices) {
            val flatMask = masks[i]
            val reshapedMask = Array(320) { y -> FloatArray(320) { x -> flatMask[y * 320 + x] } }
            val bbox = allObjects.getOrNull(i) ?: continue

            // Weak mask = менее уверенные, может иметь другое пороговое значение
            val isWeak = i >= strongObjects.size

            // Извлекаем контуры из бинарной маски, ограниченные bbox
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
        // Возвращаем контуры, первую маску и список объектов
        return Triple(allContours, masks.firstOrNull(), allObjects)
    }

    /**
     * Сигмоидная функция для нормализации значений (0..1)
     */
    private fun sigmoid(x: Float): Float = 1f / (1f + exp(-x))
}