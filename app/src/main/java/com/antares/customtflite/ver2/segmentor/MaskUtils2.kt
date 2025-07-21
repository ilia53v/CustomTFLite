package com.antares.customtflite.ver2.segmentor

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Point
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.Region
import android.util.Log
import android.util.Size
import com.antares.customtflite.data.Detection2
import com.antares.customtflite.data.YoloObject
import com.antares.customtflite.data.simplifyContour
import com.antares.customtflite.ver2.traceBoundary

object MaskUtils2 {

    private const val MAX_MASKS = 5
    private const val MIN_CONTOUR_POINTS = 3
    private const val MIN_MASK_AREA = 30

    private var reusableMask: FloatArray? = null
    private var reusableProtoBuffer: FloatArray? = null

    fun computeMasks(
        maskCoeffs: Array<FloatArray>,
        protos: Array<Array<FloatArray>> // [32][320][320]
    ): List<FloatArray> {
        val channels = protos.size              // 32
        val height = protos[0].size             // 320
        val width = protos[0][0].size           // 320
        val hw = height * width
        val protoSize = hw * channels

        val protoBuffer = reusableProtoBuffer?.takeIf { it.size == protoSize }
            ?: FloatArray(protoSize).also { reusableProtoBuffer = it }

        // Flatten protos: [C][H][W] -> [H*W*C]
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
            val mask = reusableMask?.takeIf { it.size == hw }
                ?: FloatArray(hw).also { reusableMask = it }

            val useC = minOf(coeffs.size, channels)

            for (i in 0 until hw) {
                var sum = 0f
                for (c in 0 until useC) {
                    sum += coeffs[c] * protoBuffer[i * channels + c]
                }
                mask[i] = sigmoid(sum)
            }

            masks.add(mask.copyOf())
        }

        return masks
    }

    /**
     * Извлекает контур из маски по простому порогу (без OpenCV).
     * Возвращает нормализованные [0, 1] координаты или null, если контур слишком мал.
     */
    fun extractContoursFromMask(
        mask: Array<FloatArray>,
        maskWidth: Int,
        maskHeight: Int,
        threshold: Float,
        bbox: YoloObject,
        displaySize: Size,
        minAreaThreshold: Float = 5f,
        drawDebugContours: Boolean = true
    ): Pair<List<List<PointF>>, Bitmap?> {
        val binarized = Array(maskHeight) { BooleanArray(maskWidth) }
        var nonZeroCount = 0

        for (y in 0 until maskHeight) {
            for (x in 0 until maskWidth) {
                if (mask[y][x] >= threshold) {
                    binarized[y][x] = true
                    nonZeroCount++
                }
            }
        }

        Log.d("MaskDebug", "Mask area (non-zero pixels): $nonZeroCount")

        val visited = Array(maskHeight) { BooleanArray(maskWidth) }
        val contours = mutableListOf<List<PointF>>()
        val debugBitmap = if (drawDebugContours)
            Bitmap.createBitmap(displaySize.width, displaySize.height, Bitmap.Config.ARGB_8888)
        else null
        val canvas = debugBitmap?.let { Canvas(it) }

        val colors = listOf(Color.RED, Color.GREEN, Color.BLUE, Color.YELLOW, Color.CYAN, Color.MAGENTA)

        var contourIndex = 0

        for (y in 1 until maskHeight - 1) {
            for (x in 1 until maskWidth - 1) {
                if (!visited[y][x] && binarized[y][x]) {
                    val isHole = !binarized[y][x - 1]  // Входит ли извне → наружный контур

                    val contour = traceBoundary(binarized, visited, x, y)

                    if (contour.size < 3) continue
                    val area = computePolygonArea(contour.map { PointF(it.first.toFloat(), it.second.toFloat()) })
                    if (area < minAreaThreshold) continue

                    Log.d("ContourDebug", "Contour with ${contour.size} points")
                    Log.d("HoleDebug", "isHole = $isHole, area = $area")

                    // Проецируем координаты в displaySize
                    val displayContour = contour.map { p ->
                        PointF(
                            bbox.topLeft.x + p.first * (bbox.bottomRight.x - bbox.topLeft.x) / maskWidth,
                            bbox.topLeft.y + p.second * (bbox.bottomRight.y - bbox.topLeft.y) / maskHeight
                        )
                    }

                    contours.add(displayContour)

                    // Отладочная отрисовка цветом
                    if (canvas != null) {
                        val paint = Paint().apply {
                            color = colors[contourIndex % colors.size]
                            style = Paint.Style.STROKE
                            strokeWidth = 2f
                        }
                        val path = Path()
                        displayContour.firstOrNull()?.let {
                            path.moveTo(it.x, it.y)
                        }
                        for (i in 1 until displayContour.size) {
                            path.lineTo(displayContour[i].x, displayContour[i].y)
                        }
                        path.close()
                        canvas.drawPath(path, paint)
                    }

                    contourIndex++
                }
            }
        }

        // fallback на bbox если пусто
        if (contours.isEmpty()) {
            Log.w("ContourDebug", "No contours found, using bbox")
            contours.add(listOf(
                bbox.topLeft,
                PointF(bbox.bottomRight.x, bbox.topLeft.y),
                bbox.bottomRight,
                PointF(bbox.topLeft.x, bbox.bottomRight.y)
            ))
        }

        return Pair(contours, debugBitmap)
    }

    fun computePolygonArea(points: List<PointF>): Float {
        var area = 0f
        for (i in points.indices) {
            val j = (i + 1) % points.size
            area += points[i].x * points[j].y - points[j].x * points[i].y
        }
        return kotlin.math.abs(area / 2f)
    }


    private fun sigmoid(x: Float): Float = 1f / (1f + kotlin.math.exp(-x))

    // Выпуклая оболочка методом Грэхема
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

    private fun simplifyContour(contour: List<PointF>, epsilon: Float = 2.0f): List<PointF> {
        if (contour.size < 3) return contour

        val simplified = mutableListOf<PointF>()
        simplified.add(contour.first())

        for (i in 1 until contour.size - 1) {
            val prev = simplified.last()
            val curr = contour[i]
            val dx = curr.x - prev.x
            val dy = curr.y - prev.y
            if (dx * dx + dy * dy > epsilon * epsilon) {
                simplified.add(curr)
            }
        }

        simplified.add(contour.last())
        return simplified
    }
}
