package com.srrotas.app

import org.json.JSONArray
import org.json.JSONObject
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.time.Instant
import java.util.Locale

/** pt-BR presentation only. Money is one unbreakable unit: "R$ 1.236,79". */
object StatsFormat105 {
    const val NBSP = ' '
    private val symbols = DecimalFormatSymbols(Locale.forLanguageTag("pt-BR"))

    fun money(value: Double?): String {
        if (value == null || !value.isFinite()) return "—"
        val sign = if (value < 0) "-" else ""
        return "${sign}R$$NBSP${DecimalFormat("#,##0.00", symbols).format(kotlin.math.abs(value))}"
    }

    /** Axis ticks: whole reais, still unbreakable. */
    fun moneyShort(value: Double): String = DecimalFormat("#,##0", symbols).format(value)

    fun number(value: Double?, decimals: Int = 1): String {
        if (value == null || !value.isFinite()) return "—"
        val pattern = if (decimals <= 0) "#,##0" else "#,##0." + "#".repeat(decimals)
        return DecimalFormat(pattern, symbols).format(value)
    }

    fun hours(value: Double?): String = if (value == null || !value.isFinite()) "—" else "${number(value, 1)}${NBSP}h"
    fun km(value: Double?): String = if (value == null || !value.isFinite()) "—" else "${number(value, 1)}${NBSP}km"

    fun pct(value: Double?): String {
        if (value == null || !value.isFinite()) return "—"
        return "${if (value > 0) "+" else if (value < 0) "−" else ""}${number(kotlin.math.abs(value), 1)}%"
    }

    /** Snapshot strings come from the server; only keep them on one line. */
    fun unbreakable(text: String): String = text.replace("R$ ", "R$$NBSP").replace(" km", "${NBSP}km").replace(" h", "${NBSP}h")
}

data class SeriesBucket105(
    val key: String,
    val label: String,
    val weekday: String,
    val date: String,
    val partialWindow: Boolean,
    val value: Double?,
    val status: String,
    val coverage: Double?,
) {
    /** Only explicit server states may draw a value; null always stays a gap. */
    val drawable: Boolean get() = value != null && value.isFinite() && value >= 0 && status in setOf("complete", "partial", "no_activity")
}

data class DailySeries105(
    val metric: String,
    val semantic: String,
    val unit: String,
    val status: String,
    val completeness: String?,
    val coverage: Double?,
    val sampleCount: Int,
    val total: Double?,
    val limitations: List<String>,
    val buckets: List<SeriesBucket105>,
) {
    val hasValues: Boolean get() = status != "unavailable" && buckets.any { it.drawable && it.status != "no_activity" }
}

data class GroupRow105(val label: String, val rides: Int, val ridesWithFare: Int, val revenue: Double?, val averageFare: Double?)

data class RidesSummary105(
    val status: String,
    val completed: Int,
    val withKnownFare: Int,
    val averageFare: Double?,
    val maxFare: Double?,
    val minFare: Double?,
)

data class RevenueComparison105(val eligible: Boolean, val changePct: Double?, val previousTotal: Double?)

