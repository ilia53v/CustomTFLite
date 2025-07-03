package com.antares.customtflite.ver2

import android.graphics.PointF
import kotlin.math.exp

object MaskUtils {
    private val maskSize = 160

    // Переиспользуемый буфер для одной маски
    private val maskBuffer = BooleanArray(maskSize * maskSize)

    fun computeMasks(maskData: Array<FloatArray>, protos: Array<FloatArray>): List<BooleanArray> {
        val maskCount = maskData.size
        val masks = mutableListOf<BooleanArray>()

        for (i in 0 until maskCount) {
            val coeffs = maskData[i].takeLast(32)

            // Очистить буфер
            maskBuffer.fill(false)

            for (y in 0 until maskSize) {
                for (x in 0 until maskSize) {
                    var sum = 0f
                    val idx = y * maskSize + x
                    for (j in coeffs.indices) {
                        sum += coeffs[j] * protos[j][idx]
                    }
                    maskBuffer[idx] = sigmoid(sum) > 0.5f
                }
            }
            // Копируем буфер, чтобы не перезаписывать
            masks.add(maskBuffer.copyOf())
        }
        return masks
    }

    fun extractContourFromMask(mask: BooleanArray, maskSize: Int = 160): List<PointF>? {
        val points = mutableListOf<PointF>()
        for (i in mask.indices) {
            if (mask[i]) {
                val x = (i % maskSize).toFloat() / maskSize
                val y = (i / maskSize).toFloat() / maskSize
                points.add(PointF(x, y))
            }
        }
        return if (points.isEmpty()) null else points
    }

    private fun sigmoid(x: Float): Float = (1f / (1f + kotlin.math.exp(-x)))
}