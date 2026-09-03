package com.srrotas.app

import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.view.Gravity
import android.view.View
import android.widget.TextView

/**
 * Street View 0.26.1.
 *
 * O slot agora é fixo no HUD: nunca entra/sai durante o geocoding. Enquanto o
 * destino ainda não possui coordenadas confiáveis ele fica cinza; assim que o
 * contexto persistido fica pronto, o próprio botão muda para o estado ativo.
 */
object StreetView026 {
    private const val REFRESH_INTERVAL_MS = 650L
    private const val REFRESH_ATTEMPTS = 7

    fun eligible(offer: RideOffer): Boolean {
        val ctx = offer.context ?: return false
        return StreetViewRules026.eligible(
            ctx.destinationLat,
            ctx.destinationLng,
            ctx.contextConfidence,
        )
    }

    /** Mantido para callers antigos. Na 0.26.1 o renderer usa slot(). */
    fun buttonOrNull(
        context: Context,
        offer: RideOffer,
        settings: DriverSettings,
    ): View? = slot(context, offer, settings)

    fun slot(
        context: Context,
        offer: RideOffer,
        settings: DriverSettings,
    ): View = TextView(context).apply {
        text = "Street View — Destino"
        textSize = 10.5f
        setTypeface(typeface, Typeface.BOLD)
        gravity = Gravity.CENTER
        minHeight = dp(context, 34)
        setPadding(dp(context, 10), dp(context, 6), dp(context, 10), dp(context, 6))
        contentDescription = "Abrir Street View do destino"
        isFocusable = true

        fun currentOffer(): RideOffer = resolveLatest(context, offer)

        fun refreshAppearance() {
            val available = eligible(currentOffer())
            val dark = when (settings.hudTheme.lowercase()) {
                "dark" -> true
                "light" -> false
                else -> Appearance021.isDark(context)
            }
            val p = SrTheme024.palette(dark)
            isEnabled = available
            isClickable = available
            alpha = if (available) 1f else 0.72f
            val backgroundColor = if (available) p.now else p.surfaceAlt
            val borderColor = if (available) p.now else p.line
            val foreground = if (available) Color.WHITE else p.muted
            setTextColor(foreground)
            compoundDrawableTintList = ColorStateList.valueOf(foreground)
            background = SrUi023.rounded(
                backgroundColor,
                10,
                borderColor,
                1,
                context,
            )
        }

        setOnClickListener {
            open(context, currentOffer())
        }
        refreshAppearance()

        // O geocoder roda fora do caminho quente do OCR. Em vez de recriar o
        // HUD (causando a antiga piscada), atualizamos somente este slot.
        fun schedule(attempt: Int) {
            if (attempt >= REFRESH_ATTEMPTS) return
            postDelayed({
                if (isAttachedToWindow) {
                    refreshAppearance()
                    if (!isEnabled) schedule(attempt + 1)
                }
            }, REFRESH_INTERVAL_MS)
        }
        if (!isEnabled) schedule(0)
    }

    fun open(context: Context, offer: RideOffer): Boolean {
        val resolved = resolveLatest(context, offer)
        val ctx = resolved.context ?: return false
        val lat = ctx.destinationLat ?: return false
        val lng = ctx.destinationLng ?: return false
        if (!eligible(resolved)) return false

        val uri = Uri.parse(StreetViewRules026.mapsUrl(lat, lng))
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching {
            context.startActivity(intent)
            LocalLog.append(
                context.applicationContext,
                "STREETVIEW 0.26.1 aberto destino=${"%.5f".format(lat)},${"%.5f".format(lng)}",
            )
            true
        }.onFailure {
            LocalLog.append(
                context.applicationContext,
                "STREETVIEW 0.26.1 falhou: ${it.message}",
            )
        }.getOrDefault(false)
    }

    private fun resolveLatest(context: Context, original: RideOffer): RideOffer {
        if (eligible(original)) return original
        return runCatching {
            LocalStore.get(context.applicationContext)
                .recentOffers(16)
                .firstOrNull { it.localId == original.localId }
                ?.takeIf(::eligible)
        }.getOrNull() ?: original
    }

    private fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()
}
