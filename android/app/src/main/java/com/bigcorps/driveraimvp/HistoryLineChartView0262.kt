package com.srrotas.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.view.View
import kotlin.math.max
import kotlin.math.min

/** Linha de evolução para Comparativos, preservando o gráfico de barras antigo. */
class HistoryLineChartView0262(context: Context) : View(context) {
    data class Point(val label: String, val value: Double)

    private var points: List<Point> = emptyList()
    private var suffix: String = ""
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    fun setPoints(values: List<Point>, suffix: String = "") {
        points = values.takeLast(14)
        this.suffix = suffix
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), UiKit.dp(context, 205))
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val palette = UiKit.palette(context)
        canvas.drawColor(palette.surface)
        if (points.size < 2) {
            paint.color = palette.muted
            paint.textSize = UiKit.dp(context, 13).toFloat()
            canvas.drawText(
                "São necessários pelo menos dois pontos no período.",
                UiKit.dp(context, 14).toFloat(),
                height / 2f,
                paint,
            )
            return
        }

        val left = UiKit.dp(context, 24).toFloat()
        val right = width - UiKit.dp(context, 16).toFloat()
        val top = UiKit.dp(context, 24).toFloat()
        val bottom = height - UiKit.dp(context, 42).toFloat()
        val minValue = points.minOf { it.value }
        val maxValue = points.maxOf { it.value }
        val span = max(0.01, maxValue - minValue)
        val step = (right - left) / max(1, points.lastIndex)

        paint.strokeWidth = UiKit.dp(context, 1).toFloat()
        paint.color = palette.line
        canvas.drawLine(left, bottom, right, bottom, paint)
        canvas.drawLine(left, top, left, bottom, paint)

        val path = Path()
        val coords = points.mapIndexed { index, point ->
            val x = left + step * index
            val normalized = ((point.value - minValue) / span).toFloat()
            val y = bottom - normalized * (bottom - top)
            x to y
        }
        coords.forEachIndexed { index, (x, y) ->
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = UiKit.dp(context, 3).toFloat()
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.ROUND
        paint.color = palette.primary
        canvas.drawPath(path, paint)

        paint.style = Paint.Style.FILL
        coords.forEachIndexed { index, (x, y) ->
            paint.color = palette.primary
            canvas.drawCircle(x, y, UiKit.dp(context, 4).toFloat(), paint)
            if (index == 0 || index == coords.lastIndex || points.size <= 7 || index % 2 == 0) {
                paint.color = palette.muted
                paint.textSize = UiKit.dp(context, 9).toFloat()
                val label = points[index].label.take(5)
                val tx = min(right - paint.measureText(label), max(left, x - paint.measureText(label) / 2))
                canvas.drawText(label, tx, bottom + UiKit.dp(context, 18), paint)
            }
        }

        val first = points.first().value
        val last = points.last().value
        val delta = if (first == 0.0) null else ((last - first) / kotlin.math.abs(first)) * 100.0
        paint.color = palette.ink
        paint.textSize = UiKit.dp(context, 11).toFloat()
        val header = buildString {
            append("${fmt(minValue)}–${fmt(maxValue)}$suffix")
            delta?.takeIf { it.isFinite() }?.let {
                append(" · ${if (it > 0) "+" else ""}${fmt(it)}% do primeiro ao último ponto")
            }
        }
        canvas.drawText(header, left, UiKit.dp(context, 15).toFloat(), paint)
    }

    private fun fmt(value: Double) =
        String.format(java.util.Locale("pt", "BR"), "%.2f", value)
}
