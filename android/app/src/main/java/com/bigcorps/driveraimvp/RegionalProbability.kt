package com.srrotas.app

object RegionalProbability {
    const val MIN_PROBABILITY_SAMPLES = 20

    data class Sample(
        val durationSeconds: Long,
        val offerHit: Boolean,
    )

    data class Result(
        val eligibleIntervals: Int,
        val successes: Int,
        val probabilityPct: Double?,
        val reliability: String,
    )

    fun calculate(
        samples: List<Sample>,
        horizonMinutes: Int,
    ): Result {
        require(horizonMinutes > 0)
        val horizonSeconds = horizonMinutes * 60L

        val eligible = samples.filter {
            it.durationSeconds >= horizonSeconds ||
                (
                    it.offerHit &&
                        it.durationSeconds <= horizonSeconds
                    )
        }

        val successes = samples.count {
            it.offerHit &&
                it.durationSeconds <= horizonSeconds
        }

        val reliability =
            when {
                eligible.size < MIN_PROBABILITY_SAMPLES ->
                    "insufficient"
                eligible.size < 50 -> "low"
                eligible.size < 100 -> "medium"
                else -> "high"
            }

        val probability =
            if (
                reliability == "insufficient" ||
                eligible.isEmpty()
            ) {
                null
            } else {
                round2(
                    successes.toDouble() /
                        eligible.size.toDouble() *
                        100.0,
                )
            }

        return Result(
            eligibleIntervals = eligible.size,
            successes = successes,
            probabilityPct = probability,
            reliability = reliability,
        )
    }

    private fun round2(value: Double) =
        kotlin.math.round(value * 100.0) / 100.0
}
