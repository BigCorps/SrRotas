package com.srrotas.app

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/** Compact building blocks of the vc105 Statistics screens (mock geometry). */
object StatsUi105 {
    data class Tone(val fg: Int, val soft: Int)

    fun dp(context: Context, value: Float): Int = (value * context.resources.displayMetrics.density + .5f).toInt()
    fun dp(context: Context, value: Int): Int = dp(context, value.toFloat())
    private fun dark(context: Context) = Appearance021.isDark(context)

    private fun blend(a: Int, b: Int, ratio: Float): Int {
        fun c(x: Int, y: Int) = (x + (y - x) * ratio).toInt().coerceIn(0, 255)
        return Color.rgb(c(Color.red(a), Color.red(b)), c(Color.green(a), Color.green(b)), c(Color.blue(a), Color.blue(b)))
    }

    fun tone(context: Context, name: String): Tone {
        val d = dark(context)
        val surface = SrUi023.palette(context).surface
        val fg = when (name) {
            "green" -> if (d) 0xFF4ADE80.toInt() else 0xFF16A34A.toInt()
            "orange" -> if (d) 0xFFFFA94D.toInt() else 0xFFF97316.toInt()
            "purple" -> if (d) 0xFFA78BFA.toInt() else 0xFF7C3AED.toInt()
            "slate" -> if (d) 0xFF9FB3D1.toInt() else 0xFF5B6B8C.toInt()
            "amber" -> if (d) 0xFFFFD24A.toInt() else 0xFFF5B400.toInt()
            "teal" -> if (d) 0xFF2DD4BF.toInt() else 0xFF0F9F8F.toInt()
            else -> if (d) 0xFF6CB4FF.toInt() else 0xFF1F6FEB.toInt()
        }
        return Tone(fg, blend(surface, fg, if (d) .22f else .13f))
    }

    fun blue(context: Context) = tone(context, "blue").fg
    fun ink(context: Context) = SrUi023.palette(context).ink
    fun muted(context: Context) = SrUi023.palette(context).muted
    fun line(context: Context) = SrUi023.palette(context).outline

