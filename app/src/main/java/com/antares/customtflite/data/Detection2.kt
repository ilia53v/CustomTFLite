package com.antares.customtflite.data

data class Detection2(
    val x: Float, val y: Float, val w: Float, val h: Float,
    val score: Float, val maskCoefficients: FloatArray
)