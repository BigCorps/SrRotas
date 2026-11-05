package com.srrotas.app

import android.app.Application
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * vc111 P0 Reader — evidência por amostra (diagnóstico 34 só tinha contadores). Telemetria apenas:
 * o descarte LEGS continua (nenhuma tolerância financeira foi relaxada) e o Reader2 segue shadow.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class ReaderEvidence111Test {
    private val app get() = RuntimeEnvironment.getApplication()
    @Before fun setUp() { ReaderEvidence111.install(app); ReaderEvidence111.resetForTest() }

    // Card sintético: cabeçalho diz 40 min / 13,0 km, trechos somam 33 min / 12,1 km.
    private val card = "R$ 31,90\n40 min • 13.0 km • Est. de R$2,45/km\n& 5 min • 1.6 km\nRua Exemplo, Bairro Um, São Paulo\nO 28 minutos • 10.5 km\nAv. Exemplo, Bairro Dois, São Paulo\nAceitar"

    @Test fun legsDisagreementKeepsTheDropAndRecordsOnlyNumbers() {
        assertTrue(UberLegResolver106.resolve(card) is UberLegResolver106.Resolution.Ambiguous)
        val legs = ReaderEvidence111.legs(card, 31.90)!!
        assertEquals(40, legs.totalMin); assertEquals(5, legs.pickupMin); assertEquals(28, legs.tripMin)
        assertEquals(7, legs.deltaMin); assertEquals(0.9, legs.deltaKm, 0.001)
        assertEquals("pos:min_4_10:km_le1", ReaderEvidence111.deltaBucket(legs))
        val fields = legs.fields()
        assertFalse(fields.values.any { it is String })
    }

    @Test fun sameCardAcrossFramesCountsOnceAsDistinctCard() {
        val legs = ReaderEvidence111.legs(card, 31.90)!!
        repeat(5) { ReaderEvidence111.onLegsDrop(legs, 1_000L + it * 300) }
        ReaderEvidence111.onLegsDrop(legs.copy(fare = 18.0), 3_000)
        val json = ReaderEvidence111.toJson(app)
        assertEquals(6, json.getInt("legs_frames"))
        assertEquals(2, json.getInt("legs_distinct_cards"))
        assertEquals(2, json.getJSONArray("legs_samples").length())
        assertFalse(json.toString().contains("Rua") || json.toString().contains("Av."))
    }

    @Test fun reader2OnlyCardsAreComparedWithM1OutcomeOfTheSameFrame() {
        var only = 10L; var ready = 2L
        val r2 = { only to ready }
        ReaderEvidence111.onFrame(0, { "LEGS:legs_legs_disagree_with_total" }, reader2 = r2) // baseline
        only = 11; ReaderEvidence111.onFrame(0, { "LEGS:legs_legs_disagree_with_total" }, reader2 = r2)
        only = 12; ready = 3; ReaderEvidence111.onFrame(0, { "ANCHOR:no_uber_anchor" }, reader2 = r2)
        ReaderEvidence111.onFrame(1, { "unused" }, reader2 = r2) // nada novo no Reader2: não conta
        val json = ReaderEvidence111.toJson(app)
        assertEquals(1, json.getJSONObject("reader2_only_core_vs_m1").getInt("LEGS:legs_legs_disagree_with_total"))
        assertEquals(1, json.getJSONObject("reader2_consensus_vs_m1").getInt("ANCHOR:no_uber_anchor"))
        assertEquals(1, json.getJSONArray("reader2_consensus_samples").length())
    }

    @Test fun reader2StaysShadowAndFrozenReaderSourcesAreUntouched() {
        val root = generateSequence(java.io.File("").absoluteFile) { it.parentFile }.first { java.io.File(it, "app/build.gradle.kts").exists() }
        val src = java.io.File(root, "app/src/main/java/com/bigcorps/driveraimvp/ReaderEvidence111.kt").readText()
        listOf("saveOffer", "OfferDispatcher", "promote", "dispatch(").forEach { assertFalse(it, src.contains(it)) }
    }
}
