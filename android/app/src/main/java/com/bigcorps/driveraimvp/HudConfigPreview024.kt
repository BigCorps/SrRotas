package com.srrotas.app

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import java.time.Instant
import java.util.Locale

/**
 * Prévia ilustrativa e reativa do HUD.
 *
 * Os valores da oferta são fixos apenas para permitir comparação durante a
 * edição; cores e veredito usam HudMetricEvaluation0221, o mesmo avaliador do
 * HUD real.
 */
object HudConfigPreview024 {
    data class Model(
        val settings: DriverSettings,
        val maxPickupMinutes: Int,
        val size: String,
    )

    private val sample = RideOffer(
        observedAt = Instant.EPOCH.toString(),
        sourcePackage = "preview",
        captureMethod = "preview",
        rawText = "",
        fare = 24.90,
        pickupKm = 3.2,
        tripKm = 8.1,
        totalKm = 11.3,
        pickupMinutes = 7,
        tripMinutes = 19,
        totalMinutes = 26,
        perKm = 2.20,
        perHour = 57.46,
        perMinute = 0.96,
        estimatedCost = 9.61,
        estimatedProfit = 15.29,
        profitPerHour = 35.28,
        profitPercent = 61.4,
        passengerRating = 4.82,
        advertisedPerKm = null,
        serviceType = "UberX",
        verdict = "regular",
        confidence = 1.0,
        offerType = "exclusive",
        dedupeKey = "preview-024",
    )

    fun build(context: Context, model: Model): View {
        val p = UiKit.palette(context)
        val size = Hud023Spec.normalizeSize(model.size)
        val verdict = HudMetricEvaluation0221.weightedVerdict(
            model.settings,
            sample,
            model.maxPickupMinutes,
        )

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = UiKit.rounded(
                context,
                p.surface,
                18,
                verdictColor(context, verdict),
                2,
            )
            setPadding(
                UiKit.dp(context, 11),
                UiKit.dp(context, 10),
                UiKit.dp(context, 11),
                UiKit.dp(context, 10),
            )
        }

        root.addView(
            LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(
                    UiKit.title(context, "UberX", if (size == "large") 16f else 14f),
                    LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
                )
                addView(
                    UiKit.pill(
                        context,
                        when (verdict) {
                            "boa" -> "BOA"
                            "ruim" -> "RUIM"
                            else -> "REGULAR"
                        },
                        when (verdict) {
                            "boa" -> "good"
                            "ruim" -> "bad"
                            else -> "warn"
                        },
                    ),
                )
            },
        )

        val available = setOf(
            "per_minute",
            "per_km",
            "rating",
            "per_hour",
            "profit_hour",
            "profit_percent",
            "pickup",
        )
        val keys = Hud023Spec.visibleMetricKeys(
            model.settings.hudMetricOrder,
            model.settings.hudEnabledMetrics,
            available,
        )

        if (keys.isEmpty()) {
            root.addView(
                UiKit.margin(
                    UiKit.body(
                        context,
                        "Ative ao menos uma métrica para visualizar a avaliação.",
                        10.5f,
                    ),
                    top = 8,
                ),
            )
            return root
        }

        val columns = Hud023Spec.columns(size)
        if (columns == 1) {
            keys.forEach { key ->
                root.addView(
                    metricCard(context, model, key, size),
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                    ).apply {
                        topMargin = UiKit.dp(context, 6)
                    },
                )
            }
        } else {
            keys.chunked(2).forEach { pair ->
                val row = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                }
                pair.forEachIndexed { index, key ->
                    row.addView(
                        metricCard(context, model, key, size),
                        LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                            if (index > 0) marginStart = UiKit.dp(context, 6)
                        },
                    )
                }
                if (pair.size == 1) {
                    row.addView(
                        View(context),
                        LinearLayout.LayoutParams(0, 1, 1f).apply {
                            marginStart = UiKit.dp(context, 6)
                        },
                    )
                }
                root.addView(
                    row,
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                    ).apply {
                        topMargin = UiKit.dp(context, 6)
                    },
                )
            }
        }

        root.addView(
            UiKit.margin(
                UiKit.body(
                    context,
                    "Exemplo ilustrativo · a oferta real continua vindo do OCR.",
                    8.5f,
                ).apply {
                    gravity = Gravity.CENTER
                    setTextColor(p.muted)
                },
                top = 7,
            ),
        )

        return root
    }

    private fun metricCard(
        context: Context,
        model: Model,
        key: String,
        size: String,
    ): View {
        val grade = HudMetricEvaluation0221.grade(
            key,
            sample,
            model.settings,
            model.maxPickupMinutes,
        ) ?: 1
        val color = when (grade) {
            2 -> UiKit.palette(context).good
            0 -> UiKit.palette(context).bad
            else -> UiKit.palette(context).warn
        }

        val card = LinearLayout(context).apply {
            orientation =
                if (size == "compact") {
                    LinearLayout.HORIZONTAL
                } else {
                    LinearLayout.VERTICAL
                }
            gravity = Gravity.CENTER_VERTICAL
            background = UiKit.rounded(
                context,
                if (size == "compact") Color.TRANSPARENT
                else UiKit.palette(context).surfaceAlt,
                12,
                color,
                if (size == "large") 2 else 1,
            )
            setPadding(
                UiKit.dp(context, 8),
                UiKit.dp(context, if (size == "large") 9 else 7),
                UiKit.dp(context, 8),
                UiKit.dp(context, if (size == "large") 9 else 7),
            )
        }

        val label = UiKit.body(
            context,
            labelFor(key),
            if (size == "large") 10f else 9f,
        ).apply {
            setTextColor(UiKit.palette(context).muted)
        }
        val value = UiKit.title(
            context,
            valueFor(key),
            when (size) {
                "large" -> 16.5f
                "compact" -> 12f
                else -> 13.5f
            },
        ).apply {
            setTextColor(color)
            if (size == "compact") gravity = Gravity.END
        }

        if (size == "compact") {
            card.addView(label, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            card.addView(value)
        } else {
            card.addView(label)
            card.addView(
                value,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply {
                    topMargin = UiKit.dp(context, 2)
                },
            )
        }
        return card
    }

    private fun labelFor(key: String): String = when (key) {
        "per_minute" -> "R$/min"
        "per_km" -> "R$/km"
        "rating" -> "Avaliação"
        "per_hour" -> "R$/h"
        "profit_hour" -> "Lucro/h"
        "profit_percent" -> "Margem"
        "pickup" -> "Busca"
        else -> key
    }

    private fun valueFor(key: String): String = when (key) {
        "per_minute" -> "R$ ${money(sample.perMinute)}/min"
        "per_km" -> "R$ ${money(sample.perKm)}/km"
        "rating" -> "${money(sample.passengerRating)} ★"
        "per_hour" -> "R$ ${money(sample.perHour)}/h"
        "profit_hour" -> "R$ ${money(sample.profitPerHour)}/h"
        "profit_percent" -> "${money(sample.profitPercent)}%"
        "pickup" -> "${money(sample.pickupKm)} km · ${sample.pickupMinutes} min"
        else -> "—"
    }

    private fun money(value: Double?): String =
        value?.let {
            String.format(Locale("pt", "BR"), "%.2f", it)
        } ?: "—"

    private fun verdictColor(context: Context, verdict: String): Int =
        when (verdict) {
            "boa" -> UiKit.palette(context).good
            "ruim" -> UiKit.palette(context).bad
            else -> UiKit.palette(context).warn
        }
}
