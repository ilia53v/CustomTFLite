package com.antares.customtflite.canvas_ver3.pose

import android.graphics.PointF
import kotlin.math.sqrt

object RDPUtils {
    // Точка с float координатами
    data class Point(val x: Float, val y: Float)

    fun simplify(points: List<Point>, epsilon: Float): List<Point> {
        if (points.size < 3) return points
        val first = points.first()
        val last = points.last()
        var maxDist = 0f
        var index = 0
        for (i in 1 until points.size - 1) {
            val dist = perpendicularDistance(points[i], first, last)
            if (dist > maxDist) {
                maxDist = dist
                index = i
            }
        }
        return if (maxDist > epsilon) {
            val left = simplify(points.subList(0, index + 1), epsilon)
            val right = simplify(points.subList(index, points.size), epsilon)
            left.dropLast(1) + right
        } else {
            listOf(first, last)
        }
    }

    private fun perpendicularDistance(pt: Point, lineStart: Point, lineEnd: Point): Float {
        val dx = lineEnd.x - lineStart.x
        val dy = lineEnd.y - lineStart.y
        if (dx == 0f && dy == 0f) {
            return distance(pt, lineStart)
        }
        val t = ((pt.x - lineStart.x) * dx + (pt.y - lineStart.y) * dy) / (dx * dx + dy * dy)
        val nearestX = lineStart.x + t * dx
        val nearestY = lineStart.y + t * dy
        return distance(pt, Point(nearestX, nearestY))
    }

    private fun distance(a: Point, b: Point): Float {
        val dx = a.x - b.x
        val dy = a.y - b.y
        return kotlin.math.sqrt(dx * dx + dy * dy)
    }
}