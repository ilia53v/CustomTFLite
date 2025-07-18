package com.antares.customtflite.ver2.segmentor

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.util.Log
import com.antares.customtflite.data.Detection2

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
    fun extractContourFromMask(
        mask: FloatArray,
        maskWidth: Int,
        maskHeight: Int,
        threshold: Float
    ): List<PointF>? {
        val rawPoints = mutableListOf<PointF>()
        for (y in 0 until maskHeight) {
            for (x in 0 until maskWidth) {
                val value = mask[y * maskWidth + x]
                if (value > threshold) {
                    rawPoints.add(
                        PointF(
                            x.toFloat() / maskWidth.toFloat(),
                            y.toFloat() / maskHeight.toFloat()
                        )
                    )
                }
            }
        }

        if (rawPoints.size < MIN_MASK_AREA) {
            Log.d("MaskUtils", "Contour skipped: too small (${rawPoints.size} points)")
            return null
        }

        val hull = convexHull(rawPoints)
        return if (hull.size < MIN_CONTOUR_POINTS) {
            Log.d("MaskUtils", "Convex hull too small (${hull.size})")
            null
        } else {
            hull
        }
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
}