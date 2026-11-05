package com.srrotas.app

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * vc109 — histórico digitalizado aguardando conferência (arquivo privado, um por vez).
 * Guarda somente o que a revisão precisa; o texto bruto dos quadros não é mantido.
 */
object HistoryReviewStore109 {
    private const val FILE = "uber-history-review-109.json"

    /** vc111: totais ORIGINAIS da digitalização (não mudam ao confirmar parte dos registros). */
    data class Totals(val identified: Int, val incomplete: Int, val duplicate: Int, val cancelled: Int)

    data class Pending(val rides: List<UberCompletedRide026>, val framesRead: Int, val framesWithRides: Int, val createdAt: String,
        val totals: Totals? = null, val imported: Int = 0, val duplicateOnSave: Int = 0)

    fun save(context: Context, rides: List<UberCompletedRide026>, framesRead: Int, framesWithRides: Int,
        totals: Totals? = null, imported: Int = 0, duplicateOnSave: Int = 0) {
        val json = JSONObject()
            .put("created_at", Instant.now().toString()).put("frames_read", framesRead).put("frames_with_rides", framesWithRides)
            .put("rides", JSONArray(rides.map(::toJson))).put("imported", imported).put("duplicate_on_save", duplicateOnSave)
        totals?.let { json.put("totals", JSONObject().put("identified", it.identified).put("incomplete", it.incomplete)
            .put("duplicate", it.duplicate).put("cancelled", it.cancelled)) }
        File(context.filesDir, FILE).writeText(json.toString())
    }

    fun load(context: Context): Pending? = runCatching {
        val json = JSONObject(File(context.filesDir, FILE).readText())
        val array = json.getJSONArray("rides")
        val t = json.optJSONObject("totals")
        Pending((0 until array.length()).map { fromJson(array.getJSONObject(it)) }, json.optInt("frames_read"),
            json.optInt("frames_with_rides"), json.optString("created_at"),
            t?.let { Totals(it.optInt("identified"), it.optInt("incomplete"), it.optInt("duplicate"), it.optInt("cancelled")) },
            json.optInt("imported"), json.optInt("duplicate_on_save"))
    }.getOrNull()

    fun clear(context: Context) { File(context.filesDir, FILE).delete() }

    private fun toJson(r: UberCompletedRide026) = JSONObject().put("source_key", r.sourceKey).put("captured_at", r.capturedAt)
        .put("occurred_at", r.occurredAt ?: JSONObject.NULL).put("fare", r.fare).put("service_type", r.serviceType)
        .put("pickup_label", r.pickupLabel ?: JSONObject.NULL).put("destination_label", r.destinationLabel ?: JSONObject.NULL)
        .put("confidence", r.confidence).put("duration_seconds", r.durationSeconds ?: JSONObject.NULL)
        .put("distance_km", r.distanceKm ?: JSONObject.NULL).put("surge_amount", r.surgeAmount ?: JSONObject.NULL)
        .put("extra_amount", r.extraAmount ?: JSONObject.NULL).put("ride_status", r.rideStatus)

    private fun fromJson(j: JSONObject): UberCompletedRide026 {
        fun s(k: String) = if (j.isNull(k)) null else j.optString(k)
        fun d(k: String) = if (j.isNull(k)) null else j.optDouble(k)
        return UberCompletedRide026(j.getString("source_key"), j.getString("captured_at"), s("occurred_at"), j.getDouble("fare"),
            j.getString("service_type"), s("pickup_label"), s("destination_label"), j.getDouble("confidence"),
            if (j.isNull("duration_seconds")) null else j.getInt("duration_seconds"), d("distance_km"), d("surge_amount"), d("extra_amount"),
            j.optString("ride_status", UberCompletedRide026.STATUS_COMPLETED))
    }
}

/** vc109 — estado de cada registro na conferência (puro, testável). */
object HistoryReviewRules109 {
    enum class Confidence(val label: String, val tone: String) { ALTA("Confiança alta", "good"), MEDIA("Confiança média", "warn"), BAIXA("Confiança baixa", "bad") }

    data class Entry(val ride: UberCompletedRide026, val duplicate: Boolean, val missing: List<String>) {
        val cancelled get() = ride.rideStatus == UberCompletedRide026.STATUS_CANCELLED
        val complete get() = missing.isEmpty()
        val confidence get() = when { ride.confidence >= 0.80 -> Confidence.ALTA; ride.confidence >= 0.65 -> Confidence.MEDIA; else -> Confidence.BAIXA }
        /**
         * Seleção inicial conservadora: concluída, não duplicada, COMPLETA e com confiança mínima.
         * vc111: confiança média sozinha pré-selecionava registros sem horário/destino; incompletos
         * só entram por escolha explícita do motorista.
         */
        val selectedByDefault get() = !cancelled && !duplicate && complete && confidence != Confidence.BAIXA
    }

