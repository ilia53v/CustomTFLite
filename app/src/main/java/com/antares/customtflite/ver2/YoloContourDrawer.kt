package com.antares.customtflite.ver2

import android.graphics.*
import android.util.Log
import android.util.Size
import android.util.SizeF
import androidx.core.graphics.ColorUtils
import com.antares.customtflite.ver2.segmentor.MaskUtils2
import java.util.Locale
import kotlin.random.Random

class YoloContourDrawer(private val displaySize: Size) {

    private val bboxPaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        isAntiAlias = true
    }

    private val contourFillPaint = Paint().apply {
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val contourStrokePaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        isAntiAlias = true
    }

    private val textPaint = Paint().apply {
        color = Color.WHITE
        textSize = 26f
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val overlayBitmap: Bitmap = Bitmap.createBitmap(
        displaySize.width,
        displaySize.height,
        Bitmap.Config.ARGB_8888
    )

    fun getOverlayBitmap(): Bitmap = overlayBitmap

    fun drawOverlay(
        bboxList: List<Triple<PointF, PointF, Float>>,
        allContours: List<List<List<PointF>>>
    ) {
        val canvas = Canvas(overlayBitmap)
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR) // очищаем предыдущее

        val path = Path()
        val colors = List(bboxList.size) {
            val hue = (it * 37) % 360
            Color.HSVToColor(floatArrayOf(hue.toFloat(), 0.8f, 0.95f))
        }

        bboxList.forEachIndexed { i, (tl, br, conf) ->
            val baseColor = colors[i % colors.size]
            bboxPaint.color = baseColor
            contourStrokePaint.color = baseColor
            contourFillPaint.color = ColorUtils.setAlphaComponent(baseColor, 64)

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
    }
}