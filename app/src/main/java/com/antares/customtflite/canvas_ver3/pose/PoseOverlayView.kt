package com.antares.customtflite.canvas_ver3.pose

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF
import android.view.View

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
