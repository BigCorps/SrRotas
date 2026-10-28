package com.srrotas.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.view.View
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.pow

/**
 * Native column chart with an optional line on a second axis. It only draws
 * values it receives: a null is a visible gap, never a zero bar. When there is
 * no eligible series the frame stays on screen with an explanation.
 */
class StatisticsChartView105(context: Context) : View(context) {
    data class Point(val label: String, val bar: Double?, val line: Double? = null, val partial: Boolean = false)

    private var points: List<Point> = emptyList()
    private var barName: String? = null
    private var lineName: String? = null
    private var barAxis: String? = null
    private var lineAxis: String? = null
    private var hasLine = false
    private var message: String? = null
    private var loading = false
    private var barFormat: (Double) -> String = { StatsFormat105.number(it, 0) }
    private var lineFormat: (Double) -> String = { StatsFormat105.number(it, 0) }

    private val density = resources.displayMetrics.density
    private val scaled = resources.displayMetrics.scaledDensity
    private val dark = Appearance021.isDark(context)
    private val palette = SrUi023.palette(context)
    private val barTop = if (dark) 0xFF6FB2FF.toInt() else 0xFF5AA6FF.toInt()
    private val barBottom = if (dark) 0xFF3D8BFF.toInt() else 0xFF2F86FA.toInt()
    private val barSoft = if (dark) 0xFF3A5D86.toInt() else 0xFFB9D8FF.toInt()
    private val lineColor = if (dark) 0xFF8CC2FF.toInt() else 0xFF1F5FE0.toInt()

    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = palette.outline; strokeWidth = 1f * density; style = Paint.Style.STROKE
        pathEffect = DashPathEffect(floatArrayOf(4f * density, 4f * density), 0f)
    }
    private val axisPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = palette.muted; strokeWidth = 1.2f * density }
    private val textPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = palette.muted; textSize = 11f * scaled }
    private val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = palette.muted; textSize = 11.5f * scaled }
    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = lineColor; strokeWidth = 2.4f * density; style = Paint.Style.STROKE; strokeJoin = Paint.Join.ROUND; strokeCap = Paint.Cap.ROUND
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = lineColor }
    private val gapPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = palette.outline; strokeWidth = 2f * density; strokeCap = Paint.Cap.ROUND }
    private val messagePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = palette.ink; textSize = 12.5f * scaled }

    fun setData(
        points: List<Point>,
        barName: String? = null,
        lineName: String? = null,
        barAxis: String? = null,
        lineAxis: String? = null,
        barFormat: (Double) -> String = { StatsFormat105.number(it, 0) },
        lineFormat: (Double) -> String = { StatsFormat105.number(it, 0) },
        emptyMessage: String? = null,
    ) {
        this.points = points
        this.barName = barName
        this.lineName = lineName
        this.barAxis = barAxis
        this.lineAxis = lineAxis
        this.barFormat = barFormat
        this.lineFormat = lineFormat
        this.hasLine = lineName != null
        this.loading = false
        val anyValue = points.any { (it.bar ?: 0.0) > 0.0 || (it.line ?: 0.0) > 0.0 }
        this.message = if (anyValue) null else emptyMessage ?: "Sem dados com evidência suficiente neste período."
        contentDescription = buildString {
            append(barName ?: "Gráfico")
            if (lineName != null) append(" e $lineName")
            append(": ")
            append(points.joinToString("; ") { p ->
                "${p.label} ${p.bar?.let(barFormat) ?: "sem dado"}" + if (hasLine) " / ${p.line?.let(lineFormat) ?: "sem dado"}" else ""
            })
        }
        requestLayout(); invalidate()
    }

    fun setLoading(labels: List<String>, dual: Boolean, barAxis: String? = null, lineAxis: String? = null) {
        points = labels.map { Point(it, null) }
        loading = true
        hasLine = dual
        this.barAxis = barAxis
        this.lineAxis = lineAxis
        barName = null; lineName = null
        message = "Carregando…"
        contentDescription = "Gráfico carregando"
        invalidate()
    }

    fun setMessage(labels: List<String>, text: String, dual: Boolean = false, barAxis: String? = null, lineAxis: String? = null) {
        points = labels.map { Point(it, null) }
        loading = false
        hasLine = dual
        this.barAxis = barAxis; this.lineAxis = lineAxis
        barName = null; lineName = null
        message = text
        contentDescription = text
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val legend = if (barName != null && lineName != null) 26 else 0
        val titles = if (barAxis != null || lineAxis != null) 20 else 0
        val desired = ((if (hasLine) 168 else 150) + legend + titles) * density
        setMeasuredDimension(width, resolveSize(desired.toInt(), heightMeasureSpec))
    }

    /** Axis top = 3 × a round step, so every tick is a readable round number. */
    private fun niceMax(value: Double): Double {
        if (value <= 0) return 3.0
        val raw = value / 3
        val base = 10.0.pow(floor(log10(raw)))
        val step = listOf(1.0, 2.0, 2.5, 3.0, 4.0, 5.0, 10.0).first { it * base >= raw } * base
        return step * 3
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        val showLegend = barName != null && lineName != null && !loading && message == null
        var top = 4f * density
        if (barAxis != null || lineAxis != null) {
            barAxis?.let { canvas.drawText(it, 0f, top + titlePaint.textSize, titlePaint) }
            lineAxis?.let { canvas.drawText(it, w - titlePaint.measureText(it), top + titlePaint.textSize, titlePaint) }
            top += 22f * density
        }
        val legendH = if (showLegend) 26f * density else 0f
        val labelH = 20f * density
        val bottom = h - legendH - labelH
        val barMax = niceMax(points.mapNotNull { it.bar }.maxOrNull() ?: 0.0)
        val lineMax = niceMax(points.mapNotNull { it.line }.maxOrNull() ?: 0.0)
        val ticks = 3
        val leftLabels = (0..ticks).map { barFormat(barMax * it / ticks) }
        val rightLabels = if (hasLine) (0..ticks).map { lineFormat(lineMax * it / ticks) } else emptyList()
        val noValues = loading || message != null
        val left = if (noValues) 8f * density else (leftLabels.maxOf { textPaint.measureText(it) } + 8f * density)
        val right = w - if (hasLine && !noValues) (rightLabels.maxOf { textPaint.measureText(it) } + 10f * density) else 4f * density
        val plotTop = top + 6f * density
        val plotH = bottom - plotTop

        // Grid, ticks and axes.
        for (i in 0..ticks) {
            val y = bottom - plotH * i / ticks
            if (i > 0) canvas.drawLine(left, y, right, y, gridPaint)
            if (!noValues) {
                val l = leftLabels[i]
                canvas.drawText(l, left - 6f * density - textPaint.measureText(l), y + textPaint.textSize / 3, textPaint)
                if (hasLine) canvas.drawText(rightLabels[i], right + 6f * density, y + textPaint.textSize / 3, textPaint)
            }
        }
        canvas.drawLine(left, bottom, right, bottom, axisPaint)
        if (!noValues) {
            canvas.drawLine(left, plotTop, left, bottom, axisPaint)
            if (hasLine) canvas.drawLine(right, plotTop, right, bottom, axisPaint)
        }

        val n = max(1, points.size)
        val slot = (right - left) / n
        val barW = (slot * if (n > 20) 0.7f else 0.62f).coerceAtMost(46f * density)
        val labelEvery = ceil(n / 9.0).toInt().coerceAtLeast(1)
        val centers = points.indices.map { left + slot * (it + 0.5f) }

        points.forEachIndexed { i, p ->
            val cx = centers[i]
            if (i % labelEvery == 0 || i == n - 1 && n <= 9) {
                val lw = textPaint.measureText(p.label)
                canvas.drawText(p.label, (cx - lw / 2).coerceIn(0f, w - lw), bottom + 15f * density, textPaint)
            }
            if (loading) {
                val fake = plotH * (0.35f + 0.4f * ((i * 37) % 10) / 10f)
                barPaint.shader = null; barPaint.color = palette.surfaceMuted
                canvas.drawRoundRect(RectF(cx - barW / 2, bottom - fake, cx + barW / 2, bottom), 4f * density, 4f * density, barPaint)
                return@forEachIndexed
            }
            if (message != null) return@forEachIndexed
            val v = p.bar
            if (v == null) {
                canvas.drawLine(cx - barW / 4, bottom - 3f * density, cx + barW / 4, bottom - 3f * density, gapPaint)
            } else if (v > 0) {
                val bh = (plotH * (v / barMax)).toFloat()
                val rect = RectF(cx - barW / 2, bottom - bh, cx + barW / 2, bottom)
                if (p.partial) { barPaint.shader = null; barPaint.color = barSoft }
                else barPaint.shader = LinearGradient(0f, rect.top, 0f, rect.bottom, barTop, barBottom, Shader.TileMode.CLAMP)
                val r = 4f * density
                val path = Path().apply { addRoundRect(rect, floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f), Path.Direction.CW) }
                canvas.drawPath(path, barPaint)
            }
        }
        if (hasLine && !noValues) {
            val path = Path()
            var open = false
            points.forEachIndexed { i, p ->
                val v = p.line
                if (v == null) { open = false; return@forEachIndexed }
                val y = (bottom - plotH * (v / lineMax)).toFloat()
                if (!open) path.moveTo(centers[i], y) else path.lineTo(centers[i], y)
                open = true
            }
            canvas.drawPath(path, linePaint)
            points.forEachIndexed { i, p ->
                val v = p.line ?: return@forEachIndexed
                canvas.drawCircle(centers[i], (bottom - plotH * (v / lineMax)).toFloat(), 4.2f * density, dotPaint)
            }
        }
        message?.let { text ->
            val maxWidth = ((right - left) * 0.86f).toInt().coerceAtLeast(10)
            val layout = StaticLayout.Builder.obtain(text, 0, text.length, messagePaint, maxWidth)
                .setAlignment(Layout.Alignment.ALIGN_CENTER).setLineSpacing(0f, 1.1f).build()
            canvas.save()
            canvas.translate(left + (right - left - maxWidth) / 2, plotTop + (plotH - layout.height) / 2)
            layout.draw(canvas)
            canvas.restore()
        }
        if (showLegend) {
            val y = h - 9f * density
            val swatch = 22f * density
            val gap = 8f * density
            val barLabel = barName!!
            val lineLabel = lineName!!
            val total = swatch + gap + textPaint.measureText(barLabel) + 22f * density + swatch + gap + textPaint.measureText(lineLabel)
            var x = (w - total) / 2
            barPaint.shader = null; barPaint.color = barTop
            canvas.drawRoundRect(RectF(x, y - 11f * density, x + swatch, y + 1f * density), 3f * density, 3f * density, barPaint)
            x += swatch + gap
            canvas.drawText(barLabel, x, y, textPaint)
            x += textPaint.measureText(barLabel) + 22f * density
            canvas.drawLine(x, y - 5f * density, x + swatch, y - 5f * density, linePaint)
            canvas.drawCircle(x + swatch / 2, y - 5f * density, 4f * density, dotPaint)
            x += swatch + gap
            canvas.drawText(lineLabel, x, y, textPaint)
        }
    }

    init {
        textPaint.typeface = Typeface.DEFAULT
        isFocusable = true
    }
}