    fun rounded(color: Int, radiusDp: Float, context: Context, stroke: Int? = null, strokeDp: Float = 1f) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = radiusDp * context.resources.displayMetrics.density
        if (stroke != null) setStroke(dp(context, strokeDp).coerceAtLeast(1), stroke)
    }

    /** Thin bordered surface with a soft elevation, as in the mock cards. */
    fun card(context: Context, padH: Int = 10, padV: Int = 9, radius: Float = 14f, fill: Int? = null, stroke: Int? = null): LinearLayout =
        LinearLayout(context).apply {
            val p = SrUi023.palette(context)
            background = rounded(fill ?: p.surface, radius, context, stroke ?: p.outline, 1f)
            elevation = dp(context, 1.5f).toFloat()
            setPadding(dp(context, padH), dp(context, padV), dp(context, padH), dp(context, padV))
        }

    fun text(context: Context, value: String, sp: Float, color: Int, bold: Boolean = false, lines: Int = 1): TextView = TextView(context).apply {
        text = value
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
        setTextColor(color)
        if (bold) setTypeface(Typeface.DEFAULT, Typeface.BOLD)
        includeFontPadding = false
        if (lines > 0) { maxLines = lines; ellipsize = TextUtils.TruncateAt.END }
    }

    /** One-line value that shrinks instead of wrapping; never truncates cents. */
    fun value(context: Context, value: String, maxSp: Int, minSp: Int = 11, color: Int = ink(context)): TextView =
        FitText105(context, maxSp.toFloat(), minSp.toFloat()).apply {
            text = value
            setTextColor(color)
            setTypeface(Typeface.DEFAULT, Typeface.BOLD)
            includeFontPadding = false
            maxLines = 1
            ellipsize = null
        }

    /** Deterministic shrink-to-fit (platform autosize is unreliable inside weighted rows). */
    class FitText105(context: Context, private val maxSp: Float, private val minSp: Float) : TextView(context) {
        init { setTextSize(TypedValue.COMPLEX_UNIT_SP, maxSp) }
        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val mode = MeasureSpec.getMode(widthMeasureSpec)
            if (mode != MeasureSpec.UNSPECIFIED) {
                val available = MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight
                val scaled = resources.displayMetrics.scaledDensity
                var sp = maxSp
                val probe = android.text.TextPaint(paint)
                while (sp > minSp) {
                    probe.textSize = sp * scaled
                    if (probe.measureText(text.toString()) <= available) break
                    sp -= 0.5f
                }
                if (kotlin.math.abs(textSize - sp * scaled) > 0.1f) super.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
            }
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        }
    }

    fun icon(context: Context, drawable: Int, tint: Int, sizeDp: Int): ImageView = ImageView(context).apply {
        setImageResource(drawable)
        imageTintList = ColorStateList.valueOf(tint)
        scaleType = ImageView.ScaleType.FIT_CENTER
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        layoutParams = LinearLayout.LayoutParams(dp(context, sizeDp), dp(context, sizeDp))
    }

    fun iconCircle(context: Context, drawable: Int, tone: Tone, sizeDp: Int = 38, iconDp: Int = 21, square: Boolean = false): FrameLayout =
        FrameLayout(context).apply {
            background = rounded(tone.soft, if (square) 11f else sizeDp / 2f, context)
            addView(icon(context, drawable, tone.fg, iconDp), FrameLayout.LayoutParams(dp(context, iconDp), dp(context, iconDp), Gravity.CENTER))
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            layoutParams = LinearLayout.LayoutParams(dp(context, sizeDp), dp(context, sizeDp))
        }

    /** Small yellow "!" that opens the missing data/configuration. */
    fun warnBadge(context: Context, description: String, sizeDp: Int = 20, onClick: () -> Unit): TextView = TextView(context).apply {
        text = "!"
        gravity = Gravity.CENTER
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f)
        setTypeface(Typeface.DEFAULT, Typeface.BOLD)
        setTextColor(0xFF1F2937.toInt())
        includeFontPadding = false
        background = rounded(tone(context, "amber").fg, sizeDp / 2f, context, 0xFFE3A300.toInt(), 1f)
        contentDescription = description
        isClickable = true; isFocusable = true
        setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(dp(context, sizeDp), dp(context, sizeDp))
    }

    fun chevron(context: Context, color: Int = ink(context), sizeDp: Int = 18): ImageView =
        icon(context, R.drawable.sr23_ic_chevron_right, color, sizeDp)

    fun skeleton(context: Context, widthDp: Int, heightDp: Int = 12): View = View(context).apply {
        background = rounded(blendSkeleton(context), heightDp / 2f, context)
        layoutParams = LinearLayout.LayoutParams(dp(context, widthDp), dp(context, heightDp))
        contentDescription = "Carregando"
    }

    private fun blendSkeleton(context: Context): Int {
        val p = SrUi023.palette(context)
        return blend(p.surface, tone(context, "blue").fg, if (dark(context)) .18f else .16f)
    }

    data class Chip(val label: String, val selected: Boolean = false, val filled: Boolean = false, val description: String? = null, val onClick: () -> Unit)

    fun chip(context: Context, chip: Chip): TextView = TextView(context).apply {
        val p = SrUi023.palette(context)
        val accent = blue(context)
        text = chip.label
        gravity = Gravity.CENTER
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f)
        includeFontPadding = false
        maxLines = 1
        minHeight = dp(context, 34)
        setPadding(dp(context, 15), 0, dp(context, 15), 0)
        when {
            chip.filled -> { setTextColor(Color.WHITE); setTypeface(Typeface.DEFAULT, Typeface.BOLD); background = rounded(accent, 17f, context, accent, 1.5f) }
            chip.selected -> { setTextColor(accent); setTypeface(Typeface.DEFAULT, Typeface.BOLD); background = rounded(tone(context, "blue").soft, 17f, context, accent, 1.5f) }
            else -> { setTextColor(p.ink); background = rounded(p.surface, 17f, context, blend(p.outline, p.muted, .35f), 1.2f) }
        }
        contentDescription = chip.description ?: "${chip.label}${if (chip.selected || chip.filled) ", selecionado" else ""}"
        isClickable = true; isFocusable = true
        setOnClickListener { chip.onClick() }
    }

    /** Period + categories in one scrollable line; optional "more" stays fixed on the right. */
    fun chipStrip(context: Context, chips: List<Chip>, more: (() -> Unit)? = null): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        chips.forEachIndexed { i, c ->
            row.addView(chip(context, c), LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(context, 34)).apply {
                if (i > 0) marginStart = dp(context, 7)
            })
        }
        addView(HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            isFillViewport = false
            contentDescription = "Período e categorias. Role para a direita para mais opções."
            addView(row)
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        if (more != null) addView(TextView(context).apply {
            text = "•••›"
            setTextColor(blue(context))
            setTypeface(Typeface.DEFAULT, Typeface.BOLD)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            gravity = Gravity.CENTER
            includeFontPadding = false
            contentDescription = "Mais opções: todos os relatórios"
            isClickable = true; isFocusable = true
            setPadding(dp(context, 8), 0, dp(context, 2), 0)
            setOnClickListener { more() }
        }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, dp(context, 34)))
    }

    fun sectionTitle(context: Context, value: String, sp: Float = 19f): TextView =
        text(context, value, sp, ink(context), bold = true, lines = 2)

    fun divider(context: Context): View = View(context).apply {
        setBackgroundColor(line(context))
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(context, 1f).coerceAtLeast(1))
    }

    fun grid(context: Context, cells: List<View>, columns: Int, gapDp: Int = 8): LinearLayout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        cells.chunked(columns).forEachIndexed { r, group ->
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                group.forEachIndexed { i, cell ->
                    addView(cell, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f).apply {
                        if (i > 0) marginStart = dp(context, gapDp)
                    })
                }
                repeat(columns - group.size) { addView(View(context), LinearLayout.LayoutParams(0, 1, 1f).apply { marginStart = dp(context, gapDp) }) }
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                if (r > 0) topMargin = dp(context, gapDp)
            })
        }
    }

    fun lp(context: Context, top: Int = 0, bottom: Int = 0, width: Int = LinearLayout.LayoutParams.MATCH_PARENT) =
        LinearLayout.LayoutParams(width, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(context, top); bottomMargin = dp(context, bottom)
        }

    /** Compact layout rules: 2 columns unless the screen really cannot hold them. */
    fun columns(widthDp: Int, fontScale: Float): Int = if (widthDp < 340 || (fontScale >= 1.3f && widthDp < 400)) 1 else 2
}
