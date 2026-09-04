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
 * HUD 0.26.3 sobre a base financeira validada.
 *
 * A continuidade no destino permanece informativa e fora do veredito
 * financeiro. O rótulo foi compactado para liberar área útil do HUD.
 */
object Hud025Renderer {
    fun build(
        context: Context,
        offer: RideOffer,
        settings: DriverSettings,
        layout: Hud023LayoutPrefs.State,
    ): View = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL

        LocalLog.append(context.applicationContext, "HUD_RENDER 0.26.3 · ${offer.platform}/${offer.offerType} · R$ ${offer.fare} · ${offer.totalKm ?: "?"} km · ${offer.totalMinutes ?: "?"} min")

        val legacyHud = Hud023Renderer.build(context, offer, settings, layout)
        applyWeightedBorder(context, legacyHud, offer, settings)

        val streetView = StreetView026.slot(context, offer, settings)

        if (!DestinationContinuityHud025.enabled(context)) {
            addView(legacyHud)
            addView(
                streetView,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                ).apply { topMargin = dp(context, 4) },
            )
            return@apply
        }

        val insight = DestinationContinuityClient0211.get(offer.localId)
        val slot = continuity(context, insight, settings.colorBlindMode)
        val slotLp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        )
        val hudLp = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        )

        when (DestinationContinuityHud025.position(context)) {
            DestinationContinuityHud025.POSITION_BOTTOM -> {
                addView(legacyHud, hudLp)
                slotLp.topMargin = dp(context, 4)
                addView(slot, slotLp)
            }
            else -> {
                addView(slot, slotLp)
                hudLp.topMargin = dp(context, 4)
                addView(legacyHud, hudLp)
            }
        }

        addView(
            streetView,
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
        val verdict = HudBorderRules025.weightedVerdict(settings, offer, maxPickupMinutes)
        val border = borderColor(HudBorderRules025.grade(verdict), settings.colorBlindMode)
        val dark = isDark(context, settings.hudTheme)
        val fill = when (size) {
            Hud023Spec.SIZE_COMPACT -> Color.TRANSPARENT
            else -> SrTheme024.palette(dark).surface
        }
        val radius = when (size) {
            Hud023Spec.SIZE_COMPACT -> 14
            Hud023Spec.SIZE_LARGE -> 18
            else -> 16
        }

        target.background = GradientDrawable().apply {
            cornerRadius = dp(context, radius).toFloat()
            setColor(fill)
            setStroke(dp(context, HudBorderRules025.strokeDp(size)), border)
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
                2 -> SrTheme024.LIGHT.good
                0 -> SrTheme024.LIGHT.bad
                else -> SrTheme024.LIGHT.warn
            }
        }

    private fun continuity(
        context: Context,
        insight: DestinationContinuityInsight0211?,
        colorBlind: Boolean,
    ): View {
        val grade = insight?.let(DestinationContinuityHudRules025::grade)
        val tone = when {
            insight == null -> Color.rgb(124, 135, 142)
            colorBlind && grade == 2 -> Color.rgb(0, 114, 178)
            colorBlind && grade == 0 -> Color.rgb(213, 94, 0)
            colorBlind -> Color.rgb(230, 159, 0)
            grade == 2 -> SrTheme024.LIGHT.good
            grade == 0 -> SrTheme024.LIGHT.bad
            grade == 1 -> SrTheme024.LIGHT.warn
            else -> Color.rgb(124, 135, 142)
        }
        val textColor = when {
            insight == null -> Color.WHITE
            grade == 1 && !colorBlind -> Color.rgb(24, 35, 39)
            else -> Color.WHITE
        }
        val value = insight?.let(DestinationContinuityHudRules025::value) ?: "Sem dados"

        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(context, 40)
            setPadding(dp(context, 11), dp(context, 7), dp(context, 11), dp(context, 7))
            background = rounded(context, tone, 11)

            addView(
                TextView(context).apply {
                    text = "Destino · Probab."
                    setTypeface(typeface, Typeface.BOLD)
                    setTextColor(textColor)
                    textSize = 11.5f
                    maxLines = 1
                },
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f),
            )
            addView(
                TextView(context).apply {
                    text = value
                    setTypeface(typeface, Typeface.BOLD)
                    setTextColor(textColor)
                    textSize = 13f
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
