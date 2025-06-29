package com.antares.customtflite.data


// Класс для хранения детекций
data class Detection(
    val x: Float, // центр по X (0..1)
    val y: Float, // центр по Y (0..1)
    val w: Float, // ширина (0..1)
    val h: Float, // высота (0..1)
    val score: Float
)