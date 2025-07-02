package com.antares.customtflite.intersection_point

import android.graphics.PointF

fun findAllIntersections(
    contourA: List<PointF>,
    contourB: List<PointF>
): List<PointF> {
    val intersections = mutableListOf<PointF>()

    for (i in 0 until contourA.size - 1) {
        val a1 = contourA[i]
        val a2 = contourA[i + 1]

        for (j in 0 until contourB.size - 1) {
            val b1 = contourB[j]
            val b2 = contourB[j + 1]

            getLineIntersection(a1, a2, b1, b2)?.let {
                intersections.add(it)
            }
        }
    }

    return intersections
}