    fun entries(rides: List<UberCompletedRide026>, saved: List<UberCompletedRide026>): List<Entry> = rides.map { ride ->
        val missing = buildList {
            if (ride.occurredAt == null) add("horário")
            if (ride.serviceType == "unknown") add("serviço")
            if (!ride.cancelledOrHasFare()) add("tarifa")
            if (ride.pickupLabel.isNullOrBlank()) add("busca")
            if (ride.destinationLabel.isNullOrBlank()) add("destino")
        }
        Entry(ride, saved.any { it.sourceKey == ride.sourceKey || UberRideIdentity108.sameRide(it, ride) }, missing)
    }

    fun totals(entries: List<Entry>) = HistoryReviewStore109.Totals(entries.size, entries.count { !it.complete },
        entries.count { it.duplicate }, entries.count { it.cancelled })

    /**
     * Resumo "Digitalização concluída". vc111: os totais são os ORIGINAIS da digitalização e cada
     * estado é distinto — capturados ≠ incompletos ≠ selecionados ≠ importados ≠ corridas confirmadas.
     */
    fun summary(frames: Int, totals: HistoryReviewStore109.Totals, pending: List<Entry>, selected: Set<String>,
        imported: Int, duplicateOnSave: Int): String = buildString {
        appendLine("Telas analisadas: $frames")
        appendLine("Registros capturados: ${totals.identified}")
        appendLine("Incompletos: ${totals.incomplete} · Duplicados: ${totals.duplicate + duplicateOnSave} · Canceladas: ${totals.cancelled}")
        appendLine("Aguardando conferência: ${pending.size}" + if (selected.isNotEmpty()) " · ${selected.size} selecionado(s)" else "")
        appendLine("Importados para Histórico › Digitalizados: $imported")
        append("Corridas confirmadas: 0 (importar não confirma corrida)")
    }

    private fun UberCompletedRide026.cancelledOrHasFare() = rideStatus == UberCompletedRide026.STATUS_CANCELLED || fare > 0.0
}

/**
 * vc109 — Configurações › Digitalização › Conferência do Histórico.
 * Substitui a lista textual da revisão do histórico por cards (composição do Histórico de ofertas),
 * com seleção individual, filtros de incompletos/duplicados e confirmação final. Salva somente as
 * importações escolhidas pelo caminho existente; ofertas nunca viram corridas realizadas aqui.
 */
class HistoryReviewActivity109 : Activity() {
    companion object {
        const val EXTRA_FROM_SCAN = "from_scan"
        fun open(context: Context, fromScan: Boolean = false) = context.startActivity(Intent(context, HistoryReviewActivity109::class.java).apply {
            if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(EXTRA_FROM_SCAN, fromScan)
        })
    }

    enum class Filter(val label: String) { ALL("Todos"), INCOMPLETE("Incompletos"), DUPLICATE("Duplicados"), CANCELLED("Canceladas") }

