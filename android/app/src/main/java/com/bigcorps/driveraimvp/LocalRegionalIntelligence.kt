package com.srrotas.app

import android.content.Context
import java.time.Instant
import kotlin.math.round

object LocalRegionalIntelligence {
    private const val MIN_PROBABILITY_SAMPLES = RegionalProbability.MIN_PROBABILITY_SAMPLES

    private data class Exposure(
        val journeyId: String,
        val cell: String,
        val startedAt: String,
        val durationSeconds: Long,
        val closeReason: String,
        val nextOfferLocalId: String?,
    )

    private data class LinkedOffer(
        val perKm: Double?,
        val perMinute: Double?,
        val serviceType: String,
    )

    fun build(
        context: Context,
        days: Int,
    ): RegionalIntelligenceAnalytics {
        val safeDays = days.coerceIn(1, 90)
        val to = Instant.now()
        val from = to.minusSeconds(safeDays.toLong() * 86400L)
        val db = LocalStore.get(context).readableDatabase

        val exposures = mutableListOf<Exposure>()
        db.query(
            "local_zone_exposure",
            arrayOf(
                "journey_id",
                "cell",
                "started_at",
                "duration_seconds",
                "close_reason",
                "next_offer_local_id",
            ),
            "ended_at is not null and started_at >= ? and started_at < ?",
            arrayOf(from.toString(), to.toString()),
            null,
            null,
            "started_at asc",
            "5000",
        ).use { c ->
            while (c.moveToNext()) {
                exposures += Exposure(
                    journeyId = c.getString(0),
                    cell = c.getString(1),
                    startedAt = c.getString(2),
                    durationSeconds =
                        if (c.isNull(3)) 0L else c.getLong(3),
                    closeReason =
                        if (c.isNull(4)) "unknown" else c.getString(4),
                    nextOfferLocalId =
                        if (c.isNull(5)) null else c.getString(5),
                )
            }
        }

        val linked = mutableMapOf<String, LinkedOffer>()
        db.query(
            "local_offers",
            arrayOf(
                "local_id",
                "per_km",
                "per_minute",
                "service_type",
            ),
            "observed_at >= ? and observed_at < ?",
            arrayOf(from.toString(), to.toString()),
            null,
            null,
            null,
            "5000",
        ).use { c ->
            while (c.moveToNext()) {
                linked[c.getString(0)] = LinkedOffer(
                    perKm = if (c.isNull(1)) null else c.getDouble(1),
                    perMinute =
                        if (c.isNull(2)) null else c.getDouble(2),
                    serviceType =
                        if (c.isNull(3)) "unknown" else c.getString(3),
                )
            }
        }

        val destinationCounts = mutableMapOf<String, Int>()
        val importQuality = mutableListOf<String>()

        db.rawQuery(
            """
            select c.destination_cell
            from local_offers o
            left join local_offer_context c
              on c.local_offer_id = o.local_id
            where o.observed_at >= ? and o.observed_at < ?
            limit 5000
            """.trimIndent(),
            arrayOf(from.toString(), to.toString()),
        ).use { c ->
            while (c.moveToNext()) {
                if (!c.isNull(0)) {
                    val cell = c.getString(0)
                    destinationCounts[cell] =
                        (destinationCounts[cell] ?: 0) + 1
                }
            }
        }

        db.query(
            "local_offers",
            arrayOf("capture_method"),
            "capture_method like ?",
            arrayOf("historical-import/%"),
            null,
            null,
            null,
            "5000",
        ).use { c ->
            while (c.moveToNext()) {
                importQuality += c.getString(0)
                    .removePrefix("historical-import/")
            }
        }

        val cleaned = collapseOfferBursts(exposures)
        val statisticalExposures = cleaned.first

        val rows = statisticalExposures
            .groupBy { it.cell }
            .map { (cell, group) ->
                val offerHits =
                    group.count { isOfferHit(it) }
                val linkedRows =
                    group.mapNotNull {
                        it.nextOfferLocalId?.let(linked::get)
                    }
                val times =
                    group.filter(::isOfferHit)
                        .map {
                            it.durationSeconds.toDouble() / 60.0
                        }

                RegionalCellAnalytics(
                    cell = cell,
                    exposureCount = group.size,
                    availableMinutes =
                        r2(
                            group.sumOf {
                                it.durationSeconds
                            }.toDouble() / 60.0,
                        ),
                    offerHits = offerHits,
                    meanTimeToOfferMinutes = average(times),
                    medianTimeToOfferMinutes = median(times),
                    averagePerKm =
                        average(
                            linkedRows.mapNotNull {
                                it.perKm
                            },
                        ),
                    averagePerMinute =
                        average(
                            linkedRows.mapNotNull {
                                it.perMinute
                            },
                        ),
                    destinationOfferCount =
                        destinationCounts[cell] ?: 0,
                    serviceDistribution = linkedRows.groupingBy { it.serviceType }.eachCount(),
                    p5 = horizon(group, 5),
                    p10 = horizon(group, 10),
                    p15 = horizon(group, 15),
                )
            }
            .sortedWith(
                compareByDescending<RegionalCellAnalytics> {
                    it.p10.probabilityPct ?: -1.0
                }.thenByDescending {
                    it.availableMinutes
                },
            )
            .take(12)

        return RegionalIntelligenceAnalytics(
            days = safeDays,
            minimumProbabilitySamples =
                MIN_PROBABILITY_SAMPLES,
            collectiveOptIn =
                SettingsRepository(context)
                    .load()
                    .collectiveStatsOptIn,
            collectiveAvailableRegions = 0,
            dataQuality =
                RegionalDataQualityAnalytics(
                    rawExposureCount = exposures.size,
                    exposureCount = statisticalExposures.size,
                    burstIntervalsCollapsed = cleaned.second,
                    cellsWithExposure =
                        statisticalExposures.map { it.cell }.distinct().size,
                    offersWithDestinationCell =
                        destinationCounts.values.sum(),
                    historicalPositiveOffers =
                        importQuality.size,
                    importedUnknownTime =
                        importQuality.count {
                            it == "unknown"
                        },
                    probabilityReadyCells =
                        rows.count {
                            it.p10.probabilityPct != null
                        },
                ),
            topRegions = rows,
            note =
                "Cálculo pessoal local. Screenshots históricos são eventos positivos e não entram no denominador de exposição.",
        )
    }

