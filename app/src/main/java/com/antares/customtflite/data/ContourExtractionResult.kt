package com.antares.customtflite.data

import android.graphics.PointF

/*
data class ContourExtractionResult(
    val contours: List<List<PointF>>,
    val totalContours: Int,
    val skippedSmallArea: Int,
    val skippedTooFewPoints: Int
)

*/

data class ContourExtractionResult(
    val contours: List<List<PointF>>,
    val isWeak: Boolean
)