data class StatisticsSeries105(
    val from: String,
    val to: String,
    val timeZone: String,
    val series: Map<String, DailySeries105>,
    val rides: RidesSummary105?,
    val byService: List<GroupRow105>?,
    val byDistance: List<GroupRow105>?,
    val comparison: RevenueComparison105?,
    val insight: String?,
) {
    fun matches(from: String, to: String): Boolean = runCatching {
        Instant.parse(this.from) == Instant.parse(from) && Instant.parse(this.to) == Instant.parse(to)
    }.getOrDefault(false)

    operator fun get(metric: String): DailySeries105? = series[metric]

    companion object {
        const val SCHEMA = "srrotas-statistics-series-v1"
        /** Every metric must carry exactly this semantic; offers can never become revenue. */
        val semantics = linkedMapOf(
            "realized_revenue" to "REALIZED_REVENUE",
            "completed_rides" to "REALIZED_RIDE",
            "closed_journey_hours" to "MIXED",
            "actual_distance" to "ACTUAL_DISTANCE",
            "observed_offers" to "OBSERVED_OFFER",
        )
        private val statuses = setOf("complete", "partial", "unavailable")
        private val bucketStatuses = setOf("complete", "partial", "unavailable", "no_activity")

        fun parse(body: JSONObject): StatisticsSeries105 {
            require(body.getString("schema") == SCHEMA)
            val period = body.getJSONObject("period")
            val from = period.getString("from")
            val to = period.getString("to")
            require(Instant.parse(from) < Instant.parse(to))
            val seriesJson = body.getJSONObject("series")
            val series = semantics.mapNotNull { (key, semantic) ->
                val s = seriesJson.optJSONObject(key) ?: return@mapNotNull null
                if (s.optString("metric") != key || s.optString("semantic") != semantic) return@mapNotNull null
                val status = s.optString("status").takeIf { it in statuses } ?: return@mapNotNull null
                val buckets = s.optJSONArray("buckets") ?: return@mapNotNull null
                key to DailySeries105(
                    key, semantic, s.optString("unit"), status,
                    s.stringOrNull("completeness")?.takeIf { it in setOf("complete", "partial") },
                    s.numberOrNull("coverage")?.takeIf { it in 0.0..1.0 },
                    s.optInt("sampleCount", 0).coerceAtLeast(0),
                    s.numberOrNull("total")?.takeIf { it >= 0 },
                    strings(s.optJSONArray("limitations")),
                    (0 until buckets.length()).mapNotNull { buckets.optJSONObject(it) }.mapNotNull { b ->
                        val bucketStatus = b.optString("status").takeIf { it in bucketStatuses } ?: return@mapNotNull null
                        val value = b.numberOrNull("value")
                        SeriesBucket105(
                            b.optString("key"), b.optString("label"), b.optString("weekday"), b.optString("date"),
                            b.optBoolean("partialWindow", false),
                            // A value under an unavailable state is ignored rather than trusted.
                            if (bucketStatus == "unavailable") null else value,
                            bucketStatus, b.numberOrNull("coverage"),
                        )
                    },
                )
            }.toMap()
            val rides = body.optJSONObject("rides")?.let {
                RidesSummary105(
                    it.optString("status", "unavailable"), it.optInt("completed", 0), it.optInt("withKnownFare", 0),
                    it.numberOrNull("averageFare"), it.numberOrNull("maxFare"), it.numberOrNull("minFare"),
                )
            }
            val breakdowns = body.optJSONObject("breakdowns")
            val comparison = body.optJSONObject("comparison")?.let {
                val eligible = it.optBoolean("eligible", false)
                RevenueComparison105(eligible, if (eligible) it.numberOrNull("changePct") else null, if (eligible) it.numberOrNull("previousTotal") else null)
            }
            return StatisticsSeries105(
                from, to, body.optString("time_zone", "America/Sao_Paulo"), series, rides,
                groups(breakdowns?.optJSONArray("service_type")), groups(breakdowns?.optJSONArray("distance_band")),
                comparison, body.optJSONObject("insight")?.stringOrNull("text"),
            )
        }

        private fun groups(a: JSONArray?): List<GroupRow105>? = a?.let {
            (0 until it.length()).mapNotNull { i -> it.optJSONObject(i) }.map { g ->
                GroupRow105(g.optString("label"), g.optInt("rides"), g.optInt("ridesWithFare"), g.numberOrNull("revenue"), g.numberOrNull("averageFare"))
            }
        }

        private fun strings(a: JSONArray?): List<String> =
            (0 until (a?.length() ?: 0)).mapNotNull { (a?.opt(it) as? String)?.takeIf(String::isNotBlank) }

        private fun JSONObject.numberOrNull(key: String): Double? =
            if (!has(key) || isNull(key)) null else (opt(key) as? Number)?.toDouble()?.takeIf { it.isFinite() }

        private fun JSONObject.stringOrNull(key: String): String? =
            if (!has(key) || isNull(key)) null else (opt(key) as? String)?.takeIf(String::isNotBlank)
    }
}

/** Facts for "Resumo da comparação": maxima and paired coverage only, no inference. */
object CrossSummary105 {
    data class Point(val label: String, val weekdayName: String, val a: Double?, val b: Double?)

    fun facts(points: List<Point>, aName: String, bName: String, formatA: (Double) -> String, formatB: (Double) -> String): String? {
        val paired = points.filter { it.a != null && it.b != null }
        if (paired.size < 2) return null
        val bestA = points.filter { it.a != null && it.a > 0 }.let { list -> list.maxByOrNull { it.a!! }?.takeIf { top -> list.count { it.a == top.a } == 1 } }
        val bestB = points.filter { it.b != null && it.b > 0 }.let { list -> list.maxByOrNull { it.b!! }?.takeIf { top -> list.count { it.b == top.b } == 1 } }
        val parts = mutableListOf<String>()
        if (bestA != null && bestB != null && bestA.label == bestB.label) {
            parts += "O maior valor de $aName e de $bName ocorreu no mesmo dia: ${bestA.weekdayName} (${formatA(bestA.a!!)} · ${formatB(bestB.b!!)})."
        } else {
            bestA?.let { parts += "Maior valor de $aName: ${it.weekdayName} (${formatA(it.a!!)})." }
            bestB?.let { parts += "Maior valor de $bName: ${it.weekdayName} (${formatB(it.b!!)})." }
        }
        parts += "${paired.size} de ${points.size} dias têm as duas medidas."
        return parts.joinToString(" ")
    }

    fun weekdayName(short: String): String = when (short) {
        "Dom" -> "domingo"; "Seg" -> "segunda-feira"; "Ter" -> "terça-feira"; "Qua" -> "quarta-feira"
        "Qui" -> "quinta-feira"; "Sex" -> "sexta-feira"; "Sáb" -> "sábado"; else -> short
    }
}
