package com.srrotas.app

import android.app.Application
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.time.Instant

/**
 * vc108 — recuperação pós-jornada. Resumo Uber é só referência; histórico é evidência; apenas
 * a decisão do motorista sobre uma oferta observada da jornada grava COMPLETED (revisão nova,
 * origem recovery_108, reversível). Fixtures anonimizadas.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class RideRecovery108Test {
    private val t0 = Instant.parse("2026-10-29T10:14:00Z")
    private fun at(min: Long) = t0.plusSeconds(min * 60).toString()
    private fun offer(id: String, fare: Double, min: Long) = Fixtures108.offer(id, fare, observedAt = at(min))
    private fun ride(key: String, fare: Double, min: Long?, status: String = UberCompletedRide026.STATUS_COMPLETED, service: String = "uberx") =
        UberCompletedRide026(key, at(600), min?.let(::at), fare, service, null, null, 0.9, rideStatus = status)
    private fun session(completed: Int) = UberSessionSummary026("s1", at(600), at(0), at(605), 641.76, completed, 112, 0.95)

    private fun scenario(): RideRecovery108.Report {
        val offers = listOf(
            RideRecovery108.OfferState(offer("o1", 17.99, 10), RideOperationalStatus.COMPLETED, "bubble_024"),
            RideRecovery108.OfferState(offer("o2", 11.99, 60), RideOperationalStatus.OFFERED, "offer_engine"),
            RideRecovery108.OfferState(offer("o3", 36.94, 120), RideOperationalStatus.NOT_COMPLETED, "journey_end_auto"),
            RideRecovery108.OfferState(offer("o4", 25.00, 200), RideOperationalStatus.OFFERED, "offer_engine"),
            RideRecovery108.OfferState(offer("o5", 25.00, 202), RideOperationalStatus.OFFERED, "offer_engine"),
            RideRecovery108.OfferState(offer("o6", 40.00, 300), RideOperationalStatus.OFFERED, "offer_engine"),
        )
        val imports = listOf(
            ride("i1", 17.99, 30), ride("i2", 11.99, 75), ride("i3", 25.00, 212), ride("i4", 50.00, 400),
            ride("i5", 9.99, 410, UberCompletedRide026.STATUS_CANCELLED),
            ride("i6", 40.00, 300 + 200), // 3h20 after the offer: different period, never matched
        )
        return RideRecovery108.build("j1", offers, imports, session(6))
    }

    @Test fun summaryHistoryAndOffersAreRelatedWithoutInventingRides() {
        val r = scenario()
        fun item(key: String) = r.items.single { it.key == key }
        assertEquals(RideRecovery108.Kind.ALREADY_CONFIRMED, item("i1").kind)
        assertEquals(RideRecovery108.Decision.DUPLICATE, item("i1").decision)
        assertEquals(RideRecovery108.Kind.RECOVERABLE, item("i2").kind); assertEquals("o2", item("i2").offerId)
        assertEquals(RideRecovery108.Kind.AMBIGUOUS, item("i3").kind); assertNull(item("i3").offerId)
        assertEquals(RideRecovery108.Kind.NO_OBSERVED_OFFER, item("i4").kind)
        assertEquals(RideRecovery108.Kind.CANCELLED_IN_HISTORY, item("i5").kind)
        assertEquals(RideRecovery108.Kind.NO_OBSERVED_OFFER, item("i6").kind)
        assertEquals(RideRecovery108.Kind.STARTED_NOT_CONFIRMED, item("offer:o3").kind)
        // Offer o6 (never taken, no history) is not a candidate: offers are not rides.
        assertTrue(r.items.none { it.offerId == "o6" })
        assertEquals(6, r.uberCompleted); assertEquals(1, r.confirmed); assertEquals(2, r.pending)
        assertEquals(3, r.unresolved)
        // Summary earnings are carried for reference only.
        assertEquals(641.76, r.uberEarnings!!, 0.001)
        assertTrue(RideRecoveryDialog108.summaryLine(r).contains("3 sem evidência individual"))
    }

    @Test fun oneImportNeverConfirmsTwoOffersAndServiceMustAgree() {
        val offers = listOf(RideRecovery108.OfferState(offer("a", 20.0, 0), RideOperationalStatus.OFFERED, null))
        val r = RideRecovery108.build("j", offers, listOf(ride("x", 20.0, 10), ride("y", 20.0, 20)), null)
        assertEquals(1, r.items.count { it.kind == RideRecovery108.Kind.RECOVERABLE })
        assertEquals(1, r.items.count { it.kind == RideRecovery108.Kind.NO_OBSERVED_OFFER })
        val black = RideRecovery108.build("j", offers, listOf(ride("z", 20.0, 10, service = "black")), null)
        assertEquals(RideRecovery108.Kind.NO_OBSERVED_OFFER, black.items.single().kind)
        assertNull(RideRecovery108.build("j", offers, emptyList(), null).unresolved)
    }

    @Test fun confirmationUsesExistingRevisionedPathIsIdempotentAndReversible() {
        val app = RuntimeEnvironment.getApplication()
        val store = LocalStore.get(app)
        val journey = store.startJourney()
        store.endJourney(journey.id, "test")
        val now = Instant.now()
        val o = Fixtures108.offer("dev-o1", 31.50, observedAt = now.plusSeconds(60).toString(), journeyId = journey.id)
        assertTrue(store.saveOffer(o)); store.ensureOfferedOutcome(o)
        UberDigitizationStore026.get(app).saveRides(listOf(
            UberCompletedRide026("uber-ride-dev1", now.toString(), now.plusSeconds(900).toString(), 31.50, "uberx", null, null, 0.9)))
        var report = RideRecovery108.report(app, journey.id)!!
        val item = report.items.single { it.kind == RideRecovery108.Kind.RECOVERABLE }
        val before = store.rideOutcomeForOffer(o.localId)!!
        assertTrue(RideRecovery108.confirm(app, report, item))
        val after = store.rideOutcomeForOffer(o.localId)!!
        assertEquals(RideOperationalStatus.COMPLETED, after.status)
        assertEquals(RideRecovery108.SOURCE, after.source)
        assertEquals(before.revision + 1, after.revision)
        assertTrue("queued for the existing idempotent sync", store.pendingRideOutcomes().any { it.localOfferId == o.localId })
        // Second confirmation does not write another revision.
        report = RideRecovery108.report(app, journey.id)!!
        val confirmed = report.items.single { it.offerId == o.localId }
        assertEquals(RideRecovery108.Decision.CONFIRMED, confirmed.decision)
        assertFalse(RideRecovery108.confirm(app, report, confirmed.copy(decision = RideRecovery108.Decision.PENDING)))
        assertEquals(after.revision, store.rideOutcomeForOffer(o.localId)!!.revision)
        // Undo restores the previous state through a new revision.
        assertTrue(RideRecovery108.undo(app, report, confirmed))
        val undone = store.rideOutcomeForOffer(o.localId)!!
        assertEquals(RideOperationalStatus.OFFERED, undone.status)
        assertEquals(RideRecovery108.SOURCE_UNDO, undone.source)
        assertEquals(RideRecovery108.Decision.PENDING, RideRecovery108.report(app, journey.id)!!.items.single { it.offerId == o.localId }.decision)
    }

    @Test fun rejectionChangesNothingInTheOfficialRecord() {
        val app = RuntimeEnvironment.getApplication()
        val store = LocalStore.get(app)
        val journey = store.startJourney(); store.endJourney(journey.id, "test")
        val o = Fixtures108.offer("dev-r1", 12.30, observedAt = Instant.now().plusSeconds(30).toString(), journeyId = journey.id)
        store.saveOffer(o); store.updateRideOutcome(o.localId, journey.id, RideOperationalStatus.NOT_COMPLETED, "journey_end_auto")
        val report = RideRecovery108.report(app, journey.id)!!
        val item = report.items.single { it.kind == RideRecovery108.Kind.STARTED_NOT_CONFIRMED }
        val revision = store.rideOutcomeForOffer(o.localId)!!.revision
        RideRecovery108.reject(app, report, item)
        assertEquals(revision, store.rideOutcomeForOffer(o.localId)!!.revision)
        assertEquals(RideRecovery108.Decision.REJECTED, RideRecovery108.report(app, journey.id)!!.items.single { it.key == item.key }.decision)
    }
}
