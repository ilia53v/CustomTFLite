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
import java.nio.ByteBuffer

data class PreprocessResultV2(
    val inputBuffer: ByteBuffer,
    val scale: Float,
    val padX: Int,
    val padY: Int,
    val newWidth: Int,
    val newHeight: Int
)

object MaskUtilsV2 {

    private const val MAX_MASKS = 5
    private const val MIN_CONTOUR_POINTS = 3
    private const val MIN_MASK_AREA = 30

    private var reusableMask: FloatArray? = null
    private var reusableProtoBuffer: FloatArray? = null

    fun computeMasks(
        maskCoeffs: Array<FloatArray>,
        protos: Array<Array<Array<FloatArray>>> // [1][320][320][32]
    ): List<FloatArray> {
        val channels = 32
        val height = 320
        val width = 320
        val hw = height * width
        val protoSize = hw * channels

        val protoBuffer = reusableProtoBuffer?.takeIf { it.size == protoSize }
            ?: FloatArray(protoSize).also { reusableProtoBuffer = it }

        for (c in 0 until channels) {
            for (y in 0 until height) {
                for (x in 0 until width) {
                    val index = (y * width + x) * channels + c
                    protoBuffer[index] = protos[0][y][x][c]
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

    fun extractContoursFromMask(
        mask: Array<FloatArray>,
        maskWidth: Int,
        maskHeight: Int,
        threshold: Float,
        bbox: YoloObject,
        displaySize: Size,
        scale: Float = 1f,       // scale из letterbox
        padX: Float = 0f,        // padX из letterbox
        padY: Float = 0f,        // padY из letterbox
        padding: Float = 4f,     // дополнительный отступ вокруг bbox
        minAreaRatio: Float = 0.0005f
    ): ContourExtractionResult {

        val contours = mutableListOf<List<PointF>>()
        val visited = Array(maskHeight) { BooleanArray(maskWidth) }

        val scaleX = displaySize.width / maskWidth.toFloat()
        val scaleY = displaySize.height / maskHeight.toFloat()

        // Корректируем bbox по letterbox: сначала масштабируем, потом сдвигаем с паддингом
        val left = ((bbox.topLeft.x - padX) / scale - padding).coerceAtLeast(0f)
        val right = ((bbox.bottomRight.x - padX) / scale + padding).coerceAtMost(displaySize.width.toFloat())
        val top = ((bbox.topLeft.y - padY) / scale - padding).coerceAtLeast(0f)
        val bottom = ((bbox.bottomRight.y - padY) / scale + padding).coerceAtMost(displaySize.height.toFloat())

        val minAreaAbs = displaySize.width * displaySize.height * minAreaRatio

        for (y in 0 until maskHeight) {
            for (x in 0 until maskWidth) {
                if (mask[y][x] < threshold || visited[y][x]) continue

                val rawContour = getConnectedContour(mask, x, y, threshold, visited)
                if (rawContour.size < 2) {
                    Log.d("ContourSkip", "Contour skipped: < 2 points (${rawContour.size})")
                    continue
                }

                // Масштабируем в размер displaySize
                val scaled = rawContour.map { PointF(it.x * scaleX, it.y * scaleY) }

                val areaRaw = computePolygonArea(scaled)
                Log.d("ContourRaw", "Raw contour area = $areaRaw, points = ${scaled.size}")

                // Обрезаем контур по bbox + padding
                val inside = scaled.filter { it.x in left..right && it.y in top..bottom }
                if (inside.size < 2) {
                    Log.d("ContourSkip", "Contour skipped after bbox crop: < 2 points (${inside.size})")
                    continue
                }

                val area = computePolygonArea(inside)
                if (area < minAreaAbs) {
                    Log.d("ContourSkip", "Contour skipped: area too small = $area, minAreaAbs = $minAreaAbs")
                    continue
                }

                val simplified = simplifyContour(inside)
                Log.d("ContourDebug", "Contour kept: area = $area, points = ${simplified.size}")
                contours.add(simplified)
            }
        }

        return ContourExtractionResult(
            contours = contours,
            isWeak = bbox.confidence < 0.5f
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
