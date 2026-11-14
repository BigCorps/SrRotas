package com.srrotas.app

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.widget.TextView

/** Visual classification uses only the source enum/slug; never the title or inferred demand. */
internal object RadarTwoTabsVisualV1 {
    data class Category(val label: String, val icon: Int, val color: Int)
    fun category(type: String): Category = when (type.lowercase()) {
        "music", "show" -> Category("Show", R.drawable.sr23_ic_microphone, 0xFF7C3AED.toInt())
        "sports", "arena", "stadium" -> Category("Esporte", R.drawable.sr105_ic_cup, 0xFF23854B.toInt())
        "fair_convention" -> Category("Feira", R.drawable.sr105_ic_calendar_days, 0xFFC55B12.toInt())
        "event" -> Category("Evento", R.drawable.sr105_ic_calendar, 0xFF7C3AED.toInt())
        "theatre", "arts_theatre" -> Category("Teatro", R.drawable.sr_radar_ic_theatre, 0xFFC02E83.toInt())
        "cultural" -> Category("Cultura", R.drawable.sr105_ic_star, 0xFF7C3AED.toInt())
        "family" -> Category("Família", R.drawable.sr105_ic_star, 0xFFAC7900.toInt())
        // POI slugs are supplied by the existing POI contract. No event is relabelled by its name.
        "festival" -> Category("Festival", R.drawable.sr105_ic_star, 0xFFAC7900.toInt())
        "mall" -> Category("Shopping", R.drawable.sr_radar_ic_shopping, 0xFFC02E83.toInt())
        "hospital" -> Category("Hospital", R.drawable.sr_radar_ic_hospital, 0xFFC83949.toInt())
        "hotel" -> Category("Hotel", R.drawable.sr_radar_ic_hotel, 0xFF2463C7.toInt())
        "airport" -> Category("Aeroporto", R.drawable.sr105_ic_road, 0xFF2463C7.toInt())
        "bus_terminal" -> Category("Rodoviária", R.drawable.sr105_ic_road, 0xFFC55B12.toInt())
        "mobility_hub" -> Category("Mobilidade", R.drawable.sr105_ic_road, 0xFF177E81.toInt())
        else -> Category("Local", R.drawable.sr105_ic_pin, 0xFF526976.toInt())
    }

    fun pill(context: Context, label: String, tone: Int): TextView = TextView(context).apply {
        val dark = Appearance021.isDark(context)
        val backgroundTone = blend(SrUi023.palette(context).surface, tone, if (dark) .25f else .10f)
        text = label; textSize = 10.5f; gravity = Gravity.CENTER
        setPadding(UiKit.dp(context, 8), UiKit.dp(context, 3), UiKit.dp(context, 8), UiKit.dp(context, 3))
        setTextColor(if (dark) blend(tone, Color.WHITE, .65f) else blend(tone, Color.BLACK, .18f))
        background = SrUi023.rounded(backgroundTone, 20, Color.TRANSPARENT, 0, context)
    }
    fun tabFill(context: Context) = if (Appearance021.isDark(context)) 0xFF153D59.toInt() else 0xFFE6F3FF.toInt()
    fun tabInk(context: Context) = if (Appearance021.isDark(context)) 0xFFE1F1FF.toInt() else SrUi023.palette(context).navy
    private fun blend(a: Int, b: Int, amount: Float): Int = Color.rgb(
        (Color.red(a) * (1 - amount) + Color.red(b) * amount).toInt(),
        (Color.green(a) * (1 - amount) + Color.green(b) * amount).toInt(),
        (Color.blue(a) * (1 - amount) + Color.blue(b) * amount).toInt())
}

/** Independent local visual rollout. These methods never modify runtime/assistant flags. */
internal object RadarVisualLayoutV1 {
    private fun prefs(context: Context) = context.getSharedPreferences("sr_radar_visual_v1", Context.MODE_PRIVATE)
    fun enabled(context: Context) = !prefs(context).getBoolean("legacy_layout", false)
    fun setLegacy(context: Context, legacy: Boolean) { prefs(context).edit().putBoolean("legacy_layout", legacy).apply() }
    fun initialTab(context: Context, spec: RadarDestinationSpecV1?, now: Long): RadarTwoTabsRulesV1.Tab =
        RadarTwoTabsRulesV1.Tab.values().firstOrNull { it.name == prefs(context).getString("manual_tab", null) }
            ?: if (RadarTwoTabsRulesV1.validDestination(spec, now)) RadarTwoTabsRulesV1.Tab.DESTINATION else RadarTwoTabsRulesV1.Tab.EVENTS
    fun rememberTab(context: Context, tab: RadarTwoTabsRulesV1.Tab) { prefs(context).edit().putString("manual_tab", tab.name).apply() }
}
