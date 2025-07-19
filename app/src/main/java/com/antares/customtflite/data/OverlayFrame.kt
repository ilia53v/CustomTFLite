package com.antares.customtflite.data

import android.graphics.Bitmap

data class OverlayFrame(
    val image: Bitmap,         // Маска с bbox и контурами
    val sourceFrame: Bitmap    // Кадр, на котором она была рассчитана
)