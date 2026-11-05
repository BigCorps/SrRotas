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

/** vc109 — Conferência do Histórico: cards, seleção individual, incompletos/duplicados e confirmação. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class HistoryReview109Test {
    private fun ride(key: String, fare: Double, at: String?, pickup: String? = "Rua Alfa, 10", dest: String? = "Avenida Beta, 20",
        status: String = UberCompletedRide026.STATUS_COMPLETED, confidence: Double = 0.9, service: String = "uberx") =
        UberCompletedRide026(key, "2026-11-03T22:00:00Z", at, fare, service, pickup, dest, confidence, 900, 6.2, rideStatus = status)
    private fun texts(v: View): List<TextView> = when (v) { is TextView -> listOf(v); is ViewGroup -> (0 until v.childCount).flatMap { texts(v.getChildAt(it)) }; else -> emptyList() }
    private val rides = listOf(
        ride("a", 23.51, "2026-11-01T19:34:00Z"),
        ride("b", 10.19, "2026-11-01T14:35:00Z", dest = null),
        ride("c", 0.0, "2026-11-01T15:00:00Z", status = UberCompletedRide026.STATUS_CANCELLED),
        ride("d", 31.00, "2026-11-01T12:02:00Z", confidence = 0.58),
        ride("e", 18.40, "2026-11-01T11:00:00Z"),
    )

    @Test fun rulesFlagIncompleteDuplicatesAndSelectConservatively() {
        val saved = listOf(ride("e-old", 18.40, "2026-11-01T11:00:40Z"))
        val e = HistoryReviewRules109.entries(rides, saved).associateBy { it.ride.sourceKey }
        assertTrue(e["a"]!!.complete && e["a"]!!.selectedByDefault)
        assertEquals(listOf("destino"), e["b"]!!.missing)
        assertTrue(e["c"]!!.cancelled); assertFalse(e["c"]!!.selectedByDefault)
        assertEquals(HistoryReviewRules109.Confidence.BAIXA, e["d"]!!.confidence); assertFalse(e["d"]!!.selectedByDefault)
        assertTrue(e["e"]!!.duplicate); assertFalse(e["e"]!!.selectedByDefault)
    }

    @Test fun pendingScanSurvivesUntilConfirmedOrDiscarded() {
        val app = RuntimeEnvironment.getApplication()
        HistoryReviewStore109.save(app, rides, 12, 9)
        val back = HistoryReviewStore109.load(app)!!
        assertEquals(rides, back.rides); assertEquals(12, back.framesRead)
        // Only the parsed fields are kept; no raw OCR text.
        assertFalse(File(app.filesDir, "uber-history-review-109.json").readText().contains("frames\""))
        HistoryReviewStore109.clear(app); assertNull(HistoryReviewStore109.load(app))
    }

    @Test fun screenShowsCardsFiltersAndSavesOnlyTheSelection() {
        val app = RuntimeEnvironment.getApplication()
        HistoryReviewStore109.save(app, rides, 12, 9)
        val activity = Robolectric.buildActivity(HistoryReviewActivity109::class.java).setup().get()
        val all = texts(activity.window.decorView).map { it.text.toString() }
        assertTrue(all.contains("Conferência do Histórico"))
        assertTrue(all.any { it.startsWith("Incompletos (1)") }); assertTrue(all.any { it.startsWith("Canceladas (1)") })
        assertTrue(all.contains("Busca:") && all.contains(" Rua Alfa, 10"))
        assertTrue(all.any { it.startsWith("Incompleto: falta destino") })
        // vc111: seleção inicial só de completos — a, e (b sem destino só por escolha explícita).
        val confirm = texts(activity.window.decorView).first { it.text.startsWith("Confirmar") }
        assertEquals("Confirmar 2 registro(s)", confirm.text.toString())
        // O motorista inclui explicitamente o incompleto (b).
        texts(activity.window.decorView).first { it.text.startsWith("Incluir nesta confirmação") }.performClick()
        assertEquals("Confirmar 3 registro(s)", texts(activity.window.decorView).first { it.text.startsWith("Confirmar") }.text.toString())
        // Remove one selection then confirm.
        texts(activity.window.decorView).first { it.text.startsWith("✓ Selecionado") }.performClick()
        val outcomesBefore = LocalStore.get(app).pendingRideOutcomes(250).size
        val confirmNow = texts(activity.window.decorView).first { it.text.startsWith("Confirmar") }
        assertEquals("Confirmar 2 registro(s)", confirmNow.text.toString())
        confirmNow.performClick()
        val saved = UberDigitizationStore026.get(app).ridesBetween("2026-11-01T00:00:00Z", "2026-11-02T00:00:00Z")
        assertEquals(2, saved.size)
        assertTrue(saved.none { it.rideStatus == UberCompletedRide026.STATUS_CANCELLED })
        // vc110: unselected records stay pending for later review (Histórico › Digitalizados).
        assertEquals(3, HistoryReviewStore109.load(app)!!.rides.size)
        HistoryReviewStore109.clear(app)
        // Nothing became a completed ride outcome (offers are never promoted here).
        assertEquals(outcomesBefore, LocalStore.get(app).pendingRideOutcomes(250).size)
    }

    @Test fun reachableFromSettingsDigitalizacaoAndReplacesTheTextList() {
        val root = generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }.first { File(it, "app/build.gradle.kts").exists() }
        val activity = File(root, "app/src/main/java/com/bigcorps/driveraimvp/UberDigitizationActivity026.kt").readText()
        assertTrue(activity.contains("\"Conferência do Histórico\""))
        assertFalse(activity.contains("fun previewRides("))
        assertTrue(activity.substringAfter("private fun reviewHistoryScan").contains("HistoryReviewActivity109.open(this, fromScan = true)"))
        assertTrue(File(root, "app/src/main/AndroidManifest.xml").readText().contains(".HistoryReviewActivity109\" android:exported=\"false\""))
    }
}
