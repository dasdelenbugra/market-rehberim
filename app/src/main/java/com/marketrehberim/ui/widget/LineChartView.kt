package com.marketrehberim.ui.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import com.google.android.material.color.MaterialColors

/**
 * Kütüphaneye bağımlı olmayan, tema uyumlu basit çizgi grafik.
 * Fiyat geçmişi noktalarını (List<Float>) çizer: çizgi + degrade dolgu + noktalar.
 */
class LineChartView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0,
) : View(context, attrs, defStyle) {

    private var values: List<Float> = emptyList()

    private val lineColor = MaterialColors.getColor(this, com.google.android.material.R.attr.colorPrimary, Color.GREEN)
    private val fillTop = (lineColor and 0x00FFFFFF) or 0x55000000
    private val gridColor = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOutline, Color.GRAY)

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(2.5f)
        color = lineColor
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val pointPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = lineColor
    }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1f)
        color = (gridColor and 0x00FFFFFF) or 0x33000000
    }

    fun setValues(values: List<Float>) {
        this.values = values
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (values.size < 2) return

        val padV = dp(12f)
        val padH = dp(8f)
        val w = width - padH * 2
        val h = height - padV * 2

        val min = values.min()
        val max = values.max()
        val range = (max - min).takeIf { it > 0f } ?: 1f

        val stepX = w / (values.size - 1)
        fun x(i: Int) = padH + stepX * i
        fun y(v: Float) = padV + h - ((v - min) / range) * h

        // Yatay kılavuz çizgileri
        for (g in 0..2) {
            val gy = padV + h * g / 2f
            canvas.drawLine(padH, gy, padH + w, gy, gridPaint)
        }

        // Degrade dolgu
        val fill = Path().apply {
            moveTo(x(0), y(values[0]))
            for (i in 1 until values.size) lineTo(x(i), y(values[i]))
            lineTo(x(values.size - 1), padV + h)
            lineTo(x(0), padV + h)
            close()
        }
        fillPaint.shader = LinearGradient(
            0f, padV, 0f, padV + h, fillTop, Color.TRANSPARENT, Shader.TileMode.CLAMP
        )
        canvas.drawPath(fill, fillPaint)

        // Çizgi
        val line = Path().apply {
            moveTo(x(0), y(values[0]))
            for (i in 1 until values.size) lineTo(x(i), y(values[i]))
        }
        canvas.drawPath(line, linePaint)

        // Uç noktalar (ilk ve son)
        canvas.drawCircle(x(0), y(values[0]), dp(3f), pointPaint)
        canvas.drawCircle(x(values.size - 1), y(values.last()), dp(4f), pointPaint)
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density
}
