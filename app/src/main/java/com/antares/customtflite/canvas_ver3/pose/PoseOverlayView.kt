package com.antares.customtflite.canvas_ver3.pose

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.view.View

/*
class PoseOverlayView(context: Context) : View(context) {

    private val allLandmarks = mutableListOf<List<PointF>>()
    private val paint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
        isAntiAlias = true
    }

    private val jointPaint = Paint().apply {
        style = Paint.Style.FILL
        strokeWidth = 2f
        isAntiAlias = true
    }

    private val colors = listOf(
        Color.RED,
        Color.GREEN,
        Color.BLUE,
        Color.MAGENTA,
        Color.CYAN
    )

    private val connections = listOf(
        Pair(0, 1), Pair(1, 2), Pair(2, 3), Pair(3, 7),  // Right arm
        Pair(0, 4), Pair(4, 5), Pair(5, 6), Pair(6, 8),  // Left arm
        Pair(9, 10),                                     // Shoulders
        Pair(11, 12), Pair(12, 14), Pair(14, 16),        // Right leg
        Pair(11, 13), Pair(13, 15),                      // Left leg
        Pair(12, 24), Pair(11, 23),                      // Hip to legs
        Pair(23, 24), Pair(23, 25), Pair(24, 26),
        Pair(25, 27), Pair(27, 29), Pair(29, 31),
        Pair(26, 28), Pair(28, 30), Pair(30, 32)
    )

    fun setAllPoseLandmarks(poses: List<List<PointF>>) {
        allLandmarks.clear()
        allLandmarks.addAll(poses)
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        for ((personIdx, landmarks) in allLandmarks.withIndex()) {
            val color = colors[personIdx % colors.size]
            paint.color = color
            jointPaint.color = color

            for ((startIdx, endIdx) in connections) {
                if (startIdx < landmarks.size && endIdx < landmarks.size) {
                    val start = landmarks[startIdx]
                    val end = landmarks[endIdx]
                    canvas.drawLine(
                        start.x * width,
                        start.y * height,
                        end.x * width,
                        end.y * height,
                        paint
                    )
                }
            }

            for (landmark in landmarks) {
                canvas.drawCircle(landmark.x * width, landmark.y * height, 6f, jointPaint)
            }
        }
    }
}
*/


class PoseOverlayView(context: Context) : View(context) {

    private val allLandmarks = mutableListOf<List<PointF>>()
    private val allPaths = mutableListOf<Path>()
    private val allPoints = mutableListOf<FloatArray>()

    private val paint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
        isAntiAlias = true
    }

    private val jointPaint = Paint().apply {
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val colors = listOf(
        Color.RED,
        Color.GREEN,
        Color.BLUE,
        Color.MAGENTA,
        Color.CYAN
    )

    private val connections = listOf(
        Pair(0, 1), Pair(1, 2), Pair(2, 3), Pair(3, 7),
        Pair(0, 4), Pair(4, 5), Pair(5, 6), Pair(6, 8),
        Pair(9, 10),
        Pair(11, 12), Pair(12, 14), Pair(14, 16),
        Pair(11, 13), Pair(13, 15),
        Pair(12, 24), Pair(11, 23),
        Pair(23, 24), Pair(23, 25), Pair(24, 26),
        Pair(25, 27), Pair(27, 29), Pair(29, 31),
        Pair(26, 28), Pair(28, 30), Pair(30, 32)
    )

    private var lastInvalidateTime = 0L
    private val minInvalidateInterval = 50L // минимум 20 FPS (~50 мс)

    fun setAllPoseLandmarks(poses: List<List<PointF>>) {
        if (width == 0 || height == 0) return // Ждем размеров

        allLandmarks.clear()
        allPaths.clear()
        allPoints.clear()

        allLandmarks.addAll(poses)

        val widthF = width.toFloat()
        val heightF = height.toFloat()

        for (landmarks in allLandmarks) {
            // Упрощаем точки RDP для каждого контура соединений
            val path = Path()

            for ((startIdx, endIdx) in connections) {
                if (startIdx < landmarks.size && endIdx < landmarks.size) {
                    val startPt = landmarks[startIdx]
                    val endPt = landmarks[endIdx]

                    // Создаем список точек линии (прямая, 2 точки)
                    val linePoints = listOf(
                        RDPUtils.Point(startPt.x * widthF, startPt.y * heightF),
                        RDPUtils.Point(endPt.x * widthF, endPt.y * heightF)
                    )
                    // Можно применять RDP к более сложным линиям, здесь упрощение тривиально
                    val simplified = RDPUtils.simplify(linePoints, epsilon = 1f)

                    if (simplified.isNotEmpty()) {
                        val first = simplified.first()
                        path.moveTo(first.x, first.y)
                        for (pt in simplified.drop(1)) {
                            path.lineTo(pt.x, pt.y)
                        }
                    }
                }
            }
            allPaths.add(path)

            // Кэшируем суставы
            val pointsArray = FloatArray(landmarks.size * 2)
            for ((i, pt) in landmarks.withIndex()) {
                pointsArray[i * 2] = pt.x * widthF
                pointsArray[i * 2 + 1] = pt.y * heightF
            }
            allPoints.add(pointsArray)
        }

        val now = System.currentTimeMillis()
        if (now - lastInvalidateTime > minInvalidateInterval) {
            lastInvalidateTime = now
            postInvalidateOnAnimation() // безопасно вызывать из любого потока
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        for ((personIdx, path) in allPaths.withIndex()) {
            val color = colors[personIdx % colors.size]
            paint.color = color
            jointPaint.color = color

            canvas.drawPath(path, paint)

            val pointsArray = allPoints[personIdx]
            for (i in pointsArray.indices step 2) {
                canvas.drawCircle(pointsArray[i], pointsArray[i + 1], 6f, jointPaint)
            }
        }
    }
}