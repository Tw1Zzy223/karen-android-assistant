package com.karen.assistant

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import kotlin.math.cos
import kotlin.math.sin

class WebBackdrop(context: Context, attrs: AttributeSet?) : View(context, attrs) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x30FFFFFF; strokeWidth = 1.5f; style = Paint.Style.STROKE }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width * 0.5f
        val cy = height * 0.12f
        val radius = height.toFloat()
        if (AssistantMode.iron(context)) {
            paint.color = 0x3054DDFF
            for (ring in 1..12) canvas.drawCircle(cx, cy, ring * width / 5f, paint)
            for (i in 0..8) canvas.drawLine(i * width / 8f, 0f, i * width / 8f, height.toFloat(), paint)
            return
        }
        for (i in 0..15) {
            val angle = i * Math.PI / 8
            canvas.drawLine(cx, cy, cx + cos(angle).toFloat() * radius, cy + sin(angle).toFloat() * radius, paint)
        }
        for (ring in 1..16) {
            val r = ring * width / 5f
            for (i in 0..15) {
                val a = i * Math.PI / 8
                val b = (i + 1) * Math.PI / 8
                canvas.drawLine(cx + cos(a).toFloat() * r, cy + sin(a).toFloat() * r, cx + cos(b).toFloat() * r, cy + sin(b).toFloat() * r, paint)
            }
        }
    }
}
