package com.antares.customtflite.ver2

import android.graphics.*
import android.util.Log
import android.util.Size
import android.util.SizeF
import com.antares.customtflite.ver2.segmentor.MaskUtils2

class YoloContourDrawer(
    val displaySize: Size
) {

    private val contourStrokePaint = Paint().apply {
        color = Color.MAGENTA
        style = Paint.Style.STROKE
        strokeWidth = 2f
        isAntiAlias = true
    }

    private val contourFillPaint = Paint().apply {
        color = Color.argb(80, 255, 255, 0) // жёлтый с прозрачностью
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
        bboxList: List<Triple<PointF, PointF, Float>>,
        contours: List<List<PointF>>,
        baseFrame: Bitmap // ← использовать напрямую, не копировать каждый раз
    ): Bitmap {
        val width = displaySize.width
        val height = displaySize.height

        val baseBitmap = baseFrame.copy(Bitmap.Config.ARGB_8888, true) // или предварительно созданный
        val canvas = Canvas(baseBitmap)

        // Опционально: затемнение
        canvas.drawColor(Color.argb(10, 0, 0, 0))

        bboxList.forEach { (topLeft, bottomRight, confidence) ->
            canvas.drawRect(topLeft.x, topLeft.y, bottomRight.x, bottomRight.y, bboxPaint)
            val confText = "%.2f".format(confidence)
            canvas.drawText(confText, topLeft.x + 4f, (topLeft.y - 8f).coerceAtLeast(10f), textPaint)
        }

        if (contours.isNotEmpty() && contours[0].isNotEmpty()) {
            val first = contours[0][0]
            Log.i("Contours", "First point: ${first.x}, ${first.y}")
        }
        // 🧩 Нарисуем контуры
        contours.forEachIndexed { index, contour ->
            if (contour.size < 3) return@forEachIndexed

            val path = Path()
            val first = contour.first()
            path.moveTo(first.x * width, first.y * height)

            for (pt in contour.drop(1)) {
                path.lineTo(pt.x * width, pt.y * height)
            }
            path.close()

            // ✅ Рисуем контур В ЦВЕТЕ
            canvas.drawPath(path, contourFillPaint)
            canvas.drawPath(path, contourStrokePaint)
        }


        return baseBitmap
    }
}