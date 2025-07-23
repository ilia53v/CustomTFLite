package com.antares.customtflite.ver2

import android.graphics.*
import android.util.Log
import android.util.Size
import android.util.SizeF
import androidx.core.graphics.ColorUtils
import com.antares.customtflite.ver2.segmentor.MaskUtils2
import java.util.Locale
import kotlin.random.Random

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
        allContours: List<List<List<PointF>>>,
        baseFrame: Bitmap
    ): Bitmap {
        // Создаём изменяемую копию только один раз
        val mutableBitmap = baseFrame.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(mutableBitmap)

        // Переиспользуем path вместо создания внутри цикла
        val path = Path()

        // Генератор фиксированных цветов на объект
        val colors = List(bboxList.size) {
            val hue = (it * 37) % 360 // псевдоразнообразие
            Color.HSVToColor(floatArrayOf(hue.toFloat(), 0.8f, 0.95f))
        }

        bboxList.forEachIndexed { i, (tl, br, conf) ->
            if (tl == br) return@forEachIndexed // защита от нулевого bbox

            val baseColor = colors[i % colors.size]
            bboxPaint.color = baseColor
            contourStrokePaint.color = baseColor
            contourFillPaint.color = ColorUtils.setAlphaComponent(baseColor, 64)

            // Рисуем bbox и текст
            canvas.drawRect(tl.x, tl.y, br.x, br.y, bboxPaint)

            val text = String.format(Locale.US, "%.2f", conf)
            canvas.drawText(text, tl.x + 4f, (tl.y - 8f).coerceAtLeast(12f), textPaint)

            val contourSet = allContours.getOrNull(i) ?: return@forEachIndexed
            path.reset()

            for (contour in contourSet) {
                if (contour.size < 3) continue

                path.moveTo(contour[0].x, contour[0].y)
                for (j in 1 until contour.size) {
                    path.lineTo(contour[j].x, contour[j].y)
                }
                path.close()
            }

            canvas.drawPath(path, contourFillPaint)
            canvas.drawPath(path, contourStrokePaint)
        }

        return mutableBitmap
    }
}