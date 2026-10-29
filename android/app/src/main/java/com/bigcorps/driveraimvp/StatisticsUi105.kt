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
    fun card(context: Context, padH: Int = 10, padV: Int = 9, radius: Float = 14f, fill: Int? = null, stroke: Int? = null,
        into: LinearLayout = LinearLayout(context)): LinearLayout =
        into.apply {
            val p = SrUi023.palette(context)
            background = rounded(fill ?: p.surface, radius, context, stroke ?: p.outline, 1f)
            elevation = dp(context, 1.5f).toFloat()
            setPadding(dp(context, padH), dp(context, padV), dp(context, padH), dp(context, padV))
        }

    const val TAG_ICON = "sr_kpi_icon"
    const val TAG_COLUMN = "sr_kpi_column"
    const val TAG_LABEL = "sr_kpi_label"
    /** vc107: smallest value size accepted when cards compact (never hides cents). */
    const val MIN_VALUE_SP = 14f
    /** Compact card fixed part: 6dp padding ×2 + 30dp icon + 6dp gap. */
    const val COMPACT_FIXED_DP = 48f

    /**
     * KPI card shell (vc107). Above its comfortable width it is exactly the approved
     * vc105/vc106 card (9dp padding, 38dp icon, 9dp gap, one-line label). Below it, it
     * compacts itself (6dp, 30dp icon, 6dp gap, label up to two lines) instead of
     * forcing the grid down to one column.
     */
    class KpiCard105(context: Context, private val comfortablePx: Int, private val normalIconDp: Int) : LinearLayout(context) {
        private var compactApplied: Boolean? = null
        var compact: Boolean = false
            private set

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val unspecified = MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.UNSPECIFIED
            apply(!unspecified && MeasureSpec.getSize(widthMeasureSpec) < comfortablePx)
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        }

        private fun apply(value: Boolean) {
            if (compactApplied == value) return
            compactApplied = value
            compact = value
            val pad = dp(context, if (value) 6 else 9)
            setPadding(pad, dp(context, 9), pad, dp(context, 9))
            findViewWithTag<View>(TAG_ICON)?.let { icon ->
                val size = dp(context, if (value) 30 else normalIconDp)
                icon.layoutParams = (icon.layoutParams as LinearLayout.LayoutParams).apply { width = size; height = size }
                ((icon as? FrameLayout)?.getChildAt(0))?.let { glyph ->
                    val inner = dp(context, if (value) 17 else normalIconDp - 17)
                    glyph.layoutParams = (glyph.layoutParams as FrameLayout.LayoutParams).apply { width = inner; height = inner }
                }
                (icon.background as? GradientDrawable)?.cornerRadius = size / 2f
            }
            findViewWithTag<View>(TAG_COLUMN)?.let { column ->
                column.layoutParams = (column.layoutParams as LinearLayout.LayoutParams).apply { marginStart = dp(context, if (value) 6 else 9) }
            }
            // Labels wrap only when one line does not fit; a word is never ellipsized.
            labels(this).forEach { it.maxLines = 2 }
        }

        private fun labels(view: View): List<TextView> = when {
            view is TextView && view.tag == TAG_LABEL -> listOf(view)
            view is android.view.ViewGroup -> (0 until view.childCount).flatMap { labels(view.getChildAt(it)) }
            else -> emptyList()
        }
    }

    fun kpiShell(context: Context, comfortableDp: Float, iconDp: Int, minHeightDp: Int): KpiCard105 =
        KpiCard105(context, dp(context, comfortableDp), iconDp).apply {
            val p = SrUi023.palette(context)
            background = rounded(p.surface, 14f, context, p.outline, 1f)
            elevation = dp(context, 1.5f).toFloat()
            setPadding(dp(context, 9), dp(context, 9), dp(context, 9), dp(context, 9))
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(context, minHeightDp)
        }

    /**
     * Narrowest cell in which every given KPI card still shows its full value at
     * [MIN_VALUE_SP] (scaled by the font setting), the longest label word and its "!".
     */
    fun kpiRequiredCellPx(context: Context, cells: List<View>): Int {
        val density = context.resources.displayMetrics.density
        val scaled = context.resources.displayMetrics.scaledDensity
        val bold = android.text.TextPaint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            typeface = Typeface.DEFAULT_BOLD; textSize = MIN_VALUE_SP * scaled
        }
        // Loading cards reserve room for a typical value so the grid does not jump.
        var need = bold.measureText("R$\u00a00.000,00")
        fun walk(v: View) {
            when {
                v is FitText105 -> need = maxOf(need, bold.measureText(v.text.toString()))
                v is TextView && v.tag == TAG_LABEL -> {
                    val longest = v.text.split(' ', '\u00a0').maxOfOrNull { v.paint.measureText(it) } ?: 0f
                    val badge = ((v.parent as? android.view.ViewGroup)?.childCount ?: 1) > 1
                    need = maxOf(need, longest + if (badge) 24 * density else 0f)
                }
                v is android.view.ViewGroup -> (0 until v.childCount).forEach { walk(v.getChildAt(it)) }
            }
        }
        cells.forEach(::walk)
        return kotlin.math.ceil(COMPACT_FIXED_DP * density + need + 2 * density).toInt()
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

        /**
         * Largest size (0.5sp steps) whose text fits [available] px. A probe decides without
         * touching the view; the size is applied only when it changes, and the view's own
         * paint confirms it (text-size rounding can add a pixel).
         */
        private fun fit(available: Int): Boolean {
            if (available <= 0) return false
            val value = text.toString()
            val dm = resources.displayMetrics
            val probe = android.text.TextPaint(paint)
            var sp = maxSp
            while (sp > minSp) {
                probe.textSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, dm)
                if (probe.measureText(value) <= available) break
                sp -= 0.5f
            }
            val before = textSize
            if (kotlin.math.abs(before - TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, dm)) > 0.1f) super.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
            while (sp > minSp && paint.measureText(value) > available) {
                sp -= 0.5f
                super.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
            }
            return kotlin.math.abs(textSize - before) > 0.1f
        }

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            if (MeasureSpec.getMode(widthMeasureSpec) != MeasureSpec.UNSPECIFIED) fit(MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight)
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        }

        // The final width can differ from an earlier measure pass (weights, compact switch):
        // re-fit against the real width so a value is never clipped.
        override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
            super.onSizeChanged(w, h, oldw, oldh)
            if (paint.measureText(text.toString()) > w - paddingLeft - paddingRight && fit(w - paddingLeft - paddingRight)) post { requestLayout() }
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

    /**
     * vc106 (P2): column count comes from the width this grid actually receives in
     * onMeasure (container padding, 680dp clamp, split screen, freeform), never from
     * configuration.screenWidthDp. Resizing only re-lays out the same cards: no
     * rebuild and no data request.
     */
    class AdaptiveGrid105(context: Context, private val maxColumns: Int, private val minCellDp: Float, private val gapDp: Int,
        private val requiredCellPx: ((List<View>) -> Int)? = null) : android.view.ViewGroup(context) {
        /** Last decision, kept for QA/diagnostics: measured width, breakpoint and columns. */
        var lastWidthPx = 0
            private set
        var lastRequiredPx = 0
            private set
        var columns = 1
            private set
        private val gap = dp(context, gapDp)

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val width = MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight
            val density = resources.displayMetrics.density
            val required = requiredCellPx
            columns = when {
                MeasureSpec.getMode(widthMeasureSpec) == MeasureSpec.UNSPECIFIED -> maxColumns
                // vc107: two columns whenever the content itself fits in half of the measured width.
                required != null -> {
                    lastRequiredPx = required(visibleChildren())
                    (maxColumns downTo 2).firstOrNull { c -> (width - gap * (c - 1)) / c >= lastRequiredPx } ?: 1
                }
                else -> columnsFor(width / density, minCellDp, gapDp.toFloat(), maxColumns)
            }
            lastWidthPx = width
            val cell = ((width - gap * (columns - 1)) / columns).coerceAtLeast(0)
            var height = paddingTop + paddingBottom
            visibleChildren().chunked(columns).forEachIndexed { r, row ->
                row.forEach { it.measure(MeasureSpec.makeMeasureSpec(cell, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)) }
                val rowHeight = row.maxOf { it.measuredHeight }
                // Same row, same height (matches the approved 2×4 cards).
                row.forEach { if (it.measuredHeight != rowHeight) it.measure(MeasureSpec.makeMeasureSpec(cell, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(rowHeight, MeasureSpec.EXACTLY)) }
                height += rowHeight + if (r > 0) gap else 0
            }
            setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), resolveSize(height, heightMeasureSpec))
        }

        override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
            var y = paddingTop
            visibleChildren().chunked(columns).forEach { row ->
                var x = paddingLeft
                row.forEach { child ->
                    child.layout(x, y, x + child.measuredWidth, y + child.measuredHeight)
                    x += child.measuredWidth + gap
                }
                y += row.maxOf { it.measuredHeight } + gap
            }
        }

        private fun visibleChildren() = (0 until childCount).map(::getChildAt).filter { it.visibility != View.GONE }
    }

    /** Pure rule: how many cells of at least [minCellDp] fit in [availableDp]. */
    fun columnsFor(availableDp: Float, minCellDp: Float, gapDp: Float, maxColumns: Int): Int =
        (1..maxColumns).last { n -> n == 1 || (availableDp - gapDp * (n - 1)) / n >= minCellDp }

    /** KPI card: icon + margins are fixed; label/value width grows with the font scale. */
    fun kpiMinCellDp(fontScale: Float): Float = 65f + 92f * fontScale
    fun exploreMinCellDp(fontScale: Float): Float = 46f + 52f * fontScale

    /** vc107: padding 8+8, icon 22, margin 7 — the chevron (16dp) is dropped first when the cell is narrow. */
    const val EXPLORE_COMPACT_FIXED_DP = 45f
    const val EXPLORE_CHEVRON_DP = 16f

    private fun longestWordPx(v: TextView): Float = v.text.split(' ', '\n', '\u00a0').maxOfOrNull { v.paint.measureText(it) } ?: 0f

    /** Explore cards: full words always visible; chevron hidden before the grid drops a column. */
    class ExploreCard105(context: Context) : LinearLayout(context) {
        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val label = (0 until childCount).map(::getChildAt).filterIsInstance<TextView>().firstOrNull()
            val chevron = getChildAt(childCount - 1)
            if (label != null && MeasureSpec.getMode(widthMeasureSpec) != MeasureSpec.UNSPECIFIED) {
                val density = resources.displayMetrics.density
                val comfortable = (EXPLORE_COMPACT_FIXED_DP + EXPLORE_CHEVRON_DP + 2) * density + longestWordPx(label)
                chevron.visibility = if (MeasureSpec.getSize(widthMeasureSpec) >= comfortable) View.VISIBLE else View.GONE
            }
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        }
    }

    fun exploreRequiredCellPx(context: Context, cells: List<View>): Int {
        val density = context.resources.displayMetrics.density
        val longest = cells.flatMap { c -> (c as? android.view.ViewGroup)?.let { g -> (0 until g.childCount).map(g::getChildAt) }.orEmpty() }
            .filterIsInstance<TextView>().maxOfOrNull(::longestWordPx) ?: 0f
        return kotlin.math.ceil((EXPLORE_COMPACT_FIXED_DP + 2) * density + longest).toInt()
    }

    fun grid(context: Context, cells: List<View>, maxColumns: Int, minCellDp: Float, gapDp: Int = 8,
        requiredCellPx: ((List<View>) -> Int)? = null): View =
        AdaptiveGrid105(context, maxColumns, minCellDp, gapDp, requiredCellPx).apply { cells.forEach { addView(it) } }

    /** KPI grids: content-based breakpoint, cards compact themselves before dropping to one column. */
    fun kpiGrid(context: Context, cells: List<View>): View =
        grid(context, cells, 2, kpiMinCellDp(context.resources.configuration.fontScale)) { kpiRequiredCellPx(context, it) }

    fun lp(context: Context, top: Int = 0, bottom: Int = 0, width: Int = LinearLayout.LayoutParams.MATCH_PARENT) =
        LinearLayout.LayoutParams(width, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(context, top); bottomMargin = dp(context, bottom)
        }

}
