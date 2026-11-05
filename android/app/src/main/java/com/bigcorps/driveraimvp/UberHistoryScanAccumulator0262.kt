package com.srrotas.app

import java.time.Instant
import java.time.LocalDate

/** Une capturas sucessivas da rolagem sem duplicar cartões parcialmente repetidos. */
object UberHistoryScanAccumulator0262 {
    data class Result(
        val rides: List<UberCompletedRide026>,
        val framesRead: Int,
        val framesWithRides: Int,
        /** vc111: cards completados com a continuação do quadro seguinte (rolagem parcial). */
        val combinedFromNextFrame: Int = 0,
    )

    fun parseFrames(
        frames: List<String>,
        capturedAt: Instant = Instant.now(),
    ): Result {
        val merged = linkedMapOf<String, UberCompletedRide026>()
        var parsedFrames = 0
        var combined = 0
        // vc111: último card do quadro anterior (linhas + leitura) para completar com a cauda do próximo.
        var tailLines: List<String>? = null
        var tailRide: UberCompletedRide026? = null
        var tailWindowLines: List<String> = emptyList()
        var carriedDate: LocalDate? = null
        frames.take(120).forEach { raw ->
            val layout = runCatching { UberDigitizationParser026.historyLayout(raw, relaxed = true) }.getOrNull()
            if (layout == null) { tailLines = null; tailRide = null; return@forEach }
            // 1) Cauda no topo deste quadro = continuação do último card do quadro anterior?
            val continuation = continues(tailWindowLines, layout.head)
            if (continuation && tailLines != null && tailRide != null) {
                val previous = merged[tailRide!!.sourceKey] ?: tailRide!!
                val extra = layout.head.filter { line -> tailWindowLines.none { UberDigitizationParser026.sameLine(it, line) } }
                val whole = tailLines!!
                val window = UberDigitizationParser026.HistoryWindow(whole.size - tailWindowLines.size, whole.size - tailWindowLines.size, whole.size, false)
                val reread = UberDigitizationParser026.parseWindow(whole, window, capturedAt, carriedDate, extra)
                if (reread != null && sameCard(previous, reread)) {
                    val completed = fillMissing(previous, reread)
                    if (completed != previous) {
                        merged.remove(previous.sourceKey)
                        merged[completed.sourceKey] = completed
                        combined++
                        tailRide = completed
                    }
                }
                tailWindowLines = tailWindowLines + extra
            }
            val hint = if (continuation) carriedDate else null
            // 2) Cards deste quadro (cada janela começa na própria tarifa).
            var frameRides = 0
            var lastRide: UberCompletedRide026? = null
            layout.windows.forEach { w ->
                val ride = UberDigitizationParser026.parseWindow(layout.lines, w, capturedAt, hint) ?: return@forEach
                frameRides++
                lastRide = ride
                val current = merged[ride.sourceKey]
                if (current == null || richness(ride) > richness(current)) merged[ride.sourceKey] = ride
            }
            if (frameRides > 0) parsedFrames++
            UberDigitizationParser026.lastDateHeader(layout.lines, capturedAt)?.let { carriedDate = it }
            val last = layout.windows.lastOrNull()
            if (last != null && lastRide != null) {
                tailLines = layout.lines; tailRide = lastRide
                tailWindowLines = layout.lines.subList(last.start, last.endExclusive)
            } else if (!continuation) {
                tailLines = null; tailRide = null; tailWindowLines = emptyList()
            }
        }
        // vc108: leituras divergentes do mesmo cartão (duração/distância) geravam sourceKeys
        // diferentes; a identidade estável (situação + tarifa + horário) une as duplicatas.
        val unique = mutableListOf<UberCompletedRide026>()
        merged.values.forEach { ride ->
            val index = unique.indexOfFirst { UberRideIdentity108.sameRide(it, ride) }
            if (index < 0) {
                unique += ride
            } else {
                val (rich, poor) = if (richness(ride) > richness(unique[index])) ride to unique[index] else unique[index] to ride
                // vc111: com horário igual nos dois a identidade é forte: completa campos ausentes.
                unique[index] = if (rich.occurredAt != null && poor.occurredAt != null) fillMissing(rich, poor) else rich
            }
        }
        val rides = unique.sortedWith(
            compareByDescending<UberCompletedRide026> { it.occurredAt.orEmpty() }
                .thenByDescending { it.fare },
        )
        require(rides.isNotEmpty()) { "Nenhuma corrida foi reconhecida durante a rolagem." }
        return Result(rides.take(100), frames.size, parsedFrames, combined)
    }

    /** Continuação confiável: a cauda do topo repete ao menos uma linha distintiva do último card anterior. */
    internal fun continues(previousCard: List<String>, head: List<String>): Boolean {
        if (previousCard.isEmpty() || head.isEmpty()) return false
        return head.any { line ->
            UberDigitizationParser026.distinctiveLine(line) && previousCard.any { UberDigitizationParser026.sameLine(it, line) }
        }
    }

    private fun sameCard(a: UberCompletedRide026, b: UberCompletedRide026) =
        a.rideStatus == b.rideStatus && kotlin.math.abs(a.fare - b.fare) < 0.011 && a.serviceType == b.serviceType

    /** Mantém tudo o que já foi lido em [base]; só preenche campos ausentes com [other]. */
    internal fun fillMissing(base: UberCompletedRide026, other: UberCompletedRide026): UberCompletedRide026 = base.copy(
        occurredAt = base.occurredAt ?: other.occurredAt,
        pickupLabel = base.pickupLabel ?: other.pickupLabel,
        destinationLabel = base.destinationLabel ?: other.destinationLabel?.takeUnless { d -> base.pickupLabel?.let { UberDigitizationParser026.sameLine(it, d) } == true },
        durationSeconds = base.durationSeconds ?: other.durationSeconds,
        distanceKm = base.distanceKm ?: other.distanceKm,
        surgeAmount = base.surgeAmount ?: other.surgeAmount,
        extraAmount = base.extraAmount ?: other.extraAmount,
        confidence = maxOf(base.confidence, other.confidence),
    )

    private fun richness(value: UberCompletedRide026): Double {
        var score = value.confidence * 10
        if (value.occurredAt != null) score += 3
        if (value.durationSeconds != null) score += 2
        if (value.distanceKm != null) score += 2
        if (value.pickupLabel != null) score += 2
        if (value.destinationLabel != null) score += 2
        if (value.surgeAmount != null) score += 1
        if (value.extraAmount != null) score += 1
        return score
    }
}
