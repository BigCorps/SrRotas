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

    /** 0.27: modo dedicado à compactação do painel expandido. */
    fun compactPanel(): Boolean =
        prefs.getBoolean("bubble_compact_panel_0270", true)

    fun setCompactPanel(value: Boolean) {
        prefs.edit().putBoolean("bubble_compact_panel_0270", value).apply()
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

    /**
     * RC3.4: este valor histórico passa a ser explicitamente a opacidade do
     * botão flutuante. A chave antiga é preservada para compatibilidade.
     */
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

    /** Opacidade independente do painel/janela expandida. */
    fun windowOpacityPercent(): Int =
        prefs.getInt("bubble_window_opacity_027034", 100).coerceIn(60, 100)

    fun setWindowOpacityPercent(value: Int) {
        prefs.edit()
            .putInt("bubble_window_opacity_027034", value.coerceIn(60, 100))
            .apply()
    }

    /** RC3.5: tema visual independente da janela flutuante. */
    fun windowThemeMode(): String = when (prefs.getString("bubble_window_theme_027035", "follow_app")) {
        "light" -> "light"
        "dark" -> "dark"
        else -> "follow_app"
    }

    fun setWindowThemeMode(value: String) {
        val normalized = when (value) { "light", "dark" -> value; else -> "follow_app" }
        prefs.edit().putString("bubble_window_theme_027035", normalized).apply()
    }

    /** Tempo que o balão do Assistente Ativo permanece visível. */
    fun assistantDisplaySeconds(): Int =
        prefs.getInt("active_assistant_display_seconds_027035", 12).coerceIn(5, 30)

    fun setAssistantDisplaySeconds(value: Int) {
        prefs.edit().putInt("active_assistant_display_seconds_027035", value.coerceIn(5, 30)).apply()
    }
}
