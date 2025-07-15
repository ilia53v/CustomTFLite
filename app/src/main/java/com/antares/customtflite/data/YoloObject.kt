package com.antares.customtflite.data

import android.graphics.PointF

data class YoloObject(
    val topLeft: PointF,
    val bottomRight: PointF,
    val confidence: Float
)