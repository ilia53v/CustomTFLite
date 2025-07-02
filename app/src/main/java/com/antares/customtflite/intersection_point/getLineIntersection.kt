package com.antares.customtflite.intersection_point

import android.graphics.PointF

fun getLineIntersection(p1: PointF, p2: PointF, p3: PointF, p4: PointF): PointF? {
    val s1x = p2.x - p1.x
    val s1y = p2.y - p1.y
    val s2x = p4.x - p3.x
    val s2y = p4.y - p3.y

    val denominator = (-s2x * s1y + s1x * s2y)
    if (denominator == 0f) return null // Параллельные

    val s = (-s1y * (p1.x - p3.x) + s1x * (p1.y - p3.y)) / denominator
    val t = ( s2x * (p1.y - p3.y) - s2y * (p1.x - p3.x)) / denominator

    if (s in 0f..1f && t in 0f..1f) {
        return PointF(
            p1.x + (t * s1x),
            p1.y + (t * s1y)
        )
    }

    return null
}