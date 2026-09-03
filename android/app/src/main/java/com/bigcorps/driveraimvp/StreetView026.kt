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
 * Street View 0.26.
 *
 * Usa apenas Google Maps URLs oficiais. Não incorpora SDK/Static Street View e,
 * portanto, não exige chave Google nem cria custo por visualização no Sr. Rotas.
 */
object StreetView026 {
    fun eligible(offer: RideOffer): Boolean {
        val ctx = offer.context ?: return false
        return StreetViewRules026.eligible(
            ctx.destinationLat,
            ctx.destinationLng,
            ctx.contextConfidence,
        )
    }

    fun buttonOrNull(
        context: Context,
        offer: RideOffer,
        settings: DriverSettings,
    ): View? {
        if (!eligible(offer)) return null
        val dark = when (settings.hudTheme.lowercase()) {
            "dark" -> true
            "light" -> false
            else -> Appearance021.isDark(context)
        }
        val p = SrTheme024.palette(dark)
        return TextView(context).apply {
            text = "360°  Ver destino"
            textSize = 10.5f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            minHeight = dp(context, 34)
            setPadding(dp(context, 10), dp(context, 6), dp(context, 10), dp(context, 6))
            background = SrUi023.rounded(p.now, 10, p.now, 1, context)
            compoundDrawableTintList = ColorStateList.valueOf(Color.WHITE)
            isClickable = true
            isFocusable = true
            contentDescription = "Abrir Street View do destino"
            setOnClickListener {
                open(context, offer)
            }
        }
    }

    fun open(context: Context, offer: RideOffer): Boolean {
        val ctx = offer.context ?: return false
        val lat = ctx.destinationLat ?: return false
        val lng = ctx.destinationLng ?: return false
        if (!eligible(offer)) return false

        val uri = Uri.parse(StreetViewRules026.mapsUrl(lat, lng))
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching {
            context.startActivity(intent)
            LocalLog.append(
                context.applicationContext,
                "STREETVIEW 0.26 aberto destino=${"%.5f".format(lat)},${"%.5f".format(lng)}",
            )
            true
        }.onFailure {
            LocalLog.append(
                context.applicationContext,
                "STREETVIEW 0.26 falhou: ${it.message}",
            )
        }.getOrDefault(false)
    }

    private fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()
}
