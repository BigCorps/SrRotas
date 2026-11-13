package com.srrotas.app

import android.app.Activity
import android.app.Application
import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import java.time.Instant

/** All coordinates, places and clocks below are synthetic and test-only. */
internal object RadarTwoTabsFixtures {
    val now = Instant.parse("2030-02-20T20:00:00Z").toEpochMilli()
    val spec = RadarDestinationSpecV1(-23.55, -46.63, "2030-02-20T20:30:00Z", "Destino sintético para teste", "confirmed-test-ride")
    fun event(id: String, distance: Double = 2.0) = EventRadarOpportunity026(id, "music", "Evento sintético $id",
        "Local sintético", "Endereço sintético", "2030-02-20T21:00:00Z", "2030-02-20T23:00:00Z",
        "2030-02-20T22:40:00Z", "2030-02-21T00:15:00Z", distance, "fixture_test", .8, null)
    fun events() = EventRadarResult026(listOf(event("far", 8.0), event("near", 1.2)), "ok", null, now)
    fun places(): RadarContextualResultV1 {
        val p = RadarContextualOpportunityV1("poi-test", "poi-test", "Local sintético", null, "music",
            -23.551, -46.631, 99.0, spec.eta, null, null, null, .8, 0.0, "insufficient",
            "Sem estatística de demanda", null, null, null, emptyList())
        return RadarContextualResultV1(Instant.ofEpochMilli(now).toString(), spec.lat, spec.lng, spec.eta, spec.label,
            "test-only", RadarContextualBaselineV1(null, 0, "insufficient", "none"),
            listOf(p, p.copy(id = "poi-test-2", poiId = "poi-test-2", title = "Outro local sintético", poiType = "sports", lat = -23.56)),
            RadarContextualAssistantV1(false, "insufficient", null, null, null, 0, 0))
    }
    class Data : RadarTwoTabsDataV1 {
        var current: RadarDestinationSpecV1? = spec
        var position: RadarVisibleLocationV1.Position? = RadarVisibleLocationV1.Position(-23.55, -46.63, 0)
        var eventReply: Result<EventRadarResult026> = Result.success(events())
        var placeReply: Result<RadarContextualResultV1> = Result.success(places())
        var defer = false
        var eventDone: ((Result<EventRadarResult026>) -> Unit)? = null
        var placeDone: ((Result<RadarContextualResultV1>) -> Unit)? = null
        var eventCalls = 0
        var placeCalls = 0
        override fun destination(context: Context) = current
        override fun location(context: Context) = position
        override fun destinationReason(context: Context) = "Sem corrida confirmada para teste."
        override fun events(context: Context, position: RadarVisibleLocationV1.Position, force: Boolean, done: (Result<EventRadarResult026>) -> Unit) {
            eventCalls++; if (defer) eventDone = done else done(eventReply)
        }
        override fun places(context: Context, spec: RadarDestinationSpecV1, force: Boolean, done: (Result<RadarContextualResultV1>) -> Unit) {
            placeCalls++; if (defer) placeDone = done else done(placeReply)
        }
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class RadarTwoTabsTest {
    private val f = RadarTwoTabsFixtures
    private fun texts(v: View): List<TextView> = when (v) { is TextView -> listOf(v); is ViewGroup -> (0 until v.childCount).flatMap { texts(v.getChildAt(it)) }; else -> emptyList() }
    private fun labels(v: View) = texts(v).map { it.text.toString() }
    private fun click(v: View, label: String) = texts(v).first { it.text.toString() == label }.performClick()
    private fun panel(data: RadarTwoTabsFixtures.Data, time: () -> Long = { f.now }): RadarTwoTabsPanelV1 {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val panel = RadarTwoTabsPanelV1(activity, data, time)
        activity.setContentView(panel)
        panel.measure(View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(1200, View.MeasureSpec.EXACTLY))
        panel.layout(0, 0, 800, 1200)
        panel.setSurfaceResumed(true); ShadowLooper.idleMainLooper()
        return panel
    }
    @Test fun eventsAreFutureAndOrderedByDistance() {
        val input = f.events().copy(opportunities = f.events().opportunities + listOf(
            f.event("past").copy(startsAt = "2030-02-20T19:00:00Z"), f.event("cancelled").copy(status = "cancelled"),
            f.event("pending").copy(reviewStatus = "pending"), f.event("expired").copy(egressEndAt = "2030-02-20T19:00:00Z"),
            f.event("invalid").copy(startsAt = "bad"), f.event("invalid-distance", Double.NaN)))
        val cards = RadarTwoTabsRulesV1.events(input, f.now)
        assertEquals(listOf("near", "far"), cards.map { it.id })
        assertTrue(cards.all { it.reference == "de você" })
    }
    @Test fun destinationRequiresFreshSpecAndComputesDistanceFromVerifiedCoordinates() {
        assertFalse(RadarTwoTabsRulesV1.validDestination(null, f.now))
        assertFalse(RadarTwoTabsRulesV1.validDestination(f.spec.copy(eta = "2030-02-20T19:00:00Z"), f.now))
        assertFalse(RadarTwoTabsRulesV1.validDestination(f.spec.copy(lat = Double.NaN), f.now))
        val cards = RadarTwoTabsRulesV1.destination(f.places(), f.spec, f.now)
        assertTrue(cards.first().distanceKm < 1.0) // supplied distance=99 is never displayed
        assertTrue(cards.all { it.reference == "do destino" })
        assertTrue(cards.none { it.description.contains("probabilidade", true) })
        assertTrue(RadarTwoTabsRulesV1.destination(f.places().copy(destinationLat = -20.0), f.spec, f.now).isEmpty())
    }
    @Test fun twoTabsStayVisibleWithoutRideOrLocation() {
        val data = RadarTwoTabsFixtures.Data().apply { current = null; position = null }
        val view = panel(data)
        assertTrue(labels(view).containsAll(listOf("Próximas do destino", "Eventos em geral")))
        assertFalse(labels(view).any { it in listOf("Resumo", "Oportunidades", "Áreas") })
        assertTrue(labels(view).contains("Sem destino de corrida ativa"))
        click(view, "Eventos em geral")
        assertTrue(labels(view).contains("Sem localização recente"))
        assertEquals(0, data.eventCalls); assertEquals(0, data.placeCalls)
    }
    @Test fun eventsUseUserLocationEvenWithAcceptedRide() {
        val data = RadarTwoTabsFixtures.Data(); val view = panel(data)
        click(view, "Eventos em geral")
        assertTrue(labels(view).any { it.contains("km de você") })
        assertEquals(1, data.eventCalls); assertEquals(1, data.placeCalls)
    }
    @Test fun lateDestinationCallbackCannotSurviveRideChangeOrPause() {
        val data = RadarTwoTabsFixtures.Data().apply { defer = true }; val view = panel(data)
        val old = data.placeDone!!; data.current = null; old(Result.success(f.places()))
        assertFalse(labels(view).contains("Local sintético")); assertTrue(labels(view).contains("Sem destino de corrida ativa"))
        data.current = f.spec; view.refresh(); val paused = data.placeDone!!
        view.setSurfaceResumed(false); paused(Result.success(f.places()))
        assertFalse(labels(view).contains("Local sintético"))
    }
    @Test fun lateEventsCallbackCannotKeepPreviousLocationDistances() {
        val data = RadarTwoTabsFixtures.Data().apply { current = null; defer = true }; val view = panel(data)
        click(view, "Eventos em geral"); val old = data.eventDone!!
        data.position = data.position!!.copy(lat = -23.6); old(Result.success(f.events()))
        assertFalse(labels(view).contains("Evento sintético near")); assertEquals(2, data.eventCalls)
    }
    @Test fun loadingFailureEmptyExpiredCacheAndEtaHaveHonestStates() {
        val data = RadarTwoTabsFixtures.Data().apply { defer = true }; var clock = f.now
        val view = panel(data) { clock }
        assertTrue(labels(view).any { it.contains("Carregando locais") })
        data.placeDone!!(Result.failure(java.io.IOException("timeout")))
        assertTrue(labels(view).contains("Radar indisponível"))
        data.defer = false; data.placeReply = Result.success(f.places().copy(opportunities = emptyList())); view.refresh()
        assertTrue(labels(view).contains("Sem locais verificados próximos do destino"))
        data.placeReply = Result.success(f.places()); view.refresh(); clock += RadarTwoTabsRulesV1.DESTINATION_TTL_MS + 1
        view.renderPendingIfReady(); assertTrue(labels(view).contains("Dados expirados")); assertFalse(labels(view).contains("Local sintético"))
        clock = Instant.parse(f.spec.eta).toEpochMilli(); view.renderPendingIfReady()
        assertTrue(labels(view).contains("ETA vencida ou destino indisponível"))
    }
    @Test fun expiryAndActualContainerBreakpointsAreExplicit() {
        assertTrue(RadarTwoTabsRulesV1.fresh(f.now, f.now, 100)); assertFalse(RadarTwoTabsRulesV1.fresh(0, f.now, 100))
        assertFalse(RadarTwoTabsRulesV1.fresh(f.now, f.now + 101, 100))
        assertEquals(1, RadarTwoTabsRulesV1.columns(390f, 1f)); assertEquals(2, RadarTwoTabsRulesV1.columns(780f, 1f))
        assertEquals(1, RadarTwoTabsRulesV1.columns(600f, 1.5f))
    }
    @Test fun forgedDeepLinkCannotTurnLatestOfferIntoAcceptedRide() {
        val data = RadarTwoTabsFixtures.Data().apply { current = null }; val view = panel(data)
        view.openDestination(f.spec)
        assertEquals(0, data.placeCalls); assertTrue(labels(view).contains("Sem destino de corrida ativa"))
    }
}
