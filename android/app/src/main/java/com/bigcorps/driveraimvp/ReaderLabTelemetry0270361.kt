package com.srrotas.app

import android.content.Context
import org.json.JSONObject
import java.time.Instant

/**
 * Telemetria local do Reader M2 para distinguir:
 * serviço não acionado / evento recebido / árvore candidata / screenshot / oferta.
 * Não guarda OCR bruto, endereço, coordenada nem screenshot.
 */
object ReaderLabTelemetry0270361 {
    private const val PREFS = "sr_reader_lab_m2_health_0270361"

    data class Snapshot(
        val serviceConnections: Int,
        val uberEvents: Int,
        val eligibleEvents: Int,
        val treeAttempts: Int,
        val treeCandidates: Int,
        val treeOffers: Int,
        val screenshotAttempts: Int,
        val screenshotSuccesses: Int,
        val screenshotFailures: Int,
        val visualCandidates: Int,
        val visualOffers: Int,
        val lastEventAt: Long,
        val lastOfferAt: Long,
        val lastFailureCode: Int,
    )

    private fun bump(context: Context, key: String, timestampKey: String? = null) {
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val edit = p.edit().putInt(key, p.getInt(key, 0) + 1)
        if (timestampKey != null) edit.putLong(timestampKey, System.currentTimeMillis())
        edit.apply()
    }

    fun serviceConnected(context: Context) = bump(context, "service_connections")
    fun uberEvent(context: Context) = bump(context, "uber_events", "last_event_at")
    fun eligibleEvent(context: Context) = bump(context, "eligible_events")
    fun treeAttempt(context: Context) = bump(context, "tree_attempts")
    fun treeCandidate(context: Context) = bump(context, "tree_candidates")
    fun treeOffer(context: Context) = bump(context, "tree_offers", "last_offer_at")
    fun screenshotAttempt(context: Context) = bump(context, "screenshot_attempts")
    fun screenshotSuccess(context: Context) = bump(context, "screenshot_successes")
    fun visualCandidate(context: Context) = bump(context, "visual_candidates")
    fun visualOffer(context: Context) = bump(context, "visual_offers", "last_offer_at")

    fun screenshotFailure(context: Context, code: Int) {
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        p.edit()
            .putInt("screenshot_failures", p.getInt("screenshot_failures", 0) + 1)
            .putInt("last_failure_code", code)
            .apply()
    }

    fun snapshot(context: Context): Snapshot {
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return Snapshot(
            serviceConnections = p.getInt("service_connections", 0),
            uberEvents = p.getInt("uber_events", 0),
            eligibleEvents = p.getInt("eligible_events", 0),
            treeAttempts = p.getInt("tree_attempts", 0),
            treeCandidates = p.getInt("tree_candidates", 0),
            treeOffers = p.getInt("tree_offers", 0),
            screenshotAttempts = p.getInt("screenshot_attempts", 0),
            screenshotSuccesses = p.getInt("screenshot_successes", 0),
            screenshotFailures = p.getInt("screenshot_failures", 0),
            visualCandidates = p.getInt("visual_candidates", 0),
            visualOffers = p.getInt("visual_offers", 0),
            lastEventAt = p.getLong("last_event_at", 0L),
            lastOfferAt = p.getLong("last_offer_at", 0L),
            lastFailureCode = p.getInt("last_failure_code", 0),
        )
    }

    fun summary(context: Context): String {
        val s = snapshot(context)
        val lab = ReaderLab027036.snapshot(context)
        fun whenText(value: Long): String = if (value <= 0L) "—" else runCatching {
            Instant.ofEpochMilli(value).toString()
        }.getOrDefault(value.toString())
        return buildString {
            appendLine("Saúde do Método 2 — Acessibilidade")
            appendLine("Acessibilidade habilitada: ${if (lab.accessibilityEnabled) "sim" else "não"}")
            appendLine("Conexões do serviço: ${s.serviceConnections}")
            appendLine("Eventos Uber recebidos: ${s.uberEvents}")
            appendLine("Eventos elegíveis durante jornada: ${s.eligibleEvents}")
            appendLine("Árvore lida: ${s.treeAttempts} · candidatas: ${s.treeCandidates} · ofertas: ${s.treeOffers}")
            appendLine("Screenshots M2: ${s.screenshotAttempts} · sucesso: ${s.screenshotSuccesses} · falha: ${s.screenshotFailures}")
            appendLine("Visual candidato: ${s.visualCandidates} · ofertas: ${s.visualOffers}")
            appendLine("Último evento Uber: ${whenText(s.lastEventAt)}")
            appendLine("Última oferta M2: ${whenText(s.lastOfferAt)}")
            if (s.lastFailureCode != 0) appendLine("Último código de falha screenshot: ${s.lastFailureCode}")
            appendLine()
            append("Observação: abrir um print na Galeria não aciona o AccessibilityService do Uber; o teste M2 válido é com a janela real do Uber em primeiro plano.")
        }
    }

    fun toJson(context: Context): JSONObject {
        val s = snapshot(context)
        val lab = ReaderLab027036.snapshot(context)
        return JSONObject().apply {
            put("accessibility_enabled", lab.accessibilityEnabled)
            put("service_connections", s.serviceConnections)
            put("uber_events", s.uberEvents)
            put("eligible_events", s.eligibleEvents)
            put("tree_attempts", s.treeAttempts)
            put("tree_candidates", s.treeCandidates)
            put("tree_offers", s.treeOffers)
            put("screenshot_attempts", s.screenshotAttempts)
            put("screenshot_successes", s.screenshotSuccesses)
            put("screenshot_failures", s.screenshotFailures)
            put("visual_candidates", s.visualCandidates)
            put("visual_offers", s.visualOffers)
            put("last_event_at_ms", s.lastEventAt)
            put("last_offer_at_ms", s.lastOfferAt)
            put("last_failure_code", s.lastFailureCode)
            put("privacy", "Somente contadores e timestamps técnicos; sem OCR bruto, screenshot, endereço ou coordenada.")
        }
    }

    fun reset(context: Context) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }
}
