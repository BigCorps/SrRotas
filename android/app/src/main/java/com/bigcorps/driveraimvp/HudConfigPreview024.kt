package com.srrotas.app

import android.content.Context
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import java.time.Instant

/**
 * RC3.4 — prévia usando o mesmo renderer do HUD ao vivo.
 *
 * A oferta abaixo é apenas um exemplo estático; layout, tipografia, cartões,
 * cores, opacidade, métricas e detalhes são renderizados por Hud023Renderer,
 * eliminando a segunda implementação visual que havia divergido do HUD real.
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
        val settings = model.settings.copy(
            hudCardSize = Hud023Spec.normalizeSize(model.size),
        )
        val weightedVerdict = HudBorderRules025.weightedVerdict(
            settings,
            sample,
            model.maxPickupMinutes,
        )
        val previewOffer = sample.copy(verdict = weightedVerdict)

        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(
                Hud023Renderer.build(
                    context,
                    previewOffer,
                    settings,
                    Hud023LayoutPrefs.load(context),
                ),
            )
            addView(
                UiKit.margin(
                    UiKit.body(
                        context,
                        "Exemplo ilustrativo · mesma renderização visual usada pelo HUD ao vivo.",
                        8.5f,
                    ).apply {
                        gravity = Gravity.CENTER
                        setTextColor(UiKit.palette(context).muted)
                    },
                    top = 7,
                ),
            )
        }
    }
}
