package com.srrotas.app

import android.content.Context
import android.content.res.Configuration

/** Preferências novas da 0.21, sem alterar o schema legado do SettingsRepository. */
data class Strategy021Config(
    val strategyPreset: String = "custom",
    val maxPickupMinutes: Int = 0,
    val appTheme: String = "auto",
    val hudThemeMode: String = "follow_app",
)

data class StrategyPreset021(
    val key: String,
    val label: String,
    val minPerKm: Double,
    val redPerKmBelow: Double,
    val minPerHour: Double,
    val redPerHourBelow: Double,
    val minPerMinute: Double,
    val redPerMinuteBelow: Double,
)

object StrategyPresets021 {
    val all = listOf(
        StrategyPreset021("popular", "Popular", 1.80, 1.45, 35.0, 28.0, 0.60, 0.48),
        StrategyPreset021("comfort", "Conforto", 2.20, 1.80, 42.0, 34.0, 0.72, 0.58),
        StrategyPreset021("premium", "Premium", 2.80, 2.20, 55.0, 44.0, 0.92, 0.74),
    )
    fun byKey(key: String) = all.firstOrNull { it.key == key }
}

object Strategy021Store {
    private const val FILE = "sr_rotas_strategy_021"
    private const val KEY_PRESET = "strategy_preset"
    private const val KEY_PICKUP_MIN = "max_pickup_minutes"
    private const val KEY_APP_THEME = "app_theme"
    private const val KEY_HUD_THEME_MODE = "hud_theme_mode"
    private const val KEY_WELCOME = "welcome_seen_021"

    fun load(context: Context): Strategy021Config {
        val p = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        return Strategy021Config(
            strategyPreset = p.getString(KEY_PRESET, "custom") ?: "custom",
            maxPickupMinutes = p.getInt(KEY_PICKUP_MIN, 0).coerceIn(0, 180),
            appTheme = normalizeTheme(p.getString(KEY_APP_THEME, "auto")),
            hudThemeMode = normalizeHudTheme(p.getString(KEY_HUD_THEME_MODE, "follow_app")),
        )
    }

    fun savePreset(context: Context, value: String) = edit(context, KEY_PRESET, value)
    fun saveMaxPickupMinutes(context: Context, value: Int) =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putInt(KEY_PICKUP_MIN, value.coerceIn(0, 180)).apply()
    fun saveAppTheme(context: Context, value: String) = edit(context, KEY_APP_THEME, normalizeTheme(value))
    fun saveHudThemeMode(context: Context, value: String) = edit(context, KEY_HUD_THEME_MODE, normalizeHudTheme(value))

    fun shouldShowWelcome(context: Context): Boolean =
        !context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getBoolean(KEY_WELCOME, false)

    fun markWelcomeSeen(context: Context) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putBoolean(KEY_WELCOME, true).apply()
    }

    private fun edit(context: Context, key: String, value: String) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putString(key, value).apply()
    }

    private fun normalizeTheme(value: String?) = if (value in setOf("light", "dark")) value!! else "auto"
    private fun normalizeHudTheme(value: String?) = if (value in setOf("light", "dark")) value!! else "follow_app"
}

/**
 * 0.22: o veredito visual usa média ponderada das métricas habilitadas no HUD.
 * A ordem escolhida pelo motorista aumenta o peso, mas nenhuma métrica isolada
 * derruba a corrida quando as demais compensam.
 */
object HudWeightedVerdict {
    private data class Metric(val key: String, val grade: Int)

    fun apply(settings: DriverSettings, offer: RideOffer): RideOffer {
        val ordered = settings.hudMetricOrder
            .split(',')
            .map(String::trim)
            .filter(String::isNotBlank)
        val enabled = settings.hudEnabledMetrics
            .split(',')
            .map(String::trim)
            .filter(String::isNotBlank)
            .toSet()
        val activeKeys = if (enabled.isEmpty()) ordered.toSet() else enabled

        val metricMap = linkedMapOf(
            "per_minute" to gradeHigher(offer.perMinute, settings.redPerMinuteBelow, settings.minPerMinute),
            "per_km" to gradeHigher(offer.perKm, settings.redPerKmBelow, settings.minPerKm),
            "rating" to gradeHigher(offer.passengerRating, settings.redRatingBelow, settings.goodRatingFrom),
            "per_hour" to gradeHigher(offer.perHour, settings.redPerHourBelow, settings.minPerHour),
            "profit_hour" to gradeHigher(offer.profitPerHour, settings.redProfitPerHourBelow, settings.minProfitPerHour),
            "profit_percent" to gradeHigher(offer.profitPercent, settings.redProfitPercentBelow, settings.minProfitPercent),
            "profit" to gradeHigher(offer.estimatedProfit, 0.0, settings.minProfit),
        )

        val metrics = metricMap
            .filterKeys(activeKeys::contains)
            .mapNotNull { (key, grade) -> if (grade >= 0) Metric(key, grade) else null }
        if (metrics.isEmpty()) return offer.copy(verdict = "regular")

        var weighted = 0.0
        var weights = 0.0
        metrics.forEach { metric ->
            val index = ordered.indexOf(metric.key)
            val weight = when (index) {
                0 -> 1.70
                1 -> 1.45
                2 -> 1.25
                3 -> 1.10
                else -> 1.00
            }
            weighted += metric.grade * weight
            weights += weight
        }
        val average = if (weights > 0.0) weighted / weights else 1.0
        val verdict = when {
            average >= 1.35 -> "boa"
            average < 0.72 -> "ruim"
            else -> "regular"
        }
        return offer.copy(verdict = verdict)
    }

    private fun gradeHigher(value: Double?, redBelow: Double, goodFrom: Double): Int {
        if (value == null || (redBelow <= 0.0 && goodFrom <= 0.0)) return -1
        if (redBelow > 0.0 && value < redBelow) return 0
        if (goodFrom > 0.0 && value >= goodFrom) return 2
        return 1
    }
}

object StrategyGuard021 {
    fun apply(context: Context, offer: RideOffer): RideOffer {
        val settings = SettingsRepository(context).load()
        val weighted = HudWeightedVerdict.apply(settings, offer)
        val maxMinutes = Strategy021Store.load(context).maxPickupMinutes
        val belowAbsoluteFare = settings.minFare > 0.0 && weighted.fare < settings.minFare
        val exceedsKm = settings.maxPickupKm > 0.0 && weighted.pickupKm != null && weighted.pickupKm > settings.maxPickupKm
        val exceedsMinutes = maxMinutes > 0 && weighted.pickupMinutes != null && weighted.pickupMinutes > maxMinutes
        return if (belowAbsoluteFare || exceedsKm || exceedsMinutes) weighted.copy(verdict = "ruim") else weighted
    }
}

object Appearance021 {
    fun isDark(context: Context): Boolean {
        return when (Strategy021Store.load(context).appTheme) {
            "dark" -> true
            "light" -> false
            else -> (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        }
    }
}
