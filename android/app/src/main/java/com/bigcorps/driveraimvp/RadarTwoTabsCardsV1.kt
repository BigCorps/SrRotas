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
            val row = LinearLayout(context).apply { orientation = HORIZONTAL; isBaselineAligned = false }
            for ((index, item) in chunk.withIndex()) row.addView(card(item), LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply {
                if (index > 0) marginStart = UiKit.dp(context, 10)
            })
            if (chunk.size < count) row.addView(View(context), LayoutParams(0, 1, 1f).apply { marginStart = UiKit.dp(context, 10) })
            addView(row, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { bottomMargin = UiKit.dp(context, 10) })
        }
    }
    private fun card(item: RadarTwoTabsRulesV1.Card): View {
        val p = SrUi023.palette(context)
        val linkColor = if (Appearance021.isDark(context)) p.blueBright else p.navy
        return LinearLayout(context).apply {
            orientation = VERTICAL
            tag = "radar-card:${item.id}"
            setPadding(UiKit.dp(context, 12), UiKit.dp(context, 10), UiKit.dp(context, 12), UiKit.dp(context, 4))
            background = SrUi023.rounded(p.surface, 16, if (item.id == selectedId) p.blue else p.outline,
                if (item.id == selectedId) 2 else 1, context)
            val heading = LinearLayout(context).apply { orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            heading.addView(SrUi023.icon(context, icon(item.type), p.blue, 24), LayoutParams(UiKit.dp(context, 28), UiKit.dp(context, 28)))
            heading.addView(SrUi023.title(context, item.title, 14f), LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = UiKit.dp(context, 7) })
            addView(heading)
            addView(UiKit.margin(SrUi023.title(context, RadarTwoTabsRulesV1.distanceLabel(item), 12f).apply { setTextColor(linkColor) }, top = 7))
            addView(UiKit.margin(SrUi023.body(context, item.description, 12f), top = 4))
            item.provenance?.let { addView(UiKit.margin(SrUi023.body(context, it, 10.5f), top = 5)) }
            // Details remain in the card, never a third tab or another catalogue.
            if (item.id == selectedId) item.facts.forEach { addView(UiKit.margin(SrUi023.body(context, it, 11f), top = 4)) }
            isClickable = true; isFocusable = true; isSelected = item.id == selectedId
            contentDescription = "${item.title}, ${RadarTwoTabsRulesV1.distanceLabel(item)}. Toque para detalhes."
            setOnClickListener { onSelect(item.id) }
            if (!demo && RadarExternalMapsV1.available(item.lat, item.lng, item.mapsLabel)) {
                addView(TextView(context).apply {
                    text = "Ver no Maps"; textSize = 12f; setTextColor(linkColor)
                    minHeight = UiKit.dp(context, 48); gravity = Gravity.CENTER_VERTICAL
                    setPadding(0, 0, UiKit.dp(context, 12), 0)
                    isClickable = true; isFocusable = true
                    contentDescription = "Ver ${item.title} no Maps, abre fora do Sr. Rotas"
                    setOnClickListener { RadarExternalMapsV1.open(context, item.lat, item.lng, item.mapsLabel) }
                })
            }
        }
    }
    private fun icon(type: String): Int = when (type.lowercase()) {
        "music", "show" -> R.drawable.sr23_ic_microphone
        "sports" -> R.drawable.sr105_ic_cup
        "theatre", "cultural", "arts_theatre" -> R.drawable.sr105_ic_star
        "fair_convention", "family", "event" -> R.drawable.sr105_ic_calendar_days
        "mall" -> R.drawable.sr23_ic_credit_card
        "airport", "bus_terminal", "mobility_hub" -> R.drawable.sr105_ic_road
        else -> R.drawable.sr105_ic_pin
    }
}
