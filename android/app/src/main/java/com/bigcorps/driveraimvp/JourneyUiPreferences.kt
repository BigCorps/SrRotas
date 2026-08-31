package com.srrotas.app

import android.content.Context

class JourneyUiPreferences(context: Context) {
    companion object {
        const val DEFAULT_X = 18
        const val DEFAULT_Y = 260
    }

    private val prefs =
        context.applicationContext.getSharedPreferences(
            "sr_rotas_journey_ui",
            Context.MODE_PRIVATE,
        )

    fun position(): Pair<Int, Int> =
        prefs.getInt("bubble_x", DEFAULT_X) to
            prefs.getInt("bubble_y", DEFAULT_Y)

    fun savePosition(x: Int, y: Int) {
        prefs.edit()
            .putInt("bubble_x", x.coerceAtLeast(0))
            .putInt("bubble_y", y.coerceAtLeast(0))
            .apply()
    }

    /** Usado pela ação "Restaurar posição" que será exposta na Parte 3. */
    fun resetPosition(): Pair<Int, Int> {
        prefs.edit()
            .putInt("bubble_x", DEFAULT_X)
            .putInt("bubble_y", DEFAULT_Y)
            .apply()
        return DEFAULT_X to DEFAULT_Y
    }

    fun enabled(): Boolean =
        prefs.getBoolean("bubble_enabled_024", true)

    fun setEnabled(value: Boolean) {
        prefs.edit().putBoolean("bubble_enabled_024", value).apply()
    }

    fun offerCount(): Int =
        prefs.getInt("bubble_offer_count_024", 3).coerceIn(1, 5)

    fun setOfferCount(value: Int) {
        prefs.edit().putInt("bubble_offer_count_024", value.coerceIn(1, 5)).apply()
    }

    fun textSize(): String =
        when (prefs.getString("bubble_text_size_024", "standard")) {
            "small" -> "small"
            "large" -> "large"
            else -> "standard"
        }

    fun setTextSize(value: String) {
        val normalized = when (value) {
            "small" -> "small"
            "large" -> "large"
            else -> "standard"
        }
        prefs.edit().putString("bubble_text_size_024", normalized).apply()
    }

    fun sizeDp(): Int =
        prefs.getInt("bubble_size_dp", 58).coerceIn(46, 76)

    fun setSizeDp(value: Int) {
        prefs.edit()
            .putInt("bubble_size_dp", value.coerceIn(46, 76))
            .apply()
    }

    fun cycleSize(): Int {
        val next = when (sizeDp()) {
            in 0..52 -> 58
            in 53..62 -> 70
            else -> 48
        }
        prefs.edit().putInt("bubble_size_dp", next).apply()
        return next
    }

    fun opacityPercent(): Int =
        prefs.getInt("bubble_opacity", 90).coerceIn(60, 100)

    fun setOpacityPercent(value: Int) {
        prefs.edit()
            .putInt("bubble_opacity", value.coerceIn(60, 100))
            .apply()
    }

    fun cycleOpacity(): Int {
        val next = when (opacityPercent()) {
            in 0..74 -> 85
            in 75..92 -> 100
            else -> 70
        }
        prefs.edit().putInt("bubble_opacity", next).apply()
        return next
    }
}
