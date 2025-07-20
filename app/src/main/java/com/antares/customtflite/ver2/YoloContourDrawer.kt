package com.antares.customtflite.ver2

import android.graphics.*
import android.util.Log
import android.util.Size
import android.util.SizeF
import androidx.core.graphics.ColorUtils
import com.antares.customtflite.ver2.segmentor.MaskUtils2
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

    /*fun drawDetections(
        bboxList: List<Triple<PointF, PointF, Float>>,
        contours: List<List<PointF>>,
        baseFrame: Bitmap
    ): Bitmap {
        val output = baseFrame.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(output)

        val random = Random(42)

        bboxList.forEachIndexed { i, (topLeft, bottomRight, confidence) ->
            val bboxColor = Color.rgb(random.nextInt(256), random.nextInt(256), random.nextInt(256))
            bboxPaint.color = bboxColor
            contourStrokePaint.color = bboxColor
            contourFillPaint.color = ColorUtils.setAlphaComponent(bboxColor, 64)

            // Нарисовать bbox
            canvas.drawRect(topLeft.x, topLeft.y, bottomRight.x, bottomRight.y, bboxPaint)

            val confText = "%.2f".format(confidence)
            canvas.drawText(confText, topLeft.x + 4f, (topLeft.y - 8f).coerceAtLeast(12f), textPaint)

            val contour = contours.getOrNull(i)?.takeIf { it.size > 10 } ?: return@forEachIndexed

            val path = Path()
            path.moveTo(contour[0].x, contour[0].y)
            for (pt in contour.drop(1)) {
                path.lineTo(pt.x, pt.y)
            }
            path.close()

            canvas.drawPath(path, contourFillPaint)
            canvas.drawPath(path, contourStrokePaint)
        }
        return output
    }*/
    fun drawDetections(
        bboxList: List<Triple<PointF, PointF, Float>>,
        allContours: List<List<List<PointF>>>,
        baseFrame: Bitmap
    ): Bitmap {
        val out = baseFrame.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(out)
        val random = Random(42)

        bboxList.forEachIndexed { i, (tl, br, conf) ->
            val color = Color.rgb(random.nextInt(256), random.nextInt(256), random.nextInt(256))
            bboxPaint.color = color
            contourFillPaint.color = ColorUtils.setAlphaComponent(color, 64)
            contourStrokePaint.color = color

            canvas.drawRect(tl.x, tl.y, br.x, br.y, bboxPaint)
            canvas.drawText("%.2f".format(conf), tl.x + 4f, (tl.y - 8f).coerceAtLeast(12f), textPaint)

            val contourSet = allContours.getOrNull(i) ?: return@forEachIndexed
            val path = Path()

            contourSet.forEachIndexed { idx, contour ->
                if (contour.size < 3) return@forEachIndexed
                if (idx == 0) {
                    path.moveTo(contour[0].x, contour[0].y)
                    contour.drop(1).forEach { path.lineTo(it.x, it.y) }
                    path.close()
                } else {
                    path.moveTo(contour[0].x, contour[0].y)
                    contour.drop(1).forEach { path.lineTo(it.x, it.y) }
                    path.close()
                }
            }

            canvas.drawPath(path, contourFillPaint)
            canvas.drawPath(path, contourStrokePaint)
        }
        return out
    }
}