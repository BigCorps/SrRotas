package com.srrotas.app

import android.content.Context
import android.content.Intent

class OfferDispatcher(context: Context) {
    private val appContext = context.applicationContext
    private val repo = SettingsRepository(appContext)
    private val overlay = OverlayController(appContext)
    private val localStore = LocalStore.get(appContext)

    fun saveDiagnostic(raw: String, method: String) {
        if (raw.isBlank()) return
        val trimmed = raw.take(12000)
        repo.saveLatestCapture("Texto detectado; nenhum card válido confirmado.", trimmed, method)
        LocalLog.append(appContext, "Captura $method (${trimmed.length} chars): ${trimmed.replace('\n', ' ').take(700)}")
        broadcast()
    }

    fun dispatch(offer: RideOffer, showOverlay: Boolean = true): Boolean {
        if (!OfferDeduplicator.shouldEmit(offer)) {
            LocalLog.append(appContext, "OFERTA duplicada ignorada: R$ ${offer.fare} ${offer.offerType}/${offer.serviceType}")
            return false
        }
        val enriched = offer.copy(journeyId = repo.currentJourneyId().takeIf { it.isNotBlank() })
        val summary = OfferParser.humanSummary(enriched)
        repo.saveLatestCapture(summary, enriched.rawText, enriched.captureMethod)
        localStore.saveOffer(enriched)
        LocalLog.append(
            appContext,
            "OFERTA VÁLIDA ${enriched.offerType}/${enriched.serviceType} confiança=${enriched.confidence}: ${summary.replace('\n', ' ')}",
        )
        if (showOverlay) {
            overlay.show(enriched)
            OfferNotifier.notify(appContext, enriched)
        }
        BackendClient.sendOffer(appContext, enriched)
        broadcast()
        return true
    }

    fun dispatchAll(offers: List<RideOffer>) {
        val emitted = offers.filter { dispatch(it, false) }
        if (emitted.isEmpty()) return
        emitted.maxWithOrNull(
            compareBy<RideOffer> { verdictRank(it.verdict) }
                .thenBy { it.confidence }
                .thenBy { it.perMinute ?: 0.0 }
                .thenBy { it.perKm ?: 0.0 },
        )?.let {
            overlay.show(it)
            OfferNotifier.notify(appContext, it)
        }
    }

    fun hideOverlay() = overlay.hide()
    private fun verdictRank(v: String) = when (v) { "boa" -> 3; "regular" -> 2; else -> 1 }
    private fun broadcast() { appContext.sendBroadcast(Intent(AppSignals.ACTION_CAPTURE_UPDATED).setPackage(appContext.packageName)) }
}
