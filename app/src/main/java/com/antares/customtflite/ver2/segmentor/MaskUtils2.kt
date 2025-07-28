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
import com.antares.customtflite.data.ContourExtractionResult
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
        displaySize: Size
    ): ContourExtractionResult {
        val contours = mutableListOf<List<PointF>>()
        var skippedFewPoints = 0
        var skippedSmallArea = 0
        var totalFound = 0

        val visited = Array(maskHeight) { BooleanArray(maskWidth) }

        // BBox в абсолютных display координатах
        val x1 = bbox.topLeft.x
        val y1 = bbox.topLeft.y
        val x2 = bbox.bottomRight.x
        val y2 = bbox.bottomRight.y
        val bboxWidth = x2 - x1
        val bboxHeight = y2 - y1

        val minAreaRatio = 0.02f
        val minAreaAbs = bboxWidth * bboxHeight * minAreaRatio

        for (y in 0 until maskHeight) {
            for (x in 0 until maskWidth) {
                if (mask[y][x] < threshold || visited[y][x]) continue

                val contour = getConnectedContour(mask, x, y, threshold, visited)
                totalFound++

                if (contour.size < 3) {
                    skippedFewPoints++
                    continue
                }

                // Преобразование координат в display внутри bbox
                val displayContour = contour.map { pt ->
                    val xNorm = pt.x / maskWidth
                    val yNorm = pt.y / maskHeight
                    val xScaled = x1 + xNorm * bboxWidth
                    val yScaled = y1 + yNorm * bboxHeight
                    PointF(xScaled, yScaled)
                }

                val area = computePolygonArea(displayContour)
                if (area < minAreaAbs) {
                    skippedSmallArea++
                    continue
                }

                contours.add(displayContour)
            }
        }

        Log.d(
            "ContourDebug",
            "Contours: $totalFound, valid: ${contours.size}, skipped: ${skippedFewPoints + skippedSmallArea} (small: $skippedSmallArea, <3: $skippedFewPoints)"
        )

        return ContourExtractionResult(
            contours = contours,
            totalContours = totalFound,
            skippedSmallArea = skippedSmallArea,
            skippedTooFewPoints = skippedFewPoints
        )
    }


    fun computePolygonArea(points: List<PointF>): Float {
        var area = 0f
        for (i in points.indices) {
            val j = (i + 1) % points.size
            area += points[i].x * points[j].y - points[j].x * points[i].y
        }
        return kotlin.math.abs(area / 2f)
    }

    fun getConnectedContour(
        mask: Array<FloatArray>,
        startX: Int,
        startY: Int,
        threshold: Float,
        visited: Array<BooleanArray>
    ): List<PointF> {
        val contour = mutableListOf<PointF>()
        val queue = ArrayDeque<Pair<Int, Int>>()
        val h = mask.size
        val w = mask[0].size

        queue.add(Pair(startX, startY))
        visited[startY][startX] = true

        val directions = listOf(
            Pair(0, 1), Pair(1, 0), Pair(0, -1), Pair(-1, 0),
            Pair(1, 1), Pair(-1, 1), Pair(1, -1), Pair(-1, -1)
        )

        while (queue.isNotEmpty()) {
            val (x, y) = queue.removeFirst()
            contour.add(PointF(x.toFloat(), y.toFloat()))

            for ((dx, dy) in directions) {
                val nx = x + dx
                val ny = y + dy
                if (nx in 0 until w && ny in 0 until h &&
                    !visited[ny][nx] && mask[ny][nx] >= threshold
                ) {
                    visited[ny][nx] = true
                    queue.add(Pair(nx, ny))
                }
            }
        }

        return contour
    }

    private fun sigmoid(x: Float): Float = 1f / (1f + kotlin.math.exp(-x))
}
