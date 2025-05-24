package com.sarrawi.mybattery

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

class BatteryView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : View(context, attrs, defStyle) {

    var batteryLevel: Int = 100
        set(value) {
            field = value.coerceIn(0, 100)
            invalidate()
        }

    private val borderPaint = Paint().apply {
        color = Color.BLACK
        style = Paint.Style.STROKE
        strokeWidth = 6f
        isAntiAlias = true
    }

    private val fillPaint = Paint().apply {
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val textPaint = Paint().apply {
        color = Color.BLACK
        textSize = 36f
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()

        val batteryBody = RectF(10f, 10f, w - 20f, h - 10f)
        canvas.drawRect(batteryBody, borderPaint)

        // تغيير اللون حسب النسبة
        fillPaint.color = when {
            batteryLevel <= 20 -> Color.RED
            batteryLevel <= 50 -> Color.YELLOW
            else -> Color.GREEN
        }

        val fillWidth = (batteryBody.width()) * batteryLevel / 100f
        val fillRect = RectF(batteryBody.left, batteryBody.top, batteryBody.left + fillWidth, batteryBody.bottom)
        canvas.drawRect(fillRect, fillPaint)

        // رأس البطارية
        val headWidth = 10f
        val headLeft = w - 10f
        val headTop = h / 3
        val headBottom = h * 2 / 3
        canvas.drawRect(RectF(headLeft, headTop, headLeft + headWidth, headBottom), borderPaint)

        // كتابة النسبة داخل البطارية
        val centerX = batteryBody.centerX()
        val centerY = batteryBody.centerY() - (textPaint.descent() + textPaint.ascent()) / 2
        canvas.drawText("$batteryLevel%", centerX, centerY, textPaint)
    }
}
