package com.srrotas.app

import android.content.Context
import android.os.SystemClock
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * 0.29 — gate de admissão oficial antes do CardStabilizer/HUD/persistência.
 *
 * 0.30 mantém este contrato e seu ponto de integração intactos. O runtime deste
 * objeto passa a delegar para OfferAdmissionGate030; as regras puras 0.29 abaixo
 * permanecem apenas para compatibilidade documental e testes legados. Assim a
 * regressão decimal é corrigida sem alterar OfferDispatcher nem consumidores.
 *
 * Princípios:
 * - não "corrige" decimal por heurística;
 * - usa observações temporais para detectar conflito x10 entre frames;
 * - leituras de cauda extrema precisam de confirmação curta;
 * - fallback genérico sem rota continua disponível ao diagnóstico, mas não
 *   contamina Base Pessoal/Coletiva.
 */
object OfferAdmissionGate029 {
    private const val PREFS = "sr_offer_admission_029"

    private val window = TailConfirmationWindow029()

    @Synchronized
    fun admit(
        context: Context,
        offer: RideOffer,
        stage: String,
        nowMs: Long = SystemClock.elapsedRealtime(),
    ): RideOffer? =
        OfferAdmissionGate030.admit(context, offer, stage, nowMs)

    @Synchronized
    fun resetRuntime() {
        OfferAdmissionGate030.resetRuntime()
        window.reset()
    }

    fun toJson(context: Context): JSONObject {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return JSONObject().apply {
            put("schema", "sr-offer-admission-029-v1")
            put("confirmation_window_ms", TailConfirmationWindow029.CONFIRMATION_WINDOW_MS)
            put("accepted_normal", prefs.getInt("accepted_normal", 0))
            put("accepted_confirmed_tail", prefs.getInt("accepted_confirmed_tail", 0))
            put("accepted_replaces_conflict", prefs.getInt("accepted_replaces_conflict", 0))
            put("deferred_tail", prefs.getInt("deferred_tail", 0))
            put("rejected_decimal_conflict", prefs.getInt("rejected_decimal_conflict", 0))
            put("rejected_generic_without_route", prefs.getInt("rejected_generic_without_route", 0))
            put("last_reason", prefs.getString("last_reason", "") ?: "")
            put("last_stage", prefs.getString("last_stage", "") ?: "")
            put("last_at", prefs.getLong("last_at", 0L))
            put("last_platform", prefs.getString("last_platform", "") ?: "")
            put("last_fare", prefs.getString("last_fare", "") ?: "")
            put("last_pickup_km", prefs.getString("last_pickup_km", "") ?: "")
            put("last_pickup_minutes", prefs.getString("last_pickup_minutes", "") ?: "")
            put(
                "policy",
                "Cauda extrema exige confirmação temporal; conflito decimal x10 prefere a observação menos extrema; fallback genérico sem pickup+destino não vira oferta oficial.",
            )
            put("runtime_delegated_to_030", true)
            put("privacy", "Sem OCR bruto, screenshot, endereço ou coordenada persistidos por este gate.")
        }
    }


}

/** Regras puras e testáveis do gate 0.29. */
internal object OfferAdmissionRules029 {
    private const val HISTORICAL_PICKUP_KM_P99 = 3.60
    private const val HISTORICAL_PICKUP_MIN_P99 = 12
    private const val EXTREME_PICKUP_KM = 6.0
    private const val EXTREME_PICKUP_MIN = 25
    private const val EXTREME_TRIP_KM = 40.0
    private const val EXTREME_TRIP_MIN = 75

    fun unsafeGenericFallback(offer: RideOffer): Boolean {
        if (offer.platform.lowercase() != "other") return false
        if (!offer.captureMethod.contains("other-text-fallback", ignoreCase = true)) return false

        val ctx = offer.context
        return ctx?.pickupLabel.isNullOrBlank() || ctx?.destinationLabel.isNullOrBlank()
    }

