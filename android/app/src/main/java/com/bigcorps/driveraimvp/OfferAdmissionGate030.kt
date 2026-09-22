package com.srrotas.app

import android.content.Context
import android.os.SystemClock
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * 0.30 — camada modular de admissão acessada pela fachada compatível 0.29.
 *
 * Este módulo existe para corrigir a regressão decimal confirmada em campo sem
 * reabrir OfferParser, fórmulas, HUD, Histórico, Radar ou persistência.
 *
 * Regras:
 * - nunca altera/corrige um valor por adivinhação;
 * - a identidade temporal não usa tarifa, distância ou tempo como chave;
 * - conflitos x10 podem envolver tarifa, distâncias OU tempos;
 * - uma mudança decimal conflitante precisa reaparecer de forma compatível antes
 *   de substituir a observação aceita recentemente;
 * - o Reader 2.0 shadow observa antes desta decisão, mas nunca decide a oferta.
 */
object OfferAdmissionGate030 {
    private const val PREFS = "sr_offer_admission_030"
    private val window = AdmissionWindow030()

    @Synchronized
    fun admit(
        context: Context,
        offer: RideOffer,
        stage: String,
        nowMs: Long = SystemClock.elapsedRealtime(),
    ): RideOffer? {
        val app = context.applicationContext

        // Shadow puro: coleta evidência e divergências, sem alterar a decisão M1.
        Reader2Shadow030.observe(app, offer, stage, nowMs)

        val decision = window.decide(offer, nowMs)
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        when (decision.kind) {
            AdmissionWindow030.DecisionKind.ACCEPT_NORMAL -> {
                bump(prefs, "accepted_normal")
                return offer
            }

            AdmissionWindow030.DecisionKind.ACCEPT_CONFIRMED_TAIL -> {
                bump(prefs, "accepted_confirmed_tail")
                recordLast(prefs, "confirmed_tail", stage, offer, decision.conflictFields)
                return offer
            }

            AdmissionWindow030.DecisionKind.ACCEPT_CONFIRMED_DECIMAL_CHANGE -> {
                bump(prefs, "accepted_confirmed_decimal_change")
                decision.conflictFields.forEach { bump(prefs, "confirmed_$it") }
                recordLast(prefs, "confirmed_decimal_change", stage, offer, decision.conflictFields)
                LocalLog.append(
                    app,
                    "ADMISSÃO 0.30 confirmou mudança decimal após repetição · " +
                        "campos=${decision.conflictFields.joinToString(",")} · ${offer.platform}",
                )
                return offer
            }

            AdmissionWindow030.DecisionKind.DEFER_TAIL -> {
                bump(prefs, "deferred_tail")
                recordLast(prefs, "tail_waiting_confirmation", stage, offer, emptySet())
                return null
            }

            AdmissionWindow030.DecisionKind.REJECT_DECIMAL_CONFLICT -> {
                bump(prefs, "rejected_decimal_conflict")
                decision.conflictFields.forEach { bump(prefs, "conflict_$it") }
                recordLast(prefs, "decimal_conflict_waiting_confirmation", stage, offer, decision.conflictFields)
                LocalLog.append(
                    app,
                    "ADMISSÃO 0.30 reteve conflito decimal temporal · " +
                        "campos=${decision.conflictFields.joinToString(",")} · ${offer.platform}",
                )
                return null
            }

            AdmissionWindow030.DecisionKind.REJECT_GENERIC_FALLBACK -> {
                bump(prefs, "rejected_generic_without_route")
                recordLast(prefs, "generic_fallback_without_route", stage, offer, emptySet())
                return null
            }
        }
    }

    @Synchronized
    fun resetRuntime() {
        window.reset()
        Reader2Shadow030.resetRuntime()
    }

