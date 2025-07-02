package com.antares.customtflite.intersection_point

import android.graphics.PointF

fun findAllIntersectionsInContours(contours: List<List<PointF>>): List<PointF> {
    val result = mutableListOf<PointF>()
    for (i in 0 until contours.size - 1) {
        for (j in i + 1 until contours.size) {
            result.addAll(findAllIntersections(contours[i], contours[j]))
        }
    }
    return result
}