    fun extremeTail(offer: RideOffer): Boolean {
        val pickupKm = offer.pickupKm
        val pickupMin = offer.pickupMinutes
        val pickupJointTail =
            pickupKm != null &&
                pickupMin != null &&
                pickupKm > HISTORICAL_PICKUP_KM_P99 &&
                pickupMin > HISTORICAL_PICKUP_MIN_P99

        return pickupJointTail ||
            (pickupKm != null && pickupKm > EXTREME_PICKUP_KM) ||
            (pickupMin != null && pickupMin > EXTREME_PICKUP_MIN) ||
            (offer.tripKm != null && offer.tripKm > EXTREME_TRIP_KM) ||
            (offer.tripMinutes != null && offer.tripMinutes > EXTREME_TRIP_MIN)
    }

    /**
     * Correlação deliberadamente evita pickup/trip/total: esses são justamente
     * os números que podem estar deslocados. Endereços ficam somente em memória;
     * a chave usa hash e nunca é exportada.
     */
    fun correlationKey(offer: RideOffer): String {
        val ctx = offer.context
        val routeText =
            listOf(
                ctx?.pickupLabel.orEmpty(),
                ctx?.destinationLabel.orEmpty(),
            ).joinToString("|")
                .trim('|')
                .lowercase()
                .replace(Regex("\\s+"), " ")
                .take(220)

        val structural =
            if (routeText.isNotBlank()) {
                routeText
            } else {
                offer.rawText
                    .lowercase()
                    .replace(Regex("\\d+[\\.,]?\\d*"), "#")
                    .replace(Regex("\\s+"), " ")
                    .take(500)
            }

        val fareCents = (offer.fare * 100.0).roundToInt()
        return listOf(
            offer.journeyId.orEmpty(),
            offer.platform.lowercase(),
            fareCents.toString(),
            offer.serviceType.lowercase(),
            offer.offerType.lowercase(),
            structural.hashCode().toString(),
        ).joinToString("|")
    }

    fun decimalConflict(a: RideOffer, b: RideOffer): Boolean {
        val pickupKmConflict = factorTenish(a.pickupKm, b.pickupKm)
        val pickupMinConflict = factorTenish(a.pickupMinutes?.toDouble(), b.pickupMinutes?.toDouble())
        val tripKmConflict = factorTenish(a.tripKm, b.tripKm)
        val tripMinConflict = factorTenish(a.tripMinutes?.toDouble(), b.tripMinutes?.toDouble())

        if (!(pickupKmConflict || pickupMinConflict || tripKmConflict || tripMinConflict)) return false

        // Evita chamar duas ofertas distintas de conflito só porque um número
        // coincidiu: exige pelo menos um sinal não conflitado próximo.
        val anchors = listOf(
            close(a.tripKm, b.tripKm, absolute = 0.6, relative = 0.15),
            closeInt(a.tripMinutes, b.tripMinutes, 3),
            closeInt(a.totalMinutes, b.totalMinutes, 4),
            abs(a.fare - b.fare) <= 0.02,
        )
        return anchors.count { it } >= 2
    }

    fun compatibleTail(a: RideOffer, b: RideOffer): Boolean {
        val checks = mutableListOf<Boolean>()
        comparableClose(a.pickupKm, b.pickupKm, 0.5, 0.16)?.let(checks::add)
        comparableClose(a.tripKm, b.tripKm, 0.7, 0.16)?.let(checks::add)
        comparableCloseInt(a.pickupMinutes, b.pickupMinutes, 3)?.let(checks::add)
        comparableCloseInt(a.tripMinutes, b.tripMinutes, 3)?.let(checks::add)
        comparableCloseInt(a.totalMinutes, b.totalMinutes, 4)?.let(checks::add)

        return checks.size >= 2 && checks.all { it }
    }

