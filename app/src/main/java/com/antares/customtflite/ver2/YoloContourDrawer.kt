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

    fun drawDetections_two(
        canvas: Canvas,
        bboxList: List<Triple<PointF, PointF, Float>>,
        allContours: List<List<List<PointF>>>
    ) {
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
                path.moveTo(contour[0].x, contour[0].y)
                contour.drop(1).forEach { path.lineTo(it.x, it.y) }
                path.close()
            }

            canvas.drawPath(path, contourFillPaint)
            canvas.drawPath(path, contourStrokePaint)
        }
    }

    fun drawContoursOnCanvas(
        baseFrame: Bitmap,
        allContours: List<List<List<PointF>>>
    ): Bitmap {
        val output = baseFrame.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(output)

        val colors = listOf(
            Color.RED, Color.GREEN, Color.BLUE,
            Color.YELLOW, Color.CYAN, Color.MAGENTA,
            Color.LTGRAY, Color.DKGRAY, Color.WHITE
        )

        val strokePaint = Paint().apply {
            style = Paint.Style.STROKE
            strokeWidth = 3f
            isAntiAlias = true
        }

        var contourIndex = 0
        var totalContours = 0

        for ((objectIndex, objectContours) in allContours.withIndex()) {
            for (contour in objectContours) {
                if (contour.size < 2) continue

                totalContours++
                strokePaint.color = colors[contourIndex % colors.size]

                val path = Path().apply {
                    moveTo(contour[0].x, contour[0].y)
                    for (i in 1 until contour.size) {
                        lineTo(contour[i].x, contour[i].y)
                    }
                    close()
                }

                Log.d("DrawDebug", "Drawing contour $contourIndex with ${contour.size} points")

                canvas.drawPath(path, strokePaint)
                contourIndex++
            }
        }

        Log.d("DrawDebug", "Total contours drawn: $totalContours")
        return output
    }

}