package com.srrotas.app

import org.junit.Assert.*
import org.junit.Test
import org.json.JSONObject

class PerformanceSnapshotTest {
    private val from = "2026-10-17T12:00:00.000Z"
    private val to = "2026-10-24T12:00:00.000Z"
    private fun reading(key: String = "completed_rides") = PerformanceReading(
        key, "0", 0.0, PerformanceSnapshot.semantics[key], "available", "personal_canonical+outcomes+journey_metrics", 0, "complete", null)

    @Test fun explicitZeroIsPreservedButMissingAndUnavailableNeverBecomeZero() {
        assertEquals("0", reading().display())
        assertEquals("—", reading().copy(value = null).display())
        assertEquals("—", reading().copy(status = "unavailable").display())
        assertEquals("—", reading().copy(value = Double.NaN).display())
        assertEquals("—", reading().copy(value = -1.0).display())
        assertEquals("R$ -1,00", reading("estimated_profit").copy(value = -1.0, formatted = "R$ -1,00").display())
    }

    @Test fun offersNeverMasqueradeAsRealizedRevenue() {
        val revenue = reading("realized_revenue").copy(formatted = "R$ 0,00", coverage = 1.0)
        assertEquals("R$ 0,00", revenue.display())
        assertEquals("—", revenue.copy(semantic = "OBSERVED_OFFER").display())
        assertEquals("—", revenue.copy(coverage = null).display())
        assertEquals("—", revenue.copy(coverage = 0.0).display())
        assertEquals("—", reading("offers").display())
    }

    @Test fun partialReadingsKeepTheirStateButRatesAndProfitRequireCompleteEvidence() {
        assertEquals("0", reading().copy(completeness = "partial").display())
        for (key in listOf("estimated_profit", "realized_revenue_per_hour", "realized_revenue_per_km")) {
            assertEquals("—", reading(key).copy(completeness = "partial").display())
        }
        assertEquals("—", reading().copy(completeness = null).display())
        assertEquals("—", reading().copy(sampleCount = null).display())
        assertEquals("—", reading().copy(source = "").display())
        assertEquals("—", reading().copy(coverage = 1.1).display())
    }

    @Test fun exactIntervalIsRequiredIncludingSubsecondBoundaries() {
        val snapshot = PerformanceSnapshot(from, to, emptyMap(), emptyList())
        assertTrue(snapshot.matches(from.replace(".000Z", "Z"), to))
        assertFalse(snapshot.matches("2026-10-17T12:00:00.001Z", to))
        assertFalse(snapshot.matches("invalid", to))
    }

    private fun payload() = JSONObject().put("snapshot", JSONObject()
        .put("schema", PerformanceSnapshot.SCHEMA)
        .put("period", JSONObject().put("from", from).put("to", to))
        .put("metrics", JSONObject().put("completed_rides", JSONObject()
            .put("key", "completed_rides").put("formatted", "0").put("value", 0)
            .put("semantic", "REALIZED_RIDE").put("status", "available")
            .put("source", "personal_canonical+outcomes+journey_metrics").put("sampleCount", 0)
            .put("completeness", "complete").put("coverage", JSONObject.NULL)))
        .put("limitations", org.json.JSONArray().put("Ofertas não comprovam corridas.")))

    @Test fun parserPreservesZeroNullUnavailableAndPartialContract() {
        val body = payload()
        assertEquals("0", PerformanceSnapshotJson.parse(body).metrics.getValue("completed_rides").display())
        val metric = body.getJSONObject("snapshot").getJSONObject("metrics").getJSONObject("completed_rides")
        metric.put("value", JSONObject.NULL)
        assertEquals("—", PerformanceSnapshotJson.parse(body).metrics.getValue("completed_rides").display())
        metric.put("value", 0).put("status", "unavailable")
        assertEquals("—", PerformanceSnapshotJson.parse(body).metrics.getValue("completed_rides").display())
        metric.put("status", "available").put("completeness", "partial")
        assertEquals("partial", PerformanceSnapshotJson.parse(body).metrics.getValue("completed_rides").completeness)
    }

    @Test fun malformedMetricIsOmittedAndUnexpectedSchemaIsRejected() {
        val body = payload()
        body.getJSONObject("snapshot").getJSONObject("metrics").getJSONObject("completed_rides").put("coverage", "unknown")
        assertTrue(PerformanceSnapshotJson.parse(body).metrics.isEmpty())
        body.getJSONObject("snapshot").put("schema", "unexpected")
        assertThrows(IllegalArgumentException::class.java) { PerformanceSnapshotJson.parse(body) }
    }

    @Test fun httpAuthorizationAndNetworkFailuresRemainDistinct() {
        assertNull(PerformanceFailure.forStatus(200))
        assertEquals(PerformanceProblem.UNAUTHORIZED, PerformanceFailure.forStatus(401))
        assertEquals(PerformanceProblem.FORBIDDEN, PerformanceFailure.forStatus(403))
        assertEquals(PerformanceProblem.SERVER, PerformanceFailure.forStatus(500))
        assertEquals(PerformanceProblem.CONNECTION, PerformanceFailure.classify(java.net.SocketTimeoutException()).problem)
        assertEquals(PerformanceProblem.CONNECTION, PerformanceFailure.classify(java.net.UnknownHostException()).problem)
        assertEquals(PerformanceProblem.INVALID_PAYLOAD, PerformanceFailure.classify(IllegalArgumentException()).problem)
    }

    @Test fun errorsRemainDistinctAndSafeToDisplay() {
        assertNotEquals(PerformanceProblem.UNAUTHORIZED.explanation, PerformanceProblem.FORBIDDEN.explanation)
        assertNotEquals(PerformanceProblem.CONNECTION.explanation, PerformanceProblem.INVALID_PAYLOAD.explanation)
        assertFalse(PerformanceFailure(PerformanceProblem.CONNECTION).message!!.contains("token"))
    }
}
