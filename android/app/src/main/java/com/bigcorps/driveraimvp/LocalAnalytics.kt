package com.srrotas.app

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.round

object LocalAnalytics {
    private val zone = ZoneId.of("America/Sao_Paulo")
    private val dayFmt =
        DateTimeFormatter.ofPattern("dd/MM").withZone(zone)
    private val dateKeyFmt =
        DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(zone)

    fun build(
        context: android.content.Context,
        days: Int,
        verdict: String?,
        serviceType: String?,
        offerType: String?,
    ): HistoryAnalytics {
        val store = LocalStore.get(context)
        val to = Instant.now()
        val safeDays = days.coerceIn(1, 90)
        val from = to.minusSeconds(safeDays.toLong() * 86400L)
        val previousFrom =
            from.minusSeconds(safeDays.toLong() * 86400L)

        fun filtered(a: List<RideOffer>) = a.filter { o ->
            (verdict.isNullOrBlank() || o.verdict == verdict) &&
                (serviceType.isNullOrBlank() ||
                    o.serviceType == serviceType) &&
                (offerType.isNullOrBlank() ||
                    o.offerType == offerType)
        }

        val current = filtered(
            store.offersInRange(
                from.toString(),
                to.toString(),
                2000,
            ),
        )
        val previous = filtered(
            store.offersInRange(
                previousFrom.toString(),
                from.toString(),
                2000,
            ),
        )
        val summary = summarize(current)
        val previousSummary = summarize(previous)

        val daily = current.groupBy {
            val instant = runCatching {
                Instant.parse(it.observedAt)
            }.getOrDefault(to)
            dateKeyFmt.format(instant) to dayFmt.format(instant)
        }.entries.sortedBy { it.key.first }.map { (key, rows) ->
            val s = summarize(rows)
            AnalyticsPoint(
                key.first,
                key.second,
                rows.size,
                s.averagePerKm,
                s.averagePerHour,
                s.averagePerMinute,
                s.averageEstimatedProfit,
            )
        }

        val hours = current.groupBy {
            val instant = runCatching {
                Instant.parse(it.observedAt)
            }.getOrDefault(to)
            instant.atZone(zone).hour
        }.entries.sortedBy { it.key }.map { (hour, rows) ->
            val s = summarize(rows)
            AnalyticsPoint(
                hour.toString(),
                "%02dh".format(hour),
                rows.size,
                s.averagePerKm,
                s.averagePerHour,
                s.averagePerMinute,
                s.averageEstimatedProfit,
            )
        }

        val services = current
            .groupBy { it.serviceType.ifBlank { "unknown" } }
            .map { (service, rows) ->
                val s = summarize(rows)
                ServiceAnalytics(
                    service,
                    rows.size,
                    s.averagePerKm,
                    s.averagePerHour,
                    s.averageEstimatedProfit,
                )
            }
            .sortedByDescending { it.offerCount }

        val journeys = store
            .journeysInRange(from.toString(), to.toString(), 80)
            .map { journey ->
                val js = store.journeySummary(journey.id)
                val duration =
                    if (journey.endedAt != null) {
                        runCatching {
                            (
                                Instant.parse(journey.endedAt)
                                    .toEpochMilli() -
                                    Instant.parse(journey.startedAt)
                                        .toEpochMilli()
                                ).div(60000L).toInt()
                        }.getOrNull()
                    } else {
                        null
                    }

                JourneyAnalytics(
                    id = journey.id,
                    startedAt = journey.startedAt,
                    endedAt = journey.endedAt,
                    durationMinutes = duration,
                    offerCount = js?.offerCount ?: 0,
                    goodCount = js?.goodCount ?: 0,
                    regularCount = js?.regularCount ?: 0,
                    badCount = js?.badCount ?: 0,
                    averagePerKm = js?.averagePerKm,
                    averagePerHour = js?.averagePerHour,
                    estimatedProfitObserved =
                        js?.estimatedProfitObserved,
                )
            }

        val top = current
            .sortedWith(
                compareByDescending<RideOffer> {
                    if (it.verdict == "boa") 1 else 0
                }
                    .thenByDescending {
                        it.profitPerHour ?: -1.0
                    }
                    .thenByDescending {
                        it.perMinute ?: -1.0
                    },
            )
            .take(10)
            .map {
                TopOfferAnalytics(
                    it.observedAt,
                    it.fare,
                    it.serviceType,
                    it.offerType,
                    it.verdict,
                    it.perKm,
                    it.perHour,
                    it.perMinute,
                    it.estimatedProfit,
                    it.passengerRating,
                )
            }

        return HistoryAnalytics(
            source = "local",
            from = from.toString(),
            to = to.toString(),
            summary = summary,
            comparison = AnalyticsComparison(
                previous = previousSummary,
                offerCountPct = pct(
                    summary.offerCount.toDouble(),
                    previousSummary.offerCount.toDouble(),
                ),
                averagePerKmPct = pct(
                    summary.averagePerKm,
                    previousSummary.averagePerKm,
                ),
                averagePerHourPct = pct(
                    summary.averagePerHour,
                    previousSummary.averagePerHour,
                ),
                averagePerMinutePct = pct(
                    summary.averagePerMinute,
                    previousSummary.averagePerMinute,
                ),
                averageProfitPct = pct(
                    summary.averageEstimatedProfit,
                    previousSummary.averageEstimatedProfit,
                ),
            ),
            daily = daily,
            hours = hours,
            services = services,
            journeys = journeys,
            topOffers = top,
            regionalIntelligence = LocalRegionalIntelligence.build(context, safeDays),
            truncated = current.size >= 2000,
            note =
                "Dados locais do aparelho. Valores representam ofertas observadas; não são ganhos realizados. " +
                    "A inteligência regional completa usa exposições sincronizadas da nuvem.",
        )
    }

    private fun summarize(rows: List<RideOffer>): AnalyticsSummary {
        fun avg(values: List<Double?>): Double? {
            val v = values.filterNotNull().filter(Double::isFinite)
            return if (v.isEmpty()) null else r2(v.average())
        }

        return AnalyticsSummary(
            offerCount = rows.size,
            totalOfferedFare = r2(rows.sumOf { it.fare }),
            averageFare = avg(rows.map { it.fare }),
            averagePerKm = avg(rows.map { it.perKm }),
            averagePerHour = avg(rows.map { it.perHour }),
            averagePerMinute = avg(rows.map { it.perMinute }),
            estimatedTotalProfit =
                r2(rows.sumOf { it.estimatedProfit ?: 0.0 }),
            averageEstimatedProfit =
                avg(rows.map { it.estimatedProfit }),
            goodCount = rows.count { it.verdict == "boa" },
            regularCount = rows.count { it.verdict == "regular" },
            badCount = rows.count { it.verdict == "ruim" },
        )
    }

    private fun pct(current: Double?, previous: Double?): Double? {
        if (
            current == null ||
            previous == null ||
            previous == 0.0
        ) {
            return null
        }
        return r2(
            (current - previous) /
                kotlin.math.abs(previous) *
                100.0,
        )
    }

    private fun r2(v: Double) =
        round(v * 100.0) / 100.0
}
