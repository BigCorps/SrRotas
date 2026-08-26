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
        val defaultPreset =
            if (existing.onboardingCompleted) "custom" else "popular"

        return State(
            strategyPreset = normalizePreset(
                p.getString(PRESET, defaultPreset),
            ),
            maxPickupMinutes =
                p.getInt(MAX_PICKUP_MINUTES, 8)
                    .coerceIn(0, 120),
            appTheme = normalizeTheme(
                p.getString(APP_THEME, "auto"),
            ),
            hudThemeMode = normalizeHudTheme(
                p.getString(HUD_THEME_MODE, "follow_app"),
            ),
        )
    }

    fun savePreset(
        context: Context,
        preset: String,
    ) = edit(context) {
        putString(
            PRESET,
            normalizePreset(preset),
        )
    }

    fun saveMaxPickupMinutes(
        context: Context,
        minutes: Int,
    ) = edit(context) {
        putInt(
            MAX_PICKUP_MINUTES,
            minutes.coerceIn(0, 120),
        )
    }

    fun saveAppTheme(
        context: Context,
        theme: String,
    ) = edit(context) {
        putString(
            APP_THEME,
            normalizeTheme(theme),
        )
    }

    fun saveHudThemeMode(
        context: Context,
        mode: String,
    ) = edit(context) {
        putString(
            HUD_THEME_MODE,
            normalizeHudTheme(mode),
        )
    }

    fun shouldShowWelcome(context: Context): Boolean {
        val p =
            context.getSharedPreferences(
                PREFS,
                Context.MODE_PRIVATE,
            )
        return !SettingsRepository(context)
            .load()
            .onboardingCompleted &&
            !p.getBoolean(WELCOME_SEEN, false)
    }

    fun markWelcomeSeen(context: Context) =
        edit(context) {
            putBoolean(WELCOME_SEEN, true)
        }

    private inline fun edit(
        context: Context,
        block: android.content.SharedPreferences.Editor.() -> Unit,
    ) {
        context.getSharedPreferences(
            PREFS,
            Context.MODE_PRIVATE,
        ).edit().apply(block).apply()
    }

    private fun normalizePreset(value: String?): String =
        when (value) {
            "popular",
            "comfort",
            "premium",
            "custom" -> value
            else -> "custom"
        }

    private fun normalizeTheme(value: String?): String =
        when (value) {
            "light",
            "dark" -> value
            else -> "auto"
        }

    private fun normalizeHudTheme(value: String?): String =
        when (value) {
            "light",
            "dark" -> value
            else -> "follow_app"
        }
}

/**
 * Presets existentes desde 0.21.1.
 *
 * 0.22 mantém os mesmos valores já validados. Presets alteram somente as
 * metas financeiras; limites de busca em km/min continuam independentes.
 */
object StrategyPresets021 {
    fun apply(
        context: Context,
        preset: String,
    ) {
        val repo = SettingsRepository(context)
        val s = repo.load()
        val normalized =
            when (preset) {
                "comfort",
                "premium" -> preset
                else -> "popular"
            }

        val updated =
            when (normalized) {
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
        Strategy021Store.savePreset(
            context,
            normalized,
        )
    }
}

/**
 * 0.22: o veredito visual usa média ponderada das métricas habilitadas no HUD.
 * A ordem escolhida pelo motorista aumenta o peso, mas uma métrica financeira
 * isolada não derruba a corrida quando as demais compensam.
 */
object HudWeightedVerdict {
    private data class Metric(
        val key: String,
        val grade: Int,
    )