    private fun collapseOfferBursts(
        rows: List<Exposure>,
    ): Pair<List<Exposure>, Int> {
        val lastOfferAt = mutableMapOf<String, Long>()
        val kept = mutableListOf<Exposure>()
        var collapsed = 0

        rows.sortedBy { it.startedAt }.forEach { row ->
            val key = "${row.journeyId}|${row.cell}"
            val startMs = runCatching {
                Instant.parse(row.startedAt).toEpochMilli()
            }.getOrNull()

            if (!isOfferHit(row) || startMs == null) {
                lastOfferAt.remove(key)
                kept += row
                return@forEach
            }

            val eventMs = startMs + row.durationSeconds.coerceAtLeast(0L) * 1000L
            val previous = lastOfferAt[key]
            val sameBurst =
                row.durationSeconds <= 15L &&
                    previous != null &&
                    kotlin.math.abs(startMs - previous) <= 15_000L

            lastOfferAt[key] = eventMs
            if (sameBurst) collapsed++ else kept += row
        }

        return kept to collapsed
    }

    private fun isOfferHit(row: Exposure): Boolean =
        row.closeReason == "offer_observed" &&
            !row.nextOfferLocalId.isNullOrBlank()

    private fun horizon(
        rows: List<Exposure>,
        minutes: Int,
    ): ProbabilityHorizonAnalytics {
        val result = RegionalProbability.calculate(
            rows.map {
                RegionalProbability.Sample(
                    durationSeconds = it.durationSeconds,
                    offerHit = isOfferHit(it),
                )
            },
            minutes,
        )

        return ProbabilityHorizonAnalytics(
            minutes = minutes,
            probabilityPct = result.probabilityPct,
            eligibleIntervals = result.eligibleIntervals,
            successes = result.successes,
            reliability = result.reliability,
        )
    }

    private fun average(
        values: List<Double>,
    ): Double? =
        if (values.isEmpty()) null else r2(values.average())

    private fun median(
        values: List<Double>,
    ): Double? {
        if (values.isEmpty()) return null
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return r2(
            if (sorted.size % 2 == 1) {
                sorted[mid]
            } else {
                (sorted[mid - 1] + sorted[mid]) / 2.0
            },
        )
    }

    private fun r2(value: Double) =
        round(value * 100.0) / 100.0
}
