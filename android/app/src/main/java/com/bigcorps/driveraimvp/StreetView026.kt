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
import java.util.concurrent.ConcurrentHashMap

/**
 * Destino no mapa — 0.27.
 *
 * A disponibilidade passa a usar primeiro o texto original do destino, que é
 * estável para a oferta. As coordenadas enriquecidas continuam úteis como
 * referência, mas não fazem o botão alternar habilitado/desabilitado.
 *
 * Ao abrir o mapa, o texto original é enviado junto com a coordenada quando
 * houver. Isso permite que o app de mapas resolva o endereço real em vez de
 * aceitar cegamente o primeiro resultado do Android Geocoder.
 */
object StreetView026 {
    private const val REFRESH_INTERVAL_MS = 700L
    private const val REFRESH_ATTEMPTS = 7

    private data class Target(
        val label: String?,
        val lat: Double?,
        val lng: Double?,
    ) {
        fun usable(): Boolean =
            !label.isNullOrBlank() ||
                (
                    lat != null &&
                        lng != null &&
                        lat in -90.0..90.0 &&
                        lng in -180.0..180.0 &&
                        !(lat == 0.0 && lng == 0.0)
                )
    }

    private val stableTargets = ConcurrentHashMap<String, Target>()

    fun eligible(offer: RideOffer): Boolean =
        targetFor(offer).usable()

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
        text = "Destino no mapa"
        textSize = 10.5f
        setTypeface(typeface, Typeface.BOLD)
        gravity = Gravity.CENTER
        minHeight = dp(context, 34)
        setPadding(
            dp(context, 10),
            dp(context, 6),
            dp(context, 10),
            dp(context, 6),
        )
        contentDescription = "Abrir destino no mapa"
        isFocusable = true

        fun currentOffer(): RideOffer = resolveLatest(context, offer)

        fun refreshAppearance() {
            val available = targetFor(currentOffer()).usable()
            val dark =
                when (settings.hudTheme.lowercase()) {
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
            compoundDrawableTintList =
                ColorStateList.valueOf(foreground)
            background =
                SrUi023.rounded(
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

        // Só precisamos aguardar quando ainda não existe texto nem coordenada.
        fun schedule(attempt: Int) {
            if (attempt >= REFRESH_ATTEMPTS || isEnabled) return
            postDelayed(
                {
                    if (isAttachedToWindow) {
                        refreshAppearance()
                        if (!isEnabled) schedule(attempt + 1)
                    }
                },
                REFRESH_INTERVAL_MS,
            )
        }
        if (!isEnabled) schedule(0)
    }

    fun open(context: Context, offer: RideOffer): Boolean {
        val resolved = resolveLatest(context, offer)
        val target = targetFor(resolved)
        if (!target.usable()) return false

        val uri =
            when {
                !target.label.isNullOrBlank() -> {
                    val anchorLat = target.lat ?: 0.0
                    val anchorLng = target.lng ?: 0.0
                    Uri.parse(
                        "geo:$anchorLat,$anchorLng?q=${Uri.encode(target.label)}",
                    )
                }
                target.lat != null && target.lng != null ->
                    Uri.parse(
                        "geo:${target.lat},${target.lng}?q=${target.lat},${target.lng}",
                    )
                else -> return false
            }

        val intent =
            Intent(Intent.ACTION_VIEW, uri).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }

        return runCatching {
            context.startActivity(intent)
            LocalLog.append(
                context.applicationContext,
                "DESTINO MAPA 0.27 aberto · texto=${!target.label.isNullOrBlank()} · coord=${target.lat != null && target.lng != null}",
            )
            true
        }.onFailure {
            LocalLog.append(
                context.applicationContext,
                "DESTINO MAPA 0.27 falhou: ${it.message}",
            )
        }.getOrDefault(false)
    }

    private fun targetFor(offer: RideOffer): Target {
        val ctx = offer.context
        val candidate =
            Target(
                label =
                    ctx?.destinationLabel
                        ?.trim()
                        ?.takeIf {
                            it.length >= 4 &&
                                !it.equals(
                                    "Destino não identificado",
                                    ignoreCase = true,
                                )
                        },
                lat = ctx?.destinationLat,
                lng = ctx?.destinationLng,
            )

        val old = stableTargets[offer.localId]
        val merged =
            if (old == null) {
                candidate
            } else {
                Target(
                    label = candidate.label ?: old.label,
                    lat = candidate.lat ?: old.lat,
                    lng = candidate.lng ?: old.lng,
                )
            }

        if (merged.usable()) {
            stableTargets[offer.localId] = merged
        }
        return stableTargets[offer.localId] ?: candidate
    }

    private fun resolveLatest(
        context: Context,
        original: RideOffer,
    ): RideOffer =
        runCatching {
            LocalStore.get(context.applicationContext)
                .recentOffers(24)
                .firstOrNull { it.localId == original.localId }
        }.getOrNull() ?: original

    private fun dp(context: Context, value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()
}