    fun toJson(context: Context): JSONObject {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return JSONObject().apply {
            put("schema", "sr-offer-admission-030-v1")
            put("confirmation_window_ms", AdmissionWindow030.CONFIRMATION_WINDOW_MS)
            put("accepted_normal", prefs.getInt("accepted_normal", 0))
            put("accepted_confirmed_tail", prefs.getInt("accepted_confirmed_tail", 0))
            put("accepted_confirmed_decimal_change", prefs.getInt("accepted_confirmed_decimal_change", 0))
            put("deferred_tail", prefs.getInt("deferred_tail", 0))
            put("rejected_decimal_conflict", prefs.getInt("rejected_decimal_conflict", 0))
            put("rejected_generic_without_route", prefs.getInt("rejected_generic_without_route", 0))
            put("conflict_fare", prefs.getInt("conflict_fare", 0))
            put("conflict_pickup_km", prefs.getInt("conflict_pickup_km", 0))
            put("conflict_trip_km", prefs.getInt("conflict_trip_km", 0))
            put("conflict_total_km", prefs.getInt("conflict_total_km", 0))
            put("conflict_pickup_minutes", prefs.getInt("conflict_pickup_minutes", 0))
            put("conflict_trip_minutes", prefs.getInt("conflict_trip_minutes", 0))
            put("conflict_total_minutes", prefs.getInt("conflict_total_minutes", 0))
            put("last_reason", prefs.getString("last_reason", "") ?: "")
            put("last_stage", prefs.getString("last_stage", "") ?: "")
            put("last_at", prefs.getLong("last_at", 0L))
            put("last_platform", prefs.getString("last_platform", "") ?: "")
            put("last_conflict_fields", prefs.getString("last_conflict_fields", "") ?: "")
            put(
                "identity_policy",
                "Correlação temporal não usa tarifa/distância/tempo como chave; rota/estrutura e sinais estáveis identificam a oferta.",
            )
            put(
                "decimal_policy",
                "Conflito x10 nunca é corrigido por heurística: primeira leitura conflitante é retida e uma mudança precisa de confirmação compatível.",
            )
            put("reader2_influence", false)
            put("privacy", "Sem OCR bruto, screenshot, endereço ou coordenada persistidos pelo gate.")
        }
    }

    private fun bump(
        prefs: android.content.SharedPreferences,
        key: String,
    ) {
        prefs.edit().putInt(key, prefs.getInt(key, 0) + 1).apply()
    }

    private fun recordLast(
        prefs: android.content.SharedPreferences,
        reason: String,
        stage: String,
        offer: RideOffer,
        fields: Set<String>,
    ) {
        prefs.edit()
            .putString("last_reason", reason)
            .putString("last_stage", stage)
            .putLong("last_at", System.currentTimeMillis())
            .putString("last_platform", offer.platform)
            .putString("last_conflict_fields", fields.sorted().joinToString(","))
            .apply()
    }
}

/** Regras puras para correlação/admissão 0.30. */
internal object OfferAdmissionRules030 {
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
            pickupKm != null && pickupMin != null &&
                pickupKm > HISTORICAL_PICKUP_KM_P99 &&
                pickupMin > HISTORICAL_PICKUP_MIN_P99

