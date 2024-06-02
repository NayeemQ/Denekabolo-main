package com.example.denekabolo

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat

class BoundingBoxOverlay constructor(context: Context?, attributeSet: AttributeSet):
View(context, attributeSet) {
    private var bounds: Rect = Rect()
    private val paint = Paint().apply {
        style = Paint.Style.STROKE
        color = ContextCompat.getColor(context!!, android.R.color.holo_red_light)
        strokeWidth = 10f
    }

    override fun onDraw(canvas: Canvas)
    {
        super.onDraw(canvas)
        canvas.drawRect(bounds, paint)
    }

    fun drawBounds (b: Rect)
    {
        bounds = b
        invalidate()
    }
}