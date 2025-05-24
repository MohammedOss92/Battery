package com.sarrawi.mybattery

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

class BatteryView2 @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : View(context, attrs) {

    var batteryLevel: Int = 50 // النسبة من 0 إلى 100
        set(value) {
            field = value
            invalidate()
        }

    private val borderPaint = Paint().apply {
        color = Color.GRAY
        style = Paint.Style.STROKE
        strokeWidth = 4f
        isAntiAlias = true
    }

    private val fillPaint = Paint().apply {
        color = Color.GREEN
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

        val width = width.toFloat()
        val height = height.toFloat()

        // ارتفاع رأس البطارية
        val headHeight = 20f

        // جسم البطارية (أسفل الرأس)
        val bodyTop = headHeight + 10f
        val body = RectF(10f, bodyTop, width - 10f, height - 10f)

        // رسم الإطار الخارجي
        canvas.drawRect(body, borderPaint)

        // حساب الطول الممتلئ بناءً على النسبة
        val fillHeight = (batteryLevel / 100f) * (body.height())

        // رسم التعبئة من الأسفل للأعلى داخل جسم البطارية
        canvas.drawRect(
            body.left,
            body.bottom - fillHeight,
            body.right,
            body.bottom,
            fillPaint
        )

        // رسم رأس البطارية في الأعلى
        val headWidth = width / 3
        val headLeft = (width - headWidth) / 2
        val head = RectF(headLeft, 0f, headLeft + headWidth, headHeight)
        canvas.drawRect(head, borderPaint)

        // رسم النسبة داخل البطارية
        val centerX = body.centerX()
        val centerY = body.centerY() - (textPaint.descent() + textPaint.ascent()) / 2
        canvas.drawText("$batteryLevel%", centerX, centerY, textPaint)
    }
}