        return pickupJointTail ||
            (pickupKm != null && pickupKm > EXTREME_PICKUP_KM) ||
            (pickupMin != null && pickupMin > EXTREME_PICKUP_MIN) ||
            (offer.tripKm != null && offer.tripKm > EXTREME_TRIP_KM) ||
            (offer.tripMinutes != null && offer.tripMinutes > EXTREME_TRIP_MIN)
    }

    /**
     * Chave propositalmente independente dos números suspeitos.
     * Tarifa, km e minutos NUNCA participam da identidade.
     */
    fun targetKey(offer: RideOffer): String {
        val route = routeSignature(offer)
        val structural = if (route.isNotBlank()) route else structuralSignature(offer.rawText)
        return listOf(
            offer.journeyId.orEmpty(),
            offer.platform.lowercase(),
            offer.offerType.lowercase(),
            structural.hashCode().toString(),
        ).joinToString("|")
    }

    fun routeSignature(offer: RideOffer): String {
        val ctx = offer.context ?: return ""
        val pickup = normalizeIdentity(ctx.pickupLabel.orEmpty())
        val destination = normalizeIdentity(ctx.destinationLabel.orEmpty())
        if (pickup.isBlank() || destination.isBlank()) return ""
        return "$pickup|$destination"
    }

    fun structuralSignature(rawText: String): String =
        rawText
            .lowercase()
            .replace(Regex("[0-9]+(?:[.,][0-9]+)?"), "#")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(500)

    fun likelySameOffer(a: RideOffer, b: RideOffer): Boolean {
        if (a.journeyId != b.journeyId) return false
        if (!a.platform.equals(b.platform, true)) return false
        if (!a.offerType.equals(b.offerType, true)) return false
        if (!serviceCompatible(a.serviceType, b.serviceType)) return false

        val routeA = routeSignature(a)
        val routeB = routeSignature(b)
        if (routeA.isNotBlank() && routeB.isNotBlank()) return routeA == routeB

        if (structuralSignature(a.rawText) != structuralSignature(b.rawText)) return false
        return stableAnchorCount(a, b) >= 2
    }

    fun decimalConflictFields(a: RideOffer, b: RideOffer): Set<String> = buildSet {
        if (factorTenish(a.fare, b.fare)) add("fare")
        if (factorTenish(a.pickupKm, b.pickupKm)) add("pickup_km")
        if (factorTenish(a.tripKm, b.tripKm)) add("trip_km")
        if (factorTenish(a.totalKm, b.totalKm)) add("total_km")
        if (factorTenish(a.pickupMinutes?.toDouble(), b.pickupMinutes?.toDouble())) add("pickup_minutes")
        if (factorTenish(a.tripMinutes?.toDouble(), b.tripMinutes?.toDouble())) add("trip_minutes")
        if (factorTenish(a.totalMinutes?.toDouble(), b.totalMinutes?.toDouble())) add("total_minutes")
    }

    fun compatibleObservation(a: RideOffer, b: RideOffer): Boolean {
        if (!likelySameOffer(a, b)) return false
        if (decimalConflictFields(a, b).isNotEmpty()) return false
        return comparableCloseCount(a, b) >= 3
    }

    fun compatibleTail(a: RideOffer, b: RideOffer): Boolean =
        likelySameOffer(a, b) &&
            decimalConflictFields(a, b).isEmpty() &&
            comparableCloseCount(a, b) >= 3

    private fun stableAnchorCount(a: RideOffer, b: RideOffer): Int = listOf(
        close(a.fare, b.fare, 0.05, 0.01),
        close(a.pickupKm, b.pickupKm, 0.45, 0.14),
        close(a.tripKm, b.tripKm, 0.65, 0.14),
        close(a.totalKm, b.totalKm, 0.8, 0.12),
        closeInt(a.pickupMinutes, b.pickupMinutes, 2),
        closeInt(a.tripMinutes, b.tripMinutes, 3),
        closeInt(a.totalMinutes, b.totalMinutes, 4),
    ).count { it }

    private fun comparableCloseCount(a: RideOffer, b: RideOffer): Int {
        var count = 0
        comparableClose(a.fare, b.fare, 0.05, 0.01)?.let { if (it) count++ }
        comparableClose(a.pickupKm, b.pickupKm, 0.45, 0.14)?.let { if (it) count++ }
        comparableClose(a.tripKm, b.tripKm, 0.65, 0.14)?.let { if (it) count++ }
        comparableClose(a.totalKm, b.totalKm, 0.8, 0.12)?.let { if (it) count++ }
        comparableCloseInt(a.pickupMinutes, b.pickupMinutes, 2)?.let { if (it) count++ }
        comparableCloseInt(a.tripMinutes, b.tripMinutes, 3)?.let { if (it) count++ }
        comparableCloseInt(a.totalMinutes, b.totalMinutes, 4)?.let { if (it) count++ }
        return count
    }

    internal fun factorTenish(a: Double?, b: Double?): Boolean {
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
    ): Boolean? = if (a == null || b == null) null else close(a, b, absolute, relative)

    private fun comparableCloseInt(a: Int?, b: Int?, tolerance: Int): Boolean? =
        if (a == null || b == null) null else closeInt(a, b, tolerance)

    private fun serviceCompatible(a: String, b: String): Boolean =
        a.equals(b, true) || a.equals("unknown", true) || b.equals("unknown", true)

    private fun normalizeIdentity(value: String): String =
        value.lowercase()
            .replace(Regex("[^a-z0-9à-ÿ ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .take(180)
}

/**
 * Estado curto, somente em memória. Não persiste OCR, endereço ou coordenadas.
 */
internal class AdmissionWindow030 {
    enum class DecisionKind {
        ACCEPT_NORMAL,
        ACCEPT_CONFIRMED_TAIL,
        ACCEPT_CONFIRMED_DECIMAL_CHANGE,
        DEFER_TAIL,
        REJECT_DECIMAL_CONFLICT,
        REJECT_GENERIC_FALLBACK,
    }

    data class Decision(
        val kind: DecisionKind,
        val conflictFields: Set<String> = emptySet(),
    )

    private data class Seen(
        val offer: RideOffer,
        val atMs: Long,
    )

    private data class PendingConflict(
        val offer: RideOffer,
        val atMs: Long,
        val conflictFields: Set<String>,
    )

    private val recentAccepted = linkedMapOf<String, Seen>()
    private val pendingConflicts = linkedMapOf<String, PendingConflict>()
    private val pendingTails = linkedMapOf<String, Seen>()
    private val decidedLocalIds = linkedMapOf<String, Decision>()

    fun decide(offer: RideOffer, nowMs: Long): Decision {
        purge(nowMs)
        decidedLocalIds[offer.localId]?.let { return it }

        if (OfferAdmissionRules030.unsafeGenericFallback(offer)) {
            return remember(offer.localId, Decision(DecisionKind.REJECT_GENERIC_FALLBACK))
        }

        val key = OfferAdmissionRules030.targetKey(offer)
        val previous = recentAccepted[key]
            ?.takeIf { OfferAdmissionRules030.likelySameOffer(it.offer, offer) }
        val conflicts = previous?.let { OfferAdmissionRules030.decimalConflictFields(it.offer, offer) }.orEmpty()

        if (previous != null && conflicts.isNotEmpty()) {
            val pending = pendingConflicts[key]
            if (
                pending != null &&
                nowMs - pending.atMs <= DECIMAL_CONFIRMATION_MS &&
                pending.conflictFields == conflicts &&
                OfferAdmissionRules030.compatibleObservation(pending.offer, offer)
            ) {
                recentAccepted[key] = Seen(offer, nowMs)
                pendingConflicts.remove(key)
                return remember(
                    offer.localId,
                    Decision(DecisionKind.ACCEPT_CONFIRMED_DECIMAL_CHANGE, conflicts),
                )
            }

            pendingConflicts[key] = PendingConflict(offer, nowMs, conflicts)
            return remember(
                offer.localId,
                Decision(DecisionKind.REJECT_DECIMAL_CONFLICT, conflicts),
            )
        }

        pendingConflicts.remove(key)

        if (!OfferAdmissionRules030.extremeTail(offer)) {
            pendingTails.remove(key)
            recentAccepted[key] = Seen(offer, nowMs)
            return remember(offer.localId, Decision(DecisionKind.ACCEPT_NORMAL))
        }

        if (
            previous != null &&
            nowMs - previous.atMs <= CONFIRMATION_WINDOW_MS &&
            OfferAdmissionRules030.compatibleTail(previous.offer, offer)
        ) {
            pendingTails.remove(key)
            recentAccepted[key] = Seen(offer, nowMs)
            return remember(offer.localId, Decision(DecisionKind.ACCEPT_CONFIRMED_TAIL))
        }

        val pendingTail = pendingTails[key]
        if (
            pendingTail != null &&
            nowMs - pendingTail.atMs <= CONFIRMATION_WINDOW_MS &&
            OfferAdmissionRules030.compatibleTail(pendingTail.offer, offer)
        ) {
            pendingTails.remove(key)
            recentAccepted[key] = Seen(offer, nowMs)
            return remember(offer.localId, Decision(DecisionKind.ACCEPT_CONFIRMED_TAIL))
        }

        // A primeira cauda fica como referência apenas para confirmação da cauda;
        // ela ainda não se torna observação oficialmente aceita.
        pendingTails[key] = Seen(offer, nowMs)
        return remember(offer.localId, Decision(DecisionKind.DEFER_TAIL))
    }

    fun reset() {
        recentAccepted.clear()
        pendingConflicts.clear()
        pendingTails.clear()
        decidedLocalIds.clear()
    }

    private fun remember(localId: String, decision: Decision): Decision {
        decidedLocalIds[localId] = decision
        while (decidedLocalIds.size > 160) {
            val first = decidedLocalIds.entries.firstOrNull()?.key ?: break
            decidedLocalIds.remove(first)
        }
        return decision
    }

    private fun purge(nowMs: Long) {
        recentAccepted.entries.removeAll { nowMs - it.value.atMs > CONFIRMATION_WINDOW_MS }
        pendingConflicts.entries.removeAll { nowMs - it.value.atMs > CONFIRMATION_WINDOW_MS }
        pendingTails.entries.removeAll { nowMs - it.value.atMs > CONFIRMATION_WINDOW_MS }
    }

    companion object {
        const val CONFIRMATION_WINDOW_MS = 7_000L
        const val DECIMAL_CONFIRMATION_MS = 3_500L
    }
}
