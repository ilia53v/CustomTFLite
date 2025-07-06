package com.antares.customtflite.ver2.segmentor

import android.graphics.PointF
import kotlin.math.exp

object MaskUtils {
    fun computeMasks(maskData: Array<FloatArray>, protos: Array<FloatArray>): List<Array<BooleanArray>> {
        val maskCount = maskData.size
        val maskSize = 160
        val masks = mutableListOf<Array<BooleanArray>>()

        for (i in 0 until maskCount) {
            val coeffs = maskData[i].takeLast(32)
            val mask = Array(maskSize) { BooleanArray(maskSize) }
            for (y in 0 until maskSize) {
                for (x in 0 until maskSize) {
                    var sum = 0f
                    for (j in coeffs.indices) {
                        sum += coeffs[j] * protos[j][y * maskSize + x]
                    }
                    mask[y][x] = sigmoid(sum) > 0.5f
                }
            }
            masks.add(mask)
        }
        return masks
    }

    fun extractContourFromMask(mask: Array<BooleanArray>): List<PointF>? {
        val points = mutableListOf<PointF>()
        val h = mask.size
        val w = mask[0].size
        for (y in 0 until h) {
            for (x in 0 until w) {
                if (mask[y][x]) {
                    points.add(PointF(x.toFloat() / w, y.toFloat() / h))
                }
            }
        }
        return if (points.isEmpty()) null else points
    }

    private fun sigmoid(x: Float): Float = (1f / (1f + exp(-x)))
}