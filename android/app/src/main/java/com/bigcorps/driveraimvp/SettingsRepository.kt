package com.srrotas.app

import android.content.Context

class SettingsRepository(context: Context) {
    companion object {
        const val DEFAULT_BACKEND_URL = "https://sr-rotas.vercel.app"
    }

    private val prefs = context.getSharedPreferences("driver_ai_settings", Context.MODE_PRIVATE)

    fun load(): DriverSettings = DriverSettings(
        backendUrl = prefs.getString("backend_url", DEFAULT_BACKEND_URL)?.ifBlank { DEFAULT_BACKEND_URL } ?: DEFAULT_BACKEND_URL,
        deviceToken = prefs.getString("device_token", "") ?: "",
        minPerKm = prefs.getString("min_per_km", "1.80")?.toDoubleOrNull() ?: 1.80,
        minPerHour = prefs.getString("min_per_hour", "35.0")?.toDoubleOrNull() ?: 35.0,
        minFare = prefs.getString("min_fare", "0.0")?.toDoubleOrNull() ?: 0.0,
        maxPickupKm = prefs.getString("max_pickup_km", "5.0")?.toDoubleOrNull() ?: 5.0,
        minProfit = prefs.getString("min_profit", "0.0")?.toDoubleOrNull() ?: 0.0,
        costPerKm = prefs.getString("cost_per_km", "0.85")?.toDoubleOrNull() ?: 0.85,
        ocrEnabled = prefs.getBoolean("ocr_enabled", true),
        consentAccepted = prefs.getBoolean("consent_accepted", false),
    )

    fun save(settings: DriverSettings) {
        prefs.edit()
            .putString("backend_url", settings.backendUrl.trim().trimEnd('/').ifBlank { DEFAULT_BACKEND_URL })
            .putString("device_token", settings.deviceToken)
            .putString("min_per_km", settings.minPerKm.toString())
            .putString("min_per_hour", settings.minPerHour.toString())
            .putString("min_fare", settings.minFare.toString())
            .putString("max_pickup_km", settings.maxPickupKm.toString())
            .putString("min_profit", settings.minProfit.toString())
            .putString("cost_per_km", settings.costPerKm.toString())
            .putBoolean("ocr_enabled", settings.ocrEnabled)
            .putBoolean("consent_accepted", settings.consentAccepted)
            .apply()
    }

    fun saveDeviceToken(token: String) { prefs.edit().putString("device_token", token).apply() }

    fun saveLatestCapture(summary: String, raw: String, method: String) {
        prefs.edit()
            .putString("latest_summary", summary)
            .putString("latest_raw", raw.take(12000))
            .putString("latest_method", method)
            .putLong("latest_at_ms", System.currentTimeMillis())
            .apply()
    }

    fun latestSummary(): String = prefs.getString("latest_summary", "Nenhuma oferta reconhecida ainda.") ?: "Nenhuma oferta reconhecida ainda."
    fun latestRaw(): String = prefs.getString("latest_raw", "") ?: ""
    fun latestMethod(): String = prefs.getString("latest_method", "") ?: ""

    fun setProjectionActive(active: Boolean) { prefs.edit().putBoolean("projection_active", active).apply() }
    fun isProjectionActive(): Boolean = prefs.getBoolean("projection_active", false)

    fun setCurrentJourney(id: String, startedAt: String) {
        prefs.edit().putString("current_journey_id", id).putString("current_journey_started_at", startedAt).apply()
    }
    fun currentJourneyId(): String = prefs.getString("current_journey_id", "") ?: ""
    fun currentJourneyStartedAt(): String = prefs.getString("current_journey_started_at", "") ?: ""
    fun clearCurrentJourney() { prefs.edit().remove("current_journey_id").remove("current_journey_started_at").apply() }

    @Synchronized
    fun shouldEmitOffer(dedupeKey: String, windowMs: Long = 30_000L): Boolean {
        val now = System.currentTimeMillis()
        val lastKey = prefs.getString("last_offer_key", "") ?: ""
        val lastAt = prefs.getLong("last_offer_at", 0L)
        if (lastKey == dedupeKey && now - lastAt < windowMs) return false
        prefs.edit().putString("last_offer_key", dedupeKey).putLong("last_offer_at", now).apply()
        return true
    }
}
