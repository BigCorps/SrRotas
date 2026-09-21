package com.srrotas.app

import android.content.Context
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.max

/**
 * 0.28 — último gate conservador entre parser e HUD/persistência.
 *
 * O objetivo não é "corrigir" números por adivinhação. Quando a geometria
 * interpretada produz uma impossibilidade física ou uma incoerência aritmética,
 * a leitura é descartada e registrada no diagnóstico. Isso protege o patrimônio
 * principal do Sr. Rotas: os dados observados de cada oferta.
 */
object OfferIntegrityGuard028 {
    private const val PREFS = "sr_offer_integrity_028"
    private const val MAX_PICKUP_AVG_KMH = 120.0
    private const val MAX_TRIP_AVG_KMH = 130.0

    data class Inspection(
        val accepted: Boolean,
        val reason: String? = null,
        val pickupAverageKmh: Double? = null,
        val tripAverageKmh: Double? = null,
    )

    fun inspect(offer: RideOffer): Inspection {
        val pickupSpeed = legAverageKmh(offer.pickupKm, offer.pickupMinutes)
        val tripSpeed = legAverageKmh(offer.tripKm, offer.tripMinutes)

        if (pickupSpeed != null && pickupSpeed > MAX_PICKUP_AVG_KMH) {
            return Inspection(false, "pickup_speed_outlier", pickupSpeed, tripSpeed)
        }
        if (tripSpeed != null && tripSpeed > MAX_TRIP_AVG_KMH) {
            return Inspection(false, "trip_speed_outlier", pickupSpeed, tripSpeed)
        }

        val pickupKm = offer.pickupKm
        val tripKm = offer.tripKm
        val totalKm = offer.totalKm
        if (pickupKm != null && tripKm != null && totalKm != null) {
            val expected = pickupKm + tripKm
            val tolerance = max(0.35, expected * 0.04)
            if (abs(totalKm - expected) > tolerance) {
                return Inspection(false, "total_km_mismatch", pickupSpeed, tripSpeed)
            }
        }

        val pickupMinutes = offer.pickupMinutes
        val tripMinutes = offer.tripMinutes
        val totalMinutes = offer.totalMinutes
        if (pickupMinutes != null && tripMinutes != null && totalMinutes != null) {
            if (abs(totalMinutes - (pickupMinutes + tripMinutes)) > 1) {
                return Inspection(false, "total_minutes_mismatch", pickupSpeed, tripSpeed)
            }
        }

        if (totalKm != null && totalKm > 0.0 && offer.perKm != null) {
            val expected = offer.fare / totalKm
            val tolerance = max(0.08, expected * 0.08)
            if (abs(offer.perKm - expected) > tolerance) {
                return Inspection(false, "per_km_mismatch", pickupSpeed, tripSpeed)
            }
        }

        if (totalMinutes != null && totalMinutes > 0 && offer.perMinute != null) {
            val expected = offer.fare / totalMinutes.toDouble()
            val tolerance = max(0.05, expected * 0.08)
            if (abs(offer.perMinute - expected) > tolerance) {
                return Inspection(false, "per_minute_mismatch", pickupSpeed, tripSpeed)
            }
        }

        return Inspection(true, pickupAverageKmh = pickupSpeed, tripAverageKmh = tripSpeed)
    }

    fun isPlausible(offer: RideOffer): Boolean = inspect(offer).accepted

    /**
     * Registra somente rejeições. Não persiste OCR bruto, endereço ou coordenada.
     */
    fun accept(context: Context, offer: RideOffer, stage: String): Boolean {
        val inspection = inspect(offer)
        if (inspection.accepted) return true

        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val reason = inspection.reason ?: "unknown"
        prefs.edit()
            .putInt("rejected_total", prefs.getInt("rejected_total", 0) + 1)
            .putInt("reason_$reason", prefs.getInt("reason_$reason", 0) + 1)
            .putString("last_reason", reason)
            .putString("last_stage", stage)
            .putLong("last_at", System.currentTimeMillis())
            .putString("last_platform", offer.platform)
            .putString("last_fare", offer.fare.toString())
            .putString("last_pickup_km", offer.pickupKm?.toString().orEmpty())
            .putString("last_trip_km", offer.tripKm?.toString().orEmpty())
            .putString("last_pickup_minutes", offer.pickupMinutes?.toString().orEmpty())
            .putString("last_trip_minutes", offer.tripMinutes?.toString().orEmpty())
            .putString("last_pickup_avg_kmh", inspection.pickupAverageKmh?.let(::round1)?.toString().orEmpty())
            .putString("last_trip_avg_kmh", inspection.tripAverageKmh?.let(::round1)?.toString().orEmpty())
            .apply()

        LocalLog.append(
            context.applicationContext,
            "INTEGRIDADE 0.28 rejeitou oferta · $reason · " +
                "R$ ${offer.fare} · pickup=${offer.pickupKm ?: "?"}km/${offer.pickupMinutes ?: "?"}min · " +
                "trip=${offer.tripKm ?: "?"}km/${offer.tripMinutes ?: "?"}min",
        )
        return false
    }

    fun toJson(context: Context): JSONObject {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return JSONObject().apply {
            put("schema", "sr-offer-integrity-028-v1")
            put("max_pickup_avg_kmh", MAX_PICKUP_AVG_KMH)
            put("max_trip_avg_kmh", MAX_TRIP_AVG_KMH)
            put("rejected_total", prefs.getInt("rejected_total", 0))
            put("pickup_speed_outlier", prefs.getInt("reason_pickup_speed_outlier", 0))
            put("trip_speed_outlier", prefs.getInt("reason_trip_speed_outlier", 0))
            put("total_km_mismatch", prefs.getInt("reason_total_km_mismatch", 0))
            put("total_minutes_mismatch", prefs.getInt("reason_total_minutes_mismatch", 0))
            put("per_km_mismatch", prefs.getInt("reason_per_km_mismatch", 0))
            put("per_minute_mismatch", prefs.getInt("reason_per_minute_mismatch", 0))
            put("last_reason", prefs.getString("last_reason", "") ?: "")
            put("last_stage", prefs.getString("last_stage", "") ?: "")
            put("last_at", prefs.getLong("last_at", 0L))
            put("last_platform", prefs.getString("last_platform", "") ?: "")
            put("last_fare", prefs.getString("last_fare", "") ?: "")
            put("last_pickup_km", prefs.getString("last_pickup_km", "") ?: "")
            put("last_trip_km", prefs.getString("last_trip_km", "") ?: "")
            put("last_pickup_minutes", prefs.getString("last_pickup_minutes", "") ?: "")
            put("last_trip_minutes", prefs.getString("last_trip_minutes", "") ?: "")
            put("last_pickup_avg_kmh", prefs.getString("last_pickup_avg_kmh", "") ?: "")
            put("last_trip_avg_kmh", prefs.getString("last_trip_avg_kmh", "") ?: "")
            put("privacy", "Sem OCR bruto, screenshot, endereço ou coordenada.")
        }
    }

    internal fun legAverageKmh(km: Double?, minutes: Int?): Double? {
        if (km == null || minutes == null || km <= 0.0 || minutes <= 0) return null
        return km * 60.0 / minutes.toDouble()
    }

    private fun round1(value: Double): Double = kotlin.math.round(value * 10.0) / 10.0
}
