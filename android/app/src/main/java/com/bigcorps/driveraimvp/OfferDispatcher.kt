package com.srrotas.app

import android.content.Context
import android.content.Intent

class OfferDispatcher(context: Context) {
    private val appContext = context.applicationContext
    private val repo = SettingsRepository(appContext)
    private val overlay = OverlayController(appContext)

    fun saveDiagnostic(raw: String, method: String) {
        if (raw.isBlank()) return
        val trimmed = raw.take(12000)
        repo.saveLatestCapture("Texto detectado; aguardando parser.", trimmed, method)
        LocalLog.append(appContext, "Captura $method (${trimmed.length} chars): ${trimmed.replace('\n', ' ').take(700)}")
        broadcast()
    }

    fun dispatch(offer: RideOffer, showOverlay: Boolean = true): Boolean {
        if (!repo.shouldEmitOffer(offer.dedupeKey)) return false
        val summary = OfferParser.humanSummary(offer)
        repo.saveLatestCapture(summary, offer.rawText, offer.captureMethod)
        LocalLog.append(appContext, "OFERTA ${offer.offerType} confiança=${offer.confidence}: ${summary.replace('\n', ' ')}")
        if (showOverlay) overlay.show(offer)
        BackendClient.sendOffer(appContext, offer)
        broadcast()
        return true
    }

    fun dispatchAll(offers: List<RideOffer>) {
        if (offers.isEmpty()) return
        val emitted = offers.filter { dispatch(it, showOverlay = false) }
        if (emitted.isEmpty()) return
        val best = emitted.maxWithOrNull(
            compareBy<RideOffer> { verdictRank(it.verdict) }
                .thenBy { it.confidence }
                .thenBy { it.perKm ?: 0.0 }
        )
        best?.let(overlay::show)
    }

    fun hideOverlay() = overlay.hide()

    private fun verdictRank(value: String) = when (value) {
        "boa" -> 3
        "regular" -> 2
        else -> 1
    }

    private fun broadcast() {
        appContext.sendBroadcast(Intent(AppSignals.ACTION_CAPTURE_UPDATED).setPackage(appContext.packageName))
    }
}
