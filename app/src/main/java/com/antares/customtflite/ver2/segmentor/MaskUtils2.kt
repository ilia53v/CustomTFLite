package com.antares.customtflite.ver2.segmentor

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.util.Log
import com.antares.customtflite.data.Detection2

object MaskUtils2 {

    // Максимальное количество масок для инференса (можно изменить)
    private const val MAX_MASKS = 1

    // Кэш для переиспользования масок и буфера прототипов
    private var reusableMask: FloatArray? = null
    private var reusableProtoBuffer: FloatArray? = null

    /**
     * Вычисляет маски из коэффициентов и прототипов.
     * maskCoeffs: массив FloatArray с коэффициентами (каждый длиной 32)
     * protos: массив [32][160][160] прототипов (Float)
     * Возвращает список масок — каждая маска: FloatArray размером 160x160 с float значениями от 0 до 1.
     */
    fun computeMasks(
        maskCoeffs: Array<FloatArray>,
        protos: Array<Array<FloatArray>>
    ): List<FloatArray> {
        val channels = protos.size              // 32
        val height = protos[0].size             // 160
        val width = protos[0][0].size           // 160
        val hw = height * width
        val protoSize = hw * channels

        // Инициализируем или переиспользуем буфер прототипов
        val protoBuffer = reusableProtoBuffer?.takeIf { it.size == protoSize }
            ?: FloatArray(protoSize).also { reusableProtoBuffer = it }

        // Собираем протос в линейный буфер [y * width + x] * channels + c
        for (c in 0 until channels) {
            for (y in 0 until height) {
                for (x in 0 until width) {
                    val index = (y * width + x) * channels + c
                    protoBuffer[index] = protos[c][y][x]
                }
            }
        }

        val masks = ArrayList<FloatArray>(minOf(maskCoeffs.size, MAX_MASKS))

        for (coeffs in maskCoeffs.take(MAX_MASKS)) {
            // Переиспользуем или создаем буфер маски
            val mask = reusableMask?.takeIf { it.size == hw }
                ?: FloatArray(hw).also { reusableMask = it }

            for (i in 0 until hw) {
                var sum = 0f
                for (c in 0 until channels) {
                    sum += coeffs[c] * protoBuffer[i * channels + c]
                }
                mask[i] = sigmoid(sum)
            }
            val max = mask.maxOrNull()
            val min = mask.minOrNull()
            Log.d("MaskUtils2", "mask min=$min max=$max")
            // Копируем маску, чтобы сохранить результат
            masks.add(mask.copyOf())
        }

        return masks
    }

    /**
     * Извлекает контур из бинарной маски (с float значениями).
     * mask — FloatArray длиной height*width, maskWidth и maskHeight — размеры маски
     * threshold — порог бинаризации (например, 0.5f)
     * Возвращает список точек (PointF) с нормированными координатами [0..1] по X и Y.
     */
/*    fun extractContourFromMask(
        mask: FloatArray,
        maskWidth: Int,
        maskHeight: Int,
        threshold: Float = -1f  // -1 означает "выбери автоматически"
    ): List<PointF>? {
        // Вычислить адаптивный порог (например, 80% от max)
        val maxVal = mask.maxOrNull() ?: return null
        val realThreshold = if (threshold > 0f) threshold else maxVal * 0.8f

        val rawPoints = mutableListOf<PointF>()
        for (y in 0 until maskHeight) {
            for (x in 0 until maskWidth) {
                if (mask[y * maskWidth + x] > realThreshold) {
                    rawPoints.add(PointF(x.toFloat(), y.toFloat()))
                }
            }
        }

        if (rawPoints.size < 10) return null // Шум, не рисуем

        val hull = convexHull(rawPoints)

        // Нормализуем
        return hull.map { PointF(it.x / maskWidth, it.y / maskHeight) }
    }*/

    /*fun extractContourFromMask(
        mask: FloatArray,
        maskWidth: Int,
        maskHeight: Int,
        threshold: Float = 0.3f  // тоже уменьшен!
    ): List<PointF>? {
        val points = mutableListOf<PointF>()
        for (y in 0 until maskHeight) {
            for (x in 0 until maskWidth) {
                if (mask[y * maskWidth + x] > threshold) {
                    points.add(PointF(x.toFloat() / maskWidth, y.toFloat() / maskHeight))
                }
            }
        }
        return if (points.isNotEmpty()) points else null
    }*/