    private lateinit var list: LinearLayout
    private lateinit var summary: TextView
    private lateinit var confirm: TextView
    private lateinit var filters: LinearLayout
    private var entries: List<HistoryReviewRules109.Entry> = emptyList()
    private val selected = LinkedHashSet<String>()
    private var filter = Filter.ALL
    private var pending: HistoryReviewStore109.Pending? = null
    private lateinit var concluded: LinearLayout
    private lateinit var content: LinearLayout
    private var showList = true
    private var savedNow = 0
    private var duplicateOnSave = 0
    private var totals: HistoryReviewStore109.Totals? = null
    private var filterColumns = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        UiKit.applySystemBars(this)
        val p = SrUi023.palette(this)
        val body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(UiKit.dp(this@HistoryReviewActivity109, 14), UiKit.dp(this@HistoryReviewActivity109, 16),
                UiKit.dp(this@HistoryReviewActivity109, 14), UiKit.dp(this@HistoryReviewActivity109, 26))
        }
        val scroll = ScrollView(this).apply { setBackgroundColor(p.background); addView(body); isFillViewport = true }
        setContentView(scroll)
        UiKit.applySafeArea(scroll)
        body.addView(SrUi023.title(this, "Conferência do Histórico", 22f))
        // vc110: resumo "Digitalização concluída" ao terminar a rolagem (e após salvar).
        concluded = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        body.addView(UiKit.margin(concluded, top = 8))
        content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        body.addView(content)
        summary = SrUi023.body(this, "", 11f)
        content.addView(UiKit.margin(summary, top = 4))
        // vc111: pílulas em grade responsiva (2×2 em janelas estreitas, 1×4 quando cabem sem quebrar).
        filters = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        filters.addOnLayoutChangeListener { v, l, _, r, _, ol, _, or, _ -> if (r - l != or - ol && columnsFor(r - l) != filterColumns) v.post { renderFilters() } }
        content.addView(UiKit.margin(filters, top = 10))
        val quick = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        quick.addView(chip("Selecionar completos", false) { selected.clear(); entries.filter { it.complete && it.selectedByDefault }.forEach { selected += it.ride.sourceKey }; render() },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        quick.addView(chip("Limpar seleção", false) { selected.clear(); render() },
            LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = UiKit.dp(this@HistoryReviewActivity109, 6) })
        content.addView(UiKit.margin(quick, top = 8))
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(UiKit.margin(list, top = 10))
        confirm = UiKit.primaryButton(this, "Confirmar") { confirmSelection() }
        content.addView(UiKit.margin(confirm, top = 12))
        content.addView(UiKit.margin(UiKit.secondaryButton(this, "Descartar esta digitalização") { discard() }, top = 7))
        body.addView(UiKit.margin(UiKit.secondaryButton(this, "Voltar") { finish() }, top = 7))
        showList = !intent.getBooleanExtra(EXTRA_FROM_SCAN, false)
        load()
    }

    private fun load() {
        pending = HistoryReviewStore109.load(this)
        val rides = pending?.rides.orEmpty()
        val saved = savedAround(rides)
        entries = HistoryReviewRules109.entries(rides, saved)
        pending?.let { p ->
            savedNow = maxOf(savedNow, p.imported); duplicateOnSave = maxOf(duplicateOnSave, p.duplicateOnSave)
            totals = p.totals ?: HistoryReviewRules109.totals(entries).also { t ->
                HistoryReviewStore109.save(this, p.rides, p.framesRead, p.framesWithRides, t, p.imported, p.duplicateOnSave)
            }
        }
        selected.clear(); entries.filter { it.selectedByDefault }.forEach { selected += it.ride.sourceKey }
        render()
    }

    private fun savedAround(rides: List<UberCompletedRide026>): List<UberCompletedRide026> {
        val times = rides.mapNotNull { r -> r.occurredAt?.let { runCatching { Instant.parse(it) }.getOrNull() } }
        val from = times.minOrNull() ?: return emptyList(); val to = times.maxOrNull()!!
        return runCatching { UberDigitizationStore026.get(this).ridesBetween(from.minusSeconds(180).toString(), to.plusSeconds(180).toString()) }.getOrDefault(emptyList())
    }

    private fun renderConcluded() {
        concluded.removeAllViews()
        val fromScan = intent.getBooleanExtra(EXTRA_FROM_SCAN, false)
        if (!fromScan && savedNow == 0) { concluded.visibility = android.view.View.GONE; return }
        concluded.visibility = android.view.View.VISIBLE
        val c = HistoryReviewRules109.summary(framesRead, totals ?: HistoryReviewRules109.totals(entries), entries, selected, savedNow, duplicateOnSave)
        concluded.addView(SrUi023.card(this, 12, 16).apply {
            addView(SrUi023.title(this@HistoryReviewActivity109, "Digitalização concluída", 16f))
            c.lines().forEach { addView(SrUi023.body(this@HistoryReviewActivity109, it, 11f)) }
            addView(UiKit.margin(UiKit.primaryButton(this@HistoryReviewActivity109, "Ver ofertas capturadas") { viewCaptured() }, top = 8))
            addView(UiKit.margin(UiKit.secondaryButton(this@HistoryReviewActivity109, "Concluir") { finish() }, top = 6))
        })
    }

    private var framesRead = 0

    /** vc111: com pendências mostra a conferência; tudo salvo → Histórico › Digitalizados. */
    private fun viewCaptured() {
        if (entries.isNotEmpty()) { showList = true; render(); return }
        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(MainActivity.EXTRA_BUBBLE_ACTION, MainActivity.BUBBLE_ACTION_HISTORY_DIGITIZED))
        finish()
    }

    private fun columnsFor(widthPx: Int): Int {
        val width = if (widthPx > 0) widthPx else resources.displayMetrics.widthPixels - UiKit.dp(this, 28)
        return if (width >= UiKit.dp(this, 4 * 104)) 4 else 2
    }

    private fun renderFilters() {
        filters.removeAllViews()
        filterColumns = columnsFor(filters.width)
        Filter.values().toList().chunked(filterColumns).forEachIndexed { r, group ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            group.forEachIndexed { i, f ->
                row.addView(chip("${f.label} (${visible(f).size})", f == filter) { filter = f; render() },
                    LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { if (i > 0) marginStart = UiKit.dp(this@HistoryReviewActivity109, 6) })
            }
            filters.addView(row, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
                .apply { if (r > 0) topMargin = UiKit.dp(this@HistoryReviewActivity109, 6) })
        }
    }

    private fun render() {
        framesRead = maxOf(framesRead, pending?.framesRead ?: 0)
        renderConcluded()
        // vc111: depois de salvar tudo, só o resumo (sem lista vazia, pílulas zeradas ou "Selecione registros").
        content.visibility = if (showList && entries.isNotEmpty() || (pending == null && savedNow == 0)) android.view.View.VISIBLE else android.view.View.GONE
        val p = pending
        summary.text = if (p == null || entries.isEmpty()) "Nenhum histórico aguardando conferência. Use Digitalização › Digitalizar histórico e role o histórico da Uber."
        else buildString {
            append("${entries.size} registro(s) · ${entries.count { it.complete }} completo(s) · ${entries.count { !it.complete }} incompleto(s) · ")
            append("${entries.count { it.duplicate }} duplicado(s) · ${entries.count { it.cancelled }} cancelada(s). ")
            append("${p.framesRead} tela(s) lida(s), ${p.framesWithRides} com cartões.")
            append(UberHistoryCoverage108.describe(this@HistoryReviewActivity109, entries.map { it.ride }).replace("\n\n", " "))
        }
        renderFilters()
        list.removeAllViews()
        val shown = visible(filter)
        if (shown.isEmpty() && entries.isNotEmpty()) list.addView(SrUi023.body(this, "Nenhum registro neste filtro.", 11f))
        shown.forEachIndexed { index, e -> list.addView(UiKit.margin(card(e), top = if (index == 0) 0 else 5)) }
        val count = selected.size
        confirm.text = if (count == 0) "Selecione registros para confirmar" else "Confirmar $count registro(s)"
        confirm.isEnabled = count > 0
    }

    private fun visible(f: Filter) = when (f) {
        Filter.ALL -> entries
        Filter.INCOMPLETE -> entries.filter { !it.complete }
        Filter.DUPLICATE -> entries.filter { it.duplicate }
        Filter.CANCELLED -> entries.filter { it.cancelled }
    }

    private fun card(e: HistoryReviewRules109.Entry) = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        val p = SrUi023.palette(context)
        val isSelected = e.ride.sourceKey in selected
        // vc111: card compacto (mesmos dados, menos espaço).
        setPadding(UiKit.dp(context, 10), UiKit.dp(context, 7), UiKit.dp(context, 10), UiKit.dp(context, 7))
        val frame = when { e.duplicate -> p.orange; e.cancelled -> p.red; isSelected -> UiKit.palette(context).good; else -> p.outline }
        background = SrUi023.rounded(p.surface, 13, frame, if (isSelected) 3 else 2, context)
        val top = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        top.addView(SrUi023.title(context, "Uber · ${service(e.ride.serviceType)}", 12.5f), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        when {
            e.duplicate -> top.addView(SrUi023.pill(context, "DUPLICADA", "warn"), pillLp())
            e.cancelled -> top.addView(SrUi023.pill(context, "CANCELADA", "bad"), pillLp())
        }
        top.addView(SrUi023.title(context, if (e.cancelled && e.ride.fare <= 0.0) "—" else money(e.ride.fare), 14f))
        addView(top)
        addView(SrUi023.body(context, (e.ride.occurredAt?.let(::formatTime) ?: "Horário não lido") + " · " + e.confidence.label, 9.5f))
        addView(UiKit.margin(routeLine("Busca", e.ride.pickupLabel ?: "não lida", p.red), top = 3))
        addView(UiKit.margin(routeLine("Destino", e.ride.destinationLabel ?: "não lido", p.blue), top = 3))
        val facts = listOfNotNull(
            e.ride.durationSeconds?.let { "${it / 60} min" }, e.ride.distanceKm?.let { String.format(Locale("pt", "BR"), "%.1f km", it) },
            e.ride.surgeAmount?.let { "dinâmico ${money(it)}" }, e.ride.extraAmount?.let { "extra ${money(it)}" },
        )
        if (facts.isNotEmpty()) addView(UiKit.margin(SrUi023.body(context, facts.joinToString(" · "), 10f), top = 3))
        if (!e.complete) addView(UiKit.margin(SrUi023.body(context, "Incompleto: falta ${e.missing.joinToString(", ")}", 10f).apply { setTextColor(p.orange) }, top = 3))
        if (e.duplicate) addView(UiKit.margin(SrUi023.body(context, "Esta viagem já está salva; selecionar não cria outra cópia.", 10f), top = 3))
        addView(UiKit.margin(toggle(if (isSelected) "✓ Selecionado · tocar para remover" else "Incluir nesta confirmação", isSelected) {
            if (!selected.remove(e.ride.sourceKey)) selected += e.ride.sourceKey; render()
        }, top = 6))
        contentDescription = "${service(e.ride.serviceType)} ${money(e.ride.fare)} ${if (isSelected) "selecionado" else "não selecionado"}"
    }

    private fun confirmSelection() {
        val chosen = entries.filter { it.ride.sourceKey in selected }.map { it.ride }
        if (chosen.isEmpty()) return
        val (saved, duplicate) = UberDigitizationStore026.get(this).saveRides(chosen)
        UberDigitizationClient026.sync(this, UberDigitizationResult026.Rides(chosen)) { }
        // Os não selecionados continuam aguardando conferência (Histórico › Digitalizados).
        val remaining = entries.filter { it.ride.sourceKey !in selected }.map { it.ride }
        val p = pending
        savedNow += saved; duplicateOnSave += duplicate
        if (remaining.isEmpty() || p == null) HistoryReviewStore109.clear(this)
        else HistoryReviewStore109.save(this, remaining, p.framesRead, p.framesWithRides, totals, savedNow, duplicateOnSave)
        Toast.makeText(this, "$saved registro(s) salvo(s) · $duplicate duplicado(s). Corridas sem confirmação: Histórico › Jornadas › Recuperar corridas.", Toast.LENGTH_LONG).show()
        showList = false
        load()
    }

    private fun discard() {
        HistoryReviewStore109.clear(this)
        Toast.makeText(this, "Digitalização descartada. Nada foi salvo.", Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun pillLp() = LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply { marginEnd = UiKit.dp(this@HistoryReviewActivity109, 7) }

    private fun routeLine(label: String, value: String, tone: Int) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        addView(SrUi023.body(context, "$label:", 10.5f).apply { setTextColor(tone) })
        addView(SrUi023.body(context, " $value", 10.5f), LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
    }

    private fun chip(label: String, active: Boolean, action: () -> Unit) = TextView(this).apply {
        val p = SrUi023.palette(context)
        text = label; textSize = 10.5f; gravity = Gravity.CENTER; minHeight = UiKit.dp(context, 36)
        isSingleLine = true; ellipsize = android.text.TextUtils.TruncateAt.END
        setPadding(UiKit.dp(context, 6), 0, UiKit.dp(context, 6), 0)
        setTextColor(if (active) Color.WHITE else p.blue)
        background = SrUi023.rounded(if (active) p.blue else Color.TRANSPARENT, 9, p.blue, if (active) 0 else 1, context)
        setOnClickListener { action() }
    }

    private fun toggle(label: String, on: Boolean, action: () -> Unit) = TextView(this).apply {
        val tone = UiKit.palette(context).good
        text = label; textSize = 10f; gravity = Gravity.CENTER; minHeight = UiKit.dp(context, 32)
        setTextColor(if (on) Color.WHITE else tone)
        background = SrUi023.rounded(if (on) tone else Color.TRANSPARENT, 9, tone, if (on) 0 else 1, context)
        setOnClickListener { action() }
    }

    private fun service(raw: String) = when (raw.lowercase(Locale.ROOT)) {
        "unknown", "" -> "Serviço não lido"
        "uberx" -> "UberX"; "comfort" -> "Comfort"; "black" -> "Black"; "electric" -> "Electric"; "priority" -> "Priority"; "moto" -> "Moto"
        else -> raw.replaceFirstChar { it.uppercase() }
    }
    private fun money(v: Double) = "R$ " + String.format(Locale("pt", "BR"), "%.2f", v)
    private fun formatTime(iso: String) = runCatching {
        DateTimeFormatter.ofPattern("dd/MM HH:mm", Locale("pt", "BR")).format(Instant.parse(iso).atZone(ZoneId.of("America/Sao_Paulo")))
    }.getOrDefault(iso)
}
