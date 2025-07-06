package com.antares.customtflite.data

object YoloUtils {
    fun postprocessDetections(output: Array<FloatArray>): List<Detection2> {
        // Выборка нужных данных из выходного тензора (например, score > 0.3)
        return output.mapNotNull { arr ->
            val score = arr[4]
            if (score < 0.3f) return@mapNotNull null
            val x = arr[0]
            val y = arr[1]
            val w = arr[2]
            val h = arr[3]
            val maskCoefficients = arr.copyOfRange(5, 37)
            Detection2(x, y, w, h, score, maskCoefficients)
        }
    }
}