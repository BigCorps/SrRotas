package com.srrotas.app

import org.json.JSONObject

object PerformanceSnapshotJson {
    fun parse(body: JSONObject): PerformanceSnapshot {
        val snapshot = body.getJSONObject("snapshot")
        require(snapshot.getString("schema") == PerformanceSnapshot.SCHEMA)
        val period = snapshot.getJSONObject("period")
        val from = period.getString("from")
        val to = period.getString("to")
        require(java.time.Instant.parse(from) < java.time.Instant.parse(to))
        val metrics = snapshot.getJSONObject("metrics")
        val readings = PerformanceSnapshot.semantics.keys.mapNotNull { key ->
            val metric = metrics.optJSONObject(key) ?: return@mapNotNull null
            if (metric.optString("key") != key) return@mapNotNull null
            if (listOf("formatted", "status").any { metric.opt(it) !is String }) return@mapNotNull null
            if (listOf("semantic", "source", "completeness").any { !metric.isNull(it) && metric.opt(it) !is String }) return@mapNotNull null
            // Null is an explicit absence; a wrong type must never become known coverage.
            if (!metric.has("coverage") || (!metric.isNull("coverage") && metric.opt("coverage") !is Number)) return@mapNotNull null
            fun number(name: String) = (metric.opt(name) as? Number)?.toDouble()
            val sample = number("sampleCount")
            key to PerformanceReading(key, metric.optString("formatted", "—"), number("value"),
                metric.opt("semantic") as? String, metric.optString("status"),
                metric.opt("source") as? String, sample?.takeIf { it.isFinite() && it >= 0 && it <= Int.MAX_VALUE && it % 1.0 == 0.0 }?.toInt(),
                metric.opt("completeness") as? String, number("coverage"))
        }.toMap()
        val limitations = snapshot.optJSONArray("limitations")
        return PerformanceSnapshot(from, to, readings, (0 until (limitations?.length() ?: 0)).mapNotNull {
            (limitations?.opt(it) as? String)?.takeIf(String::isNotBlank)
        }, parseIssues(body, from, to))
    }

    /** Optional guidance from existing structured answers; never inferred from missing KPI values. */
    private fun parseIssues(body: JSONObject, from: String, to: String): Set<PerformanceDataIssue> {
        val answers = body.optJSONArray("answers") ?: return emptySet()
        val summary = (0 until answers.length()).mapNotNull { answers.optJSONObject(it) }.firstOrNull {
            it.optString("version") == "srrotas-natural-question-v1" && it.optString("intent") == "PERIOD_SUMMARY" &&
                it.optString("status") in setOf("ok", "insufficient")
        } ?: return emptySet()
        val evidence = summary.optJSONObject("evidence") ?: return emptySet()
        if (evidence.optString("source") != "personal_canonical+outcomes+journey_metrics" || evidence.optString("semantic") != "MIXED") return emptySet()
        val period = evidence.optJSONObject("period") ?: return emptySet()
        val samePeriod = PerformanceSnapshot(period.optString("from"), period.optString("to"), emptyMap(), emptyList()).matches(from, to)
        if (!samePeriod) return emptySet()
        val details = summary.optJSONObject("details") ?: return emptySet()
        fun count(objectValue: JSONObject?, key: String): Double? = (objectValue?.opt(key) as? Number)?.toDouble()
            ?.takeIf { it.isFinite() && it >= 0 && it <= Int.MAX_VALUE && it % 1.0 == 0.0 }
        return buildSet {
            val productivity = details.optJSONObject("productivity")
            val total = count(productivity, "journeyCount")
            val closed = count(productivity, "closedJourneyCount")
            if (total != null && closed != null && total > 0 && closed < total) add(PerformanceDataIssue.JOURNEY_DURATION)
            val distance = details.optJSONObject("distance_coverage")
            val denominator = count(distance, "denominator")
            val numerator = count(distance, "numerator")
            if (denominator != null && numerator != null && denominator > 0 && numerator < denominator) add(PerformanceDataIssue.ODOMETER)
        }
    }
}
