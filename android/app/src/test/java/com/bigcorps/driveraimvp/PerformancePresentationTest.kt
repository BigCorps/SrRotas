package com.srrotas.app

import org.junit.Assert.*
import org.junit.Test
import org.json.JSONArray
import org.json.JSONObject

class PerformancePresentationTest {
    private val from = "2026-10-17T12:00:00Z"
    private val to = "2026-10-24T12:00:00Z"
    private fun reading(key: String = "completed_rides", status: String = "available", completeness: String = "complete") =
        PerformanceReading(key, "0", if (status == "available") 0.0 else null, PerformanceSnapshot.semantics[key], status,
            "personal_canonical+outcomes+journey_metrics", 0, completeness, null)
    private fun snapshot(vararg readings: PerformanceReading) = PerformanceSnapshot(from, to, readings.associateBy { it.key }, emptyList())

    @Test fun explicitJourneyCauseOffersActionAndInvokesTheSameCallbackUsedByDialog() {
        val visited = mutableListOf<PerformanceDestination>()
        val action = PerformancePresentation.warningAction("actual_distance", null, setOf(PerformanceDataIssue.ODOMETER)) { visited += it }
        assertNotNull(action)
        assertTrue(visited.isEmpty())
        action!!.invoke()
        assertEquals(listOf(PerformanceDestination.JOURNEYS), visited)
        assertNull(PerformancePresentation.warningAction("completed_rides", null, setOf(PerformanceDataIssue.ODOMETER)) { fail() })
    }

    @Test fun historicalOrUnknownAbsenceHasNoInventedResolution() {
        assertNull(PerformancePresentation.warningAction("actual_distance", null, emptySet()) { fail("No destination should exist") })
        for (problem in listOf(PerformanceProblem.LOADING, PerformanceProblem.CONNECTION, PerformanceProblem.SERVER,
            PerformanceProblem.INVALID_PAYLOAD, PerformanceProblem.PERIOD_MISMATCH)) {
            assertNull(PerformancePresentation.warningAction("actual_distance", problem, setOf(PerformanceDataIssue.ODOMETER)) { fail() })
        }
    }

    @Test fun accessAndConfigurationActionsUseExistingScreens() {
        for (problem in listOf(PerformanceProblem.NO_SESSION, PerformanceProblem.UNAUTHORIZED, PerformanceProblem.ACCESS_RESTRICTED, PerformanceProblem.FORBIDDEN)) {
            var destination: PerformanceDestination? = null
            PerformancePresentation.warningAction("estimated_profit", problem, emptySet()) { destination = it }!!.invoke()
            assertEquals(PerformanceDestination.ACCOUNT, destination)
        }
        assertEquals(PerformanceDestination.SETTINGS, PerformancePresentation.warningDestination("", PerformanceProblem.NO_CONFIGURATION, emptySet()))
    }

    @Test fun loadingAndHttpErrorsAreNotHistoricalPendingData() {
        val stale = snapshot(reading(status = "unavailable", completeness = "partial"))
        val loading = PerformancePresentation.notice(stale, PerformanceProblem.LOADING)
        assertEquals("Consultando resumo…", loading.title)
        assertNull(loading.banner)
        for (problem in listOf(PerformanceProblem.NO_SESSION, PerformanceProblem.ACCESS_RESTRICTED, PerformanceProblem.UNAUTHORIZED,
            PerformanceProblem.FORBIDDEN, PerformanceProblem.CONNECTION, PerformanceProblem.PERIOD_MISMATCH)) {
            assertNull(PerformancePresentation.notice(stale, problem).banner)
        }
        assertEquals("Acesso às estatísticas restrito", PerformancePresentation.notice(null, PerformanceProblem.ACCESS_RESTRICTED).title)
    }

    @Test fun pendingBannerCountsOnlyExplicitStatesAndNotMissingKeys() {
        assertNull(PerformancePresentation.notice(snapshot(), null).banner)
        val allUnavailable = snapshot(*PerformancePresentation.titles.keys.map { reading(it, "unavailable") }.toTypedArray())
        assertEquals("7 indicadores indisponíveis", PerformancePresentation.notice(allUnavailable, null).banner)
        val partial = snapshot(reading(completeness = "partial"))
        assertEquals("Dados parciais", PerformancePresentation.notice(partial, null).title)
        assertEquals("1 indicador com dados parciais", PerformancePresentation.notice(partial, null).banner)
        assertEquals("Dados do período", PerformancePresentation.notice(snapshot(reading()), null).title) // Explicit zero is valid.
        assertEquals("Sem dados suficientes", PerformancePresentation.notice(snapshot(), null).title)
    }

    @Test fun unavailableAndPartialAreDisjointAcrossTheSevenMainIndicators() {
        val both = snapshot(reading(status = "unavailable", completeness = "partial"))
        assertEquals("1 indicador indisponível", PerformancePresentation.notice(both, null).banner)
        val seven = snapshot(*PerformancePresentation.titles.keys.map { reading(it, "unavailable", "partial") }.toTypedArray())
        assertEquals("7 indicadores indisponíveis", PerformancePresentation.notice(seven, null).banner)
        val mixed = snapshot(reading(status = "unavailable", completeness = "partial"), reading("actual_distance", completeness = "partial"))
        assertEquals("1 indicador indisponível · 1 indicador com dados parciais", PerformancePresentation.notice(mixed, null).banner)
    }

