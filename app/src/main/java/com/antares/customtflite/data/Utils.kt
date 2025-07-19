package com.antares.customtflite.data

import android.graphics.PointF
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.sqrt

fun simplifyContour(points: List<PointF>, epsilon: Float = 4f): List<PointF> {
    if (points.size < 3) return points
    val deduplicated = deduplicatePoints(points, minDistance = 2f)
    return ramerDouglasPeucker(deduplicated, epsilon)
}

private fun deduplicatePoints(points: List<PointF>, minDistance: Float): List<PointF> {
    if (points.isEmpty()) return emptyList()
    val result = mutableListOf<PointF>()
    var lastPoint = points.first()
    result.add(lastPoint)

    for (i in 1 until points.size) {
        val current = points[i]
        val dx = current.x - lastPoint.x
        val dy = current.y - lastPoint.y
        val distSq = dx * dx + dy * dy
        if (distSq >= minDistance * minDistance) {
            result.add(current)
            lastPoint = current
        }
    }
    return result
}

private fun ramerDouglasPeucker(points: List<PointF>, epsilon: Float): List<PointF> {
    if (points.size < 3) return points

    var maxDist = 0f
    var index = 0
    val start = points.first()
    val end = points.last()

    for (i in 1 until points.size - 1) {
        val dist = perpendicularDistance(points[i], start, end)
        if (dist > maxDist) {
            index = i
            maxDist = dist
        }
    }

    return if (maxDist > epsilon) {
        val rec1 = ramerDouglasPeucker(points.subList(0, index + 1), epsilon)
        val rec2 = ramerDouglasPeucker(points.subList(index, points.size), epsilon)
        rec1.dropLast(1) + rec2
    } else {
        listOf(start, end)
    }
}

private fun perpendicularDistance(p: PointF, start: PointF, end: PointF): Float {
    val dx = end.x - start.x
    val dy = end.y - start.y
    if (dx == 0f && dy == 0f) {
        return hypot(p.x - start.x, p.y - start.y)
    }
    val numerator = abs(dy * p.x - dx * p.y + end.x * start.y - end.y * start.x)
    val denominator = sqrt(dx * dx + dy * dy)
    return numerator / denominator
}