package com.srrotas.app

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
 * Visual QA of the four vc105 screens rendered by the real Android View stack
 * (Robolectric native graphics). Opt-in: `gradle :app:testDebugUnitTest -PqaScreenshots`.
 * Fixture numbers are QA data only and never reach production code.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class)
class StatisticsQa105Test {
    private val out = File(System.getProperty("sr.qa.out") ?: "build/qa-vc105").apply { mkdirs() }
    private val from = "2026-10-21T18:00:00.000Z"
    private val to = "2026-10-28T18:00:00.000Z"

    data class Device(val name: String, val qualifiers: String, val fontScale: Float = 1f)
    private val phone = Device("phone411", "w411dp-h914dp-port-420dpi")
    private val small = Device("phone360", "w360dp-h780dp-port-xhdpi")
    private val tablet = Device("smx626b-port", "w800dp-h1280dp-port-xhdpi")
    private val tabletLand = Device("smx626b-land", "w1280dp-h800dp-land-xhdpi")

    private object NoActions : StatsActions105 {
        override fun openPeriod() {}
        override fun openScreen(screen: StatsScreen105) {}
        override fun openSection(section: StatisticsSection026.Section) {}
        override fun openOffers() {}
        override fun back() {}
        override fun cardClicked(key: String, title: String, reading: PerformanceReading?) {}
        override fun warning(key: String, title: String, reading: PerformanceReading?) {}
        override fun sources() {}
        override fun pendings() {}
        override fun seriesInfo(title: String, series: DailySeries105?) {}
        override fun info(title: String, message: String) {}
        override fun table(title: String, subtitle: String, rows: List<Pair<String, String>>, note: String) {}
        override fun choose(title: String, options: List<Pair<String, Boolean>>, onPick: (Int) -> Unit) {}
        override fun openCross(a: CrossMetric105, b: CrossMetric105) {}
        override fun retry() {}
    }

    // ------------------------------------------------------------- fixtures

    private fun metric(key: String, semantic: String, value: Double?, formatted: String, status: String = "available",
        completeness: String? = "complete", coverage: Double? = 1.0) = JSONObject().apply {
        put("key", key); put("formatted", formatted); put("value", value ?: JSONObject.NULL); put("semantic", semantic)
        put("status", status); put("source", "personal_canonical+outcomes+journey_metrics"); put("sampleCount", 31)
        put("completeness", completeness ?: JSONObject.NULL); put("coverage", coverage ?: JSONObject.NULL)
    }

    private fun snapshot(empty: Boolean = false): PerformanceSnapshot {
        val metrics = JSONObject()
        if (empty) {
            PerformanceSnapshot.semantics.forEach { (key, semantic) -> metrics.put(key, metric(key, semantic, null, "—", "unavailable", null, null)) }
        } else {
            metrics.put("realized_revenue", metric("realized_revenue", "REALIZED_REVENUE", 1236.79, "R$ 1.236,79"))
            metrics.put("estimated_profit", metric("estimated_profit", "ESTIMATED_COST", 842.1, "R$ 842,10"))
            metrics.put("completed_rides", metric("completed_rides", "REALIZED_RIDE", null, "—", "unavailable", "partial", 0.0))
            metrics.put("closed_journey_hours", metric("closed_journey_hours", "MIXED", null, "—", "unavailable", "partial", 0.0))
            metrics.put("actual_distance", metric("actual_distance", "ACTUAL_DISTANCE", 426.0, "426 km", completeness = "partial"))
            metrics.put("realized_revenue_per_hour", metric("realized_revenue_per_hour", "REALIZED_REVENUE", 42.45, "R$ 42,45/h"))
            metrics.put("realized_revenue_per_km", metric("realized_revenue_per_km", "REALIZED_REVENUE", 2.93, "R$ 2,93/km"))
        }
        val body = JSONObject().put("snapshot", JSONObject().put("schema", PerformanceSnapshot.SCHEMA)
            .put("period", JSONObject().put("from", from).put("to", to)).put("metrics", metrics).put("limitations", JSONArray()))
        return PerformanceSnapshotJson.parse(body)
    }

    private val days = listOf("Ter" to "21/10", "Qua" to "22/10", "Qui" to "23/10", "Sex" to "24/10", "Sáb" to "25/10", "Dom" to "26/10", "Seg" to "27/10", "Ter" to "28/10")

