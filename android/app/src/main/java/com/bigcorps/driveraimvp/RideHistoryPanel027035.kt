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
    // vc110: "Ofertas do Reader" (padrão, como antes) × "Digitalizados" (histórico Uber importado).
    private var digitized = false
    private val modes = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
    private lateinit var more: android.view.View

    init {
        isFillViewport = true
        setBackgroundColor(UiKit.palette(context).background)
        val root = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL }
        root.addView(SrAppHeader023(context, "Histórico", "Todas as ofertas persistidas neste aparelho."))
        val body = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(UiKit.dp(context, 14), UiKit.dp(context, 10), UiKit.dp(context, 14), UiKit.dp(context, 26))
        }
        body.addView(modes)
        body.addView(UiKit.margin(status, top = 8))
        body.addView(UiKit.margin(host, top = 7))
        more = UiKit.margin(UiKit.secondaryButton(context, "Carregar mais ofertas") {
            limit = (limit + 100).coerceAtMost(1000)
            refresh()
        }, top = 10)
        body.addView(more)
        root.addView(body, LinearLayout.LayoutParams(SrUi023.maxContentWidthPx(context), LinearLayout.LayoutParams.WRAP_CONTENT))
        addView(root, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        refresh()
    }

    private fun renderModes() {
        modes.removeAllViews()
        listOf("Ofertas do Reader" to false, "Digitalizados" to true).forEachIndexed { i, (label, value) ->
            val active = digitized == value
            val p = SrUi023.palette(context)
            modes.addView(TextView(context).apply {
                text = label; textSize = 11f; gravity = Gravity.CENTER; minHeight = UiKit.dp(context, 38)
                setTextColor(if (active) Color.WHITE else p.blue)
                background = SrUi023.rounded(if (active) p.blue else Color.TRANSPARENT, 10, p.blue, if (active) 0 else 1, context)
                setOnClickListener { digitized = value; refresh() }
            }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { if (i > 0) marginStart = UiKit.dp(context, 6) })
        }
    }

    /** vc111: aberto pelo resumo da digitalização depois de salvar todos os registros. */
    fun showDigitized() { digitized = true; refresh() }

    fun refresh() {
        renderModes()
        more.visibility = if (digitized) GONE else VISIBLE
        if (digitized) { refreshDigitized(); return }
        val offers = LocalStore.get(context).recentOffers(limit)
        status.text = "${offers.size} oferta(s) persistida(s) · mais recentes primeiro"
        host.removeAllViews()
        if (offers.isEmpty()) {
            host.addView(SrUi023.body(context, "Nenhuma oferta capturada ainda.", 11f))
            return
        }
        offers.forEachIndexed { index, offer -> host.addView(UiKit.margin(card(offer), top = if (index == 0) 0 else 7)) }
    }

    /** Importações do histórico Uber: origem e situação explícitas; não são corridas confirmadas. */
    private fun refreshDigitized() {
        host.removeAllViews()
        val pending = HistoryReviewStore109.load(context)
        val rows = UberDigitizationStore026.get(context).recentRides(200)
        status.text = "${rows.size} registro(s) importado(s) do histórico Uber · não contam como corridas confirmadas sem decisão em Recuperar corridas"
        if (pending != null && pending.rides.isNotEmpty()) {
            host.addView(UiKit.margin(SrUi023.softCard(context, "info", 10).apply {
                addView(SrUi023.title(context, "${pending.rides.size} registro(s) aguardando conferência", 12.5f))
                addView(SrUi023.body(context, "Da última digitalização: confira e confirme o que deve ser salvo.", 10f))
                addView(UiKit.margin(UiKit.primaryButton(context, "Ver ofertas capturadas") { HistoryReviewActivity109.open(context) }, top = 6))
            }, bottom = 7))
        }
        if (rows.isEmpty()) {
            host.addView(SrUi023.body(context, "Nenhum registro digitalizado salvo. Use Configurações › Digitalização › Digitalizar histórico.", 11f))
            return
        }
        rows.forEachIndexed { index, row -> host.addView(UiKit.margin(importCard(row), top = if (index == 0) 0 else 7)) }
    }

    private fun importCard(row: UberDigitizationStore026.StoredRide) = LinearLayout(context).apply {
        val r = row.ride
        val p = SrUi023.palette(context)
        orientation = LinearLayout.VERTICAL
        setPadding(UiKit.dp(context, 11), UiKit.dp(context, 10), UiKit.dp(context, 11), UiKit.dp(context, 10))
        val cancelled = r.rideStatus == UberCompletedRide026.STATUS_CANCELLED
        background = SrUi023.rounded(p.surface, 13, if (cancelled) p.red else p.purple, 2, context)
        val top = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        top.addView(SrUi023.title(context, "Uber · ${importService(r.serviceType)}", 12.5f), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        top.addView(SrUi023.pill(context, if (cancelled) "CANCELADA" else "IMPORTADA", if (cancelled) "bad" else "purple"),
            LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { marginEnd = UiKit.dp(context, 7) })
        top.addView(SrUi023.title(context, if (cancelled && r.fare <= 0.0) "—" else money(r.fare), 14f))
        addView(top)
        addView(SrUi023.body(context, (r.occurredAt?.let(::formatTime) ?: "Horário não lido") + " · Origem: histórico Uber digitalizado · " +
            if (row.synced) "sincronizado" else "envio pendente", 9.5f))
        addView(UiKit.margin(routeLine("Busca", r.pickupLabel ?: "não lida", p.red), top = 6))
        addView(UiKit.margin(routeLine("Destino", r.destinationLabel ?: "não lido", p.blue), top = 3))
    }

    private fun importService(raw: String) = when (raw.lowercase(Locale.ROOT)) {
        "unknown", "" -> "serviço não lido"; "uberx" -> "UberX"; "comfort" -> "Comfort"; "black" -> "Black"
        "electric" -> "Electric"; "priority" -> "Priority"; "moto" -> "Moto"; else -> raw.replaceFirstChar { it.uppercase() }
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

        // vc110: célula/coordenada aproximada não é endereço; sem texto lido mostra "não lido".
        val pickup = o.context?.pickupLabel?.takeIf(String::isNotBlank) ?: "não lida"
        val dest = o.context?.destinationLabel?.takeIf(String::isNotBlank) ?: "não lido"
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
        val label = (if (pickup) c?.pickupLabel else c?.destinationLabel)?.takeIf(String::isNotBlank)
        if (label == null) {
            Toast.makeText(context, if (pickup) "Endereço de busca não lido nesta oferta." else "Endereço de destino não lido nesta oferta.", Toast.LENGTH_SHORT).show()
            return
        }
        // Coordenadas só acompanham um endereço lido (geocodificado a partir dele).
        val intent = OfferMaps.searchIntent(label, if (pickup) c?.pickupLat else c?.destinationLat, if (pickup) c?.pickupLng else c?.destinationLng) ?: return
        runCatching { context.startActivity(intent) }
    }

    private fun openRoute(o: RideOffer) {
        val c = o.context
        if (c?.pickupLabel.isNullOrBlank() || c?.destinationLabel.isNullOrBlank()) {
            Toast.makeText(context, "Rota combinada precisa de busca e destino lidos.", Toast.LENGTH_SHORT).show()
            return
        }
        val intent = CombinedRoute0212.intent(c) ?: return
        runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    private fun platform(o: RideOffer) = when (o.platform.lowercase()) { "uber" -> "Uber"; "99" -> "99"; else -> o.platform.ifBlank { "Oferta" } }
    private fun service(o: RideOffer) = o.serviceType.takeIf { it.isNotBlank() && it != "unknown" } ?: o.offerType
    private fun money(v: Double) = String.format(Locale("pt", "BR"), "R$ %.2f", v)
    private fun value(v: Double?) = v?.let { String.format(Locale("pt", "BR"), "%.2f", it) } ?: "—"
    private fun formatTime(value: String) = runCatching {
        DateTimeFormatter.ofPattern("dd/MM HH:mm", Locale("pt", "BR")).withZone(ZoneId.of("America/Sao_Paulo")).format(Instant.parse(value))
    }.getOrDefault(value.take(16))
}
