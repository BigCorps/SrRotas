package com.srrotas.app

import java.time.Instant

/** Une capturas sucessivas da rolagem sem duplicar cartões parcialmente repetidos. */
object UberHistoryScanAccumulator0262 {
    data class Result(
        val rides: List<UberCompletedRide026>,
        val framesRead: Int,
        val framesWithRides: Int,
    )

    fun parseFrames(
        frames: List<String>,
        capturedAt: Instant = Instant.now(),
    ): Result {
        val merged = linkedMapOf<String, UberCompletedRide026>()
        var parsedFrames = 0
        frames.take(120).forEach { raw ->
            val rides = runCatching {
                UberDigitizationParser026.parseRidesFrame(raw, capturedAt, relaxed = true)
            }.getOrDefault(emptyList())
            if (rides.isNotEmpty()) parsedFrames++
            rides.forEach { ride ->
                val current = merged[ride.sourceKey]
                if (current == null || richness(ride) > richness(current)) {
                    merged[ride.sourceKey] = ride
                }
            }
        }
        val rides = merged.values.sortedWith(
            compareByDescending<UberCompletedRide026> { it.occurredAt.orEmpty() }
                .thenByDescending { it.fare },
        )
        require(rides.isNotEmpty()) { "Nenhuma corrida foi reconhecida durante a rolagem." }
        return Result(rides.take(100), frames.size, parsedFrames)
    }

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
