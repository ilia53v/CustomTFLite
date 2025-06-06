package com.antares.customtflite

/*data class Contour(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
)*/

/*
data class Contour(
    val points: List<Pair<Float, Float>> // нормализованные координаты [0..1]
)*/


data class Contour(
    val points: List<Pair<Float, Float>>,
    val score: Float,
    val classId: Int
)