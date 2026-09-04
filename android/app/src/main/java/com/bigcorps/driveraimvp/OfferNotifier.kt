package com.srrotas.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.speech.tts.TextToSpeech
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Notificações de ofertas.
 *
 * 0.26.2 mantém um único grupo estável com as três últimas corridas/ofertas
 * reconhecidas. A preferência de notificação textual controla diretamente o
 * grupo: desligar remove resumo e filhos; ligar recompõe a partir do banco local.
 */
object OfferNotifier {
    private const val CHANNEL = "sr_rotas_offers"
    private const val GROUP = "sr_rotas_recent_offers_0262"
    private const val SUMMARY_ID = 3199
    private const val CHILD_ID_BASE = 3190
    private const val MAX_RECENT = 3

    private var tts: TextToSpeech? = null
    private var ttsReady = false

    fun notify(context: Context, offer: RideOffer) {
        val settings = SettingsRepository(context).load()
        if (settings.textNotificationEnabled) {
            postRecent(context, offer)
        } else {
            cancelRecent(context)
        }
        if (settings.voiceNotificationEnabled) speak(context, offer, settings)
    }

    fun syncTextNotificationState(context: Context, enabled: Boolean) {
        if (enabled) postRecent(context, null) else cancelRecent(context)
    }

    private fun postRecent(context: Context, current: RideOffer?) {
        val nm = context.getSystemService(NotificationManager::class.java)
        ensureChannel(nm)

        val stored = LocalStore.get(context)
            .recentOffers(12)
            .filterNot { it.captureMethod.startsWith("historical-import/") }
        val offers = buildList {
            current?.let(::add)
            addAll(stored)
        }.distinctBy { it.localId }
            .take(MAX_RECENT)

        if (offers.isEmpty()) {
            cancelRecent(context)
            return
        }

        offers.forEachIndexed { index, offer ->
            val builder = childBuilder(context, offer, index)
            runCatching { nm.notify(CHILD_ID_BASE + index, builder.build()) }
                .onFailure { LocalLog.append(context, "Notificação de oferta falhou: ${it.message}") }
        }
        for (index in offers.size until MAX_RECENT) nm.cancel(CHILD_ID_BASE + index)

        val openApp = PendingIntent.getActivity(
            context,
            3189,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val inbox = Notification.InboxStyle()
            .setBigContentTitle("Sr. Rotas · ${offers.size} últimas corridas")
        offers.forEachIndexed { index, offer ->
            inbox.addLine("${index + 1}. ${serviceLabel(offer)} · R$ ${money(offer.fare)} · ${mainMetric(offer)}")
        }

        val summary = Notification.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Sr. Rotas · ${offers.size} últimas corridas")
            .setContentText("Buscar, Destino e Combinado disponíveis em cada registro.")
            .setStyle(inbox)
            .setGroup(GROUP)
            .setGroupSummary(true)
            .setOnlyAlertOnce(true)
            .setOngoing(false)
            .setContentIntent(openApp)
            .build()
        runCatching { nm.notify(SUMMARY_ID, summary) }
            .onFailure { LocalLog.append(context, "Resumo de notificações falhou: ${it.message}") }
    }

    private fun childBuilder(context: Context, offer: RideOffer, index: Int): Notification.Builder {
        val ctx = offer.context
        val pickup = safeLabel(ctx, pickup = true)
        val destination = safeLabel(ctx, pickup = false)
        val financial = OfferParser.humanSummary(offer)
        val details = buildString {
            append("Buscar: ${pickup ?: "não identificado"}\n")
            append("Destino: ${destination ?: "não identificado"}\n")
            append(financial)
            ctx?.estimatedArrivalAt?.let { eta -> formatEta(eta)?.let { append("\nChegada est.: $it") } }
        }
        val openApp = PendingIntent.getActivity(
            context,
            3200 + index,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = Notification.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("${index + 1} · ${serviceLabel(offer)} · R$ ${money(offer.fare)}")
            .setContentText("${pickup ?: "Busca não identificada"} → ${destination ?: "Destino não identificado"}")
            .setStyle(Notification.BigTextStyle().bigText(details))
            .setGroup(GROUP)
            .setOnlyAlertOnce(index > 0)
            .setAutoCancel(false)
            .setContentIntent(openApp)

        val requestSeed = 3300 + index * 20
        OfferMaps.pendingIntent(
            context,
            requestSeed + 1,
            pickup,
            ctx?.pickupLat,
            ctx?.pickupLng,
        )?.let { action ->
            builder.addAction(
                Notification.Action.Builder(
                    android.R.drawable.ic_menu_mylocation,
                    "Buscar",
                    action,
                ).build(),
            )
        }
        OfferMaps.pendingIntent(
            context,
            requestSeed + 2,
            destination,
            ctx?.destinationLat,
            ctx?.destinationLng,
        )?.let { action ->
            builder.addAction(
                Notification.Action.Builder(
                    android.R.drawable.ic_menu_mylocation,
                    "Destino",
                    action,
                ).build(),
            )
        }
        combinedPendingIntent(context, requestSeed + 3, sanitizedContext(ctx, pickup, destination))?.let { action ->
            builder.addAction(
                Notification.Action.Builder(
                    android.R.drawable.ic_menu_send,
                    "Combinado",
                    action,
                ).build(),
            )
        }
        return builder
    }

    private fun combinedPendingIntent(context: Context, requestCode: Int, ctx: OfferContext?): PendingIntent? {
        val intent = CombinedRoute0212.intent(ctx) ?: return null
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun sanitizedContext(ctx: OfferContext?, pickup: String?, destination: String?): OfferContext? {
        ctx ?: return null
        return ctx.copy(pickupLabel = pickup, destinationLabel = destination)
    }

    private fun safeLabel(ctx: OfferContext?, pickup: Boolean): String? {
        ctx ?: return null
        OfferContextQuality0242.confirmedDisplayLabel(ctx, pickup)?.let { return it }
        val label = if (pickup) ctx.pickupLabel else ctx.destinationLabel
        return label?.trim()?.takeIf {
            ctx.contextConfidence >= 0.80 && OfferContextQuality0242.canGeocode(it)
        }?.take(140)
    }

    private fun cancelRecent(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.cancel(SUMMARY_ID)
        repeat(MAX_RECENT) { nm.cancel(CHILD_ID_BASE + it) }
    }

    private fun ensureChannel(nm: NotificationManager) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL,
                    "Ofertas Sr. Rotas",
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply {
                    description = "Resumo opcional das três últimas ofertas reconhecidas."
                },
            )
        }
    }

