package com.antares.customtflite.ver2

import android.graphics.*
import android.util.Log
import android.util.Size
import android.util.SizeF
import com.antares.customtflite.ver2.segmentor.MaskUtils2

class YoloContourDrawer(
    private val outputSize: Size,
    private val displaySize: Size
) {
    private val strokePaint = Paint().apply {
        strokeWidth = 3f
        style = Paint.Style.STROKE
        isAntiAlias = true
        strokeJoin = Paint.Join.ROUND
        strokeCap = Paint.Cap.ROUND
        color = Color.YELLOW
    }

    private val fillPaint = Paint().apply {
        style = Paint.Style.FILL
        isAntiAlias = true
        color = Color.argb(100, 255, 255, 0)
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
        bboxList: List<Triple<PointF, PointF, Float>>,
        contours: List<List<PointF>>
    ): Bitmap {
        val baseBitmap = Bitmap.createBitmap(outputSize.width, outputSize.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(baseBitmap)
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)

        // Рисуем боксы
        bboxList.forEach { (topLeft, bottomRight, confidence) ->
            canvas.drawRect(topLeft.x, topLeft.y, bottomRight.x, bottomRight.y, bboxPaint)
            val confText = "%.2f".format(confidence)
            canvas.drawText(confText, topLeft.x + 4f, (topLeft.y - 8f).coerceAtLeast(10f), textPaint)
        }

        // Рисуем контуры
        contours.forEach { contour ->
            if (contour.isEmpty()) return@forEach

            val path = Path().apply {
                moveTo(contour[0].x * outputSize.width, contour[0].y * outputSize.height)
                for (pt in contour.drop(1)) {
                    lineTo(pt.x * outputSize.width, pt.y * outputSize.height)
                }
                close()
            }
            canvas.drawPath(path, fillPaint)
            canvas.drawPath(path, strokePaint)
        }

        // Масштабируем Bitmap под размер видео
        return Bitmap.createScaledBitmap(baseBitmap, displaySize.width, displaySize.height, true)
    }
}