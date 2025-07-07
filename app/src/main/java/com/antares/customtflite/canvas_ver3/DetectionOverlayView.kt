package com.antares.customtflite.canvas_ver3

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.view.View
import com.antares.customtflite.canvas_ver3.pose.drawSkeleton
import kotlin.math.abs
import kotlin.math.sqrt

class DetectionOverlayView(context: Context) : View(context) {

    private val paint = Paint().apply {
        color = Color.RED
        style = Paint.Style.STROKE
        strokeWidth = 3f
        isAntiAlias = true
    }

    private var rawContours: List<List<PointF>> = emptyList()
    private val simplifiedContours: MutableList<Path> = mutableListOf()

    // Публичный метод обновления контуров
    fun setContours(contours: List<List<PointF>>) {
        rawContours = contours
        simplifiedContours.clear()
        for (contour in rawContours) {
            val simplified = simplify(contour, 0.005f) // нормализованный epsilon
            simplifiedContours.add(buildPath(simplified))
        }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        for (path in simplifiedContours) {
            canvas.drawPath(path, paint)
        }
    }

    // Упрощение RDP
    private fun simplify(points: List<PointF>, epsilon: Float): List<PointF> {
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

        if (dx == 0f && dy == 0f) return distance(pt, lineStart)

        val numerator = abs(dy * pt.x - dx * pt.y + lineEnd.x * lineStart.y - lineEnd.y * lineStart.x)
        val denominator = sqrt(dx * dx + dy * dy)
        return numerator / denominator
    }

    private fun distance(a: PointF, b: PointF): Float {
        val dx = a.x - b.x
        val dy = a.y - b.y
        return sqrt(dx * dx + dy * dy)
    }

    private fun buildPath(contour: List<PointF>): Path {
        val path = Path()
        if (contour.isEmpty()) return path
        val width = this.width.toFloat()
        val height = this.height.toFloat()

        path.moveTo(contour[0].x * width, contour[0].y * height)
        for (i in 1 until contour.size) {
            path.lineTo(contour[i].x * width, contour[i].y * height)
        }
        path.close()
        return path
    }
}