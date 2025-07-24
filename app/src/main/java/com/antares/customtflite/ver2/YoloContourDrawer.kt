package com.antares.customtflite.ver2

import android.graphics.*
import android.util.Log
import android.util.Size
import android.util.SizeF
import androidx.core.graphics.ColorUtils
import com.antares.customtflite.data.YoloObject
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
        bboxes: List<Triple<PointF, PointF, Float>>,
        contours: List<List<List<PointF>>>, // сгруппированы по объектам
        confidenceThreshold: Float = 0.4f,
        minAreaAbs: Float = 0.01f // может зависеть от размера кадра
    ) {
        val canvas = Canvas(overlayBitmap)
        canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)

        for (i in bboxes.indices) {
            val (topLeft, bottomRight, confidence) = bboxes[i]
            if (confidence < confidenceThreshold) continue

            val objectContours = contours.getOrNull(i) ?: continue

            for (contour in objectContours) {
                if (contour.size < 3) {
                    Log.d("ContourSkip", "Contour skipped: < 3 points (${contour.size})")
                    continue
                }

                val area = MaskUtils2.computePolygonArea(contour)
                if (area < minAreaAbs) {
                    Log.d("ContourSkip", "Contour skipped: area too small = $area, minAreaAbs = $minAreaAbs")
                    continue
                }

                //  Нарисовать контур
                val path = Path().apply {
                    contour.forEachIndexed { index, point ->
                        if (index == 0) moveTo(point.x, point.y) else lineTo(point.x, point.y)
                    }
                    close()
                }
                canvas.drawPath(path, contourStrokePaint)
            }

            // Нарисовать bbox
            canvas.drawRect(RectF(topLeft.x, topLeft.y, bottomRight.x, bottomRight.y), bboxPaint)

            // Нарисовать текст
            canvas.drawText(
                String.format("%.2f", confidence),
                topLeft.x,
                topLeft.y - 4,
                textPaint
            )
        }
    }

}