package com.srrotas.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.view.Gravity
import android.widget.LinearLayout
import org.json.JSONObject
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

/**
 * vc110 — confirmação de corrida a partir de uma releitura, SOMENTE com vínculo confiável:
 * oferta original registrada, jornada existente e mesma tarifa. A decisão é aplicada no processo
 * principal pelo mecanismo canônico (`JourneyCoordinator.correctRide` / `markDoingRide`), é
 * idempotente (mesmo estado = nada muda) e pode ser desfeita (estado anterior guardado).
 * Imagem sem vínculo nunca gera corrida.
 */
object RescanRideDecision110 {
    const val ACTION = "com.srrotas.app.RESCAN_RIDE_DECISION_110"
    const val SOURCE = "rescan_110"
    const val SOURCE_UNDO = "rescan_110_undo"
    private const val LEDGER = "rescan_ride_decisions_110"

    enum class Kind { CONFIRM_COMPLETED, DOING_RIDE, UNDO }

    /** Vínculo confiável entre a releitura e uma oferta + jornada existentes. */
    fun trustworthy(original: RideOffer?, fresh: RideOffer?, journeyExists: Boolean): Boolean =
        original != null && fresh != null && !original.journeyId.isNullOrBlank() && journeyExists &&
            abs(original.fare - fresh.fare) <= 0.011

    /** `:rescan`: pede ao processo principal; nada é gravado neste processo. */
    fun request(context: Context, offerId: String, expectedFare: Double, kind: Kind) {
        context.sendBroadcast(Intent(context, RescanRideDecisionReceiver110::class.java).setAction(ACTION)
            .putExtra("offer_id", offerId).putExtra("fare", expectedFare).putExtra("kind", kind.name))
    }

    data class Outcome(val applied: Boolean, val code: String)

    /** Processo principal: revalida e aplica. */
    fun apply(context: Context, offerId: String, expectedFare: Double, kind: Kind): Outcome {
        val store = LocalStore.get(context)
        val offer = store.offerByLocalId(offerId) ?: return Outcome(false, "offer_missing")
        val journeyId = offer.journeyId?.takeIf { it.isNotBlank() } ?: return Outcome(false, "journey_missing")
        if (store.journey(journeyId) == null) return Outcome(false, "journey_missing")
        if (abs(offer.fare - expectedFare) > 0.011) return Outcome(false, "fare_mismatch")
        val current = store.rideOutcomeForOffer(offerId)
        val prefs = context.getSharedPreferences(LEDGER, Context.MODE_PRIVATE)
        return when (kind) {
            Kind.UNDO -> {
                val previous = prefs.getString(offerId, null) ?: return Outcome(false, "nothing_to_undo")
                if (current?.source != SOURCE) return Outcome(false, "changed_elsewhere")
                val status = runCatching { RideOperationalStatus.valueOf(previous) }.getOrDefault(RideOperationalStatus.OFFERED)
                JourneyCoordinator.correctRide(context, offerId, status, SOURCE_UNDO)
                prefs.edit().remove(offerId).apply()
                Outcome(true, "undone")
            }
            Kind.CONFIRM_COMPLETED, Kind.DOING_RIDE -> {
                val target = if (kind == Kind.DOING_RIDE) RideOperationalStatus.DOING_RIDE else RideOperationalStatus.COMPLETED
                if (current?.status == target) return Outcome(false, "already_${target.name.lowercase()}")
                prefs.edit().putString(offerId, (current?.status ?: RideOperationalStatus.OFFERED).name).apply()
                val active = SettingsRepository(context).currentJourneyId() == journeyId
                if (target == RideOperationalStatus.DOING_RIDE && active) {
                    JourneyCoordinator.markDoingRide(context, offerId, SOURCE)
                } else {
                    JourneyCoordinator.correctRide(context, offerId, RideOperationalStatus.COMPLETED, SOURCE)
                }
                Outcome(true, "applied")
            }
        }
    }

