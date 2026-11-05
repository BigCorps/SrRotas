package com.srrotas.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * vc111 — evidência por amostra para os descartes do Reader (sem texto do OCR, endereço ou imagem).
 *
 * Diagnóstico 34: 66 quadros `LEGS:legs_legs_disagree_with_total` (inclusive o relatório manual do
 * motorista), 27 `ANCHOR`, 498 `FARE:no_money_line`; Reader2 viu 61 cards completos sem M1 e 4
 * chegaram a consenso — mas só havia contadores, sem como comparar caso a caso. Aqui:
 *  1. LEGS: os NÚMEROS do card (tarifa, total do cabeçalho, trechos) e a diferença total × soma —
 *     mostra se é tolerância/arredondamento sistemático ou leitura errada de um dígito;
 *  2. Reader2 × M1: quando o Reader2 (shadow, inalterado) conta um card completo só dele ou um
 *     consenso, registra o que o M1 fez NO MESMO quadro (oferta, ou etapa/motivo do descarte);
 *  3. quadros × cards: amostras distintas por impressão numérica (≈ ofertas reais), não por quadro.
 * Telemetria apenas: nenhuma regra financeira, gate ou promoção do Reader2 muda.
 */
internal object ReaderEvidence111 {
    data class Legs(val fare: Double?, val totalMin: Int, val totalKm: Double, val pickupMin: Int, val pickupKm: Double, val tripMin: Int, val tripKm: Double) {
        val deltaMin get() = totalMin - (pickupMin + tripMin)
        val deltaKm get() = Math.round((totalKm - (pickupKm + tripKm)) * 100.0) / 100.0
        val fingerprint get() = "${fare ?: 0.0}|$totalMin|$totalKm|$pickupMin|$pickupKm|$tripMin|$tripKm"
        fun fields(): Map<String, Any?> = mapOf("n_fare" to fare, "n_total_min" to totalMin, "n_total_km" to totalKm,
            "n_pickup_min" to pickupMin, "n_pickup_km" to pickupKm, "n_trip_min" to tripMin, "n_trip_km" to tripKm,
            "n_delta_min" to deltaMin, "n_delta_km" to deltaKm)
    }

    private const val PREFS = "sr_reader_evidence_111"
    private const val MAX_SAMPLES = 20
    private const val DISTINCT_WINDOW_MS = 30_000L
    @Volatile private var context: Context? = null
    private var lastR2Only = -1L
    private var lastR2Ready = -1L
    private val recent = LinkedHashMap<String, Long>()

    fun install(app: Context) { context = app.applicationContext }

    /** Números do card cujo total do cabeçalho não fecha com os dois trechos. */
    fun legs(text: String, fare: Double?): Legs? {
        val all = UberLegResolver106.legs(text)
        val header = all.singleOrNull { it.header } ?: return null
        val route = all.filterNot { it.header }.sortedBy { it.line }
        if (route.size != 2) return null
        return Legs(fare, header.minutes, header.km, route[0].minutes, route[0].km, route[1].minutes, route[1].km)
    }

    fun deltaBucket(l: Legs): String {
        val m = kotlin.math.abs(l.deltaMin)
        val min = when { m <= 1 -> "min_ok"; m <= 3 -> "min_2_3"; m <= 10 -> "min_4_10"; else -> "min_gt10" }
        val k = kotlin.math.abs(l.deltaKm)
        val km = when { k <= 0.2 -> "km_ok"; k <= 1.0 -> "km_le1"; k <= 5.0 -> "km_le5"; else -> "km_gt5" }
        return "${if (l.deltaMin >= 0) "pos" else "neg"}:$min:$km"
    }

