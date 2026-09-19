package com.srrotas.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Rect
import android.provider.Settings
import org.json.JSONObject
import java.time.Instant
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Laboratório M1/M2 com modos efetivos.
 *
 * M1: MediaProjection oficial.
 * Compare: M1 oficial + M2 somente árvore de Acessibilidade em shadow.
 * M2: Acessibilidade isolada; MediaProjection não é iniciado pelo shell.
 */
object ReaderLab027036 {
    const val MODE_M1 = "m1"
    const val MODE_COMPARE = "compare"
    const val MODE_M2 = "m2"

    private const val PREFS = "sr_reader_lab_027036"
    private const val RECENT_WINDOW_MS = 22_000L
    private const val SAME_METHOD_DEDUPE_MS = 8_000L
    private const val MAX_RECENT = 40
    private const val KEY_RC37_MIGRATED = "rc37_mode_migrated"

    private data class Seen(
        val method: String,
        val at: Long,
        val candidate: ReaderLabRules027036.Candidate,
        var matched: Boolean = false,
    )

    data class Snapshot(
        val mode: String,
        val m1Seen: Int,
        val m2Seen: Int,
        val matched: Int,
        val m1Complete: Int,
        val m2Complete: Int,
        val m1CorePoints: Int,
        val m2CorePoints: Int,
        val latest: String,
        val accessibilityEnabled: Boolean,
    ) {
        val m1Only: Int get() = (m1Seen - matched).coerceAtLeast(0)
        val m2Only: Int get() = (m2Seen - matched).coerceAtLeast(0)
    }

    private val recent = CopyOnWriteArrayList<Seen>()
    private val processedM1 = LinkedHashSet<String>()

    fun migrateForConsolidation(context: Context) {
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (p.getBoolean(KEY_RC37_MIGRATED, false)) return
        val disclosure = p.getBoolean("accessibility_disclosure_accepted", false)
        val bounds = intArrayOf(p.getInt("uber_l", -1), p.getInt("uber_t", -1), p.getInt("uber_r", -1), p.getInt("uber_b", -1))
        p.edit().clear()
            .putString("mode", MODE_M1)
            .putBoolean(KEY_RC37_MIGRATED, true)
            .putBoolean("accessibility_disclosure_accepted", disclosure)
            .apply()
        if (bounds[0] >= 0) p.edit().putInt("uber_l", bounds[0]).putInt("uber_t", bounds[1]).putInt("uber_r", bounds[2]).putInt("uber_b", bounds[3]).apply()
        ReaderLabTelemetry0270361.reset(context)
        recent.clear()
        processedM1.clear()
        LocalLog.append(context, "RC3.7 Reader Lab migrado para M1 seguro por padrão")
    }

    fun mode(context: Context): String = when (
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("mode", MODE_M1)
    ) {
        MODE_COMPARE -> MODE_COMPARE
        MODE_M2 -> MODE_M2
        else -> MODE_M1
    }

    fun setMode(context: Context, value: String) {
        val normalized = when (value) { MODE_COMPARE -> MODE_COMPARE; MODE_M2 -> MODE_M2; else -> MODE_M1 }
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        p.edit().putString("mode", normalized).putBoolean(KEY_RC37_MIGRATED, true).apply()
        reset(context)
        ReaderLabTelemetry0270361.reset(context)
        LocalLog.append(context, "RC3.7 Reader Lab mudou para ${modeLabel(normalized)}")
    }

    fun m2Enabled(context: Context): Boolean = mode(context) != MODE_M1
    fun m1Enabled(context: Context): Boolean = mode(context) != MODE_M2
    fun comparisonMode(context: Context): Boolean = mode(context) == MODE_COMPARE
    fun m2Isolated(context: Context): Boolean = mode(context) == MODE_M2

    fun disclosureAccepted(context: Context): Boolean =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("accessibility_disclosure_accepted", false)

    fun showDisclosureAndOpenSettings(activity: Activity) {
        AlertDialog.Builder(activity)
            .setTitle("Leitor experimental M2")
            .setMessage(
                "Durante uma jornada de teste, o Sr. Rotas pode usar a Acessibilidade para observar somente a janela do Uber Driver. " +
                    "No modo Comparativo o M2 usa apenas a árvore de Acessibilidade e não executa um segundo OCR concorrente. " +
                    "No modo M2 isolado o MediaProjection não inicia e os resultados ficam fora da Base Pessoal/Coletiva até validação."
            )
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Continuar") { _, _ ->
                activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                    .putBoolean("accessibility_disclosure_accepted", true).apply()
                activity.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
            .show()
    }

