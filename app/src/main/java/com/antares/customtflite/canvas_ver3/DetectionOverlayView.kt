package com.antares.customtflite.canvas_ver3

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.view.View

class DetectionOverlayView(context: Context) : View(context) {

    private var cachedPaths: List<Path>? = null

    private var contours: List<List<PointF>> = emptyList()
    private val paint = Paint().apply {
        color = Color.RED
        strokeWidth = 4f
        style = Paint.Style.STROKE
        isAntiAlias = true
    }

    /*fun setContours(newContours: List<List<PointF>>) {
        if (newContours != contours) {
            contours = newContours
            invalidate()
        }
    }*/

    fun setContours(newContours: List<List<PointF>>) {
        contours = newContours
        cachedPaths = newContours.mapNotNull { contour ->
            if (contour.size > 1) {
                Path().apply {
                    moveTo(contour[0].x * width, contour[0].y * height)
                    for (p in contour.drop(1)) {
                        lineTo(p.x * width, p.y * height)
                    }
                    close()
                }
            } else null
        }
        invalidate()
    }


    override fun onDraw(canvas: Canvas) {
        /*super.onDraw(canvas)

        contours.forEach { contour ->
            if (contour.size > 1) {
                val path = Path()
                val first = contour.first()
                path.moveTo(first.x * width, first.y * height)
                for (point in contour.drop(1)) {
                    path.lineTo(point.x * width, point.y * height)
                }
                path.close()
                canvas.drawPath(path, paint)
            }
        }*/
        super.onDraw(canvas)
        cachedPaths?.forEach { canvas.drawPath(it, paint) }
    }
}