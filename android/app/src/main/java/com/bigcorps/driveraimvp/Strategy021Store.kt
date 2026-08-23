package com.srrotas.app

import android.content.Context

object Strategy021Store {
    private const val PREFS = "sr_rotas_021"
    private const val PRESET = "strategy_preset"
    private const val MAX_PICKUP_MINUTES = "max_pickup_minutes"
    private const val APP_THEME = "app_theme"
    private const val HUD_THEME_MODE = "hud_theme_mode"
    private const val WELCOME_SEEN = "welcome_021_seen"

    data class State(
        val strategyPreset: String,
        val maxPickupMinutes: Int,
        val appTheme: String,
        val hudThemeMode: String,
    )

    fun load(context: Context): State {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val existing = SettingsRepository(context).load()
        val defaultPreset = if (existing.onboardingCompleted) "custom" else "popular"
        return State(
            strategyPreset = normalizePreset(p.getString(PRESET, defaultPreset)),
            maxPickupMinutes = p.getInt(MAX_PICKUP_MINUTES, 8).coerceIn(0, 120),
            appTheme = normalizeTheme(p.getString(APP_THEME, "auto")),
            hudThemeMode = normalizeHudTheme(p.getString(HUD_THEME_MODE, "follow_app")),
        )
    }

    fun savePreset(context: Context, preset: String) =
        edit(context) { putString(PRESET, normalizePreset(preset)) }

    fun saveMaxPickupMinutes(context: Context, minutes: Int) =
        edit(context) { putInt(MAX_PICKUP_MINUTES, minutes.coerceIn(0, 120)) }

    fun saveAppTheme(context: Context, theme: String) =
        edit(context) { putString(APP_THEME, normalizeTheme(theme)) }

    fun saveHudThemeMode(context: Context, mode: String) =
        edit(context) { putString(HUD_THEME_MODE, normalizeHudTheme(mode)) }

    fun shouldShowWelcome(context: Context): Boolean {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return !SettingsRepository(context).load().onboardingCompleted && !p.getBoolean(WELCOME_SEEN, false)
    }

    fun markWelcomeSeen(context: Context) =
        edit(context) { putBoolean(WELCOME_SEEN, true) }

    private inline fun edit(context: Context, block: android.content.SharedPreferences.Editor.() -> Unit) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().apply(block).apply()
    }

    private fun normalizePreset(value: String?): String =
        when (value) { "popular", "comfort", "premium", "custom" -> value; else -> "custom" }

    private fun normalizeTheme(value: String?): String =
        when (value) { "light", "dark" -> value; else -> "auto" }

    private fun normalizeHudTheme(value: String?): String =
        when (value) { "light", "dark" -> value; else -> "follow_app" }
}

object StrategyPresets021 {
    fun apply(context: Context, preset: String) {
        val repo = SettingsRepository(context)
        val s = repo.load()
        val updated = when (preset) {
            "comfort" -> s.copy(
                redPerKmBelow = 1.50, minPerKm = 1.80,
                redPerMinuteBelow = 0.50, minPerMinute = 0.65,
                redPerHourBelow = 30.0, minPerHour = 39.0,
                maxPickupKm = 5.0,
            )
            "premium" -> s.copy(
                redPerKmBelow = 1.80, minPerKm = 2.20,
                redPerMinuteBelow = 0.65, minPerMinute = 0.85,
                redPerHourBelow = 39.0, minPerHour = 51.0,
                maxPickupKm = 6.0,
            )
            else -> s.copy(
                redPerKmBelow = 1.20, minPerKm = 1.50,
                redPerMinuteBelow = 0.40, minPerMinute = 0.50,
                redPerHourBelow = 24.0, minPerHour = 30.0,
                maxPickupKm = 4.0,
            )
        }
        repo.save(updated)
        Strategy021Store.saveMaxPickupMinutes(
            context,
            when (preset) {
                "comfort" -> 10
                "premium" -> 12
                else -> 8
            },
        )
        Strategy021Store.savePreset(context, preset)
    }
}

object StrategyGuard021 {
    /**
     * Camada pós-parser. Não altera OCR, CardStabilizer, dedupe nem extração financeira.
     */
    fun apply(context: Context, offer: RideOffer): RideOffer {
        val settings = SettingsRepository(context).load()
        val maxMinutes = Strategy021Store.load(context).maxPickupMinutes
        val exceedsKm =
            settings.maxPickupKm > 0.0 &&
                offer.pickupKm != null &&
                offer.pickupKm > settings.maxPickupKm
        val exceedsMinutes =
            maxMinutes > 0 &&
                offer.pickupMinutes != null &&
                offer.pickupMinutes > maxMinutes

        return if ((exceedsKm || exceedsMinutes) && offer.verdict != "ruim") {
            offer.copy(verdict = "ruim")
        } else {
            offer
        }
    }
}

object Appearance021 {
    fun isDark(context: Context): Boolean = when (Strategy021Store.load(context).appTheme) {
        "dark" -> true
        "light" -> false
        else -> (context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) == android.content.res.Configuration.UI_MODE_NIGHT_YES
    }
}
