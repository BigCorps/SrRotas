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
        val kpis = panel.substringAfter("val titles = linkedMapOf(").substringBefore("Ofertas e atividade")
        assertFalse(kpis.contains("totalOfferedFare"))
        assertFalse(kpis.contains("estimatedTotalProfit"))
        assertFalse(kpis.contains("LocalAnalytics"))
        assertTrue(kpis.contains("reading?.display()"))
        assertTrue(panel.contains("PerformanceSnapshotClient.fetch(context, days, data.from, data.to)"))
        assertTrue(panel.contains("requestGeneration != generation"))
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
        assertTrue(panel.contains(".showAppearanceOnly()"))
        assertTrue(panel.contains("HorizontalScrollView(context)"))
        assertTrue(panel.contains("fontScale >= 1.3f"))
        assertTrue(panel.contains("selectSection(StatisticsSection026.Section.COMPARISONS)"))
        assertTrue(panel.contains("selectSection(StatisticsSection026.Section.JOURNEYS)"))
        assertTrue(panel.contains("dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)"))
        assertFalse(panel.contains("AlertDialog" + ".Builder"))
        assertFalse(panel.contains("Cruzar métricas"))
        assertFalse(panel.contains("R$ 1.248"))
    }
}
