package com.srrotas.app

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView

/**
 * 0.25.0 compõe o HUD congelado 0.23/0.24 com os sinais de acabamento 0.25.
 *
 * - conteúdo, métricas e veredito textual continuam vindo do HUD validado;
 * - a borda representa explicitamente a média ponderada das métricas ativas;
 * - continuidade no destino continua sendo um sinal assíncrono e não altera
 *   a classificação financeira da oferta.
 */
object Hud025Renderer {
    fun build(
        context: Context,
        offer: RideOffer,
        settings: DriverSettings,
        layout: Hud023LayoutPrefs.State,
    ): View = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL

        val legacyHud = Hud023Renderer.build(context, offer, settings, layout)
        applyWeightedBorder(context, legacyHud, offer, settings)
        addView(legacyHud)

        if (!DestinationContinuityHud025.enabled(context)) return@apply
        val insight = DestinationContinuityClient0211.get(offer.localId) ?: return@apply
        addView(
            continuity(context, insight, settings.colorBlindMode),
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply { topMargin = dp(context, 4) },
        )
    }

    private fun applyWeightedBorder(
        context: Context,
        hud: View,
        offer: RideOffer,
        settings: DriverSettings,
    ) {
        val target = hud as? LinearLayout ?: return
        val size = Hud023Spec.normalizeSize(settings.hudCardSize)
        val maxPickupMinutes = Strategy021Store.load(context).maxPickupMinutes
        val verdict = HudBorderRules025.weightedVerdict(
            settings,
            offer,
            maxPickupMinutes,
        )
        val border = borderColor(
            HudBorderRules025.grade(verdict),
            settings.colorBlindMode,
        )
        val dark = isDark(context, settings.hudTheme)
        val fill = when (size) {
            Hud023Spec.SIZE_COMPACT -> Color.TRANSPARENT
            else -> if (dark) Color.rgb(7, 55, 70) else Color.rgb(255, 253, 246)
        }
        val radius = when (size) {
            Hud023Spec.SIZE_COMPACT -> 14
            Hud023Spec.SIZE_LARGE -> 18
            else -> 16
        }

        target.background = GradientDrawable().apply {
            cornerRadius = dp(context, radius).toFloat()
            setColor(fill)
            setStroke(
                dp(context, HudBorderRules025.strokeDp(size)),
                border,
            )
        }
    }

    private fun borderColor(grade: Int, colorBlind: Boolean): Int =
        if (colorBlind) {
            when (grade) {
                2 -> Color.rgb(0, 125, 204)
                0 -> Color.rgb(230, 86, 0)
                else -> Color.rgb(255, 190, 0)
            }
        } else {
            when (grade) {
                2 -> Color.rgb(0, 198, 145)
                0 -> Color.rgb(238, 66, 78)
                else -> Color.rgb(255, 181, 0)
            }
        }

    private fun continuity(
        context: Context,
        insight: DestinationContinuityInsight0211,
        colorBlind: Boolean,
    ): View {
        val grade = DestinationContinuityHudRules025.grade(insight)
        val tone = when {
            colorBlind && grade == 2 -> Color.rgb(0, 114, 178)
            colorBlind && grade == 0 -> Color.rgb(213, 94, 0)
            colorBlind -> Color.rgb(230, 159, 0)
            grade == 2 -> Color.rgb(16, 168, 134)
            grade == 0 -> Color.rgb(217, 92, 82)
            grade == 1 -> Color.rgb(230, 182, 49)
            else -> Color.rgb(122, 139, 142)
        }
        val textColor =
            if (grade == 1 || (colorBlind && grade == null)) Color.rgb(7, 55, 70)
            else Color.WHITE

        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(context, 9), dp(context, 5), dp(context, 9), dp(context, 5))
            background = rounded(context, tone, 10)

            addView(
                TextView(context).apply {
                    text = "Nova corrida no destino"
                    setTypeface(typeface, Typeface.BOLD)
                    setTextColor(textColor)
                    textSize = 10f
                    maxLines = 1
                },
                LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f,
                ),
            )
            addView(
                TextView(context).apply {
                    text = DestinationContinuityHudRules025.value(insight)
                    setTypeface(typeface, Typeface.BOLD)
                    setTextColor(textColor)
                    textSize = 11f
                    gravity = Gravity.END
                    maxLines = 1
                },
            )
        }
    }

    private fun isDark(context: Context, theme: String): Boolean = when (theme) {
        "dark" -> true
        "light" -> false
        else ->
            (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
    }

    private fun rounded(context: Context, color: Int, radiusDp: Int) =
        GradientDrawable().apply {
            cornerRadius = dp(context, radiusDp).toFloat()
            setColor(color)
        }

    private fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()
}
