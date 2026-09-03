package com.srrotas.app

/** Regras puras da rodada 0.26.1 para cobertura do HUD sem aceitar frames ruins. */
object HudReliabilityRules0261 {
    const val FORCE_OCR_AFTER_MS = 1_250L

    fun forceOcrDue(lastAcceptedAtMs: Long, nowMs: Long, forceAfterMs: Long = FORCE_OCR_AFTER_MS): Boolean =
        forceAfterMs > 0L &&
            lastAcceptedAtMs > 0L &&
            nowMs >= lastAcceptedAtMs &&
            nowMs - lastAcceptedAtMs >= forceAfterMs

    fun rejectUberGeometry(
        totalKm: Double,
        totalMinutes: Int,
        perKm: Double,
        hasHourEvidence: Boolean,
    ): Boolean {
        if (totalMinutes >= 180 && !hasHourEvidence) return true
        if (totalMinutes >= 90 && totalKm < 3.0) return true
        if (totalKm >= 20.0 && perKm < 0.30) return true
        return false
    }

    fun verticalBand(
        currentY: Int,
        previousFareY: Int?,
        nextFareY: Int?,
        naturalTop: Int,
        naturalBottom: Int,
    ): IntRange {
        val top = previousFareY
            ?.let { it + (currentY - it) / 2 }
            ?.coerceAtLeast(naturalTop)
            ?: naturalTop
        val bottom = nextFareY
            ?.let { currentY + (it - currentY) / 2 }
            ?.coerceAtMost(naturalBottom)
            ?: naturalBottom
        return top.coerceAtMost(bottom)..bottom.coerceAtLeast(top)
    }
}
