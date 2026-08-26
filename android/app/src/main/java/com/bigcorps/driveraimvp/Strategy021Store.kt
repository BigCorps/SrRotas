package com.srrotas.app

import android.content.Context
import android.content.res.Configuration

/**
 * Preferências de estratégia 0.21/0.22.
 *
 * IMPORTANTE: mantém exatamente o mesmo arquivo e as mesmas chaves da 0.21
 * para não apagar/resetar preferências de quem já usa o app.
 */
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

    fun savePreset(context: Context, preset: String) = edit(context) {
        putString(PRESET, normalizePreset(preset))
    }

    fun saveMaxPickupMinutes(context: Context, minutes: Int) = edit(context) {
        putInt(MAX_PICKUP_MINUTES, minutes.coerceIn(0, 120))
    }

    fun saveAppTheme(context: Context, theme: String) = edit(context) {
        putString(APP_THEME, normalizeTheme(theme))
    }

    fun saveHudThemeMode(context: Context, mode: String) = edit(context) {
        putString(HUD_THEME_MODE, normalizeHudTheme(mode))
    }

    fun shouldShowWelcome(context: Context): Boolean {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return !SettingsRepository(context).load().onboardingCompleted &&
            !p.getBoolean(WELCOME_SEEN, false)
    }

    fun markWelcomeSeen(context: Context) = edit(context) {
        putBoolean(WELCOME_SEEN, true)
    }

    private inline fun edit(
        context: Context,
        block: android.content.SharedPreferences.Editor.() -> Unit,
    ) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .apply(block)
            .apply()
    }

    private fun normalizePreset(value: String?): String = when (value) {
        "popular", "comfort", "premium", "custom" -> value
        else -> "custom"
    }

    private fun normalizeTheme(value: String?): String = when (value) {
        "light", "dark" -> value
        else -> "auto"
    }

    private fun normalizeHudTheme(value: String?): String = when (value) {
        "light", "dark" -> value
        else -> "follow_app"
    }
}

/** Presets existentes desde 0.21.1. */
object StrategyPresets021 {
    fun apply(context: Context, preset: String) {
        val repo = SettingsRepository(context)
        val s = repo.load()
        val normalized = when (preset) {
            "comfort", "premium" -> preset
            else -> "popular"
        }

        val updated = when (normalized) {
            "comfort" -> s.copy(
                redPerKmBelow = 1.50,
                minPerKm = 1.80,
                redPerMinuteBelow = 0.50,
                minPerMinute = 0.65,
                redPerHourBelow = 30.0,
                minPerHour = 39.0,
            )
            "premium" -> s.copy(
                redPerKmBelow = 1.80,
                minPerKm = 2.20,
                redPerMinuteBelow = 0.65,
                minPerMinute = 0.85,
                redPerHourBelow = 39.0,
                minPerHour = 51.0,
            )
            else -> s.copy(
                redPerKmBelow = 1.20,
                minPerKm = 1.50,
                redPerMinuteBelow = 0.40,
                minPerMinute = 0.50,
                redPerHourBelow = 24.0,
                minPerHour = 30.0,
            )
        }

        repo.save(updated)
        Strategy021Store.savePreset(context, normalized)
    }
}

/**
 * 0.22.1: Busca passa a ser uma métrica normal da média ponderada.
 * Nenhuma métrica isolada possui poder de pintar todo o HUD de vermelho.
 */
object HudWeightedVerdict {
    fun apply(settings: DriverSettings, offer: RideOffer): RideOffer {
        // Mantém a assinatura antiga para testes/callers sem Context. O limite
        // padrão de 8 min só é usado aqui; StrategyGuard usa o valor real salvo.
        return offer.copy(
            verdict = HudMetricEvaluation0221.weightedVerdict(
                settings = settings,
                offer = offer,
                maxPickupMinutes = 8,
            ),
        )
    }

    fun apply(settings: DriverSettings, offer: RideOffer, maxPickupMinutes: Int): RideOffer =
        offer.copy(
            verdict = HudMetricEvaluation0221.weightedVerdict(
                settings = settings,
                offer = offer,
                maxPickupMinutes = maxPickupMinutes,
            ),
        )
}

/**
 * Camada pós-parser. A Busca participa da avaliação, mas deixou de ser veto.
 * A única trava absoluta preservada é o valor mínimo explicitamente definido.
 */
object StrategyGuard021 {
    fun apply(context: Context, offer: RideOffer): RideOffer {
        val settings = SettingsRepository(context).load()
        val maxMinutes = Strategy021Store.load(context).maxPickupMinutes
        val weighted = HudWeightedVerdict.apply(settings, offer, maxMinutes)

        val belowAbsoluteFare = settings.minFare > 0.0 && weighted.fare < settings.minFare
        return if (belowAbsoluteFare) weighted.copy(verdict = "ruim") else weighted
    }
}

object Appearance021 {
    fun isDark(context: Context): Boolean = when (Strategy021Store.load(context).appTheme) {
        "dark" -> true
        "light" -> false
        else ->
            (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
    }
}
