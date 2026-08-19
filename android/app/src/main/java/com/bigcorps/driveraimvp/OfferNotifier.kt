package com.srrotas.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.speech.tts.TextToSpeech
import java.util.Locale

object OfferNotifier {
    private const val CHANNEL = "sr_rotas_offers"
    private var tts: TextToSpeech? = null
    private var ttsReady = false

    fun notify(context: Context, offer: RideOffer) {
        val settings = SettingsRepository(context).load()
        if (settings.textNotificationEnabled) post(context, offer)
        if (settings.voiceNotificationEnabled) speak(context, offer, settings)
    }

    private fun post(context: Context, offer: RideOffer) {
        val nm = context.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Ofertas Sr. Rotas", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Resumo opcional das ofertas reconhecidas."
                },
            )
        }
        val pending = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val summary = OfferParser.humanSummary(offer)
        val notification = Notification.Builder(context, CHANNEL)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(
                "Sr. Rotas • ${when (offer.verdict) { "boa" -> "BOA"; "ruim" -> "RUIM"; else -> "ATENÇÃO" }}",
            )
            .setContentText(summary.replace('\n', ' '))
            .setStyle(Notification.BigTextStyle().bigText(summary))
            .setAutoCancel(true)
            .setContentIntent(pending)
            .build()

        runCatching {
            nm.notify((offer.localId.hashCode() and 0x7fffffff) % 100000 + 3000, notification)
        }.onFailure { LocalLog.append(context, "Notificação de oferta falhou: ${it.message}") }
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
                "per_hour" -> offer.perHour?.let {
                    val value = String.format(Locale("pt", "BR"), "%.0f", it)
                    append("$value reais por hora. ")
                }
                "total_km" -> offer.totalKm?.let {
                    val value = String.format(Locale("pt", "BR"), "%.1f", it)
                    append("Distância $value quilômetros. ")
                }
                "total_minutes" -> offer.totalMinutes?.let { append("Duração $it minutos. ") }
            }
        }
    }.trim()

    private fun StringBuilder.appendMetric(value: Double, phrase: (String) -> String) {
        append(phrase(String.format(Locale("pt", "BR"), "%.2f", value)))
    }

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
