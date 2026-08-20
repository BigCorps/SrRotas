package com.srrotas.app

import android.content.Context
import android.content.SharedPreferences
import kotlin.math.max
import kotlin.math.min

/**
 * 0.20.1 hotfix
 *
 * Objetivo:
 * - fazer o novo menu flutuante respeitar o Painel de Rota sem depender de
 *   callbacks adicionais da tela de configuração;
 * - aceitar aliases de chaves para sobreviver à evolução do painel durante o beta;
 * - manter retrocompatibilidade com JourneyUiPreferences e SettingsRepository.
 */
object JourneyBubbleVisualPrefs {
    data class Snapshot(
        val iconSizeDp: Int,
        val panelWidthDp: Int,
        val opacityPercent: Int,
        val fontSp: Float,
        val theme: String,
        val colorBlind: Boolean,
        val positionLabel: String,
        val posX: Int,
        val posY: Int,
    ) {
        fun signature(): String = listOf(
            iconSizeDp,
            panelWidthDp,
            opacityPercent,
            fontSp,
            theme,
            colorBlind,
            positionLabel,
            posX,
            posY,
        ).joinToString("|")
    }

    fun snapshot(context: Context): Snapshot {
        val journey = JourneyUiPreferences(context)
        val settings = SettingsRepository(context).load()
        val density = context.resources.displayMetrics.density
        val screenWidthDp = (context.resources.displayMetrics.widthPixels / density).toInt()
        val screenHeightDp = (context.resources.displayMetrics.heightPixels / density).toInt()

        val opacity = clampInt(
            resolveInt(
                context,
                listOf(
                    "journey_bubble_opacity_percent",
                    "journey_panel_opacity_percent",
                    "route_panel_opacity_percent",
                    "journey_opacity_percent",
                    "route_panel_opacity",
                    "journey_bubble_opacity",
                ),
                fallback = journey.opacityPercent(),
            ),
            35,
            100,
        )

        val sizeToken = resolveString(
            context,
            listOf(
                "journey_bubble_size",
                "journey_panel_size",
                "route_panel_card_size",
                "route_panel_size",
                "journey_card_size",
            ),
            fallback = settings.hudCardSize,
        )

        val iconSizeDp = when (sizeToken.lowercase()) {
            "compact", "small", "pequeno" -> 54
            "large", "grande" -> 76
            else -> max(58, journey.sizeDp())
        }

        val panelWidthDp = when (sizeToken.lowercase()) {
            "compact", "small", "pequeno" -> 270
            "large", "grande" -> min(360, screenWidthDp - 20)
            else -> min(328, screenWidthDp - 20)
        }.coerceAtLeast(250)

        val theme = resolveString(
            context,
            listOf(
                "journey_bubble_theme",
                "journey_panel_theme",
                "route_panel_theme",
                "journey_theme",
            ),
            fallback = settings.hudTheme,
        ).ifBlank { "light" }

        val fontSp = clampFloat(
            resolveFloat(
                context,
                listOf(
                    "journey_bubble_font_size",
                    "journey_panel_font_size",
                    "route_panel_font_size",
                    "journey_font_size",
                ),
                fallback = settings.hudFontSize.toFloat(),
            ),
            11f,
            24f,
        )

        val colorBlind = resolveBoolean(
            context,
            listOf(
                "journey_bubble_color_blind",
                "journey_panel_color_blind",
                "route_panel_color_blind",
                "journey_color_blind",
                "daltonism_mode",
                "color_blind_mode",
            ),
            fallback = false,
        )

        val positionLabel = resolveString(
            context,
            listOf(
                "journey_bubble_position",
                "journey_panel_position",
                "route_panel_position",
                "journey_position",
            ),
            fallback = "manual",
        ).ifBlank { "manual" }

        val savedPosition = journey.position()
        val resolved = if (positionLabel.equals("manual", ignoreCase = true)) {
            savedPosition
        } else {
            presetPosition(positionLabel, screenWidthDp, screenHeightDp)
        }

        return Snapshot(
            iconSizeDp = iconSizeDp,
            panelWidthDp = panelWidthDp,
            opacityPercent = opacity,
            fontSp = fontSp,
            theme = theme,
            colorBlind = colorBlind,
            positionLabel = positionLabel,
            posX = resolved.first,
            posY = resolved.second,
        )
    }

    private fun presetPosition(label: String, widthDp: Int, heightDp: Int): Pair<Int, Int> {
        val marginX = 12
        val top = 120
        val centerY = max(90, (heightDp * 0.32).toInt())
        val bottomY = max(120, (heightDp * 0.58).toInt())
        val rightX = max(marginX, widthDp - 92)
        val centerX = max(marginX, (widthDp / 2) - 36)
        return when (label.lowercase()) {
            "centro", "center" -> centerX to centerY
            "esquerda", "left", "left_top", "top_left" -> marginX to top
            "left_center", "esquerda_centro" -> marginX to centerY
            "right_top", "top_right", "direita" -> rightX to top
            "right_center", "direita_centro" -> rightX to centerY
            "bottom_left", "esquerda_baixo" -> marginX to bottomY
            "bottom_right", "direita_baixo" -> rightX to bottomY
            else -> marginX to top
        }
    }

    private fun resolveString(context: Context, keys: List<String>, fallback: String): String {
        val direct = findValue(context, keys) { prefs, key ->
            if (!prefs.contains(key)) null else prefs.all[key]?.toString()
        }
        return direct ?: fallback
    }

    private fun resolveInt(context: Context, keys: List<String>, fallback: Int): Int {
        val value = findValue(context, keys) { prefs, key ->
            if (!prefs.contains(key)) return@findValue null
            when (val raw = prefs.all[key]) {
                is Int -> raw
                is Long -> raw.toInt()
                is Float -> raw.toInt()
                is String -> raw.toIntOrNull()
                else -> null
            }
        }
        return value ?: fallback
    }

    private fun resolveFloat(context: Context, keys: List<String>, fallback: Float): Float {
        val value = findValue(context, keys) { prefs, key ->
            if (!prefs.contains(key)) return@findValue null
            when (val raw = prefs.all[key]) {
                is Float -> raw
                is Int -> raw.toFloat()
                is Long -> raw.toFloat()
                is String -> raw.replace(',', '.').toFloatOrNull()
                else -> null
            }
        }
        return value ?: fallback
    }

    private fun resolveBoolean(context: Context, keys: List<String>, fallback: Boolean): Boolean {
        val value = findValue(context, keys) { prefs, key ->
            if (!prefs.contains(key)) return@findValue null
            when (val raw = prefs.all[key]) {
                is Boolean -> raw
                is String -> raw.equals("true", true) || raw == "1"
                is Int -> raw != 0
                else -> null
            }
        }
        return value ?: fallback
    }

    private fun <T> findValue(
        context: Context,
        keys: List<String>,
        reader: (SharedPreferences, String) -> T?,
    ): T? {
        val prefNames = listOf(
            "${context.packageName}_preferences",
            "sr_rotas_settings",
            "srrotas_settings",
            "settings",
            "journey_ui",
            "strategy",
            "strategy_settings",
            "route_panel",
            "route_panel_settings",
        )

        prefNames.forEach { name ->
            val prefs = context.getSharedPreferences(name, Context.MODE_PRIVATE)
            keys.forEach { key ->
                reader(prefs, key)?.let { return it }
            }
        }
        return null
    }

    private fun clampInt(value: Int, minValue: Int, maxValue: Int): Int =
        max(minValue, min(maxValue, value))

    private fun clampFloat(value: Float, minValue: Float, maxValue: Float): Float =
        max(minValue, min(maxValue, value))
}
