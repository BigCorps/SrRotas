package com.srrotas.app

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Locale

/**
 * 0.33.4 — restaura as métricas de decisão no primeiro nível da janela.
 *
 * Não recalcula oferta/veredito e não cria thresholds paralelos:
 * toda classificação financeira vem de HudMetricEvaluation0221.
 */
object FloatingOfferMetrics0334 {
    data class MetricSpec(
        val key: String,
        val label: String,
        val value: String,
        val grade: Int?,
    )

    /**
     * Função pura para regressão/QA. Core da janela é fixo quando o dado existe;
     * métricas extras respeitam habilitação/ordem do HUD já configurado.
     */
    fun specs(
        offer: RideOffer,
        settings: DriverSettings,
        maxPickupMinutes: Int,
    ): List<MetricSpec> {
        val result = mutableListOf<MetricSpec>()

        fun canonical(
            key: String,
            label: String,
            value: String?,
        ) {
            if (value == null) return
            result += MetricSpec(
                key = key,
                label = label,
                value = value,
                grade = HudMetricEvaluation0221.grade(
                    key,
                    offer,
                    settings,
                    maxPickupMinutes,
                ),
            )
        }

        // Contrato visual restaurado: decisão financeira + esforço total.
        canonical("per_km", "R$/km", offer.perKm?.let { "R$ ${fmt(it)}" })
        canonical("per_minute", "R$/min", offer.perMinute?.let { "R$ ${fmt(it)}" })
        canonical("per_hour", "R$/h", offer.perHour?.let { "R$ ${fmt(it)}" })
        offer.totalKm?.let {
            result += MetricSpec("total_km", "km", fmt(it), null)
        }
        offer.totalMinutes?.let {
            result += MetricSpec("total_minutes", "min", it.toString(), null)
        }
        canonical("profit", "Lucro est.*", offer.estimatedProfit?.let { "R$ ${fmt(it)}" })

        val extras = linkedMapOf<String, Pair<String, String>?>()
        extras["rating"] = offer.passengerRating?.let { "Nota" to fmt(it) }
        extras["profit_hour"] = offer.profitPerHour?.let { "Lucro/h" to "R$ ${fmt(it)}" }
        extras["profit_percent"] = offer.profitPercent?.let { "Margem" to "${fmt(it)}%" }

        val visibleExtras = Hud023Spec.visibleMetricKeys(
            settings.hudMetricOrder,
            settings.hudEnabledMetrics,
            extras.filterValues { it != null }.keys,
        )

        visibleExtras.forEach { key ->
            if (result.any { it.key == key }) return@forEach
            val pair = extras[key] ?: return@forEach
            result += MetricSpec(
                key = key,
                label = pair.first,
                value = pair.second,
                grade = HudMetricEvaluation0221.grade(
                    key,
                    offer,
                    settings,
                    maxPickupMinutes,
                ),
            )
        }

        return result
    }

    fun build(
        context: Context,
        offer: RideOffer,
        surfaceColor: Int,
        inkColor: Int,
    ): View? {
        val settings = SettingsRepository(context).load()
        val maxPickupMinutes = Strategy021Store.load(context).maxPickupMinutes
        val metrics = specs(offer, settings, maxPickupMinutes)
        if (metrics.isEmpty()) return null

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
        }

        var index = 0
        while (index < metrics.size) {
            val first = metrics[index]
            val second = metrics.getOrNull(index + 1)
            if (second == null) {
                root.addView(
                    pill(context, first, settings, surfaceColor, inkColor),
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                    ).apply {
                        if (index > 0) topMargin = dp(context, normal = 6, compact = 4)
                    },
                )
                index += 1
                continue
            }

            val row = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
            }
            row.addView(
                pill(context, first, settings, surfaceColor, inkColor),
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
            )
            row.addView(
                pill(context, second, settings, surfaceColor, inkColor),
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginStart = dp(context, normal = 6, compact = 4)
                },
            )
            root.addView(
                row,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply {
                    if (index > 0) topMargin = dp(context, normal = 6, compact = 4)
                },
            )
            index += 2
        }
        return root
    }

    private fun pill(
        context: Context,
        spec: MetricSpec,
        settings: DriverSettings,
        surfaceColor: Int,
        inkColor: Int,
    ): View {
        val stroke = gradeColor(context, spec.grade, settings.colorBlindMode)
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = UiKit.rounded(context, surfaceColor, 10, stroke, if (spec.grade == null) 1 else 2)
            setPadding(
                dp(context, normal = 8, compact = 6),
                dp(context, normal = 6, compact = 4),
                dp(context, normal = 8, compact = 6),
                dp(context, normal = 6, compact = 4),
            )

            addView(
                TextView(context).apply {
                    text = spec.label
                    setTextColor(inkColor)
                    textSize = textSp(context, 8.8f)
                    maxLines = 1
                },
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
            )
            addView(
                TextView(context).apply {
                    text = spec.value
                    setTextColor(inkColor)
                    setTypeface(typeface, Typeface.BOLD)
                    textSize = textSp(context, 10.2f)
                    gravity = Gravity.END
                    maxLines = 1
                },
            )
        }
    }

    private fun gradeColor(
        context: Context,
        grade: Int?,
        colorBlind: Boolean,
    ): Int {
        if (grade == null) return UiKit.palette(context).line
        if (colorBlind) {
            return when (grade) {
                2 -> Color.rgb(0, 114, 178)
                0 -> Color.rgb(213, 94, 0)
                else -> Color.rgb(230, 159, 0)
            }
        }
        return when (grade) {
            2 -> UiKit.palette(context).good
            0 -> UiKit.palette(context).bad
            else -> UiKit.palette(context).warn
        }
    }

    private fun textSp(context: Context, base: Float): Float {
        val prefs = JourneyUiPreferences(context)
        val textScale = when (prefs.textSize()) {
            "small" -> 0.88f
            "large" -> 1.16f
            else -> 1f
        }
        val compactScale = if (prefs.compactPanel()) 0.92f else 1f
        return base * textScale * compactScale
    }

    private fun dp(context: Context, normal: Int, compact: Int): Int =
        UiKit.dp(context, if (JourneyUiPreferences(context).compactPanel()) compact else normal)

    private fun fmt(value: Double): String =
        String.format(Locale("pt", "BR"), "%.2f", value)
}
