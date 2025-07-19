package com.antares.customtflite.data

import android.graphics.PointF
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.sqrt

/*
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
}*/


fun simplifyContour(points: List<PointF>, epsilon: Float = 2f): List<PointF> {
    if (points.size < 3) return points

    fun perpendicularDistance(p: PointF, a: PointF, b: PointF): Float {
        val dx = b.x - a.x
        val dy = b.y - a.y
        if (dx == 0f && dy == 0f) return hypot(p.x - a.x, p.y - a.y)
        val t = ((p.x - a.x) * dx + (p.y - a.y) * dy) / (dx * dx + dy * dy)
        val projX = a.x + t * dx
        val projY = a.y + t * dy
        return hypot(p.x - projX, p.y - projY)
    }

    fun rdp(points: List<PointF>, epsilon: Float): List<PointF> {
        if (points.size < 3) return points
        var maxDist = 0f
        var index = 0
        for (i in 1 until points.size - 1) {
            val dist = perpendicularDistance(points[i], points[0], points.last())
            if (dist > maxDist) {
                index = i
                maxDist = dist
            }
        }
        return if (maxDist > epsilon) {
            val res1 = rdp(points.subList(0, index + 1), epsilon)
            val res2 = rdp(points.subList(index, points.size), epsilon)
            res1.dropLast(1) + res2
        } else listOf(points.first(), points.last())
    }

    return rdp(points, epsilon)
}