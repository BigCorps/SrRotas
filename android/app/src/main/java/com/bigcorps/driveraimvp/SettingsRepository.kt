package com.srrotas.app

import android.content.Context

class SettingsRepository(context: Context) {
    companion object { const val DEFAULT_BACKEND_URL = "https://sr-rotas.vercel.app" }
    private val prefs = context.getSharedPreferences("driver_ai_settings", Context.MODE_PRIVATE)

    fun load(): DriverSettings = DriverSettings(
        backendUrl = prefs.getString("backend_url", DEFAULT_BACKEND_URL)?.ifBlank { DEFAULT_BACKEND_URL } ?: DEFAULT_BACKEND_URL,
        deviceToken = prefs.getString("device_token", "") ?: "",
        minPerKm = double("min_per_km", 1.80), redPerKmBelow = double("red_per_km_below", 1.45),
        minPerHour = double("min_per_hour", 35.0), redPerHourBelow = double("red_per_hour_below", 28.0),
        goodRatingFrom = double("good_rating_from", 4.85), redRatingBelow = double("red_rating_below", 4.70),
        minPerMinute = double("min_per_minute", 0.60), redPerMinuteBelow = double("red_per_minute_below", 0.48),
        minFare = double("min_fare", 0.0), maxPickupKm = double("max_pickup_km", 5.0), minProfit = double("min_profit", 0.0),
        minProfitPerHour = double("min_profit_per_hour", 0.0), redProfitPerHourBelow = double("red_profit_per_hour_below", 0.0),
        minProfitPercent = double("min_profit_percent", 0.0), redProfitPercentBelow = double("red_profit_percent_below", 0.0),
        costPerKm = double("cost_per_km", 0.85),
        ocrEnabled = prefs.getBoolean("ocr_enabled", true), consentAccepted = prefs.getBoolean("consent_accepted", false),
        hudMetricOrder = prefs.getString("hud_metric_order", DriverSettings().hudMetricOrder) ?: DriverSettings().hudMetricOrder,
        hudEnabledMetrics = prefs.getString("hud_enabled_metrics", DriverSettings().hudEnabledMetrics) ?: DriverSettings().hudEnabledMetrics,
        hudPosition = prefs.getString("hud_position", "left") ?: "left", hudTheme = prefs.getString("hud_theme", "light") ?: "light",
        colorBlindMode = prefs.getBoolean("color_blind_mode", false), hudOpacity = prefs.getInt("hud_opacity", 90).coerceIn(30,100),
        hudFontSize = prefs.getInt("hud_font_size", 13).coerceIn(11,20),
        textNotificationEnabled = prefs.getBoolean("text_notification_enabled", false), voiceNotificationEnabled = prefs.getBoolean("voice_notification_enabled", false),
        privateScreenshotEnabled = prefs.getBoolean("private_screenshot_enabled", false),
        defaultPassengerMessage = prefs.getString("default_passenger_message", DriverSettings().defaultPassengerMessage) ?: DriverSettings().defaultPassengerMessage,
    )

    fun save(s: DriverSettings) {
        prefs.edit()
            .putString("backend_url", s.backendUrl.trim().trimEnd('/').ifBlank { DEFAULT_BACKEND_URL }).putString("device_token", s.deviceToken)
            .putString("min_per_km", s.minPerKm.toString()).putString("red_per_km_below", s.redPerKmBelow.toString())
            .putString("min_per_hour", s.minPerHour.toString()).putString("red_per_hour_below", s.redPerHourBelow.toString())
            .putString("good_rating_from", s.goodRatingFrom.toString()).putString("red_rating_below", s.redRatingBelow.toString())
            .putString("min_per_minute", s.minPerMinute.toString()).putString("red_per_minute_below", s.redPerMinuteBelow.toString())
            .putString("min_fare", s.minFare.toString()).putString("max_pickup_km", s.maxPickupKm.toString()).putString("min_profit", s.minProfit.toString())
            .putString("min_profit_per_hour", s.minProfitPerHour.toString()).putString("red_profit_per_hour_below", s.redProfitPerHourBelow.toString())
            .putString("min_profit_percent", s.minProfitPercent.toString()).putString("red_profit_percent_below", s.redProfitPercentBelow.toString())
            .putString("cost_per_km", s.costPerKm.toString()).putBoolean("ocr_enabled", s.ocrEnabled).putBoolean("consent_accepted", s.consentAccepted)
            .putString("hud_metric_order", s.hudMetricOrder).putString("hud_enabled_metrics", s.hudEnabledMetrics).putString("hud_position", s.hudPosition)
            .putString("hud_theme", s.hudTheme).putBoolean("color_blind_mode", s.colorBlindMode).putInt("hud_opacity", s.hudOpacity.coerceIn(30,100))
            .putInt("hud_font_size", s.hudFontSize.coerceIn(11,20)).putBoolean("text_notification_enabled", s.textNotificationEnabled)
            .putBoolean("voice_notification_enabled", s.voiceNotificationEnabled).putBoolean("private_screenshot_enabled", s.privateScreenshotEnabled)
            .putString("default_passenger_message", s.defaultPassengerMessage.take(600)).apply()
    }

    private fun double(key: String, fallback: Double) = prefs.getString(key, fallback.toString())?.toDoubleOrNull() ?: fallback
    fun saveDeviceToken(token: String) { prefs.edit().putString("device_token", token).apply() }
    fun saveLatestCapture(summary: String, raw: String, method: String) { prefs.edit().putString("latest_summary",summary).putString("latest_raw",raw.take(12000)).putString("latest_method",method).putLong("latest_at_ms",System.currentTimeMillis()).apply() }
    fun latestSummary(): String = prefs.getString("latest_summary", "Nenhuma oferta reconhecida ainda.") ?: "Nenhuma oferta reconhecida ainda."
    fun latestRaw(): String = prefs.getString("latest_raw", "") ?: ""
    fun latestMethod(): String = prefs.getString("latest_method", "") ?: ""
    fun setProjectionActive(active: Boolean) { prefs.edit().putBoolean("projection_active", active).apply() }
    fun isProjectionActive(): Boolean = prefs.getBoolean("projection_active", false)
    fun setCurrentJourney(id: String, startedAt: String) { prefs.edit().putString("current_journey_id",id).putString("current_journey_started_at",startedAt).apply() }
    fun currentJourneyId(): String = prefs.getString("current_journey_id", "") ?: ""
    fun currentJourneyStartedAt(): String = prefs.getString("current_journey_started_at", "") ?: ""
    fun clearCurrentJourney() { prefs.edit().remove("current_journey_id").remove("current_journey_started_at").apply() }
    @Synchronized fun shouldEmitOffer(dedupeKey: String, windowMs: Long = 30_000L): Boolean { val now=System.currentTimeMillis();val lastKey=prefs.getString("last_offer_key","")?:"";val lastAt=prefs.getLong("last_offer_at",0L);if(lastKey==dedupeKey&&now-lastAt<windowMs)return false;prefs.edit().putString("last_offer_key",dedupeKey).putLong("last_offer_at",now).apply();return true }
}
