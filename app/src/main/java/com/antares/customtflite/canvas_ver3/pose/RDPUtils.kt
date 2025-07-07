package com.antares.customtflite.canvas_ver3.pose

import android.graphics.PointF
import kotlin.math.sqrt

object RDPUtils {

    fun simplify(points: List<PointF>, epsilon: Float): List<PointF> {
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
            (left.dropLast(1) + right)
        } else {
            listOf(first, last)
        }
    }

    private fun perpendicularDistance(pt: PointF, lineStart: PointF, lineEnd: PointF): Float {
        val dx = lineEnd.x - lineStart.x
        val dy = lineEnd.y - lineStart.y

        if (dx == 0f && dy == 0f) {
            return distance(pt, lineStart)
        }

        val numerator = Math.abs(
            dy * pt.x - dx * pt.y + lineEnd.x * lineStart.y - lineEnd.y * lineStart.x
        )
        val denominator = sqrt(dx * dx + dy * dy)
        return (numerator / denominator).toFloat()
    }

    private fun distance(a: PointF, b: PointF): Float {
        val dx = a.x - b.x
        val dy = a.y - b.y
        return sqrt(dx * dx + dy * dy)
    }
}