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
        })
    }
}
