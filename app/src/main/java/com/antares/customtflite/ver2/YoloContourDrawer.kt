package com.antares.customtflite.ver2

import android.graphics.*
import android.util.Log
import android.util.Size
import android.util.SizeF
import com.antares.customtflite.ver2.segmentor.MaskUtils2

class YoloContourDrawer(
    private val displaySize: Size
) {
    val strokePaint = Paint().apply {
        color = Color.RED
        style = Paint.Style.STROKE
        strokeWidth = 2f
        isAntiAlias = true
    }

    val fillPaint = Paint().apply {
        color = Color.argb(20, 255, 255, 0) // прозрачный жёлтый
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val bboxPaint = Paint().apply {
        strokeWidth = 2f
        style = Paint.Style.STROKE
        color = Color.CYAN
        isAntiAlias = true
    }

    private val textPaint = Paint().apply {
        color = Color.WHITE
        textSize = 32f
        isAntiAlias = true
        style = Paint.Style.FILL
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        setShadowLayer(2f, 1f, 1f, Color.BLACK)
    }

    fun drawDetections(
        bboxList: List<Triple<PointF, PointF, Float>>, // координаты в пикселях
        contours: List<List<PointF>> // нормализованные [0.0, 1.0]
    ): Bitmap {
        val width = displaySize.width
        val height = displaySize.height

        val baseBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(baseBitmap)
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        canvas.drawColor(Color.argb(100, 0, 0, 0)) // лёгкий затемняющий фон

        // 🔲 Bounding boxes
        bboxList.forEach { (topLeft, bottomRight, confidence) ->
            canvas.drawRect(topLeft.x, topLeft.y, bottomRight.x, bottomRight.y, bboxPaint)
            val confText = "%.2f".format(confidence)
            canvas.drawText(confText, topLeft.x + 4f, (topLeft.y - 8f).coerceAtLeast(10f), textPaint)
        }

        // 🧩 Рисуем контуры
        contours.forEach { contour ->
            if (contour.size < 3) return@forEach

            val path = Path().apply {
                val first = contour.first()
                moveTo(first.x * width, first.y * height)
                for (pt in contour.drop(1)) {
                    lineTo(pt.x * width, pt.y * height)
                }
                close()
            }

            canvas.drawPath(path, fillPaint)   // Заливка (например, жёлтая с alpha)
            canvas.drawPath(path, strokePaint) // Контур (например, красный)
        }

        return baseBitmap
    }
}