    private fun series(metric: String, semantic: String, unit: String, values: List<Double?>, status: String = "complete"): JSONObject = JSONObject().apply {
        put("metric", metric); put("semantic", semantic); put("unit", unit); put("status", status)
        put("completeness", if (status == "unavailable") JSONObject.NULL else status); put("coverage", 1.0); put("sampleCount", 31)
        put("total", values.filterNotNull().sum().takeIf { status != "unavailable" } ?: JSONObject.NULL)
        put("limitations", JSONArray().put("Fixture de QA."))
        put("buckets", JSONArray().apply {
            days.forEachIndexed { i, (weekday, date) ->
                val v = values[i]
                put(JSONObject().put("key", "2026-10-${21 + i}").put("label", weekday).put("weekday", weekday).put("date", date)
                    .put("partialWindow", i == 0 || i == days.lastIndex).put("value", v ?: JSONObject.NULL)
                    .put("status", if (status == "unavailable") "unavailable" else if (v == null) "unavailable" else "complete").put("coverage", 1.0))
            }
        })
    }

    private fun dailySeries(empty: Boolean = false): StatisticsSeries105 {
        val revenue = listOf(62.4, 148.9, 109.3, 190.5, 181.2, 240.8, 266.69, 37.0)
        val body = JSONObject().put("schema", StatisticsSeries105.SCHEMA).put("time_zone", "America/Sao_Paulo")
            .put("period", JSONObject().put("from", from).put("to", to))
            .put("series", JSONObject()
                .put("realized_revenue", if (empty) series("realized_revenue", "REALIZED_REVENUE", "BRL", List(8) { null }, "unavailable") else series("realized_revenue", "REALIZED_REVENUE", "BRL", revenue))
                .put("completed_rides", series("completed_rides", "REALIZED_RIDE", "rides", if (empty) List(8) { null } else listOf(2.0, 4.0, 3.0, 5.0, 4.0, 6.0, 6.0, 1.0), if (empty) "unavailable" else "complete"))
                .put("closed_journey_hours", series("closed_journey_hours", "MIXED", "hours", if (empty) List(8) { null } else listOf(1.5, 4.1, 3.2, 5.4, 4.6, 7.8, 6.9, 1.0), if (empty) "unavailable" else "complete"))
                .put("actual_distance", series("actual_distance", "ACTUAL_DISTANCE", "km", if (empty) List(8) { null } else listOf(22.0, 51.0, null, 74.0, 66.0, 98.0, 92.0, 23.0), if (empty) "unavailable" else "partial"))
                .put("observed_offers", series("observed_offers", "OBSERVED_OFFER", "offers", listOf(9.0, 24.0, 18.0, 31.0, 27.0, 36.0, 33.0, 8.0))))
            .put("rides", JSONObject().put("status", if (empty) "unavailable" else "complete").put("completed", if (empty) 0 else 31).put("withKnownFare", if (empty) 0 else 31)
                .put("averageFare", if (empty) JSONObject.NULL else 39.9).put("maxFare", if (empty) JSONObject.NULL else 112.3).put("minFare", if (empty) JSONObject.NULL else 8.9))
            .put("comparison", JSONObject().put("eligible", !empty).put("changePct", if (empty) JSONObject.NULL else 12.4).put("previousTotal", if (empty) JSONObject.NULL else 1100.35))
            .put("insight", if (empty) JSONObject.NULL else JSONObject().put("text", "Suas tarifas de corridas concluídas foram 12,4% maiores que nos 7 dias anteriores. Dia de maior valor: segunda-feira, 27/10 (R$ 266,69).").put("basis", "comparison+daily"))
        if (!empty) body.put("breakdowns", JSONObject().put("service_type", JSONArray().put(JSONObject().put("label", "UberX").put("rides", 24).put("ridesWithFare", 24).put("revenue", 902.4).put("averageFare", 37.6))))
        return StatisticsSeries105.parse(body)
    }

    private fun offers(empty: Boolean = false): HistoryAnalytics {
        val summary = if (empty) AnalyticsSummary() else AnalyticsSummary(offerCount = 186, averagePerKm = 2.71, averagePerHour = 39.8, goodCount = 121, regularCount = 40, badCount = 25)
        val hours = if (empty) emptyList() else (6..22 step 2).map { h -> AnalyticsPoint("$h", "${h.toString().padStart(2, '0')}h", 6 + (h * 7) % 19, null, 28.0 + (h * 13) % 21, null, null) }
        return HistoryAnalytics("cloud", from, to, summary, null, emptyList(), hours, emptyList(), emptyList(), emptyList(), null, false, "")
    }

    private fun state(kind: String) = when (kind) {
        "carregando" -> StatsState105("7 dias", 7, null, PerformanceProblem.LOADING, null, PerformanceProblem.LOADING, null, true, false)
        "sem-dados" -> StatsState105("7 dias", 7, snapshot(empty = true), null, dailySeries(empty = true), null, offers(empty = true), false, true)
        "sem-sessao" -> StatsState105("7 dias", 7, null, PerformanceProblem.NO_SESSION, null, PerformanceProblem.NO_SESSION, offers(), false, false)
        else -> StatsState105("7 dias", 7, snapshot(), null, dailySeries(), null, offers(), false, true)
    }

