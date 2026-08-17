package com.srrotas.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import kotlin.math.max

class HistoryChartView(context: Context) : View(context) {
    data class Bar(val label: String, val value: Double)
    private var bars: List<Bar> = emptyList()
    private var suffix: String = ""
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    fun setBars(values: List<Bar>, suffix: String = "") {
        bars = values.takeLast(14)
        this.suffix = suffix
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), UiKit.dp(context, 190))
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val p = UiKit.palette(context)
        canvas.drawColor(p.surface)
        if (bars.isEmpty()) {
            paint.color = p.muted; paint.textSize = UiKit.dp(context, 13).toFloat()
            canvas.drawText("Ainda não há dados suficientes.", UiKit.dp(context, 14).toFloat(), height / 2f, paint)
            return
        }

        val left = UiKit.dp(context, 12).toFloat()
        val right = width - UiKit.dp(context, 8).toFloat()
        val top = UiKit.dp(context, 18).toFloat()
        val bottom = height - UiKit.dp(context, 34).toFloat()
        val maxValue = max(0.01, bars.maxOf { it.value })
        val gap = UiKit.dp(context, 5).toFloat()
        val barW = max(UiKit.dp(context, 8).toFloat(), (right - left - gap * (bars.size - 1)) / bars.size)

        paint.color = p.line; paint.strokeWidth = UiKit.dp(context, 1).toFloat()
        canvas.drawLine(left, bottom, right, bottom, paint)

        bars.forEachIndexed { index, bar ->
            val x = left + index * (barW + gap)
            val h = ((bar.value / maxValue) * (bottom - top)).toFloat()
            paint.color = p.primary
            canvas.drawRoundRect(RectF(x, bottom - h, x + barW, bottom), UiKit.dp(context, 5).toFloat(), UiKit.dp(context, 5).toFloat(), paint)

            paint.color = p.muted; paint.textSize = UiKit.dp(context, 9).toFloat()
            val label = bar.label.take(5)
            canvas.save()
            canvas.rotate(-45f, x + barW / 2, bottom + UiKit.dp(context, 7))
            canvas.drawText(label, x, bottom + UiKit.dp(context, 17), paint)
            canvas.restore()
        }

        paint.color = p.ink; paint.textSize = UiKit.dp(context, 11).toFloat()
        canvas.drawText("máx. ${String.format(java.util.Locale("pt","BR"), "%.2f", maxValue)}$suffix", left, UiKit.dp(context, 13).toFloat(), paint)
    }
}