    internal fun speechText(offer: RideOffer, settings: DriverSettings): String = buildString {
        append(
            when (offer.verdict) {
                "boa" -> "Oferta boa. "
                "ruim" -> "Oferta abaixo da meta. "
                else -> "Oferta atenção. "
            },
        )
        HudPresentation.voiceMetricOrder(settings).forEach { key ->
            when (key) {
                "fare" -> appendMetric(offer.fare) { value -> "Valor $value reais. " }
                "per_minute" -> offer.perMinute?.let { appendMetric(it) { value -> "$value reais por minuto. " } }
                "per_km" -> offer.perKm?.let { appendMetric(it) { value -> "$value reais por quilômetro. " } }
                "per_hour" -> offer.perHour?.let { append("${String.format(Locale("pt", "BR"), "%.0f", it)} reais por hora. ") }
                "total_km" -> offer.totalKm?.let { append("Distância ${String.format(Locale("pt", "BR"), "%.1f", it)} quilômetros. ") }
                "total_minutes" -> offer.totalMinutes?.let { append("Duração $it minutos. ") }
                "destination" -> safeLabel(offer.context, pickup = false)?.let { append("Destino $it. ") }
            }
        }
    }.trim()

    private fun mainMetric(offer: RideOffer): String = when {
        offer.totalKm != null && offer.totalMinutes != null ->
            "${String.format(Locale("pt", "BR"), "%.1f", offer.totalKm)} km · ${offer.totalMinutes} min"
        offer.totalKm != null -> "${String.format(Locale("pt", "BR"), "%.1f", offer.totalKm)} km"
        offer.totalMinutes != null -> "${offer.totalMinutes} min"
        else -> offer.verdict.uppercase(Locale.ROOT)
    }

    private fun serviceLabel(offer: RideOffer): String =
        offer.serviceType.takeIf { it.isNotBlank() && it != "unknown" }
            ?.replaceFirstChar { it.uppercase() }
            ?: offer.platform.replaceFirstChar { it.uppercase() }

    private fun money(value: Double): String = String.format(Locale("pt", "BR"), "%.2f", value)
    private fun StringBuilder.appendMetric(value: Double, phrase: (String) -> String) {
        append(phrase(String.format(Locale("pt", "BR"), "%.2f", value)))
    }
    private fun formatEta(value: String): String? = runCatching {
        DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault()).format(Instant.parse(value))
    }.getOrNull()

    private fun speak(context: Context, offer: RideOffer, settings: DriverSettings) {
        val text = speechText(offer, settings)
        if (text.isBlank()) return
        val current = tts
        if (current != null && ttsReady) {
            current.speak(text, TextToSpeech.QUEUE_FLUSH, null, "sr-rotas-offer")
            return
        }
        tts = TextToSpeech(context.applicationContext) { status ->
            ttsReady = status == TextToSpeech.SUCCESS
            if (ttsReady) {
                tts?.language = Locale("pt", "BR")
                tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "sr-rotas-offer")
            }
        }
    }
}