    /** Uma amostra por card distinto (mesma impressão numérica em até 30 s = mesmo card em vários quadros). */
    @Synchronized fun onLegsDrop(l: Legs, nowMs: Long = System.currentTimeMillis()) {
        val prefs = prefs() ?: return
        recent.entries.removeAll { nowMs - it.value > DISTINCT_WINDOW_MS }
        val distinct = recent.put(l.fingerprint, nowMs) == null
        val edit = prefs.edit().putInt("legs_frames", prefs.getInt("legs_frames", 0) + 1)
        if (distinct) {
            edit.putInt("legs_distinct_cards", prefs.getInt("legs_distinct_cards", 0) + 1)
            val bucket = "legs_bucket:" + deltaBucket(l)
            edit.putInt(bucket, prefs.getInt(bucket, 0) + 1)
            edit.putString("legs_samples", append(prefs.getString("legs_samples", "[]"), JSONObject(l.fields()).put("at", nowMs)))
        }
        edit.apply()
    }

    /**
     * Chamado uma vez por quadro oficial classificado. Se o Reader2 contou neste quadro um card
     * completo só dele (ou consenso), registra o desfecho do M1 no mesmo quadro.
     */
    @Synchronized fun onFrame(offers: Int, m1: () -> String, nowMs: Long = System.currentTimeMillis(),
        reader2: () -> Pair<Long, Long> = { Reader2Consensus0321.diagnosticSnapshot().let { it.reader2OnlyCoreSeen to it.consensusReadyReader2Only } }) {
        val (only, ready) = runCatching(reader2).getOrNull() ?: return
        val onlyInc = if (lastR2Only < 0 || only < lastR2Only) 0 else only - lastR2Only
        val readyInc = if (lastR2Ready < 0 || ready < lastR2Ready) 0 else ready - lastR2Ready
        lastR2Only = only; lastR2Ready = ready
        if (onlyInc <= 0 && readyInc <= 0) return
        val prefs = prefs() ?: return
        val outcome = if (offers > 0) "m1_offer" else runCatching(m1).getOrDefault("m1_unknown")
        val edit = prefs.edit()
        if (onlyInc > 0) edit.putInt("r2only:$outcome", prefs.getInt("r2only:$outcome", 0) + 1)
        if (readyInc > 0) {
            edit.putInt("r2ready:$outcome", prefs.getInt("r2ready:$outcome", 0) + 1)
            edit.putString("r2_samples", append(prefs.getString("r2_samples", "[]"), JSONObject().put("at", nowMs).put("m1", outcome)))
        }
        edit.apply()
    }

    fun toJson(app: Context): JSONObject {
        val prefs = app.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val all = prefs.all
        fun group(prefix: String) = JSONObject().apply { all.forEach { (k, v) -> if (k.startsWith(prefix) && v is Int) put(k.removePrefix(prefix), v) } }
        return JSONObject().put("schema", "sr-reader-evidence-111")
            .put("legs_frames", prefs.getInt("legs_frames", 0)).put("legs_distinct_cards", prefs.getInt("legs_distinct_cards", 0))
            .put("legs_delta_buckets", group("legs_bucket:"))
            .put("legs_samples", JSONArray(prefs.getString("legs_samples", "[]")))
            .put("reader2_only_core_vs_m1", group("r2only:")).put("reader2_consensus_vs_m1", group("r2ready:"))
            .put("reader2_consensus_samples", JSONArray(prefs.getString("r2_samples", "[]")))
            .put("note", "Somente números/códigos. Quadros ≠ cards: legs_distinct_cards agrupa o mesmo card em vários quadros (30 s).")
    }

    private fun prefs() = context?.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun append(raw: String?, item: JSONObject): String {
        val array = runCatching { JSONArray(raw ?: "[]") }.getOrDefault(JSONArray())
        array.put(item)
        while (array.length() > MAX_SAMPLES) array.remove(0)
        return array.toString()
    }

    internal fun resetForTest() { lastR2Only = -1; lastR2Ready = -1; recent.clear(); context?.getSharedPreferences(PREFS, 0)?.edit()?.clear()?.commit() }
}