    /*fun drawRawMask(
        mask: FloatArray,
        maskWidth: Int = 160,
        maskHeight: Int = 160,
        targetWidth: Int,
        targetHeight: Int
    ): Bitmap {
        val bmp = Bitmap.createBitmap(maskWidth, maskHeight, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(maskWidth * maskHeight)

        for (i in mask.indices) {
            val value = (mask[i].coerceIn(0f, 1f) * 255).toInt()
            pixels[i] = Color.argb(value, 255, 0, 0)
        }

        bmp.setPixels(pixels, 0, maskWidth, 0, 0, maskWidth, maskHeight)

        // Масштабируем до размера видеофрейма
        return Bitmap.createScaledBitmap(bmp, targetWidth, targetHeight, false)
    }

    fun extractContourFromMask(
        mask: FloatArray,
        maskWidth: Int,
        maskHeight: Int,
        threshold: Float = 0.3f // Меньше порог -> больше точек
    ): List<PointF>? {
        val binaryMask = BooleanArray(mask.size) { i -> mask[i] > threshold }

        val contours = mutableListOf<List<PointF>>()
        val visited = BooleanArray(mask.size)

        val dirs = listOf(
            Pair(0, 1), Pair(1, 0), Pair(0, -1), Pair(-1, 0),
            Pair(1, 1), Pair(1, -1), Pair(-1, 1), Pair(-1, -1)
        )

        for (y in 1 until maskHeight - 1) {
            for (x in 1 until maskWidth - 1) {
                val idx = y * maskWidth + x
                if (!visited[idx] && binaryMask[idx]) {
                    val contour = mutableListOf<PointF>()
                    val queue = ArrayDeque<Pair<Int, Int>>()
                    queue.add(Pair(x, y))

                    while (queue.isNotEmpty()) {
                        val (cx, cy) = queue.removeFirst()
                        val cidx = cy * maskWidth + cx
                        if (cx in 0 until maskWidth && cy in 0 until maskHeight && !visited[cidx] && binaryMask[cidx]) {
                            visited[cidx] = true
                            contour.add(PointF(cx / maskWidth.toFloat(), cy / maskHeight.toFloat()))
                            dirs.forEach { (dx, dy) ->
                                queue.add(Pair(cx + dx, cy + dy))
                            }
                        }
                    }

                    if (contour.size > 20) {
                        contours.add(contour)
                    }
                }
            }
        }

        return contours.maxByOrNull { it.size } ?: emptyList()
    }
*/

    fun extractContourFromMask(
        mask: FloatArray,
        maskWidth: Int,
        maskHeight: Int,
        threshold: Float = 0.5f
    ): List<PointF>? {
        val points = mutableListOf<PointF>()

        for (y in 0 until maskHeight) {
            for (x in 0 until maskWidth) {
                val value = mask[y * maskWidth + x]
                if (value > threshold) {
                    points.add(PointF(x.toFloat(), y.toFloat()))
                }
            }
        }

        if (points.isEmpty()) return null

        // Нормализуем координаты к [0..1]
        return points.map { PointF(it.x / maskWidth, it.y / maskHeight) }
    }

    fun drawRawMask(
        mask: FloatArray,
        maskWidth: Int,
        maskHeight: Int,
        canvasWidth: Int,
        canvasHeight: Int,
        threshold: Float = 0.5f
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(canvasWidth, canvasHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint().apply {
            style = Paint.Style.FILL
            color = Color.RED
            alpha = 100
        }

        val scaleX = canvasWidth.toFloat() / maskWidth
        val scaleY = canvasHeight.toFloat() / maskHeight

        for (y in 0 until maskHeight) {
            for (x in 0 until maskWidth) {
                val value = mask[y * maskWidth + x]
                if (value > threshold) {
                    canvas.drawRect(
                        x * scaleX,
                        y * scaleY,
                        (x + 1) * scaleX,
                        (y + 1) * scaleY,
                        paint
                    )
                }
            }
        }

        return bitmap
    }


    private fun sigmoid(x: Float): Float =
        (1f / (1f + kotlin.math.exp(-x)))

    // Алгоритм выпуклой оболочки (Graham Scan)
    private fun convexHull(points: List<PointF>): List<PointF> {
        if (points.size < 3) return points

        val sorted = points.sortedWith(compareBy({ it.x }, { it.y }))

        val lower = mutableListOf<PointF>()
        for (p in sorted) {
            while (lower.size >= 2 && cross(lower[lower.size - 2], lower[lower.size - 1], p) <= 0) {
                lower.removeAt(lower.size - 1)
            }
            lower.add(p)
        }

        val upper = mutableListOf<PointF>()
        for (p in sorted.asReversed()) {
            while (upper.size >= 2 && cross(upper[upper.size - 2], upper[upper.size - 1], p) <= 0) {
                upper.removeAt(upper.size - 1)
            }
            upper.add(p)
        }

        lower.removeAt(lower.size - 1)
        upper.removeAt(upper.size - 1)

        return lower + upper
    }

    private fun cross(o: PointF, a: PointF, b: PointF): Float {
        return (a.x - o.x) * (b.y - o.y) - (a.y - o.y) * (b.x - o.x)
    }
}
