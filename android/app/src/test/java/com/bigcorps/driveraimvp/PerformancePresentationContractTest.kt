package com.srrotas.app

import java.io.File
import org.junit.Assert.*
import org.junit.Test

/** Architecture boundaries requested for Android Statistics stage 1. */
class PerformancePresentationContractTest {
    private fun source(name: String): String {
        val path = "app/src/main/java/com/bigcorps/driveraimvp/$name.kt"
        return generateSequence(File(System.getProperty("user.dir") ?: ".")) { it.parentFile }
            .flatMap { sequenceOf(File(it, path), File(it, "android/$path")) }
            .first { it.isFile }.readText()
    }

    @Test fun canonicalKpisCannotReadObservedOfferTotalsOrLocalAggregators() {
        val panel = source("HistoryPanel")
        val screens = source("StatisticsScreens105")
        val kpis = screens.substringAfter("fun overview(").substringBefore("Ofertas e atividade")
        assertTrue(panel.contains("StatisticsScreens105.overview(context, state(data), this)"))
        assertFalse(kpis.contains("totalOfferedFare"))
        assertFalse(kpis.contains("estimatedTotalProfit"))
        assertFalse(kpis.contains("LocalAnalytics"))
        assertTrue(kpis.contains("reading?.display()"))
        assertTrue(panel.contains("PerformanceSnapshotClient.fetch(context, days, data.from, data.to)"))
        assertTrue(panel.contains("requestGeneration != generation"))
        // vc105: daily series use exactly the same window as the snapshot.
        assertTrue(panel.contains("StatisticsSeriesClient105.fetch(context, data.from, data.to)"))
        assertFalse(panel.contains("listOf(\"Hoje\""))
    }

    @Test fun snapshotTransportIsIsolatedReadOnlyAndKeepsAuthorization() {
        val client = source("PerformanceSnapshotClient")
        assertTrue(client.contains("/api/v1/statistics?"))
        assertTrue(client.contains("section=resumo"))
        assertTrue(client.contains("requestMethod = \"GET\""))
        assertTrue(client.contains("\"Authorization\", \"Bearer"))
        assertTrue(client.contains("instanceFollowRedirects = false"))
        assertTrue(client.contains("snapshot.matches(from, to)"))
        assertFalse(client.contains("\"POST\""))
        assertFalse(client.contains("LocalAnalytics"))
        assertFalse(client.contains("Log."))
        assertTrue(source("BackendClient").contains("/api/v1/analytics?"))
    }

    @Test fun nativeShellAndRealDestinationsStayInPlace() {
        val activity = source("ConsolidatedMainActivity027037")
        assertTrue(activity.contains("SrAppHeader023(this@ConsolidatedMainActivity027037, \"Estatísticas\""))
        val panel = source("HistoryPanel")
        assertFalse(panel.contains("showAppearanceOnly()"))
        // vc107: one canonical header for every screen; the theme control lives in the shared pill.
        val header = source("SrAppHeader023")
        assertFalse(header.contains("appearanceAction"))
        assertFalse(header.contains("stacked"))
        assertTrue(header.contains("R.drawable.sr23_ic_sun_moon"))
        assertTrue(header.contains("activity.showAppearanceOnly()"))
        assertFalse(activity.contains("appearanceAction"))
        val screens = source("StatisticsScreens105")
        val ui = source("StatisticsUi105")
        assertTrue(screens.contains("setOnClickListener {"))
        assertTrue(panel.contains("PerformancePresentation.cardDestination(key)"))
        assertTrue(panel.contains("PerformancePresentation.warningAction"))
        assertTrue(panel.contains("dialog.dismiss()\n                    action()"))
        assertTrue(ui.contains("HorizontalScrollView(context)"))
        // vc106: columns come from the measured container width, scaled by the font.
        assertTrue(ui.contains("class AdaptiveGrid105") && ui.contains("fun kpiMinCellDp(fontScale: Float)"))
        assertFalse(screens.contains("screenWidthDp"))
        assertTrue(screens.contains("a.openSection(StatisticsSection026.Section.COMPARISONS)"))
        assertTrue(screens.contains("a.openSection(StatisticsSection026.Section.JOURNEYS)"))
        assertTrue(panel.contains("dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)"))
        assertFalse(panel.contains("AlertDialog" + ".Builder"))
        // vc105: "Cruzar métricas" is a real native destination now, never a decorative button.
        assertTrue(screens.contains("a.openScreen(StatsScreen105.CROSS)"))
        assertTrue(panel.contains("StatsScreen105.CROSS -> StatisticsScreens105.cross("))
        for (main in listOf(panel, screens, ui)) {
            assertFalse(main.contains("R$ 1.248"))
            assertFalse(main.contains("1.236,79"))
            assertFalse(main.contains("AlertDialog" + ".Builder"))
        }
    }
}
