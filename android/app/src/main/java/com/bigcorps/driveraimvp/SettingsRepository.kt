package com.bigcorps.driveraimvp

import android.content.Context

class SettingsRepository(context: Context) {
    private val prefs = context.getSharedPreferences("driver_ai_settings", Context.MODE_PRIVATE)

    fun load(): DriverSettings = DriverSettings(
        backendUrl = prefs.getString("backend_url", "") ?: "",
        deviceToken = prefs.getString("device_token", "") ?: "",
        minPerKm = prefs.getString("min_per_km", "1.80")?.toDoubleOrNull() ?: 1.80,
        minPerHour = prefs.getString("min_per_hour", "35.0")?.toDoubleOrNull() ?: 35.0,
        costPerKm = prefs.getString("cost_per_km", "0.85")?.toDoubleOrNull() ?: 0.85,
        ocrEnabled = prefs.getBoolean("ocr_enabled", true),
        consentAccepted = prefs.getBoolean("consent_accepted", false),
    )

    fun save(settings: DriverSettings) {
        prefs.edit()
            .putString("backend_url", settings.backendUrl.trim().trimEnd('/'))
            .putString("device_token", settings.deviceToken)
            .putString("min_per_km", settings.minPerKm.toString())
            .putString("min_per_hour", settings.minPerHour.toString())
            .putString("cost_per_km", settings.costPerKm.toString())
            .putBoolean("ocr_enabled", settings.ocrEnabled)
            .putBoolean("consent_accepted", settings.consentAccepted)
            .apply()
    }

    fun saveDeviceToken(token: String) {
        prefs.edit().putString("device_token", token).apply()
    }

    fun saveLatestCapture(summary: String, raw: String, method: String) {
        prefs.edit()
            .putString("latest_summary", summary)
            .putString("latest_raw", raw)
            .putString("latest_method", method)
            .putLong("latest_at_ms", System.currentTimeMillis())
            .apply()
    }

    fun latestSummary(): String = prefs.getString("latest_summary", "Nenhuma oferta reconhecida ainda.") ?: ""
    fun latestRaw(): String = prefs.getString("latest_raw", "") ?: ""
    fun latestMethod(): String = prefs.getString("latest_method", "") ?: ""
}