    fun extremenessScore(offer: RideOffer): Double {
        var score = 0.0
        offer.pickupKm?.let { score += it / HISTORICAL_PICKUP_KM_P99 }
        offer.pickupMinutes?.let { score += it.toDouble() / HISTORICAL_PICKUP_MIN_P99.toDouble() }
        offer.tripKm?.let { score += it / 32.066 }
        offer.tripMinutes?.let { score += it.toDouble() / 57.0 }
        return score
    }

    private fun factorTenish(a: Double?, b: Double?): Boolean {
        if (a == null || b == null || a <= 0.0 || b <= 0.0) return false
        val ratio = max(a, b) / min(a, b)
        return ratio in 8.0..12.5
    }

    private fun close(a: Double?, b: Double?, absolute: Double, relative: Double): Boolean {
        if (a == null || b == null) return false
        val tolerance = max(absolute, max(abs(a), abs(b)) * relative)
        return abs(a - b) <= tolerance
    }

    private fun closeInt(a: Int?, b: Int?, tolerance: Int): Boolean =
        a != null && b != null && abs(a - b) <= tolerance

    private fun comparableClose(
        a: Double?,
        b: Double?,
        absolute: Double,
        relative: Double,
    ): Boolean? =
        if (a == null || b == null) null else close(a, b, absolute, relative)

    private fun comparableCloseInt(a: Int?, b: Int?, tolerance: Int): Boolean? =
        if (a == null || b == null) null else closeInt(a, b, tolerance)
}

/**
 * Janela curta e em memória. Ela não persiste o conteúdo de oferta.
 */
internal class TailConfirmationWindow029 {
    enum class Decision {
        ACCEPT_NORMAL,
        ACCEPT_CONFIRMED_TAIL,
        ACCEPT_REPLACES_CONFLICT,
        DEFER_TAIL,
        REJECT_DECIMAL_CONFLICT,
        REJECT_GENERIC_FALLBACK,
    }

    private data class Seen(
        val offer: RideOffer,
        val atMs: Long,
    )

    private val recent = linkedMapOf<String, Seen>()

    fun decide(offer: RideOffer, nowMs: Long): Decision {
        purge(nowMs)

        if (OfferAdmissionRules029.unsafeGenericFallback(offer)) {
            return Decision.REJECT_GENERIC_FALLBACK
        }

        val key = OfferAdmissionRules029.correlationKey(offer)
        val previous = recent[key]

        if (previous != null && OfferAdmissionRules029.decimalConflict(previous.offer, offer)) {
            val previousScore = OfferAdmissionRules029.extremenessScore(previous.offer)
            val currentScore = OfferAdmissionRules029.extremenessScore(offer)
            return if (currentScore > previousScore + 0.5) {
                // Mantém a observação anterior como referência por mais alguns frames.
                Decision.REJECT_DECIMAL_CONFLICT
            } else {
                recent[key] = Seen(offer, nowMs)
                Decision.ACCEPT_REPLACES_CONFLICT
            }
        }

        if (!OfferAdmissionRules029.extremeTail(offer)) {
            recent[key] = Seen(offer, nowMs)
            return Decision.ACCEPT_NORMAL
        }

        if (
            previous != null &&
            nowMs - previous.atMs <= CONFIRMATION_WINDOW_MS &&
            OfferAdmissionRules029.compatibleTail(previous.offer, offer)
        ) {
            recent[key] = Seen(offer, nowMs)
            return Decision.ACCEPT_CONFIRMED_TAIL
        }

        recent[key] = Seen(offer, nowMs)
        return Decision.DEFER_TAIL
    }

    fun reset() {
        recent.clear()
    }

    private fun purge(nowMs: Long) {
        val iterator = recent.entries.iterator()
        while (iterator.hasNext()) {
            if (nowMs - iterator.next().value.atMs > CONFIRMATION_WINDOW_MS) {
                iterator.remove()
            }
        }
    }

    companion object {
        const val CONFIRMATION_WINDOW_MS = 7_000L
    }
}
