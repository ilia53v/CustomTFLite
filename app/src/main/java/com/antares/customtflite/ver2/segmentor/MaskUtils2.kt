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
    /*fun extractContourFromMask(
        mask: Array<FloatArray>,
        maskWidth: Int,
        maskHeight: Int,
        threshold: Float,
        bbox: YoloObject,
        displaySize: Size
    ): List<PointF>? {
        val points = mutableListOf<PointF>()

        val scaleX = displaySize.width / maskWidth.toFloat()
        val scaleY = displaySize.height / maskHeight.toFloat()

        val left = bbox.topLeft.x
        val top = bbox.topLeft.y
        val right = bbox.bottomRight.x
        val bottom = bbox.bottomRight.y

        for (y in 1 until maskHeight - 1) {
            for (x in 1 until maskWidth - 1) {
                if (mask[y][x] > threshold) {
                    val px = x * scaleX
                    val py = y * scaleY
                    if (px in left..right && py in top..bottom) {
                        points.add(PointF(px, py))
                    }
                }
            }
        }

        if (points.isEmpty()) return null

        // Упрощение контура (опционально)
        return simplifyContour(points)
    }
*/

    fun extractContoursFromMask(
        mask: Array<FloatArray>,
        maskWidth: Int,
        maskHeight: Int,
        threshold: Float,
        bbox: YoloObject,
        displaySize: Size
    ): List<List<PointF>> {
        val contours = mutableListOf<List<PointF>>()

        // Convert bbox to mask-space integer bounds
        val left = (bbox.topLeft.x * maskWidth / displaySize.width).toInt().coerceIn(0, maskWidth - 1)
        val top = (bbox.topLeft.y * maskHeight / displaySize.height).toInt().coerceIn(0, maskHeight - 1)
        val right = (bbox.bottomRight.x * maskWidth / displaySize.width).toInt().coerceIn(0, maskWidth - 1)
        val bottom = (bbox.bottomRight.y * maskHeight / displaySize.height).toInt().coerceIn(0, maskHeight - 1)

        // Extract ROI binary mask
        val binary = Array(bottom - top + 1) { y ->
            BooleanArray(right - left + 1) { x ->
                mask[top + y][left + x] > threshold
            }
        }

        val roiWidth = binary[0].size
        val roiHeight = binary.size

        // Отладка — площадь маски
        val maskArea = binary.sumOf { row -> row.count { it } }
        Log.i("MaskDebug", "Mask area (non-zero pixels): $maskArea")

        // Boundary tracing (external + holes)
        val visited = Array(roiHeight) { BooleanArray(roiWidth) }

        fun traceBoundary(startX: Int, startY: Int): List<Point> {
            val contour = mutableListOf<Point>()
            var x = startX
            var y = startY
            var dir = 0  // starting direction

            val dx = intArrayOf(1, 1, 0, -1, -1, -1, 0, 1)
            val dy = intArrayOf(0, -1, -1, -1, 0, 1, 1, 1)

            do {
                contour.add(Point(x, y))
                visited[y][x] = true

                var found = false
                for (i in 0 until 8) {
                    val ndir = (dir + i) % 8
                    val nx = x + dx[ndir]
                    val ny = y + dy[ndir]

                    if (nx in 0 until roiWidth && ny in 0 until roiHeight && binary[ny][nx] && !visited[ny][nx]) {
                        x = nx
                        y = ny
                        dir = (ndir + 5) % 8 // turn right
                        found = true
                        break
                    }
                }

                if (!found) break

            } while (x != startX || y != startY)

            return contour
        }

        // Scan entire binary mask
        for (y in 1 until roiHeight - 1) {
            for (x in 1 until roiWidth - 1) {
                if (binary[y][x] && !visited[y][x]) {
                    val boundary = traceBoundary(x, y)
                    if (boundary.size >= 3) {
                        // Map back to full-mask space → display space
                        val contour = boundary.map { p ->
                            val px = (left + p.x).toFloat() / maskWidth * displaySize.width
                            val py = (top + p.y).toFloat() / maskHeight * displaySize.height
                            PointF(px, py)
                        }
                        Log.i("ContourDebug", "Contour with ${contour.size} points")
                        contours.add(simplifyContour(contour))
                    }
                }
            }
        }

        // fallback: если контуров нет — рисуем bbox
        if (contours.isEmpty()) {
            Log.w("Fallback", "No contours found, fallback to bbox")
            val fallback = listOf(
                bbox.topLeft,
                PointF(bbox.bottomRight.x, bbox.topLeft.y),
                bbox.bottomRight,
                PointF(bbox.topLeft.x, bbox.bottomRight.y)
            )
            contours.add(fallback)
        }

        return contours
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
