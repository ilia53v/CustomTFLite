package com.antares.customtflite


import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View

class SegmentationView(context: Context) : View(context) {
    var paths: List<Path> = emptyList()
    private val paint = Paint().apply {
        color = Color.RED
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        paths.forEach { path ->
            canvas.drawPath(path, paint)
        }
    }
}