    fun apply(
        settings: DriverSettings,
        offer: RideOffer,
    ): RideOffer {
        val ordered =
            settings.hudMetricOrder
                .split(',')
                .map(String::trim)
                .filter(String::isNotBlank)

        val enabled =
            settings.hudEnabledMetrics
                .split(',')
                .map(String::trim)
                .filter(String::isNotBlank)
                .toSet()

        val activeKeys =
            if (enabled.isEmpty()) {
                ordered.toSet()
            } else {
                enabled
            }

        val metricMap = linkedMapOf(
            "per_minute" to
                gradeHigher(
                    offer.perMinute,
                    settings.redPerMinuteBelow,
                    settings.minPerMinute,
                ),
            "per_km" to
                gradeHigher(
                    offer.perKm,
                    settings.redPerKmBelow,
                    settings.minPerKm,
                ),
            "rating" to
                gradeHigher(
                    offer.passengerRating,
                    settings.redRatingBelow,
                    settings.goodRatingFrom,
                ),
            "per_hour" to
                gradeHigher(
                    offer.perHour,
                    settings.redPerHourBelow,
                    settings.minPerHour,
                ),
            "profit_hour" to
                gradeHigher(
                    offer.profitPerHour,
                    settings.redProfitPerHourBelow,
                    settings.minProfitPerHour,
                ),
            "profit_percent" to
                gradeHigher(
                    offer.profitPercent,
                    settings.redProfitPercentBelow,
                    settings.minProfitPercent,
                ),
            "profit" to
                gradeHigher(
                    offer.estimatedProfit,
                    0.0,
                    settings.minProfit,
                ),
        )

        val metrics =
            metricMap
                .filterKeys(activeKeys::contains)
                .mapNotNull { (key, grade) ->
                    if (grade >= 0) {
                        Metric(key, grade)
                    } else {
                        null
                    }
                }

        if (metrics.isEmpty()) {
            return offer.copy(
                verdict = "regular",
            )
        }

        var weighted = 0.0
        var weights = 0.0

        metrics.forEach { metric ->
            val index = ordered.indexOf(metric.key)
            val weight =
                when (index) {
                    0 -> 1.70
                    1 -> 1.45
                    2 -> 1.25
                    3 -> 1.10
                    else -> 1.00
                }

            weighted += metric.grade * weight
            weights += weight
        }

        val average =
            if (weights > 0.0) {
                weighted / weights
            } else {
                1.0
            }

        val verdict =
            when {
                average >= 1.35 -> "boa"
                average < 0.72 -> "ruim"
                else -> "regular"
            }

        return offer.copy(
            verdict = verdict,
        )
    }

    private fun gradeHigher(
        value: Double?,
        redBelow: Double,
        goodFrom: Double,
    ): Int {
        if (
            value == null ||
            (redBelow <= 0.0 && goodFrom <= 0.0)
        ) {
            return -1
        }

        if (
            redBelow > 0.0 &&
            value < redBelow
        ) {
            return 0
        }

        if (
            goodFrom > 0.0 &&
            value >= goodFrom
        ) {
            return 2
        }

        return 1
    }
}

/**
 * Camada pós-parser. OCR, parser, dedupe e estabilização continuam intocados.
 *
 * A média ponderada decide a parte financeira do HUD. Limites absolutos de
 * estratégia (tarifa mínima e busca máxima) continuam sendo travas explícitas.
 */
object StrategyGuard021 {
    fun apply(
        context: Context,
        offer: RideOffer,
    ): RideOffer {
        val settings =
            SettingsRepository(context)
                .load()

        val weighted =
            HudWeightedVerdict.apply(
                settings,
                offer,
            )

        val maxMinutes =
            Strategy021Store.load(context)
                .maxPickupMinutes

        val belowAbsoluteFare =
            settings.minFare > 0.0 &&
            weighted.fare < settings.minFare

        val exceedsKm =
            settings.maxPickupKm > 0.0 &&
            weighted.pickupKm != null &&
            weighted.pickupKm > settings.maxPickupKm

        val exceedsMinutes =
            maxMinutes > 0 &&
            weighted.pickupMinutes != null &&
            weighted.pickupMinutes > maxMinutes

        return if (
            belowAbsoluteFare ||
            exceedsKm ||
            exceedsMinutes
        ) {
            weighted.copy(
                verdict = "ruim",
            )
        } else {
            weighted
        }
    }
}

object Appearance021 {
    fun isDark(context: Context): Boolean =
        when (
            Strategy021Store.load(context)
                .appTheme
        ) {
            "dark" -> true
            "light" -> false
            else ->
                (
                    context.resources.configuration.uiMode and
                        Configuration.UI_MODE_NIGHT_MASK
                    ) ==
                    Configuration.UI_MODE_NIGHT_YES
        }
}