    // ------------------------------------------------------------- rendering

    private fun activity(device: Device, dark: Boolean): MainActivity {
        RuntimeEnvironment.setQualifiers(device.qualifiers)
        RuntimeEnvironment.setFontScale(device.fontScale)
        val activity = Robolectric.buildActivity(MainActivity::class.java).get()
        Strategy021Store.saveAppTheme(activity, if (dark) "dark" else "light")
        return activity
    }

    private fun shell(activity: MainActivity, body: View): Pair<LinearLayout, LinearLayout> {
        val holder = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER_HORIZONTAL
            setPadding(SrUi023.dp(activity, 14), SrUi023.dp(activity, 10), SrUi023.dp(activity, 14), SrUi023.dp(activity, 18))
            // Same 680dp clamp as HistoryPanel.onMeasure.
            addView(object : FrameLayout(activity) {
                override fun onMeasure(w: Int, h: Int) {
                    val max = UiKit.dp(context, 680)
                    val width = MeasureSpec.getSize(w)
                    super.onMeasure(if (width > max) MeasureSpec.makeMeasureSpec(max, MeasureSpec.EXACTLY) else w, h)
                }
            }.apply { addView(body) }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        }
        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(SrUi023.palette(activity).background)
            addView(SrAppHeader023(activity, "Estatísticas", ""))
            addView(ScrollView(activity).apply { addView(holder) }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
            addView(SrBottomNav023(activity, SrBottomNav023.Route.HISTORY) {}, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                setMargins(SrUi023.dp(activity, 10), 0, SrUi023.dp(activity, 10), SrUi023.dp(activity, 7))
            })
        }
        return root to holder
    }

    private fun save(view: View, w: Int, h: Int, name: String) {
        view.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY))
        view.layout(0, 0, w, h)
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        FileOutputStream(File(out, "$name.png")).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun build(activity: MainActivity, screen: StatsScreen105, s: StatsState105): View = when (screen) {
        StatsScreen105.OVERVIEW -> StatisticsScreens105.overview(activity, s, NoActions)
        StatsScreen105.REVENUE -> StatisticsScreens105.revenue(activity, s, NoActions)
        StatsScreen105.LIBRARY -> StatisticsScreens105.library(activity, s, NoActions)
        StatsScreen105.CROSS -> StatisticsScreens105.cross(activity, s, NoActions, CrossMetric105.REVENUE, CrossMetric105.HOURS) { _, _ -> }
        else -> StatisticsScreens105.hours(activity, s, NoActions)
    }

    /** Viewport capture (first screen as the driver sees it) + full scroll capture. */
    private fun capture(device: Device, dark: Boolean, screen: StatsScreen105, kind: String, full: Boolean = true): View {
        val activity = activity(device, dark)
        val dm = activity.resources.displayMetrics
        val w = (activity.resources.configuration.screenWidthDp * dm.density).toInt()
        val h = (activity.resources.configuration.screenHeightDp * dm.density).toInt()
        val body = build(activity, screen, state(kind))
        val (root, _) = shell(activity, body)
        val name = "${screen.name.lowercase()}-$kind-${device.name}${if (device.fontScale != 1f) "-font${device.fontScale}" else ""}-${if (dark) "escuro" else "claro"}"
        save(root, w, h, name)
        if (full) {
            val (fullRoot, holder) = shell(activity(device, dark), build(activity, screen, state(kind)))
            holder.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
            val extra = holder.measuredHeight - (h - SrUi023.dp(activity, 150))
            if (extra > 0) save(fullRoot, w, h + extra, "$name-completa")
        }
        return root
    }

    private fun texts(view: View): List<TextView> = when (view) {
        is TextView -> listOf(view)
        is ViewGroup -> (0 until view.childCount).flatMap { texts(view.getChildAt(it)) }
        else -> emptyList()
    }

    @Test fun capturesFourScreensLightDarkAndStates() {
        for (dark in listOf(false, true)) {
            for (screen in listOf(StatsScreen105.OVERVIEW, StatsScreen105.REVENUE, StatsScreen105.LIBRARY, StatsScreen105.CROSS)) {
                capture(phone, dark, screen, "disponivel")
            }
            capture(phone, dark, StatsScreen105.OVERVIEW, "carregando", full = false)
            capture(phone, dark, StatsScreen105.OVERVIEW, "sem-dados", full = false)
            capture(phone, dark, StatsScreen105.REVENUE, "sem-dados", full = false)
            capture(phone, dark, StatsScreen105.CROSS, "carregando", full = false)
        }
        capture(phone, false, StatsScreen105.OVERVIEW, "sem-sessao", full = false)
        capture(phone, false, StatsScreen105.HOURS, "disponivel", full = false)
        capture(phone, false, StatsScreen105.CROSS, "sem-dados", full = false)
        capture(phone, false, StatsScreen105.LIBRARY, "sem-dados", full = false)
    }

    @Test fun capturesTabletSmallPhoneAndLargeFont() {
        for (screen in listOf(StatsScreen105.OVERVIEW, StatsScreen105.REVENUE, StatsScreen105.LIBRARY, StatsScreen105.CROSS)) {
            capture(tablet, false, screen, "disponivel", full = false)
            capture(small, false, screen, "disponivel", full = false)
        }
        capture(tablet, true, StatsScreen105.OVERVIEW, "disponivel", full = false)
        capture(tabletLand, false, StatsScreen105.OVERVIEW, "disponivel", full = false)
        capture(phone.copy(fontScale = 1.3f), false, StatsScreen105.OVERVIEW, "disponivel")
        capture(small.copy(fontScale = 1.3f), false, StatsScreen105.OVERVIEW, "disponivel")
    }

    @Test fun moneyStaysOnOneLineWithCentsAcrossWidthsAndFontScales() {
        val values = listOf(9.9, 999.99, 1236.79, 12345.67, 123456.78)
        for (device in listOf(small, small.copy(fontScale = 1.3f), phone, tablet)) {
            val activity = activity(device, false)
            val dm = activity.resources.displayMetrics
            val w = (activity.resources.configuration.screenWidthDp * dm.density).toInt()
            val cells = values.map { v ->
                StatisticsScreens105.kpiCard(activity, StatisticsScreens105.kpis.first(), StatsFormat105.money(v), false, true, {}, {})
            }
            val grid = StatsUi105.grid(activity, cells, 2, StatsUi105.kpiMinCellDp(device.fontScale))
            grid.measure(View.MeasureSpec.makeMeasureSpec(w - SrUi023.dp(activity, 28), View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
            grid.layout(0, 0, grid.measuredWidth, grid.measuredHeight)
            val moneyViews = texts(grid).filter { it.text.startsWith("R$") }
            assertEquals(values.size, moneyViews.size)
            moneyViews.forEachIndexed { i, tv ->
                assertEquals("${device.name}/${device.fontScale}: ${tv.text}", 1, tv.lineCount)
                assertTrue(tv.text.toString().endsWith(StatsFormat105.money(values[i]).takeLast(3)))
                val needed = tv.paint.measureText(tv.text.toString())
                assertTrue("${tv.text} cut at ${device.name}: $needed > ${tv.width}", needed <= tv.width - tv.paddingLeft - tv.paddingRight + 1)
            }
        }
    }

    @Test fun overviewFirstViewportHoldsEightKpisAndSingleBanner() {
        val activity = activity(phone, false)
        val root = capture(phone, false, StatsScreen105.OVERVIEW, "disponivel", full = false)
        val all = texts(root).map { it.text.toString() }
        StatisticsScreens105.kpis.forEach { assertTrue(it.title, all.contains(it.title)) }
        assertTrue(all.contains("3 pendências nos dados"))
        assertFalse(all.any { it.contains("Dados parciais") && it.contains("!") })
        assertFalse(all.any { it.contains("Seu desempenho") || it.contains("Ofertas observadas · nuvem") })
        listOf("Cruzar\nmétricas", "Comparar\nperíodos", "Todos os\nrelatórios").forEach { assertTrue(it, all.contains(it)) }
        // The eight KPI cards and the banner must end above the bottom navigation.
        val banner = texts(root).first { it.text == "3 pendências nos dados" }
        val loc = IntArray(2).also { banner.getLocationInWindow(it) }
        val navTop = root.height - SrUi023.dp(activity, 92)
        assertTrue("banner at ${loc[1]} nav $navTop", loc[1] + banner.height < navTop)
    }

    @Test fun panelNavigationBackAndPeriodChange() {
        val activity = activity(phone, false)
        val panel = HistoryPanel(activity)
        var depth = false
        panel.onDepthChanged = { depth = it }
        assertFalse(panel.hasInnerScreen())
        panel.openScreen(StatsScreen105.LIBRARY)
        panel.openScreen(StatsScreen105.CROSS)
        assertTrue(depth)
        assertTrue(panel.handleBack())
        assertTrue(panel.hasInnerScreen()) // back to library
        assertTrue(panel.handleBack())
        assertFalse(panel.hasInnerScreen())
        assertFalse(panel.handleBack())
        panel.openSection(StatisticsSection026.Section.COMPARISONS)
        assertTrue(panel.hasInnerScreen())
        assertTrue(panel.handleBack())
        assertFalse(depth)
        panel.cardClicked("realized_revenue", "Tarifas de corridas concluídas", null)
        assertTrue(panel.hasInnerScreen())
        assertTrue(texts(panel).any { it.text == "Tarifas de corridas concluídas" })
    }
}
