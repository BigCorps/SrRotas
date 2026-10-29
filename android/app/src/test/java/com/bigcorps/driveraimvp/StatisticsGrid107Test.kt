package com.srrotas.app

import android.app.Application
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * vc107: two KPI columns whenever the content fits in half of the measured width.
 * Cards compact themselves (never below 14sp × font scale for values, never
 * hiding cents or the "!") before the grid drops to one column.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE) // real text metrics
@Config(sdk = [35], application = Application::class)
class StatisticsGrid107Test {
    private object NoActions : StatsActions105 {
        override fun openPeriod() {}; override fun openScreen(screen: StatsScreen105) {}; override fun openSection(section: StatisticsSection026.Section) {}
        override fun openOffers() {}; override fun back() {}; override fun cardClicked(key: String, title: String, reading: PerformanceReading?) {}
        override fun warning(key: String, title: String, reading: PerformanceReading?) {}; override fun sources() {}; override fun pendings() {}
        override fun seriesInfo(title: String, series: DailySeries105?) {}; override fun info(title: String, message: String) {}
        override fun table(title: String, subtitle: String, rows: List<Pair<String, String>>, note: String) {}
        override fun choose(title: String, options: List<Pair<String, Boolean>>, onPick: (Int) -> Unit) {}
        override fun openCross(a: CrossMetric105, b: CrossMetric105) {}; override fun retry() {}
    }

    private fun snapshot(revenue: String): PerformanceSnapshot {
        fun m(key: String, sem: String, v: Double?, f: String, st: String = "available", c: String? = "complete") = JSONObject().put("key", key)
            .put("formatted", f).put("value", v ?: JSONObject.NULL).put("semantic", sem).put("status", st).put("source", "personal_canonical+outcomes+journey_metrics")
            .put("sampleCount", 31).put("completeness", c ?: JSONObject.NULL).put("coverage", 1.0)
        val metrics = JSONObject()
            .put("realized_revenue", m("realized_revenue", "REALIZED_REVENUE", 1.0, revenue))
            .put("estimated_profit", m("estimated_profit", "ESTIMATED_COST", 842.1, "R$ 842,10"))
            .put("completed_rides", m("completed_rides", "REALIZED_RIDE", null, "—", "unavailable", null))
            .put("closed_journey_hours", m("closed_journey_hours", "MIXED", 29.4, "29,4 h"))
            .put("actual_distance", m("actual_distance", "ACTUAL_DISTANCE", 426.0, "426 km", c = "partial"))
            .put("realized_revenue_per_hour", m("realized_revenue_per_hour", "REALIZED_REVENUE", 42.45, "R$ 42,45/h"))
            .put("realized_revenue_per_km", m("realized_revenue_per_km", "REALIZED_REVENUE", 2.93, "R$ 2,93/km"))
        return PerformanceSnapshotJson.parse(JSONObject().put("snapshot", JSONObject().put("schema", PerformanceSnapshot.SCHEMA)
            .put("period", JSONObject().put("from", "2026-10-21T18:00:00Z").put("to", "2026-10-28T18:00:00Z")).put("metrics", metrics).put("limitations", JSONArray())))
    }

    data class Result(val columns: Int, val compact: Boolean, val requiredDp: Int, val minValueSp: Float, val cut: Int, val detail: String = "")

    private fun texts(v: View): List<TextView> = when (v) { is TextView -> listOf(v); is ViewGroup -> (0 until v.childCount).flatMap { texts(v.getChildAt(it)) }; else -> emptyList() }
    private fun find(v: View): StatsUi105.AdaptiveGrid105? = when (v) { is StatsUi105.AdaptiveGrid105 -> v; is ViewGroup -> (0 until v.childCount).firstNotNullOfOrNull { find(v.getChildAt(it)) }; else -> null }

    private fun render(containerDp: Int, font: Float, revenue: String = "R$ 12.345,67"): Result {
        RuntimeEnvironment.setQualifiers("w${containerDp + 28}dp-h900dp-xhdpi")
        RuntimeEnvironment.setFontScale(font)
        val activity = Robolectric.buildActivity(MainActivity::class.java).get()
        val s = StatsState105("7 dias", 7, snapshot(revenue), null, null, PerformanceProblem.CONNECTION,
            HistoryAnalytics("cloud", "", "", AnalyticsSummary(offerCount = 186, averagePerKm = 2.71, averagePerHour = 39.8, goodCount = 121), null,
                emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), null, false, ""), false, true)
        val frame = FrameLayout(activity).apply { addView(StatisticsScreens105.overview(activity, s, NoActions)) }
        val density = activity.resources.displayMetrics.density
        val w = (containerDp * density).toInt()
        frame.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        frame.layout(0, 0, w, frame.measuredHeight)
        val grid = find(frame)!!
        val cards = (0 until grid.childCount).map(grid::getChildAt)
        val money = texts(grid).filter { it.text.startsWith("R$") && it.text.any(Char::isDigit) }
        val cutViews = money.filter { it.paint.measureText(it.text.toString()) > it.width - it.paddingLeft - it.paddingRight + 1 || it.lineCount > 1 }
        val cut = cutViews.size
        val detail = cutViews.joinToString { "${it.text}: need=${it.paint.measureText(it.text.toString())} width=${it.width} lines=${it.lineCount} sp=${it.textSize / activity.resources.displayMetrics.scaledDensity}" }
        return Result(grid.columns, cards.any { (it as? StatsUi105.KpiCard105)?.compact == true },
            Math.round(grid.lastRequiredPx / density), money.minOf { it.textSize / activity.resources.displayMetrics.scaledDensity }, cut, detail)
    }

    @Test fun twoColumnsWheneverTheContentFits() {
        for (font in listOf(1f, 1.15f, 1.3f, 1.5f)) for (w in 280..700 step 20) {
            val r = render(w, font)
            assertEquals("cut at ${w}dp/$font: $r", 0, r.cut)
            if (r.columns == 2) assertTrue("value below ${StatsUi105.MIN_VALUE_SP}sp at ${w}dp/$font: $r", r.minValueSp >= StatsUi105.MIN_VALUE_SP - 0.01f)
            else assertTrue("one column only when two cannot fit (${w}dp/$font): $r", (w - 8) / 2 < r.requiredDp)
        }
    }

    @Test fun tabletSplitAndPopupWidthsNowKeepTwoColumns() {
        // Widths that vc106 forced into one column.
        assertEquals(2, render(372, 1.3f).columns)
        assertEquals(2, render(317, 1.2f).columns)
        assertEquals(2, render(360, 1.3f).columns)
        assertEquals(2, render(340, 1.15f).columns)
    }

    @Test fun approvedPhoneLayoutsStayUncompacted() {
        assertEquals(Result(2, false, render(332, 1f).requiredDp, render(332, 1f).minValueSp, 0).compact, render(332, 1f).compact)
        assertFalse(render(383, 1.3f).compact)
        assertFalse(render(652, 1f).compact)
        assertEquals(2, render(332, 1f).columns)
    }
}
