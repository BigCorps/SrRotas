package com.srrotas.app

import android.content.Context
import android.content.Intent
import android.os.SystemClock

class OfferDispatcher(context: Context) {
    companion object {
        private const val DUPLICATE_LOG_INTERVAL_MS = 4_000L
    }

    private val appContext = context.applicationContext
    private val repo = SettingsRepository(appContext)
    private val overlay = OverlayController(appContext)
    private val localStore = LocalStore.get(appContext)
    private var lastDuplicateLogAt = 0L
    private var suppressedDuplicateLogs = 0

    fun saveDiagnostic(raw: String, method: String) {
        if (raw.isBlank()) return
        val trimmed = raw.take(12000)
        repo.saveLatestCapture("Texto detectado; nenhum card válido confirmado.", trimmed, method)
        LocalLog.append(appContext, "Captura $method (${trimmed.length} chars): ${trimmed.replace('\n', ' ').take(700)}")
        broadcast()
    }

    fun dispatch(offer: RideOffer, showOverlay: Boolean = true): Boolean {
        val enriched = prepare(offer) ?: return false
        if (showOverlay) {
            overlay.show(enriched)
            OfferNotifier.notify(appContext, enriched)
        }
        persist(enriched, updateLatest = true)
        broadcast()
        return true
    }

    fun dispatchAll(offers: List<RideOffer>) {
        val emitted = offers.mapNotNull(::prepare)
        if (emitted.isEmpty()) return

        val best = emitted.maxWithOrNull(
            compareBy<RideOffer> { verdictRank(it.verdict) }
                .thenBy { it.confidence }
                .thenBy { it.perMinute ?: 0.0 }
                .thenBy { it.perKm ?: 0.0 },
        )

        // O HUD aparece antes das gravações locais do lote para reduzir a latência percebida.
        best?.let {
            overlay.show(it)
            OfferNotifier.notify(appContext, it)
            repo.saveLatestCapture(OfferParser.humanSummary(it), it.rawText, it.captureMethod)
        }

        emitted.forEach { persist(it, updateLatest = false) }
        broadcast()
    }

    fun hideOverlay() = overlay.hide()

    private fun prepare(offer: RideOffer): RideOffer? {
        if (!OfferDeduplicator.shouldEmit(offer)) {
            logDuplicate(offer)
            return null
        }
        return offer.copy(journeyId = repo.currentJourneyId().takeIf { it.isNotBlank() })
    }

    private fun persist(enriched: RideOffer, updateLatest: Boolean) {
        val summary = OfferParser.humanSummary(enriched)
        if (updateLatest) repo.saveLatestCapture(summary, enriched.rawText, enriched.captureMethod)
        if (!localStore.saveOffer(enriched)) return
        LocalLog.append(
            appContext,
            "OFERTA VÁLIDA ${enriched.offerType}/${enriched.serviceType} confiança=${enriched.confidence}: ${summary.replace('\n', ' ')}",
        )
        BackendClient.sendOffer(appContext, enriched)
    }

    private fun logDuplicate(offer: RideOffer) {
        suppressedDuplicateLogs++
        val now = SystemClock.elapsedRealtime()
        if (now - lastDuplicateLogAt < DUPLICATE_LOG_INTERVAL_MS) return
        val suppressed = (suppressedDuplicateLogs - 1).coerceAtLeast(0)
        val suffix = if (suppressed > 0) " (+$suppressed repetidas suprimidas no log)" else ""
        LocalLog.append(appContext, "OFERTA duplicada ignorada: R$ ${offer.fare} ${offer.offerType}/${offer.serviceType}$suffix")
        suppressedDuplicateLogs = 0
        lastDuplicateLogAt = now
    }

    private fun verdictRank(v: String) = when (v) { "boa" -> 3; "regular" -> 2; else -> 1 }
    private fun broadcast() { appContext.sendBroadcast(Intent(AppSignals.ACTION_CAPTURE_UPDATED).setPackage(appContext.packageName)) }
}
