package com.srrotas.app

/** Regras puras do fluxo de hodômetro da tela Agora. */
object JourneyFlowRules026 {
    const val DRAFT_TTL_MS = 30L * 60L * 1000L

    fun decimalFlexible(text: String?): Double? {
        val raw = text?.trim().orEmpty().replace(" ", "")
        if (raw.isBlank()) return null
        val normalized = when {
            raw.contains(',') -> raw.replace(".", "").replace(',', '.')
            Regex("^\\d{1,3}(?:\\.\\d{3})+$").matches(raw) -> raw.replace(".", "")
            else -> raw
        }
        return normalized.toDoubleOrNull()?.takeIf { it.isFinite() }
    }

    fun validEnd(startKm: Double?, endKm: Double?): Boolean =
        endKm == null || startKm == null || endKm >= startKm

    fun draftIsFresh(savedAtMs: Long, nowMs: Long): Boolean =
        savedAtMs > 0L && nowMs >= savedAtMs && nowMs - savedAtMs <= DRAFT_TTL_MS

    fun energyKind(mode: String): String? = when (mode.trim().lowercase()) {
        "fuel" -> JourneyMetricsRules026.KIND_FUEL
        "electric" -> JourneyMetricsRules026.KIND_ELECTRIC
        else -> null
    }

    fun unitFor(kind: String): String = when (kind) {
        JourneyMetricsRules026.KIND_ELECTRIC -> JourneyMetricsRules026.UNIT_KWH
        else -> JourneyMetricsRules026.UNIT_LITER
    }
}
