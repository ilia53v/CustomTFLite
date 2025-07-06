package com.antares.customtflite.ver2

import android.graphics.*
import android.util.SizeF

class YoloContourDrawer(
    private val viewSize: SizeF
) {
    // Цвета для заливки и обводки (циклично по индексам)
    private val baseColors = listOf(
        Color.RED, Color.GREEN, Color.BLUE,
        Color.CYAN, Color.MAGENTA, Color.YELLOW,
        Color.WHITE, Color.LTGRAY, Color.rgb(255, 165, 0), // оранжевый
        Color.rgb(128, 0, 128) // фиолетовый
    )

    private val strokePaint = Paint().apply {
        strokeWidth = 3f
        style = Paint.Style.STROKE
        isAntiAlias = true
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
    }

    private val fillPaint = Paint().apply {
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    fun drawContours(
        contours: List<List<PointF>>,
        outputBitmap: Bitmap
    ) {
        val canvas = Canvas(outputBitmap)
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)

        val paint = Paint().apply {
            color = Color.RED
            strokeWidth = 3f
            style = Paint.Style.STROKE
            isAntiAlias = true
        }

        contours.forEachIndexed { index, contour ->
            if (contour.size < 3) return@forEachIndexed // нужно минимум 3 точки для заливки

            val path = Path().apply {
                val start = contour.first()
                moveTo(start.x * viewSize.width, start.y * viewSize.height)
                for (point in contour.drop(1)) {
                    lineTo(point.x * viewSize.width, point.y * viewSize.height)
                }
                close()
            }

            val baseColor = baseColors[index % baseColors.size]

            // Настроим полупрозрачную заливку
            fillPaint.color = baseColor
            fillPaint.alpha = 100 // из 255, 100 — полупрозрачность

            // Отрисуем заливку и обводку
            canvas.drawPath(path, fillPaint)

            strokePaint.color = baseColor
            canvas.drawPath(path, strokePaint)
        }
    }
}