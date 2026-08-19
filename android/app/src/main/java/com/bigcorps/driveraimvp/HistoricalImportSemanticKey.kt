package com.srrotas.app

import java.security.MessageDigest
import java.time.Instant
import kotlin.math.round

object HistoricalImportSemanticKey {
    /**
     * Quando o horário é conhecido, replica deliberadamente o fingerprint
     * congelado do OfferParser v1 para também deduplicar contra uma oferta que
     * já tenha sido capturada ao vivo no mesmo bucket de 2 minutos.
     *
     * Com horário desconhecido não fingimos bucket temporal: usamos os campos
     * semânticos + hash do arquivo para evitar colapsar corridas distintas.
     */
    fun key(
        offer: RideOffer,
        observedAt: String,
        fileSha256: String,
        confidence: HistoricalTimeConfidence,
    ): String {
        val material =
            if (confidence != HistoricalTimeConfidence.UNKNOWN) {
                val occurrenceBucket = runCatching {
                    Instant.parse(observedAt).epochSecond / 120L
                }.getOrElse { 0L }
                listOf(
                    offer.fare.r2(),
                    offer.pickupKm?.r2(),
                    offer.tripKm?.r2(),
                    offer.totalKm?.r2(),
                    occurrenceBucket,
                ).joinToString("|")
            } else {
                listOf(
                    "unknown-time",
                    offer.fare.r2(),
                    offer.pickupKm?.r2(),
                    offer.tripKm?.r2(),
                    offer.totalKm?.r2(),
                    offer.totalMinutes,
                    offer.serviceType.lowercase(),
                    offer.context?.pickupLabel.orEmpty().normalizePlace(),
                    offer.context?.destinationLabel.orEmpty().normalizePlace(),
                    fileSha256.take(24),
                ).joinToString("|")
            }

        return sha256(material).take(40)
    }

    private fun String.normalizePlace(): String =
        lowercase()
            .replace(Regex("""[^\p{L}\p{N}]+"""), " ")
            .trim()
            .take(100)

    private fun Double.r2(): Double =
        round(this * 100.0) / 100.0

    private fun sha256(value: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(value.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
