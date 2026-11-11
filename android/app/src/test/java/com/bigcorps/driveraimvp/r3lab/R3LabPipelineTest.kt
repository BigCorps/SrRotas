package com.srrotas.app.r3lab

import android.app.Application
import android.graphics.Rect
import com.srrotas.app.DriverSettings
import com.srrotas.app.SpatialOcrLine
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Seleção de janelas, filtro visual, fila de OCR, perfis (modo D) e estabilização. Cards Uber com a
 * geometria do tablet SM-X626B (1188×1599) já usada nos testes do vc109; endereços fictícios.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class R3LabPipelineTest {
    private val settings = DriverSettings(costPerKm = 0.82)
    private fun l(text: String, top: Int, left: Int = 60) = SpatialOcrLine(text, Rect(left, top, left + 18 * text.length, top + 44))
    private val uber = R3ProfileManager().also(R3DefaultProfiles::installDefaults)
    private val profile get() = uber.activeFor(R3AllowedPackages.UBER)!!

    private fun card(fare: String = "R$ 37,27", top: Int = 698, left: Int = 60, pickup: String = "& 8 min • 2.4 km", trip: String = "O 39 minutos • 10.5 km",
                     header: String = "47 min • 12.9 km • Est. de R$2,89/km") = listOf(
        l("Comfort", top - 60, left), l(fare, top, left), l(header, top + 105, left), l("+R$3,50 incluído", top + 181, left),
        l(pickup, top + 304, left), l("Av. Exemplo, Bairro Fictício, São Paulo", top + 381, left), l("x 5,00 (4)", top + 454, left),
        l(trip, top + 559, left), l("Rua Exemplo, 76, Bairro Seis, São Paulo", top + 638, left), l("Aceitar", top + 759, left),
    )

    // ---------------------------------------------------------------- janelas
    @Test fun onlyUber99WindowsAndSplitScreenKeepsBoth() {
        val w = listOf(
            R3Window(1, true, R3AllowedPackages.UBER, 0, 0, 600, 1600), R3Window(2, true, R3AllowedPackages.NINETY_NINE, 600, 0, 1200, 1600),
            R3Window(3, true, "com.example.bank", 0, 0, 1200, 1600), R3Window(4, false, R3AllowedPackages.UBER, 0, 0, 1200, 120),
        )
        val s = R3WindowSelector.select(w, 1200, 1600, emptyList(), 10_000)
        assertEquals(listOf(R3AllowedPackages.UBER, R3AllowedPackages.NINETY_NINE), s.targets.map { it.packageName })
        assertFalse(s.operationalAppVisible)
        assertTrue(R3WindowSelector.select(w + R3Window(5, true, "com.srrotas.app", 0, 0, 400, 400), 1200, 1600, emptyList(), 0).operationalAppVisible)
    }

    @Test fun rootlessWindowNeedsRecentEventFromTheSameWindow() {
        val w = listOf(R3Window(9, true, null, 0, 0, 1200, 1600))
        assertTrue(R3WindowSelector.select(w, 1200, 1600, emptyList(), 10_000).targets.isEmpty())
        val old = R3WindowSelector.EventEvidence(R3AllowedPackages.UBER, 9, 1_000)
        assertTrue(R3WindowSelector.select(w, 1200, 1600, listOf(old), 10_000).targets.isEmpty())
        val other = R3WindowSelector.EventEvidence(R3AllowedPackages.UBER, 8, 9_500)
        assertTrue(R3WindowSelector.select(w, 1200, 1600, listOf(other), 10_000).targets.isEmpty())
        val t = R3WindowSelector.select(w, 1200, 1600, listOf(R3WindowSelector.EventEvidence(R3AllowedPackages.UBER, 9, 9_000)), 10_000).targets.single()
        assertTrue(t.rootless)
    }

    // ---------------------------------------------------------------- filtro e fila
    @Test fun identicalFramesSkipOcrButAreRefreshedPeriodically() {
        val gate = R3FrameGate(threshold = 2.0, forceMs = 2_500)
        val a = IntArray(64 * 64) { 100 }; val b = IntArray(64 * 64) { if (it < 400) 255 else 100 }
        assertTrue(gate.evaluate("u#1", a, 0).process)
        assertFalse(gate.evaluate("u#1", a, 1_000).process)
        assertTrue(gate.evaluate("u#1", b, 1_200).process)
        assertEquals("forced_refresh", gate.evaluate("u#1", b, 3_800).reason)
        assertTrue("windows are independent", gate.evaluate("99#2", a, 3_900).process)
    }

    @Test fun profileReadsTallComfortCardAndPassesOfficialParserAndIntegrity() {
        val c = R3ProfileExtractor.extract(profile, card(), "frame-1", "obs-1", settings, 1188).single()
        assertTrue(c.rejection, c.accepted)
        assertEquals("37,27", c.fields["fare"]!!.value)
        assertEquals("8", c.fields["pickup_min"]!!.value); assertEquals("39", c.fields["trip_min"]!!.value)
        assertEquals("47", c.fields["total_min"]!!.value)
        assertTrue(c.fields["pickup_address"]!!.value!!.startsWith("Av. Exemplo"))
        assertTrue(c.fields["destination_address"]!!.value!!.startsWith("Rua Exemplo"))
        assertEquals(37.27, c.offer!!.fare, 0.001); assertEquals(47, c.offer!!.totalMinutes)
        assertTrue(c.fields.values.all { it.originFrameId == "frame-1" && it.observationId == "obs-1" && it.bounds != null })
        assertFalse("bonus is never the fare", c.fields.values.any { it.value == "3,50" })
    }

    @Test fun headerTotalThatDoesNotMatchTheLegsIsRejectedNotGuessed() {
        val c = R3ProfileExtractor.extract(profile, card(header = "60 min • 20.0 km • Est. de R$2,89/km"), "f", "o", settings, 1188).single()
        assertFalse(c.accepted); assertEquals("legs_disagree_with_total", c.rejection)
        assertNull(c.fields["pickup_min"])
    }

    @Test fun singleLegOrMissingLegsAreRejectedWithReason() {
        val one = card().filterNot { it.text.startsWith("O 39") }
        assertEquals("single_leg", R3ProfileExtractor.extract(profile, one, "f", "o", settings, 1188).single().rejection)
    }

    @Test fun hoursAreNeverReadAsMinutes() {
        val c = R3ProfileExtractor.extract(profile, card(trip = "O 1 h 10 min • 30.4 km", header = "1 h 18 min • 32.8 km • Est. de R$1,13/km"),
            "f", "o", settings, 1188).single()
        assertEquals("70", c.fields["trip_min"]!!.value)
        assertTrue(c.rejection, c.accepted)
    }

    @Test fun twoCardsSideBySideBecomeTwoCandidates() {
        val c = R3ProfileExtractor.extract(profile, card() + card(fare = "R$ 18,40", left = 640, pickup = "& 3 min • 1.0 km",
            trip = "O 12 minutos • 4.0 km", header = "15 min • 5.0 km • Est. de R$3,68/km"), "f", "o", settings, 1188)
        assertEquals(listOf("37,27", "18,40"), c.map { it.fields["fare"]!!.value })
        assertTrue(c.all { it.accepted })
    }

    @Test fun ninetyNineProfileIsIndependent() {
        val p99 = uber.activeFor(R3AllowedPackages.NINETY_NINE)!!
        assertEquals("99", p99.platform)
        assertNotEquals(profile.sha256, p99.sha256)
        assertNotNull(p99.match("leg", "4 min · 1,2 km"))
    }

    // ---------------------------------------------------------------- estabilização
    private fun cand(fare: String, accepted: Boolean = true, extra: Map<String, String> = emptyMap(), frame: String, obs: String, conf: Double = 0.9): R3Candidate {
        val base = mapOf("fare" to fare, "pickup_min" to "8", "pickup_km" to "2.4", "trip_min" to "39", "trip_km" to "10.5") + extra
        return R3Candidate("uber", base.mapValues { (k, v) -> R3FieldEvidence(k, frame, obs, v, R3FieldOrigin.OBSERVED, conf) }, null,
            if (accepted) null else "x", conf)
    }

    @Test fun sameCardAcrossFramesIsOneOfferWithRecoveredFieldFromItsRealFrame() {
        val s = R3Stabilizer(idleMs = 1_500, maxAgeMs = 6_000)
        s.observe("u#1", R3Stabilizer.Seen("f1", "o1", cand("37,27", frame = "f1", obs = "o1", conf = 0.95), 0))
        s.observe("u#1", R3Stabilizer.Seen("f2", "o2", cand("37,27", extra = mapOf("destination_address" to "Rua Exemplo, 76"), frame = "f2", obs = "o2", conf = 0.8), 400))
        assertTrue(s.flush(1_000).isEmpty())
        val stable = s.flush(2_000).single()
        assertEquals("o1", stable.primary.observationId)
        assertEquals(listOf("o2"), stable.supplemental.map { it.observationId })
        val dest = stable.fields["destination_address"]!!
        assertEquals(R3FieldOrigin.RECOVERED, dest.origin); assertEquals("f2", dest.originFrameId)
    }

    @Test fun differentOffersAndDifferentWindowsNeverMerge() {
        val s = R3Stabilizer()
        s.observe("u#1", R3Stabilizer.Seen("f1", "o1", cand("37,27", frame = "f1", obs = "o1"), 0))
        s.observe("u#1", R3Stabilizer.Seen("f1", "o1", cand("18,40", frame = "f1", obs = "o1"), 0))
        s.observe("99#2", R3Stabilizer.Seen("f3", "o3", cand("37,27", frame = "f3", obs = "o3"), 0))
        val out = s.flush(5_000)
        assertEquals(3, out.size)
        assertEquals(2, out.count { it.primary.frameId == "f1" })
    }

    // ---------------------------------------------------------------- regressões da revisão cruzada
    @Test fun fareWithThousandsIsReadWholeNeverTruncated() {
        assertEquals("1.234,56", profile.match("fare", "R$ 1.234,56")!!["fare"])
        assertEquals("37,27", profile.match("fare", "R$ 37,27")!!["fare"])
        assertNull(profile.match("fare", "R$ 1.2345"))
    }

    @Test fun hrAbbreviationIsHoursNotMinutes() {
        val c = R3ProfileExtractor.extract(profile, card(trip = "O 1 hr 10 min • 30.4 km", header = "1 h 18 min • 32.8 km • Est. de R$1,13/km"),
            "f", "o", settings, 1188).single()
        assertEquals("70", c.fields["trip_min"]!!.value)
    }

    @Test fun staticCardOnScreenForLongIsOneOfferAndReopeningIsSuppressed() {
        val s = R3Stabilizer()
        var t = 0L
        while (t <= 15_000) { s.observe("u#1", R3Stabilizer.Seen("f$t", "o$t", cand("37,27", frame = "f$t", obs = "o$t"), t)); assertTrue(s.flush(t).isEmpty()); t += 2_500 }
        assertEquals(1, s.flush(t + 4_000).size)
        assertFalse("same card seen again right after closing does not open a new offer",
            s.observe("u#1", R3Stabilizer.Seen("fx", "ox", cand("37,27", frame = "fx", obs = "ox"), t + 5_000)))
        assertTrue(s.observe("u#1", R3Stabilizer.Seen("fy", "oy", cand("37,27", frame = "fy", obs = "oy"), t + 30_000)))
    }

    @Test fun differentRejectedCardsWithSameFareAndReasonDoNotMerge() {
        fun rej(service: String, f: String) = R3Candidate("uber", mapOf(
            "fare" to R3FieldEvidence("fare", f, "o$f", "20,00", R3FieldOrigin.OBSERVED, 0.9),
            "service_type" to R3FieldEvidence("service_type", f, "o$f", service, R3FieldOrigin.OBSERVED, 0.8)), null, "single_leg", 0.4)
        assertNotEquals(rej("UberX", "a").identity("u#1"), rej("Comfort", "b").identity("u#1"))
    }

    @Test fun occludedTargetIsMarkedSoDisplayCropsSkipIt() {
        val uberW = R3Window(1, true, R3AllowedPackages.UBER, 0, 0, 1200, 1600, layer = 1)
        val bubble = R3Window(2, false, "com.example.chat", 900, 300, 1150, 550, layer = 5)
        val navBar = R3Window(3, false, null, 0, 1540, 1200, 1600, layer = 9, isSystem = true)
        assertTrue(R3WindowSelector.select(listOf(uberW, bubble), 1200, 1600, emptyList(), 0).targets.single().occluded)
        assertFalse(R3WindowSelector.select(listOf(uberW, navBar), 1200, 1600, emptyList(), 0).targets.single().occluded)
    }

    @Test fun modeCNeverStoresComputedTotalsAsObserved() {
        val root = generateSequence(java.io.File("").absoluteFile) { it.parentFile }.first { java.io.File(it, "app/build.gradle.kts").exists() }
        val src = java.io.File(root, "app/src/main/java/com/bigcorps/driveraimvp/r3lab/R3Interpretation.kt").readText()
            .substringAfter("object R3OfficialInterpretation")
        assertFalse(src.contains("ev(\"total_min\"") || src.contains("ev(\"total_km\""))
        val hooks = java.io.File(root, "app/src/main/java/com/bigcorps/driveraimvp/DriverAccessibilityService.kt").readText()
        assertFalse("operational builds never load R3Supervisor eagerly", hooks.contains("R3Supervisor.labBuild()"))
        // vc112: carregado só com R3Mode.hooksEnabled (lab ou R3 escolhido + aviso); desligamento não o carrega.
        assertEquals(2, Regex("R3Mode.hooksEnabled\\(this\\)\\) com.srrotas.app.r3lab.R3Supervisor").findAll(hooks).count())
        assertEquals(2, Regex("R3Mode.supervisorLoaded\\) com.srrotas.app.r3lab.R3Supervisor.detach").findAll(hooks).count())
    }
}
