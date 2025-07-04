package com.antares.customtflite.ver2

import android.graphics.PointF
import android.util.Log
import kotlin.math.exp


object MaskUtils {

/*    fun computeMasks(
        maskData: Array<FloatArray>,                        // [8400][32]
        protos: Array<Array<FloatArray>>                   // [32][160][160]
    ): List<Array<BooleanArray>> {
        val maskSize = 160
        val protoCount = protos.size // 32
        val maskCount = maskData.size

        Log.d("MaskUtils", "Start computeMasks: maskCount=$maskCount, protoCount=$protoCount")

        val masks = mutableListOf<Array<BooleanArray>>()

        // Предварительно подготовим массив [32][160][160] -> [160][160][32] для кэш-эффективности
        val transposedProtos = Array(maskSize) { y ->
            Array(maskSize) { x ->
                FloatArray(protoCount) { j -> protos[j][y][x] }
            }
        }

        Log.d("MaskUtils", "Protos transposed to [160][160][32]")

        // Можно ограничить число обрабатываемых масок для теста
        val maxMasksToProcess = 10
        val count = minOf(maskCount, maxMasksToProcess)

        for (i in 0 until count) {
            val coeffs = maskData[i]
            val mask = Array(maskSize) { BooleanArray(maskSize) }

            for (y in 0 until maskSize) {
                for (x in 0 until maskSize) {
                    val proto = transposedProtos[y][x]
                    var sum = 0f
                    for (j in 0 until protoCount) {
                        sum += coeffs[j] * proto[j]
                    }
                    mask[y][x] = sigmoid(sum) > 0.5f
                }
            }

            masks.add(mask)
            if (i < 3) Log.d("MaskUtils", "Mask $i computed")
        }

        Log.d("MaskUtils", "computeMasks finished: returned ${masks.size} masks")

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
                    Log.d("MaskUtils", "Extracted ${points.size} points from mask")

                }
            }
        }
        return if (points.isEmpty()) null else points
    }

    private fun sigmoid(x: Float): Float = (1f / (1f + exp(-x)))

    */

    fun computeMasks(
        maskData: Array<FloatArray>,                        // [8400][32]
        protos: Array<Array<FloatArray>>                   // [32][160][160]
    ): List<Array<BooleanArray>> {
        val maskSize = 160
        val protoCount = protos.size // 32
        val maskCount = maskData.size

        Log.d("MaskUtils", "Start computeMasks: maskCount=$maskCount, protoCount=$protoCount")

        val masks = mutableListOf<Array<BooleanArray>>()

        // Транспонируем [32][160][160] в [160][160][32] для быстрой выборки
        val transposedProtos = Array(maskSize) { y ->
            Array(maskSize) { x ->
                FloatArray(protoCount) { j -> protos[j][y][x] }
            }
        }

        Log.d("MaskUtils", "Protos transposed to [160][160][32]")

        val maxMasksToProcess = 10
        val count = minOf(maskCount, maxMasksToProcess)

        for (i in 0 until count) {
            val coeffs = maskData[i]
            val mask = Array(maskSize) { BooleanArray(maskSize) }

            for (y in 0 until maskSize) {
                for (x in 0 until maskSize) {
                    val proto = transposedProtos[y][x]
                    var sum = 0f
                    for (j in 0 until protoCount) {
                        sum += coeffs[j] * proto[j]
                    }
                    mask[y][x] = sigmoid(sum) > 0.5f
                }
            }

            masks.add(mask)
            if (i < 3) Log.d("MaskUtils", "Mask $i computed")
        }

        Log.d("MaskUtils", "computeMasks finished: returned ${masks.size} masks")
        return masks
    }

    fun extractContourFromMask(mask: Array<BooleanArray>): List<PointF>? {
        val h = mask.size
        val w = mask[0].size
        val points = mutableListOf<PointF>()

        for (y in 0 until h) {
            for (x in 0 until w) {
                if (mask[y][x]) {
                    val nx = x.toFloat() / w
                    val ny = y.toFloat() / h
                    points.add(PointF(nx, ny))
                }
            }
        }

        //Log.d("MaskUtils", "Extracted ${points.size} points from mask")

        if (points.isNotEmpty()) {
            Log.d("MaskUtils", "Contour point: ${points.first().x}, ${points.first().y}")
            if (points.first().x > 1f || points.first().y > 1f) {
                Log.w("MaskUtils", "Suspicious point: ${points.first()} (not normalized?)")
            }
        }
        return if (points.isEmpty()) null else points
    }

    private fun sigmoid(x: Float): Float = 1f / (1f + kotlin.math.exp(-x))
}
