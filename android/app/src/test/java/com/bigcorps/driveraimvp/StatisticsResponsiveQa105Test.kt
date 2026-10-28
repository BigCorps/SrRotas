package com.srrotas.app

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream

/**
 * P2 matrix: the Visão rápida grid inside containers of a given width while the
 * window configuration may differ (tablet, split screen, freeform). Uses only the
 * public overview builder, so the same file renders vc105 (before) and vc106 (after).
 * Opt-in: -PqaScreenshots. Writes PNGs + a JSON summary.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class)
class StatisticsResponsiveQa105Test {
    private val out = File(System.getenv("SR_RESPONSIVE_OUT") ?: (System.getProperty("sr.qa.out") ?: "build/qa-vc105") + "/responsive").apply { mkdirs() }

    private object NoActions : StatsActions105 {
        override fun openPeriod() {}; override fun openScreen(screen: StatsScreen105) {}; override fun openSection(section: StatisticsSection026.Section) {}
        override fun openOffers() {}; override fun back() {}; override fun cardClicked(key: String, title: String, reading: PerformanceReading?) {}
        override fun warning(key: String, title: String, reading: PerformanceReading?) {}; override fun sources() {}; override fun pendings() {}
        override fun seriesInfo(title: String, series: DailySeries105?) {}; override fun info(title: String, message: String) {}
        override fun table(title: String, subtitle: String, rows: List<Pair<String, String>>, note: String) {}
        override fun choose(title: String, options: List<Pair<String, Boolean>>, onPick: (Int) -> Unit) {}
        override fun openCross(a: CrossMetric105, b: CrossMetric105) {}; override fun retry() {}
    }

    private fun snapshot(): PerformanceSnapshot {
        fun m(key: String, sem: String, v: Double?, f: String, st: String = "available", c: String? = "complete") = JSONObject().put("key", key)
            .put("formatted", f).put("value", v ?: JSONObject.NULL).put("semantic", sem).put("status", st).put("source", "personal_canonical+outcomes+journey_metrics")
            .put("sampleCount", 31).put("completeness", c ?: JSONObject.NULL).put("coverage", 1.0)
        val metrics = JSONObject()
            .put("realized_revenue", m("realized_revenue", "REALIZED_REVENUE", 12345.67, "R$ 12.345,67"))
            .put("estimated_profit", m("estimated_profit", "ESTIMATED_COST", 842.1, "R$ 842,10"))
            .put("completed_rides", m("completed_rides", "REALIZED_RIDE", null, "—", "unavailable", null))
            .put("closed_journey_hours", m("closed_journey_hours", "MIXED", 29.4, "29,4 h"))
            .put("actual_distance", m("actual_distance", "ACTUAL_DISTANCE", 426.0, "426 km", c = "partial"))
            .put("realized_revenue_per_hour", m("realized_revenue_per_hour", "REALIZED_REVENUE", 42.45, "R$ 42,45/h"))
            .put("realized_revenue_per_km", m("realized_revenue_per_km", "REALIZED_REVENUE", 2.93, "R$ 2,93/km"))
        return PerformanceSnapshotJson.parse(JSONObject().put("snapshot", JSONObject().put("schema", PerformanceSnapshot.SCHEMA)
            .put("period", JSONObject().put("from", "2026-10-21T18:00:00Z").put("to", "2026-10-28T18:00:00Z")).put("metrics", metrics).put("limitations", JSONArray())))
    }

    data class Scenario(val name: String, val windowQualifiers: String, val fontScale: Float, val containerDp: Int)

    private fun topIn(v: View, root: View): Int { var y = 0; var c: View? = v; while (c != null && c !== root) { y += c.top; c = c.parent as? View }; return y }
    private fun texts(v: View): List<TextView> = when (v) { is TextView -> listOf(v); is ViewGroup -> (0 until v.childCount).flatMap { texts(v.getChildAt(it)) }; else -> emptyList() }

    @Test fun matrix() {
        val scenarios = mutableListOf<Scenario>()
        for (font in listOf(1f, 1.15f, 1.3f)) for (w in 300..700 step 40) scenarios += Scenario("container${w}-font$font", "w${w + 28}dp-h900dp-port-xhdpi", font, w)
        // Window wider than the usable container (tablet pane / freeform / split with side content).
        scenarios += Scenario("tablet-window800-container330", "w800dp-h1280dp-port-xhdpi", 1f, 330)
        scenarios += Scenario("tablet-window800-container360-font1.3", "w800dp-h1280dp-port-xhdpi", 1.3f, 360)
        scenarios += Scenario("split-landscape-window640-container612", "w640dp-h752dp-land-xhdpi", 1f, 612)
        scenarios += Scenario("split-portrait-window400-container372-font1.3", "w400dp-h600dp-port-xhdpi", 1.3f, 372)
        scenarios += Scenario("phone345-font1.2", "w345dp-h760dp-port-xhdpi", 1.2f, 317)
        val summary = JSONArray()
        for (sc in scenarios) {
            RuntimeEnvironment.setQualifiers(sc.windowQualifiers)
            RuntimeEnvironment.setFontScale(sc.fontScale)
            val activity = Robolectric.buildActivity(MainActivity::class.java).get()
            Strategy021Store.saveAppTheme(activity, "light")
            val density = activity.resources.displayMetrics.density
            val s = StatsState105("7 dias", 7, snapshot(), null, null, PerformanceProblem.CONNECTION,
                HistoryAnalytics("cloud", "", "", AnalyticsSummary(offerCount = 186, averagePerKm = 2.71, averagePerHour = 39.8, goodCount = 121), null,
                    emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), null, false, ""), false, true)
            val body = StatisticsScreens105.overview(activity, s, NoActions)
            val frame = FrameLayout(activity).apply { setBackgroundColor(SrUi023.palette(activity).background); addView(body) }
            val w = (sc.containerDp * density).toInt()
            frame.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
            frame.layout(0, 0, w, frame.measuredHeight)
            val bitmap = Bitmap.createBitmap(w, frame.measuredHeight, Bitmap.Config.ARGB_8888)
            frame.draw(Canvas(bitmap))
            FileOutputStream(File(out, "${sc.name}.png")).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            // First two KPI titles are unique on screen ("Corridas" also names a chip).
            val titles = listOf("Tarifas concluídas", "Resultado estimado")
            val kpiLabels = texts(frame).filter { it.text.toString() in titles }
            // Cards are the label's third ancestor; a row shares the card top.
            val cards = kpiLabels.map { ((it.parent as View).parent as View).parent as View }
            val firstRowTop = cards.minOf { topIn(it, frame) }
            val perRow = cards.count { topIn(it, frame) == firstRowTop }
            val money = texts(frame).filter { it.text.startsWith("R$") && it.text.any(Char::isDigit) }
            val cut = money.filter { it.paint.measureText(it.text.toString()) > it.width - it.paddingLeft - it.paddingRight + 1 || it.lineCount > 1 }
            val minSp = money.minOf { it.textSize / activity.resources.displayMetrics.scaledDensity }
            val cardWidthDp = cards.first().width / density
            summary.put(JSONObject().put("scenario", sc.name).put("window", sc.windowQualifiers).put("font", sc.fontScale.toDouble()).put("container_dp", sc.containerDp)
                .put("kpi_columns", perRow).put("kpi_card_dp", Math.round(cardWidthDp)).put("min_money_sp", Math.round(minSp * 10) / 10.0)
                .put("money_cut", JSONArray(cut.map { it.text.toString() })))
        }
        File(out, "summary.json").writeText(summary.toString(2))
    }
}
