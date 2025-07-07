package com.antares.customtflite.canvas_ver3.pose

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PointF

 fun drawSkeleton(canvas: Canvas, landmarks: List<PointF>, width: Int, height: Int) {
    val connections = listOf(
        11 to 13, 13 to 15, // Left arm
        12 to 14, 14 to 16, // Right arm
        11 to 12,           // Shoulders
        23 to 24,           // Hips
        11 to 23, 12 to 24, // Torso
        23 to 25, 25 to 27, // Left leg
        24 to 26, 26 to 28  // Right leg
    )

    val jointPaint = Paint().apply {
        color = Color.BLUE
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    val bonePaint = Paint().apply {
        color = Color.GREEN
        strokeWidth = 4f
        style = Paint.Style.STROKE
        isAntiAlias = true
    }

    for ((startIdx, endIdx) in connections) {
        if (startIdx < landmarks.size && endIdx < landmarks.size) {
            val start = landmarks[startIdx]
            val end = landmarks[endIdx]
            canvas.drawLine(start.x * width, start.y * height, end.x * width, end.y * height, bonePaint)
        }
    }

    landmarks.forEach {
        canvas.drawCircle(it.x * width, it.y * height, 6f, jointPaint)
    }
}