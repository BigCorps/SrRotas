package com.srrotas.app

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Histórico de ofertas persistidas, atualizado pelo shell após cada captura oficial. */
class RideHistoryPanel027035(context: Context) : ScrollView(context) {
    private val host = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private val status = SrUi023.body(context, "", 10.5f)
    private var limit = 100

    init {
        isFillViewport = true
        setBackgroundColor(UiKit.palette(context).background)
        val root = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL }
        root.addView(SrAppHeader023(context, "Histórico", "Todas as ofertas persistidas neste aparelho."))
        val body = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(UiKit.dp(context, 14), UiKit.dp(context, 10), UiKit.dp(context, 14), UiKit.dp(context, 26))
        }
        body.addView(status)
        body.addView(UiKit.margin(host, top = 7))
        body.addView(UiKit.margin(UiKit.secondaryButton(context, "Carregar mais ofertas") {
            limit = (limit + 100).coerceAtMost(1000)
            refresh()
        }, top = 10))
        root.addView(body, LinearLayout.LayoutParams(SrUi023.maxContentWidthPx(context), LinearLayout.LayoutParams.WRAP_CONTENT))
        addView(root, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        refresh()
    }

    fun refresh() {
        val offers = LocalStore.get(context).recentOffers(limit)
        status.text = "${offers.size} oferta(s) persistida(s) · mais recentes primeiro"
        host.removeAllViews()
        if (offers.isEmpty()) {
            host.addView(SrUi023.body(context, "Nenhuma oferta capturada ainda.", 11f))
            return
        }
        offers.forEachIndexed { index, offer -> host.addView(UiKit.margin(card(offer), top = if (index == 0) 0 else 7)) }
    }

    private fun card(o: RideOffer) = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(UiKit.dp(context, 11), UiKit.dp(context, 10), UiKit.dp(context, 11), UiKit.dp(context, 10))
        val tone = when (o.verdict.lowercase()) {
            "boa", "good" -> SrUi023.palette(context).teal
            "ruim", "bad" -> SrUi023.palette(context).red
            else -> SrUi023.palette(context).orange
        }
        val completed = LocalStore.get(context).rideOutcomeForOffer(o.localId)?.status == RideOperationalStatus.COMPLETED
        val frameTone = if (completed) UiKit.palette(context).good else tone
        background = SrUi023.rounded(SrUi023.palette(context).surface, 13, frameTone, if (completed) 3 else 2, context)

        val top = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        top.addView(SrUi023.title(context, "${platform(o)} · ${service(o)}", 12.5f), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        if (completed) top.addView(SrUi023.pill(context, "✓ REALIZADA", "good"), LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { marginEnd = UiKit.dp(context, 7) })
        top.addView(SrUi023.title(context, money(o.fare), 14f))
        addView(top)
        addView(SrUi023.body(context, formatTime(o.observedAt), 9.5f))

        val pickup = o.context?.pickupLabel?.takeIf(String::isNotBlank) ?: o.context?.pickupCell?.takeIf(String::isNotBlank) ?: "—"
        val dest = o.context?.destinationLabel?.takeIf(String::isNotBlank) ?: o.context?.destinationCell?.takeIf(String::isNotBlank) ?: "—"
        addView(UiKit.margin(routeLine("Busca", pickup, SrUi023.palette(context).red), top = 6))
        addView(UiKit.margin(routeLine("Destino", dest, SrUi023.palette(context).blue), top = 3))
        addView(UiKit.margin(metricRow(o), top = 7))

        val actionRow = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        actionRow.addView(smallButton(if (completed) "✓ Corrida realizada · desfazer" else "Fiz essa corrida", UiKit.palette(context).good, filled = completed) {
            val next = if (completed) RideOperationalStatus.NOT_COMPLETED else RideOperationalStatus.COMPLETED
            JourneyCoordinator.correctRide(context, o.localId, next)
            JourneyBubbleController.refresh(context)
            Toast.makeText(context, if (completed) "Marcação removida." else "Corrida marcada como realizada.", Toast.LENGTH_SHORT).show()
            post { refresh() }
        }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        addView(UiKit.margin(actionRow, top = 8))

        val maps = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
        maps.addView(smallButton("Busca", SrUi023.palette(context).teal) { openPoint(o, true) }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        maps.addView(smallButton("Destino", SrUi023.palette(context).blue) { openPoint(o, false) }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = UiKit.dp(context, 5) })
        maps.addView(smallButton("Rota", SrUi023.palette(context).purple) { openRoute(o) }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = UiKit.dp(context, 5) })
        addView(UiKit.margin(maps, top = 6))
    }

    private fun routeLine(label: String, value: String, tone: Int) = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        addView(SrUi023.body(context, "$label:", 10.5f).apply { setTextColor(tone) })
        addView(SrUi023.body(context, " $value", 10.5f), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
    }

    private fun metricRow(o: RideOffer) = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        listOf(
            "R$/km" to value(o.perKm), "R$/min" to value(o.perMinute), "R$/h" to value(o.perHour),
            "km" to (o.totalKm?.let { String.format(Locale("pt", "BR"), "%.1f", it) } ?: "—"),
            "min" to (o.totalMinutes?.toString() ?: "—"),
        ).forEachIndexed { i, (label, v) ->
            addView(LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
                background = SrUi023.rounded(SrUi023.palette(context).surfaceMuted, 9, SrUi023.palette(context).outline, 1, context)
                setPadding(3, 4, 3, 4)
                addView(SrUi023.body(context, label, 8f).apply { gravity = Gravity.CENTER })
                addView(SrUi023.body(context, v, 9.5f).apply { gravity = Gravity.CENTER })
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { if (i > 0) marginStart = UiKit.dp(context, 3) })
        }
    }

    private fun smallButton(label: String, tone: Int, filled: Boolean = false, action: () -> Unit) = TextView(context).apply {
        text = label; textSize = 9.5f; gravity = Gravity.CENTER; minHeight = UiKit.dp(context, 36)
        setTextColor(if (filled) Color.WHITE else tone)
        background = SrUi023.rounded(if (filled) tone else Color.TRANSPARENT, 9, tone, if (filled) 0 else 1, context)
        setOnClickListener { action() }
    }

    private fun openPoint(o: RideOffer, pickup: Boolean) {
        val c = o.context
        val lat = if (pickup) c?.pickupLat else c?.destinationLat
        val lng = if (pickup) c?.pickupLng else c?.destinationLng
        val label = if (pickup) c?.pickupLabel ?: c?.pickupCell else c?.destinationLabel ?: c?.destinationCell
        val uri = if (lat != null && lng != null) Uri.parse("geo:$lat,$lng?q=$lat,$lng(${Uri.encode(label ?: "Sr. Rotas")})") else label?.let { Uri.parse("geo:0,0?q=${Uri.encode(it)}") }
        if (uri != null) runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    private fun openRoute(o: RideOffer) {
        val c = o.context ?: return
        val origin = c.pickupLabel ?: c.pickupCell
        val dest = c.destinationLabel ?: c.destinationCell
        if (origin.isNullOrBlank() || dest.isNullOrBlank()) return
        val uri = Uri.parse("https://www.google.com/maps/dir/?api=1&origin=${Uri.encode(origin)}&destination=${Uri.encode(dest)}")
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    private fun platform(o: RideOffer) = when (o.platform.lowercase()) { "uber" -> "Uber"; "99" -> "99"; else -> o.platform.ifBlank { "Oferta" } }
    private fun service(o: RideOffer) = o.serviceType.takeIf { it.isNotBlank() && it != "unknown" } ?: o.offerType
    private fun money(v: Double) = String.format(Locale("pt", "BR"), "R$ %.2f", v)
    private fun value(v: Double?) = v?.let { String.format(Locale("pt", "BR"), "%.2f", it) } ?: "—"
    private fun formatTime(value: String) = runCatching {
        DateTimeFormatter.ofPattern("dd/MM HH:mm", Locale("pt", "BR")).withZone(ZoneId.of("America/Sao_Paulo")).format(Instant.parse(value))
    }.getOrDefault(value.take(16))
}
