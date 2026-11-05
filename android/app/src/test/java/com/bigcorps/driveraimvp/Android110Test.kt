package com.srrotas.app

import android.app.Application
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.time.Instant

/** vc110 — instrumentação do Reader, Histórico › Digitalizados, resumo da digitalização e decisão de corrida na releitura. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class Android110Test {
    private val root = generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }.first { File(it, "app/build.gradle.kts").exists() }
    private fun src(name: String) = File(root, "app/src/main/java/com/bigcorps/driveraimvp/$name").readText()
    private fun texts(v: View): List<TextView> = when (v) { is TextView -> listOf(v); is ViewGroup -> (0 until v.childCount).flatMap { texts(v.getChildAt(it)) }; else -> emptyList() }

    @Test fun timelineMeasuresMilestonesDurationsAndDiscardReasonsWithoutText() {
        val app = RuntimeEnvironment.getApplication()
        ReaderTimeline110.journeyStarted(app, "journey-110")
        ReaderTimeline110.projectionActive()
        ReaderTimeline110.onTrace("FRAME_CAPTURED", mapOf("width" to 1188))
        ReaderTimeline110.onTrace("OCR_OK", mapOf("chars" to 300))
        ReaderTimeline110.onTrace("SCREEN_CLASSIFIED", mapOf("platform" to "uber", "candidate" to false, "reason" to "home/ocioso", "offers" to 0))
        ReaderTimeline110.onTrace("SCREEN_CLASSIFIED", mapOf("platform" to "uber", "candidate" to true, "reason" to "card", "offers" to 0))
        ReaderTimeline110.onTrace("OFFER_DROP", mapOf("drop_stage" to "LEGS", "drop_reason" to "legs_single_leg"))
        ReaderTimeline110.projectionInactive("projection_stopped_by_system")
        ReaderTimeline110.offerPersisted(Fixtures108.offer("t1", 17.99, context = OfferContext(pickupLabel = "Rua Alfa, 1", destinationLabel = "Rua Beta, 2")))
        ReaderTimeline110.offerPersisted(Fixtures108.offer("t2", 11.99, context = OfferContext(pickupLabel = "Rua Alfa, 1")))
        val json = ReaderTimeline110.toJson()
        val m = json.getJSONObject("milestones_ms_after_journey_start")
        listOf("journey_started", "projection_active", "first_frame", "first_ocr", "first_uber_candidate", "first_offer_persisted").forEach { assertFalse(it, m.isNull(it)) }
        assertTrue(m.isNull("first_hud_offer"))
        val c = json.getJSONObject("counters")
        assertEquals(2, c.getInt("offers_persisted")); assertEquals(1, c.getInt("offers_with_both")); assertEquals(2, c.getInt("offers_with_pickup"))
        val reasons = json.getJSONObject("discard_reasons")
        assertEquals(1, reasons.getInt("screen:home_idle")); assertEquals(1, reasons.getInt("drop:LEGS:legs_single_leg"))
        assertEquals(1, reasons.getInt("projection_inactive:projection_stopped_by_system"))
        assertFalse(json.toString().contains("Rua Alfa"))
        // A new journey starts a clean session.
        ReaderTimeline110.journeyStarted(app, "journey-111")
        assertFalse(ReaderTimeline110.toJson().getJSONObject("counters").has("offers_persisted"))
    }

    @Test fun knownSampleComparesShownOffersWithWhatTheReaderRegistered() {
        val app = RuntimeEnvironment.getApplication()
        ReaderTimeline110.journeyStarted(app, "journey-ks")
        ReaderTimeline110.offerPersisted(Fixtures108.offer("before", 9.0))
        KnownSample110.start(app)
        assertTrue(KnownSample110.running(app))
        repeat(3) { ReaderTimeline110.offerPersisted(Fixtures108.offer("k$it", 10.0 + it, context = OfferContext(pickupLabel = "Rua A, 1", destinationLabel = "Rua B, 2"))) }
        val r = KnownSample110.stop(app, 4)!!
        assertEquals(3, r.persisted); assertEquals(0.75, r.detectionRate!!, 1e-9); assertEquals(3, r.withBoth)
        assertFalse(KnownSample110.running(app))
        assertEquals(1, KnownSample110.toJson(app).getJSONArray("results").length())
        assertTrue(src("SettingsPanel027037.kt").contains("KnownSampleDialog110.show(context)"))
        assertTrue(src("ReaderLabCombinedDiagnostic0270361.kt").contains("ReaderTimeline110.toJson()"))
        assertFalse(src("MediaProjectionOcrService.kt").contains("ReaderTimeline110"))
    }

    @Test fun historyNeverShowsACellCodeAsAnAddressAndListsDigitizedImports() {
        val app = RuntimeEnvironment.getApplication()
        val store = LocalStore.get(app)
        store.saveOffer(Fixtures108.offer("cell", 20.0, context = OfferContext(pickupCell = "g2:-2355:-4663", destinationCell = "g2:-2360:-4670")))
        UberDigitizationStore026.get(app).saveRides(listOf(
            UberCompletedRide026("imp-1", "2026-11-05T10:00:00Z", "2026-11-05T09:00:00Z", 23.51, "uberx", "Rua Alfa, 10", null, 0.9)))
        HistoryReviewStore109.save(app, listOf(UberCompletedRide026("pend-1", "2026-11-05T10:00:00Z", "2026-11-05T08:00:00Z", 12.0, "uberx", null, null, 0.9)), 5, 3)
        val activity = Robolectric.buildActivity(MainActivity::class.java).get()
        val panel = RideHistoryPanel027035(activity)
        val shown = texts(panel).map { it.text.toString() }
        assertFalse(shown.toString(), shown.any { it.contains("g2:") }); assertTrue(shown.toString(), shown.any { it == " não lida" || it == " não lido" })
        texts(panel).first { it.text == "Digitalizados" }.performClick()
        val digit = texts(panel).map { it.text.toString() }
        assertTrue(digit.any { it.contains("registro(s) importado(s) do histórico Uber") })
        assertTrue(digit.any { it.contains("Origem: histórico Uber digitalizado") }); assertTrue(digit.contains("IMPORTADA"))
        assertTrue(digit.any { it.contains("registro(s) aguardando conferência") }); assertTrue(digit.contains("Ver ofertas capturadas"))
        // The Reader offers stay the default view.
        texts(panel).first { it.text == "Ofertas do Reader" }.performClick()
        assertTrue(texts(panel).map { it.text.toString() }.any { it.contains("oferta(s) persistida(s)") })
        HistoryReviewStore109.clear(app)
    }

    @Test fun digitizationEndsWithASummaryThenTheVisualReview() {
        val app = RuntimeEnvironment.getApplication()
        val rides = listOf(
            UberCompletedRide026("s1", "2026-11-05T10:00:00Z", "2026-11-04T19:00:00Z", 23.51, "uberx", "Rua A, 1", "Rua B, 2", 0.9),
            UberCompletedRide026("s2", "2026-11-05T10:00:00Z", "2026-11-04T18:00:00Z", 10.19, "uberx", "Rua A, 1", null, 0.9),
            UberCompletedRide026("s3", "2026-11-05T10:00:00Z", "2026-11-04T17:00:00Z", 8.0, "uberx", "Rua A, 1", "Rua B, 2", 0.5),
        )
        HistoryReviewStore109.save(app, rides, 14, 11)
        val controller = Robolectric.buildActivity(HistoryReviewActivity109::class.java,
            android.content.Intent(app, HistoryReviewActivity109::class.java).putExtra(HistoryReviewActivity109.EXTRA_FROM_SCAN, true)).setup()
        val a = controller.get()
        var all = texts(a.window.decorView).map { it.text.toString() }
        assertTrue(all.contains("Digitalização concluída"))
        listOf("Telas analisadas: 14", "Registros capturados: 3", "Incompletos: 1 · Duplicados: 0 · Canceladas: 0",
            "Importados para Histórico › Digitalizados: 0").forEach { assertTrue(it, all.contains(it)) }
        assertTrue(all.contains("Ver ofertas capturadas") && all.contains("Concluir"))
        texts(a.window.decorView).first { it.text == "Ver ofertas capturadas" }.performClick()
        texts(a.window.decorView).first { it.text.startsWith("Confirmar ") }.performClick()
        all = texts(a.window.decorView).map { it.text.toString() }
        // vc111: só o completo (s1) é pré-selecionado; totais originais preservados após confirmar.
        assertTrue(all.toString(), all.contains("Importados para Histórico › Digitalizados: 1"))
        assertTrue(all.toString(), all.contains("Registros capturados: 3") && all.contains("Incompletos: 1 · Duplicados: 0 · Canceladas: 0"))
        assertTrue(all.toString(), all.contains("Corridas confirmadas: 0 (importar não confirma corrida)"))
        // The unselected records (incomplete s2, low-confidence s3) stay pending after closing and reopening.
        assertEquals(listOf("s2", "s3"), HistoryReviewStore109.load(app)!!.rides.map { it.sourceKey })
        assertEquals(1, UberDigitizationStore026.get(app).recentRides().count { it.ride.sourceKey in setOf("s1", "s2") })
        HistoryReviewStore109.clear(app)
    }

    @Test fun rescanRideDecisionIsCanonicalIdempotentAndReversible() {
        val app = RuntimeEnvironment.getApplication()
        val store = LocalStore.get(app)
        val journey = store.startJourney(); store.endJourney(journey.id, "test")
        val o = Fixtures108.offer("rs-1", 33.10, observedAt = Instant.now().toString(), journeyId = journey.id)
        store.saveOffer(o); store.ensureOfferedOutcome(o)
        assertTrue(RescanRideDecision110.trustworthy(o, o.copy(localId = "fresh"), true))
        assertFalse(RescanRideDecision110.trustworthy(o, o.copy(fare = 34.0), true))
        assertFalse(RescanRideDecision110.trustworthy(null, o, true))
        assertFalse(RescanRideDecision110.trustworthy(o.copy(journeyId = null), o, true))
        assertEquals("fare_mismatch", RescanRideDecision110.apply(app, o.localId, 30.0, RescanRideDecision110.Kind.CONFIRM_COMPLETED).code)
        assertTrue(RescanRideDecision110.apply(app, o.localId, 33.10, RescanRideDecision110.Kind.CONFIRM_COMPLETED).applied)
        val done = store.rideOutcomeForOffer(o.localId)!!
        assertEquals(RideOperationalStatus.COMPLETED, done.status); assertEquals(RescanRideDecision110.SOURCE, done.source)
        assertFalse(RescanRideDecision110.apply(app, o.localId, 33.10, RescanRideDecision110.Kind.CONFIRM_COMPLETED).applied)
        assertEquals(done.revision, store.rideOutcomeForOffer(o.localId)!!.revision)
        assertTrue(RescanRideDecision110.apply(app, o.localId, 33.10, RescanRideDecision110.Kind.UNDO).applied)
        assertEquals(RideOperationalStatus.OFFERED, store.rideOutcomeForOffer(o.localId)!!.status)
        assertEquals("offer_missing", RescanRideDecision110.apply(app, "nope", 1.0, RescanRideDecision110.Kind.CONFIRM_COMPLETED).code)
    }

    @Test fun rescanNeverStaysProcessingAndKeepsTheLease() {
        val a = src("OfferRescanActivityV1.kt")
        assertTrue(a.contains("ui_timeout_60s")); assertTrue(a.contains("main.postDelayed(it, 60_000L)"))
        val timeout = a.substringAfter("hardTimeout = Runnable {").substringBefore("}.also")
        assertFalse(timeout.contains("complete()"))
        assertTrue(a.contains("RescanRideDecision110.request(") && !a.contains("correctRide("))
        assertTrue(File(root, "app/src/main/AndroidManifest.xml").readText().contains(".RescanRideDecisionReceiver110\" android:exported=\"false\""))
    }
}
