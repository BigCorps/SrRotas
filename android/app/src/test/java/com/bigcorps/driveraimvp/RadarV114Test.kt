package com.srrotas.app

import android.app.Application
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.time.Instant
import java.time.ZoneId

/** vc114 (issue #11) — Radar com duas abas: semântica, estados, grade responsiva, rollback. Fixtures fictícias. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class RadarV114Test {
    private val app get() = RuntimeEnvironment.getApplication()
    private val zone = ZoneId.of("America/Sao_Paulo")
    private val now = Instant.parse("2026-11-13T15:00:00Z") // 12:00 em São Paulo, sexta

    @Before fun clean() { app.getSharedPreferences("sr_radar_v114", 0).edit().clear().commit() }

    private fun opp(id: String, type: String, km: Double, potential: String = "insufficient", ctx: RadarContextualContextV1? = null) =
        RadarContextualOpportunityV1(id, "poi-$id", "Lugar $id", null, type, -23.55, -46.63, km, "2026-11-13T15:30:00Z",
            null, null, null, 0.7, 0.5, potential, "headline", null, null, ctx, emptyList())

    private fun event(id: String, type: String, km: Double, start: String, end: String, conf: Double = 0.9) =
        EventRadarOpportunity026(id, type, "Evento $id", "Arena Exemplo", "Rua Exemplo, 1", start, end, end, end, km, "fixture", conf, null)

    // ---------------------------------------------------------------- semântica (nada inventado)
    @Test fun reasonsKeepEvidenceKindsDistinct() {
        assertEquals("Alta incidência observada", RadarV114.opportunityReason(opp("a", "mall", 1.0, "high"), false))
        assertEquals("Shopping próximo", RadarV114.opportunityReason(opp("b", "mall", 1.0, "medium"), false))
        assertEquals("Hospital próximo", RadarV114.opportunityReason(opp("c", "hospital", 1.0, "insufficient"), false))
        val ctx = RadarContextualContextV1("e", "Show", "music", "2026-11-14T01:00:00Z", "2026-11-14T01:45:00Z", 0.9)
        assertEquals("Evento registrado · saída 22:00–22:45", RadarV114.opportunityReason(opp("d", "venue", 1.0, "high", ctx), true, zone))
        // Contexto de evento NÃO elegível nunca vira "evento"; proximidade nunca vira incidência.
        assertEquals("Local de eventos próximo", RadarV114.opportunityReason(opp("e", "venue", 1.0, "low", ctx), false))
        assertFalse(RadarV114.opportunityReason(opp("f", "hotel", 1.0, "medium"), false).contains("incidência"))
    }

    @Test fun categoriesAndDistanceAlwaysNameTheirReference() {
        assertEquals("Shopping", RadarV114.placeCategory("shopping_mall", false).label)
        assertEquals("Hotel", RadarV114.placeCategory("hotel", false).label)
        assertEquals("Evento", RadarV114.placeCategory("hospital", true).label)
        assertEquals("Show", RadarV114.eventCategory("music").label)
        assertEquals("Esporte", RadarV114.eventCategory("sports").label)
        assertEquals("2,4 km do destino", RadarV114.distance(2.4, true))
        assertEquals("1,8 km de você", RadarV114.distance(1.8, false))
        assertNull(RadarV114.distance(Double.NaN, false))
    }

    @Test fun dayTimeOnlyFromValidTimestamps() {
        assertEquals("Hoje · 20h00", RadarV114.dayTime("2026-11-13T23:00:00Z", now, zone))
        assertEquals("Amanhã · 09h00", RadarV114.dayTime("2026-11-14T12:00:00Z", now, zone))
        assertEquals("Dom · 18h00", RadarV114.dayTime("2026-11-15T21:00:00Z", now, zone))
        assertNull(RadarV114.dayTime("", now, zone)); assertNull(RadarV114.dayTime(null, now, zone)); assertNull(RadarV114.dayTime("amanhã", now, zone))
    }

    @Test fun eventsAreFutureOrOngoingNearestFirst() {
        val list = listOf(
            event("far", "music", 5.6, "2026-11-13T21:00:00Z", "2026-11-14T00:00:00Z"),
            event("ended", "music", 0.5, "2026-11-13T10:00:00Z", "2026-11-13T13:00:00Z"),
            event("near", "sports", 1.8, "2026-11-13T22:30:00Z", "2026-11-14T01:00:00Z"),
            event("lowconf", "family", 1.0, "2026-11-13T22:30:00Z", "2026-11-14T01:00:00Z", conf = 0.3),
        )
        assertEquals(listOf("near", "far"), RadarV114.visibleEvents(list, now).map { it.id })
        val noEnd = listOf(event("pastNoEnd", "music", 1.0, "2026-11-13T10:00:00Z", ""), event("futureNoEnd", "music", 1.2, "2026-11-13T20:00:00Z", ""))
        assertEquals(listOf("futureNoEnd"), RadarV114.visibleEvents(noEnd, now).map { it.id })
    }

    @Test fun twoColumnsOnlyWhenTheyFit() {
        assertEquals(2, RadarV114.columns(680f, 1f))   // SM-X626B, janela do app
        assertEquals(2, RadarV114.columns(387f, 1f))   // celular 411dp
        assertEquals(1, RadarV114.columns(320f, 1f))   // janela estreita
        assertEquals(1, RadarV114.columns(387f, 1.3f)) // fonte grande no celular
        assertEquals(2, RadarV114.columns(680f, 1.5f))
    }

    @Test fun distanceReferenceIsLegitimateOrAbsent() {
        assertEquals(RadarV114.Reference.YOU, RadarV114.reference(true, true))
        assertEquals(RadarV114.Reference.DESTINATION, RadarV114.reference(false, true))
        assertEquals(RadarV114.Reference.NONE, RadarV114.reference(false, false))
        assertTrue(RadarV114.staleSource("2026-11-11T10:00:00Z", now)); assertFalse(RadarV114.staleSource("2026-11-13T10:00:00Z", now))
        assertFalse(RadarV114.staleSource(null, now))
    }

    // ---------------------------------------------------------------- tela
    private fun texts(v: View): List<String> = when (v) {
        is TextView -> listOf(v.text.toString())
        is ViewGroup -> (0 until v.childCount).flatMap { texts(v.getChildAt(it)) }
        else -> emptyList()
    }
    private fun find(v: View, pred: (View) -> Boolean): List<View> =
        (if (pred(v)) listOf(v) else emptyList()) + if (v is ViewGroup) (0 until v.childCount).flatMap { find(v.getChildAt(it), pred) } else emptyList()
    private fun layout(v: View, wDp: Int) {
        val w = (wDp * v.resources.displayMetrics.density).toInt()
        v.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        v.layout(0, 0, w, v.measuredHeight)
    }
    private val spec = RadarDestinationSpecV1(-23.56, -46.65, "2099-01-01T00:00:00Z", "Av. Exemplo, 100", "offer-1")
    private fun result(vararg o: RadarContextualOpportunityV1) = RadarContextualResultV1("2026-11-13T15:00:00Z", -23.56, -46.65, "2099-01-01T00:00:00Z",
        "Av. Exemplo, 100", "cell", RadarContextualBaselineV1(null, 0, "insufficient", "insufficient"), o.toList(),
        RadarContextualAssistantV1(false, "none", null, null, null, 0, 0))

    @Test fun exactlyTwoTabsNoLegacyTabsNoInternalMap() {
        val panel = RadarPanelV114(app).apply { offline = true }
        val all = texts(panel)
        assertTrue(all.contains("Próximas do destino")); assertTrue(all.contains("Eventos em geral"))
        listOf("Resumo", "Oportunidades", "Áreas").forEach { assertFalse(it, all.contains(it)) }
        assertTrue(find(panel) { it.javaClass.name.contains("MapView", true) }.isEmpty())
        val tabs = find(panel) { it.contentDescription == "sr114_radar_tabs" }.single() as ViewGroup
        assertEquals(2, tabs.childCount)
        val src = File(generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "app/build.gradle.kts").exists() }, "app/src/main/java/com/bigcorps/driveraimvp")
        listOf("RadarPanelV114.kt", "RadarV114.kt").forEach { f ->
            val t = File(src, f).readText()
            assertFalse(f, t.contains("maplibre", true) || t.contains("MapView") || t.contains("com.google.android.gms.maps"))
        }
    }

    @Test fun destinationTabWithoutConfirmedRideNeverUsesTheLastOffer() {
        val panel = RadarPanelV114(app).apply { showDestinationForTest(null, null) }
        val all = texts(panel).joinToString("\n")
        assertTrue(all.contains("Sem corrida em andamento"))
        assertTrue(find(panel) { it.contentDescription?.toString()?.startsWith("sr114_grid") == true }.isEmpty())
        // Fonte única do destino: corrida em andamento → oferta persistida (não latestOffer).
        assertTrue(File(generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "app/build.gradle.kts").exists() },
            "app/src/main/java/com/bigcorps/driveraimvp/RadarPanelV114.kt").readText().contains("RadarDestinationContextV1.current(context)"))
    }

    @Test fun destinationCardsShowCategoryNameDistanceReasonAndMapsLink() {
        val panel = RadarPanelV114(app).apply { showDestinationForTest(spec, result(opp("1", "mall", 3.1, "high"), opp("2", "hospital", 4.6))) }
        layout(panel, 680)
        val all = texts(panel)
        assertTrue(all.contains("Destino da corrida (lido da oferta)")); assertTrue(all.contains("Av. Exemplo, 100"))
        assertTrue(all.contains("● Corrida em andamento"))
        assertTrue(all.contains("Shopping") && all.contains("Lugar 1") && all.contains("3,1 km do destino") && all.contains("Alta incidência observada"))
        assertTrue(all.contains("Hospital próximo"))
        assertEquals(2, all.count { it == "Ver no Maps" })
    }

    @Test fun emptyAndFailureAreDistinguishable() {
        val empty = texts(RadarPanelV114(app).apply { showDestinationForTest(spec, result()) }).joinToString("\n")
        assertTrue(empty.contains("Nenhuma oportunidade verificada") && empty.contains("não é um erro"))
        val failed = texts(RadarPanelV114(app).apply { showDestinationForTest(spec, null, error = RadarRegion109.failure("Unable to resolve host")) }).joinToString("\n")
        assertTrue(failed.contains("Sem conexão com o Radar"))
        assertFalse(failed.contains("Nenhuma oportunidade"))
        val evEmpty = texts(RadarPanelV114(app).apply { showEventsForTest(RadarV114.Reference.YOU, EventRadarResult026(emptyList(), "ok", null)) }).joinToString("\n")
        assertTrue(evEmpty.contains("Nenhum evento verificado") && evEmpty.contains("não é um erro de conexão"))
        val evNoRef = texts(RadarPanelV114(app).apply { showEventsForTest(RadarV114.Reference.NONE, null) }).joinToString("\n")
        assertTrue(evNoRef.contains("não simula sua posição"))
    }

    @Test fun eventsTabShowsReferenceAndOnlyValidTimes() {
        val start = Instant.now().plusSeconds(3600).toString(); val end = Instant.now().plusSeconds(3 * 3600).toString()
        val panel = RadarPanelV114(app).apply {
            showEventsForTest(RadarV114.Reference.DESTINATION, EventRadarResult026(listOf(
                event("a", "music", 2.3, start, end), event("b", "fair_convention", 1.1, "sem-hora", end)), "ok", Instant.now().toString()))
        }
        layout(panel, 411)
        val all = texts(panel)
        assertTrue(all.contains("1,1 km do destino")); assertTrue(all.contains("2,3 km do destino"))
        assertTrue(all.any { it.startsWith("Hoje · ") || it.startsWith("Amanhã · ") })
        assertEquals("only one valid time", 1, all.count { it.contains(" · ") && (it.startsWith("Hoje") || it.startsWith("Amanhã")) })
        assertTrue(all.indexOf("Evento b") < all.indexOf("Evento a")) // mais próximo primeiro
    }

    @Test fun gridAdaptsColumnsToWidth() {
        val wide = RadarPanelV114(app).apply { showDestinationForTest(spec, result(opp("1", "mall", 1.0), opp("2", "hotel", 2.0))) }
        layout(wide, 680); wide.showDestinationForTest(spec, result(opp("1", "mall", 1.0), opp("2", "hotel", 2.0)))
        assertTrue(find(wide) { it.contentDescription == "sr114_grid_cols_2" }.isNotEmpty())
        val narrow = RadarPanelV114(app).apply { showDestinationForTest(spec, result(opp("1", "mall", 1.0))) }
        layout(narrow, 300); narrow.showDestinationForTest(spec, result(opp("1", "mall", 1.0)))
        assertTrue(find(narrow) { it.contentDescription == "sr114_grid_cols_1" }.isNotEmpty())
    }

    // ---------------------------------------------------------------- shell e rollback
    @Test fun radarRouteShowsTwoTabsByDefaultAndRollbackRestoresThePreviousSurface() {
        RadarContextualFlagsV1.disableAll(app)
        val activity = Robolectric.buildActivity(MainActivity::class.java).setup().get()
        val root = activity.window.decorView
        val v114 = find(root) { it is RadarPanelV114 }.single()
        val legacy = find(root) { it is RadarPanel027035 }.single()
        val contextual = find(root) { it is RadarContextualPanelV1 }.single()
        assertEquals(View.VISIBLE, v114.visibility); assertEquals(View.GONE, legacy.visibility); assertEquals(View.GONE, contextual.visibility)
        RadarV114.setRollback(app, true)
        val again = Robolectric.buildActivity(MainActivity::class.java).setup().get().window.decorView
        assertEquals(View.GONE, find(again) { it is RadarPanelV114 }.single().visibility)
        assertEquals(View.VISIBLE, find(again) { it is RadarPanel027035 }.single().visibility)
        val shell = File(generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "app/build.gradle.kts").exists() },
            "app/src/main/java/com/bigcorps/driveraimvp/ConsolidatedMainActivity027037.kt").readText()
        assertTrue("assistant deep links keep the contextual surface", shell.contains("val twoTabs = !forced && ::radarV114Panel.isInitialized && RadarV114.enabled(this)"))
        assertTrue("a real deep-linked destination survives later refreshes", shell.contains("radarSurface.pending != null || radarContextualPanel.holdsRealDestination()"))
        assertFalse("Radar Contextual flags still default false", RadarContextualFlagsV1.uiEnabled(app))
    }
}