    fun isAccessibilityEnabled(context: Context): Boolean {
        val enabled = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
        val pkg = context.packageName
        return enabled.split(':').any { entry -> entry.contains(pkg, true) && entry.contains("DriverAccessibilityService", true) }
    }

    fun updateUberBounds(context: Context, bounds: Rect) {
        if (bounds.width() <= 0 || bounds.height() <= 0) return
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putInt("uber_l", bounds.left).putInt("uber_t", bounds.top).putInt("uber_r", bounds.right).putInt("uber_b", bounds.bottom).apply()
    }

    fun cropDiagnosticBitmap(context: Context, source: Bitmap): Bitmap {
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val l = p.getInt("uber_l", -1); val t = p.getInt("uber_t", -1); val r = p.getInt("uber_r", -1); val b = p.getInt("uber_b", -1)
        if (l < 0 || t < 0 || r <= l || b <= t || r > source.width || b > source.height) return source
        val w = r - l; val h = b - t
        if (w < source.width * .30 || h < source.height * .30) return source
        if (l == 0 && t == 0 && r == source.width && b == source.height) return source
        return runCatching { Bitmap.createBitmap(source, l, t, w, h) }.getOrDefault(source)
    }

    fun recordM2(context: Context, offer: RideOffer) = record(context, "M2", offer)

    fun pollOfficialM1(context: Context) {
        if (!m1Enabled(context)) return
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (processedM1.isEmpty()) prefs.getStringSet("processed_m1", emptySet())?.takeLast(80)?.forEach(processedM1::add)
        val now = System.currentTimeMillis()
        LocalStore.get(context).recentOffers(30)
            .filter { it.captureMethod.startsWith("media-projection") || it.captureMethod.contains("shadow_recovered") }
            .sortedBy { it.observedAt }
            .forEach { offer ->
                val observed = runCatching { Instant.parse(offer.observedAt).toEpochMilli() }.getOrDefault(now)
                val contextReady = offer.context?.hasTextContext() == true
                if (!contextReady && now - observed < 6_000L) return@forEach
                if (processedM1.add(offer.localId)) record(context, "M1", offer)
            }
        while (processedM1.size > 100) processedM1.remove(processedM1.first())
        prefs.edit().putStringSet("processed_m1", processedM1.toSet()).apply()
    }

    @Synchronized
    private fun record(context: Context, method: String, offer: RideOffer) {
        val now = System.currentTimeMillis()
        cleanup(now)
        val candidate = offer.toLabCandidate(now)
        if (recent.any { it.method == method && now - it.at <= SAME_METHOD_DEDUPE_MS && ReaderLabRules027036.sameOffer(it.candidate, candidate, SAME_METHOD_DEDUPE_MS) }) return
        val seen = Seen(method, now, candidate)
        val counterpart = recent.asReversed().firstOrNull {
            it.method != method && !it.matched && now - it.at <= RECENT_WINDOW_MS && ReaderLabRules027036.sameOffer(it.candidate, candidate, RECENT_WINDOW_MS)
        }
        if (counterpart != null) { counterpart.matched = true; seen.matched = true }
        recent += seen
        while (recent.size > MAX_RECENT) recent.removeAt(0)

        val core = ReaderLabRules027036.completeness(candidate)
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val prefix = method.lowercase()
        val edit = prefs.edit()
            .putInt("${prefix}_seen", prefs.getInt("${prefix}_seen", 0) + 1)
            .putInt("${prefix}_core_points", prefs.getInt("${prefix}_core_points", 0) + core.score)
        if (core.complete) edit.putInt("${prefix}_complete", prefs.getInt("${prefix}_complete", 0) + 1)
        if (counterpart != null) edit.putInt("matched", prefs.getInt("matched", 0) + 1).putString("latest", "M1+M2") else edit.putString("latest", method)
        edit.putLong("latest_at", now).apply()
        LocalLog.append(context, "ReaderLab $method · core=${core.score}/5 · completo=${core.complete} · pareado=${counterpart != null} · ${ReaderLabRules027036.fingerprint(candidate)}")
    }

    fun methodForOfficial(offer: RideOffer): String {
        val now = System.currentTimeMillis(); cleanup(now); val candidate = offer.toLabCandidate(now)
        return if (recent.any { it.method == "M2" && now - it.at <= RECENT_WINDOW_MS && ReaderLabRules027036.sameOffer(it.candidate, candidate, RECENT_WINDOW_MS) }) "M1+M2" else "M1"
    }

