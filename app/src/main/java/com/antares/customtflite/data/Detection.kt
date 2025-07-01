package com.antares.customtflite.data

import android.graphics.PointF


// Класс для хранения детекций
data class Detection(
    val x: Float = 0f,
    val y: Float = 0f,
    val w: Float = 0f,
    val h: Float = 0f,
    val score: Float = 0f,
    val contourPoints: List<PointF> = emptyList()
)