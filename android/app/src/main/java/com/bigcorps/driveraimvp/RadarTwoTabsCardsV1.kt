package com.srrotas.app

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView

/** Compact native cards. Width belongs to this container, including split-screen, not the device. */
internal class RadarTwoTabsCardsV1(context: Context) : LinearLayout(context) {
    private var items: List<RadarTwoTabsRulesV1.Card> = emptyList()
    private var selectedId: String? = null
    private var demo = false
    private var columns = 0
    private var onSelect: (String) -> Unit = {}
    init { orientation = VERTICAL }

    fun submit(value: List<RadarTwoTabsRulesV1.Card>, selected: String?, preview: Boolean, select: (String) -> Unit) {
        items = value; selectedId = selected; demo = preview; onSelect = select
        rebuild(if (width > 0) width else (resources.configuration.screenWidthDp * resources.displayMetrics.density).toInt())
    }
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w != oldw && w > 0) post { if (width == w) rebuild(w) }
    }
    private fun rebuild(w: Int) {
        val count = RadarTwoTabsRulesV1.columns(w / resources.displayMetrics.density, resources.configuration.fontScale)
        columns = count
        removeAllViews()
        for (chunk in items.chunked(count)) {
            val row = object : LinearLayout(context) {
                override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                    super.onMeasure(widthMeasureSpec, heightMeasureSpec)
                    // Measure content first: MATCH_PARENT in a ScrollView can expand to the viewport.
                    // Then equalize siblings, keeping their Maps footers aligned without tall empty cards.
                    val tallest = (0 until childCount).maxOfOrNull { getChildAt(it).measuredHeight } ?: 0
                    for (i in 0 until childCount) {
                        val child = getChildAt(i)
                        if (child.measuredHeight != tallest) child.measure(
                            View.MeasureSpec.makeMeasureSpec(child.measuredWidth, View.MeasureSpec.EXACTLY),
                            View.MeasureSpec.makeMeasureSpec(tallest, View.MeasureSpec.EXACTLY))
                    }
                }
            }.apply { orientation = HORIZONTAL; isBaselineAligned = false }
            for ((index, item) in chunk.withIndex()) row.addView(card(item), LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply {
                if (index > 0) marginStart = UiKit.dp(context, 10)
            })
            if (chunk.size < count) row.addView(View(context), LayoutParams(0, 1, 1f).apply { marginStart = UiKit.dp(context, 10) })
            addView(row, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { bottomMargin = UiKit.dp(context, 10) })
        }
    }
    private fun card(item: RadarTwoTabsRulesV1.Card): View {
        val p = SrUi023.palette(context)
        val category = RadarTwoTabsVisualV1.category(item.type)
        val linkColor = RadarTwoTabsVisualV1.tabInk(context)
        return LinearLayout(context).apply {
            orientation = VERTICAL
            tag = "radar-card:${item.id}"
            setPadding(UiKit.dp(context, 12), UiKit.dp(context, 12), UiKit.dp(context, 12), 0)
            background = SrUi023.rounded(p.surface, 16, if (item.id == selectedId) p.blue else p.outline,
                if (item.id == selectedId) 2 else 1, context)
            val heading = LinearLayout(context).apply { orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            val icon = android.widget.FrameLayout(context).apply {
                background = SrUi023.rounded(category.color, 10, Color.TRANSPARENT, 0, context)
                addView(SrUi023.icon(context, category.icon, Color.WHITE, 25),
                    android.widget.FrameLayout.LayoutParams(UiKit.dp(context, 25), UiKit.dp(context, 25), Gravity.CENTER))
            }
            heading.addView(icon, LayoutParams(UiKit.dp(context, 42), UiKit.dp(context, 42)))
            heading.addView(LinearLayout(context).apply {
                orientation = VERTICAL
                addView(SrUi023.title(context, item.title, 14.5f))
                addView(RadarTwoTabsVisualV1.pill(context, category.label, category.color),
                    LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT).apply { topMargin = UiKit.dp(context, 4) })
            }, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = UiKit.dp(context, 10) })
            addView(heading)
            addView(UiKit.margin(SrUi023.title(context, RadarTwoTabsRulesV1.distanceLabel(item), 13f).apply { setTextColor(linkColor) }, top = 9))
            if (item.description.isNotBlank()) addView(UiKit.margin(SrUi023.body(context, item.description, 11.5f), top = 5))
            item.timing?.let { addView(UiKit.margin(SrUi023.body(context, it, 11.5f), top = 4)) }
            item.provenance?.let { addView(UiKit.margin(SrUi023.body(context, it, 10f), top = 5)) }
            if (item.id == selectedId) item.facts.forEach { addView(UiKit.margin(SrUi023.body(context, it, 11f), top = 4)) }
            isClickable = true; isFocusable = true; isSelected = item.id == selectedId
            contentDescription = "${item.title}, ${category.label}, ${RadarTwoTabsRulesV1.distanceLabel(item)}. Toque para detalhes."
            setOnClickListener { onSelect(item.id) }
            addView(View(context), LayoutParams(1, 0, 1f))
            if (!demo && RadarExternalMapsV1.available(item.lat, item.lng, item.mapsLabel)) {
                addView(View(context).apply { setBackgroundColor(p.outline) }, LayoutParams(LayoutParams.MATCH_PARENT, UiKit.dp(context, 1)).apply { topMargin = UiKit.dp(context, 10) })
                addView(LinearLayout(context).apply {
                    orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                    minimumHeight = UiKit.dp(context, 48); isClickable = true; isFocusable = true
                    contentDescription = "Ver ${item.title} no Maps, abre fora do Sr. Rotas"
                    addView(TextView(context).apply { text = "Ver no Maps"; textSize = 12f; setTextColor(linkColor) }, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
                    addView(SrUi023.icon(context, R.drawable.sr23_ic_chevron_right, linkColor, 16))
                    setOnClickListener { RadarExternalMapsV1.open(context, item.lat, item.lng, item.mapsLabel) }
                }, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
            } else addView(View(context), LayoutParams(1, UiKit.dp(context, 12)))
        }
    }
}