    @Test fun availabilityAndCountsIgnoreAuxiliaryMetrics() {
        // Even a displayable auxiliary entry cannot stand in for a main KPI.
        val auxiliaryOnly = PerformanceSnapshot(from, to, mapOf("offers" to reading()), emptyList())
        assertEquals("Sem dados suficientes", PerformancePresentation.notice(auxiliaryOnly, null).title)
        assertNull(PerformancePresentation.notice(auxiliaryOnly, null).banner)
        val mainAndAuxiliary = auxiliaryOnly.copy(metrics = auxiliaryOnly.metrics +
            ("completed_rides" to reading(status = "unavailable", completeness = "partial")))
        assertEquals("Sem dados suficientes", PerformancePresentation.notice(mainAndAuxiliary, null).title)
        assertEquals("1 indicador indisponível", PerformancePresentation.notice(mainAndAuxiliary, null).banner)
        val mainZero = auxiliaryOnly.copy(metrics = auxiliaryOnly.metrics + ("completed_rides" to reading()))
        assertEquals("Dados do período", PerformancePresentation.notice(mainZero, null).title)
    }

    @Test fun cardsOnlyLinkToRealRelatedSections() {
        assertEquals(PerformanceDestination.RIDES, PerformancePresentation.cardDestination("completed_rides"))
        assertEquals(PerformanceDestination.RIDES, PerformancePresentation.cardDestination("realized_revenue"))
        assertEquals(PerformanceDestination.JOURNEYS, PerformancePresentation.cardDestination("closed_journey_hours"))
        assertEquals(PerformanceDestination.JOURNEYS, PerformancePresentation.cardDestination("actual_distance"))
        assertNull(PerformancePresentation.cardDestination("estimated_profit"))
        assertNull(PerformancePresentation.cardDestination("invented_report"))
    }

    @Test fun driverFacingSourcesDoNotExposeKeysOrRawOriginStrings() {
        for ((key, title) in PerformancePresentation.titles) { assertFalse(title.contains(key)); assertFalse(title.contains("_")) }
        assertFalse(PerformancePresentation.sourceLabel("personal_canonical+outcomes+journey_metrics").contains("personal_canonical"))
        assertFalse(PerformancePresentation.sourceLabel("unknown_token_secret").contains("unknown_token_secret"))
        val text = PerformancePresentation.evidenceText(reading().copy(coverage = 0.75, sampleCount = 24))
        assertTrue(text.contains("75%"))
        assertTrue(text.contains("24 registro(s)"))
        assertTrue(text.contains("completa"))
        assertFalse(text.contains("0 a 1"))
    }


    private fun payload(): JSONObject {
        val summary = JSONObject().put("version", "srrotas-natural-question-v1").put("intent", "PERIOD_SUMMARY").put("status", "ok")
            .put("evidence", JSONObject().put("semantic", "MIXED").put("source", "personal_canonical+outcomes+journey_metrics")
                .put("period", JSONObject().put("from", from).put("to", to)))
            .put("details", JSONObject().put("productivity", JSONObject().put("journeyCount", 3).put("closedJourneyCount", 2))
                .put("distance_coverage", JSONObject().put("numerator", 1).put("denominator", 3)))
        return JSONObject().put("snapshot", JSONObject().put("schema", PerformanceSnapshot.SCHEMA)
            .put("period", JSONObject().put("from", from).put("to", to)).put("metrics", JSONObject()))
            .put("answers", JSONArray().put(summary))
    }

    @Test fun optionalGuidanceRequiresExplicitSamePeriodEvidence() {
        val body = payload()
        assertEquals(setOf(PerformanceDataIssue.JOURNEY_DURATION, PerformanceDataIssue.ODOMETER), PerformanceSnapshotJson.parse(body).issues)
        body.getJSONArray("answers").getJSONObject(0).getJSONObject("evidence").getJSONObject("period").put("from", "2026-10-16T12:00:00Z")
        assertTrue(PerformanceSnapshotJson.parse(body).issues.isEmpty())
        val unknownSource = payload()
        unknownSource.getJSONArray("answers").getJSONObject(0).getJSONObject("evidence").put("source", "other_source")
        assertTrue(PerformanceSnapshotJson.parse(unknownSource).issues.isEmpty())
    }

    @Test fun missingMalformedOrCompleteRecordsNeverBecomeMissingOdometerOrOpenJourneys() {
        val body = payload()
        val details = body.getJSONArray("answers").getJSONObject(0).getJSONObject("details")
        details.getJSONObject("productivity").put("closedJourneyCount", "unknown")
        details.getJSONObject("distance_coverage").put("numerator", JSONObject.NULL)
        assertTrue(PerformanceSnapshotJson.parse(body).issues.isEmpty())
        details.getJSONObject("productivity").put("closedJourneyCount", 3)
        details.getJSONObject("distance_coverage").put("numerator", 3)
        assertTrue(PerformanceSnapshotJson.parse(body).issues.isEmpty())
        body.remove("answers")
        assertTrue(PerformanceSnapshotJson.parse(body).issues.isEmpty())
    }
}
