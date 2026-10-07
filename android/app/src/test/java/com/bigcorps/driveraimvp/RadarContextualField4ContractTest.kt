package com.srrotas.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class RadarContextualField4ContractTest {
    private val androidRoot: File by lazy {
        val cwd = File(System.getProperty("user.dir") ?: ".").absoluteFile
        generateSequence(cwd) { it.parentFile }
            .firstOrNull { base ->
                File(base, "app/build.gradle.kts").isFile &&
                    File(base, "app/src/main/java/com/bigcorps/driveraimvp/SrRotasApplication.kt").isFile
            }
            ?: error("Raiz Android não encontrada.")
    }

    private val sourceRoot = File(androidRoot, "app/src/main/java/com/bigcorps/driveraimvp")
    private fun source(name: String) = File(sourceRoot, name).readText()

    @Test
    fun eachOpenCreatesLoadingAndRejectsObsoleteSuccessAndFailure() {
        val panel = source("RadarContextualPanelV1.kt")
        val open = panel.substringAfter("fun openDestination(").substringBefore("fun refresh()")
        assertFalse(panel.contains("private val status"))
        assertTrue(open.contains("val loading = SrUi023.body"))
        assertTrue(open.contains("addView(loading)"))
        assertTrue(open.contains("val generation = ++requestGeneration"))
        assertTrue(open.contains("generation != requestGeneration"))
        assertTrue(open.contains("spec?.localOfferId != value.localOfferId"))
        assertTrue(open.contains("RadarDestinationContextV1.current(context)?.localOfferId != value.localOfferId"))
        assertFalse(open.substringBefore("response.onSuccess").contains("!isShown"))
        assertTrue(open.contains("renderOrDefer()"))
        assertTrue(open.indexOf("return@fetch") < open.indexOf("response.onSuccess"))
        for (entry in listOf("fun openDemo()", "private fun renderIdle()", "override fun onDetachedFromWindow()")) {
            assertTrue(panel.substringAfter(entry).substringBefore("\n    }").contains("invalidateRequests()"))
        }
        assertTrue(panel.substringAfter("override fun onVisibilityChanged").substringBefore("fun openDestination").contains("releaseMap(\"hidden\")"))
    }

    @Test
    fun lazyMapAndObservationalReadersRemainPreserved() {
        val panel = source("RadarContextualPanelV1.kt")
        assertTrue(panel.contains("private var map: RadarMiniMapViewV1? = null"))
        assertTrue(panel.contains("private fun ensureMap()"))
        assertTrue(panel.contains("private fun releaseMap(reason:"))
        assertTrue(panel.contains("renderPendingIfReady()"))
        assertFalse(panel.substringAfter("init {").substringBefore("override fun onDetachedFromWindow").contains("ensureMap()"))
        assertTrue(source("Reader2Accumulator032.kt").contains("put(\"promotion_effect\", false)"))
        assertTrue(source("Reader2Consensus0321.kt").contains("put(\"controlled_hybrid_effect\", false)"))
    }

    @Test
    fun diagnosticSeparatesRejectKindsAndRetainsSpatialEvidenceAndReason() {
        val trace = source("RadarHudTrace024.kt")
        assertTrue(trace.contains("candidate_no_offer_count"))
        assertTrue(trace.contains("integrity_reject_count"))
        assertTrue(trace.contains("event.optInt(\"blocked_offers\", -1) > 0 -> \"integrity_reject\""))
        assertTrue(trace.contains("event.optInt(\"blocked_offers\", -1) == 0 -> \"candidate_no_offer\""))
        assertTrue(trace.contains("!event.has(\"blocked_offers\")"))
        assertTrue(trace.contains("parse_reject_reason_counts"))
        assertTrue(trace.contains("put(\"reject_kind\", it)"))
        for (metric in listOf("fare_lines_zero", "fare_present_cluster_zero", "uber_anchor_samples", "uber_anchor_geometry_pairs_lt_2", "99_anchor_samples", "navigation_noise_samples")) {
            assertTrue(trace.contains(metric))
        }
    }
}