    fun previous(context: Context, offerId: String): String? =
        context.getSharedPreferences(LEDGER, Context.MODE_PRIVATE).getString(offerId, null)

    fun toJson(context: Context): JSONObject = JSONObject(context.getSharedPreferences(LEDGER, Context.MODE_PRIVATE).all as Map<*, *>)
}

class RescanRideDecisionReceiver110 : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != RescanRideDecision110.ACTION) return
        val id = intent.getStringExtra("offer_id") ?: return
        val kind = runCatching { RescanRideDecision110.Kind.valueOf(intent.getStringExtra("kind").orEmpty()) }.getOrNull() ?: return
        val fare = intent.getDoubleExtra("fare", Double.NaN)
        val pending = goAsync()
        android.os.Handler(android.os.Looper.getMainLooper()).post {
            try {
                val outcome = runCatching { RescanRideDecision110.apply(context.applicationContext, id, fare, kind) }.getOrNull()
                RescanTrace109.record(context, "RIDE_DECISION", "${kind.name}:${outcome?.code ?: "error"}")
            } finally { pending.finish() }
        }
    }
}

/**
 * vc110 — card de oferta na composição aprovada do Histórico (sem acionar o HUD operacional).
 */
object OfferCardView110 {
    fun build(context: Context, o: RideOffer, title: String = "Oferta relida"): LinearLayout = LinearLayout(context).apply {
        val p = SrUi023.palette(context)
        orientation = LinearLayout.VERTICAL
        setPadding(UiKit.dp(context, 11), UiKit.dp(context, 10), UiKit.dp(context, 11), UiKit.dp(context, 10))
        val tone = when (o.verdict.lowercase()) { "boa", "good" -> p.teal; "ruim", "bad" -> p.red; else -> p.orange }
        background = SrUi023.rounded(p.surface, 13, tone, 2, context)
        val top = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val service = o.serviceType.takeIf { it.isNotBlank() && it != "unknown" } ?: o.offerType
        top.addView(SrUi023.title(context, "$title · Uber · $service", 12.5f), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        top.addView(SrUi023.title(context, String.format(Locale("pt", "BR"), "R$ %.2f", o.fare), 14f))
        addView(top)
        addView(SrUi023.body(context, runCatching {
            DateTimeFormatter.ofPattern("dd/MM HH:mm", Locale("pt", "BR")).withZone(ZoneId.of("America/Sao_Paulo")).format(Instant.parse(o.observedAt))
        }.getOrDefault(""), 9.5f))
        fun route(label: String, value: String?, color: Int) = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(SrUi023.body(context, "$label:", 10.5f).apply { setTextColor(color) })
            addView(SrUi023.body(context, " ${value ?: "não lido"}", 10.5f), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }
        addView(UiKit.margin(route("Busca", o.context?.pickupLabel, p.red), top = 6))
        addView(UiKit.margin(route("Destino", o.context?.destinationLabel, p.blue), top = 3))
        val metrics = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        listOf(
            "R$/km" to o.perKm, "R$/min" to o.perMinute, "R$/h" to o.perHour,
        ).map { (k, v) -> k to (v?.let { String.format(Locale("pt", "BR"), "%.2f", it) } ?: "—") }
            .plus("km" to (o.totalKm?.let { String.format(Locale("pt", "BR"), "%.1f", it) } ?: "—"))
            .plus("min" to (o.totalMinutes?.toString() ?: "—"))
            .forEachIndexed { i, (label, v) ->
                metrics.addView(LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
                    background = SrUi023.rounded(p.surfaceMuted, 9, p.outline, 1, context)
                    setPadding(3, 4, 3, 4)
                    addView(SrUi023.body(context, label, 8f).apply { gravity = Gravity.CENTER })
                    addView(SrUi023.body(context, v, 9.5f).apply { gravity = Gravity.CENTER })
                }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { if (i > 0) marginStart = UiKit.dp(context, 3) })
            }
        addView(UiKit.margin(metrics, top = 7))
    }
}