    fun snapshot(context: Context): Snapshot {
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return Snapshot(
            mode = mode(context), m1Seen = p.getInt("m1_seen", 0), m2Seen = p.getInt("m2_seen", 0), matched = p.getInt("matched", 0),
            m1Complete = p.getInt("m1_complete", 0), m2Complete = p.getInt("m2_complete", 0),
            m1CorePoints = p.getInt("m1_core_points", 0), m2CorePoints = p.getInt("m2_core_points", 0),
            latest = p.getString("latest", "—") ?: "—", accessibilityEnabled = isAccessibilityEnabled(context),
        )
    }

    fun summary(context: Context): String {
        val s = snapshot(context)
        fun pct(points: Int, seen: Int): String = if (seen <= 0) "—" else "${(points * 100 / (seen * 5)).coerceIn(0, 100)}%"
        return buildString {
            appendLine("Reader Lab M1 × M2")
            appendLine("Modo: ${modeLabel(s.mode)}")
            appendLine("Acessibilidade M2: ${if (s.accessibilityEnabled) "ativa" else "desativada"}")
            appendLine("M1 detectadas: ${s.m1Seen} · core completo: ${s.m1Complete} · completude média: ${pct(s.m1CorePoints, s.m1Seen)}")
            appendLine("M2 detectadas: ${s.m2Seen} · core completo: ${s.m2Complete} · completude média: ${pct(s.m2CorePoints, s.m2Seen)}")
            appendLine("Pareadas M1+M2: ${s.matched}")
            appendLine("Somente M1: ${s.m1Only} · Somente M2: ${s.m2Only}")
            append("Core: horário + embarque + tempo até embarque + destino + tempo total.")
        }
    }

    fun shareSummary(context: Context) {
        val s = snapshot(context)
        val json = JSONObject().apply {
            put("generated_at", Instant.now().toString()); put("mode", s.mode); put("accessibility_enabled", s.accessibilityEnabled)
            put("m1_seen", s.m1Seen); put("m2_seen", s.m2Seen); put("matched", s.matched); put("m1_complete", s.m1Complete); put("m2_complete", s.m2Complete)
            put("m1_core_points", s.m1CorePoints); put("m2_core_points", s.m2CorePoints); put("latest", s.latest)
        }
        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"; putExtra(Intent.EXTRA_SUBJECT, "Sr. Rotas · Reader Lab M1 x M2"); putExtra(Intent.EXTRA_TEXT, summary(context) + "\n\n" + json.toString(2)); addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }, "Compartilhar diagnóstico").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun reset(context: Context) {
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val keepMode = mode(context); val disclosure = disclosureAccepted(context); val migrated = p.getBoolean(KEY_RC37_MIGRATED, true)
        val bounds = intArrayOf(p.getInt("uber_l", -1), p.getInt("uber_t", -1), p.getInt("uber_r", -1), p.getInt("uber_b", -1))
        p.edit().clear().putString("mode", keepMode).putBoolean(KEY_RC37_MIGRATED, migrated).putBoolean("accessibility_disclosure_accepted", disclosure).apply()
        if (bounds[0] >= 0) p.edit().putInt("uber_l", bounds[0]).putInt("uber_t", bounds[1]).putInt("uber_r", bounds[2]).putInt("uber_b", bounds[3]).apply()
        recent.clear(); processedM1.clear()
    }

    fun modeLabel(mode: String): String = when (mode) {
        MODE_M1 -> "M1 — MediaProjection"
        MODE_M2 -> "M2 — Acessibilidade isolada"
        else -> "Comparativo — M1 + M2 árvore"
    }

    private fun cleanup(now: Long) { recent.removeAll { now - it.at > RECENT_WINDOW_MS * 2 } }

    private fun RideOffer.toLabCandidate(receivedAt: Long) = ReaderLabRules027036.Candidate(
        platform = platform, fare = fare, pickupKm = pickupKm, tripKm = tripKm, totalKm = totalKm,
        pickupMinutes = pickupMinutes, totalMinutes = totalMinutes ?: tripMinutes,
        pickupLabel = context?.pickupLabel, destinationLabel = context?.destinationLabel,
        observedAtMs = runCatching { Instant.parse(observedAt).toEpochMilli() }.getOrDefault(receivedAt),
    )

    private fun <T> Set<T>.takeLast(max: Int): List<T> = toList().takeLast(max)
}
