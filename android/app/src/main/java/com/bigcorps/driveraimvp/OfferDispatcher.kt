package com.srrotas.app

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.SystemClock

class OfferDispatcher(context: Context) {
    companion object {
        private const val DUPLICATE_LOG_INTERVAL_MS = 4_000L
    }

    private val appContext = context.applicationContext
    private val repo = SettingsRepository(appContext)
    private val overlay = OverlayController(appContext)
    private val localStore = LocalStore.get(appContext)
    private val stabilizer = CardStabilizer()
    private val stabilizerHandler = Handler(Looper.getMainLooper())
    private val stabilizerLock = Any()

    private var lastDuplicateLogAt = 0L
    private var suppressedDuplicateLogs = 0
    private val flushRunnable = Runnable { flushReadyStabilized() }

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

    /**
     * MediaProjection 0.5.4:
     * HUD = imediato; histórico/backend = melhor leitura em até 750 ms.
     */
    fun submitStabilized(offers: List<RideOffer>) {
        if (offers.isEmpty()) return

        // Captura o journeyId enquanto a jornada ainda está ativa. Assim o último
        // card não perde vínculo caso o Activity encerre a jornada antes do onDestroy do serviço.
        val activeJourneyId = repo.currentJourneyId().takeIf { it.isNotBlank() }
        val staged = offers.map { offer ->
            if (offer.journeyId == null && activeJourneyId != null) offer.copy(journeyId = activeJourneyId) else offer
        }

        previewBest(staged)

        val ready: List<CardStabilizer.StableResult>
        synchronized(stabilizerLock) {
            ready = stabilizer.submit(staged, SystemClock.elapsedRealtime())
            scheduleNextFlushLocked()
        }
        persistStableResults(ready)
    }

    /** Leitura auxiliar: mantém o comportamento imediato anterior. */
    fun dispatchAll(offers: List<RideOffer>) {
        persistStableResults(offers.map { CardStabilizer.StableResult(it, 1, 0) })
    }

    /** Garante que a última oferta não seja perdida ao encerrar a jornada. */
    fun flushStabilized() {
        stabilizerHandler.removeCallbacks(flushRunnable)
        val pending = synchronized(stabilizerLock) { stabilizer.flushAll() }
        persistStableResults(pending)
    }

    fun hideOverlay() = overlay.hide()

    private fun previewBest(offers: List<RideOffer>) {
        // Não pisca no HUD uma leitura parcial fraca como R$3,00/R$14,00 dos testes.
        val candidates = offers.filterNot(stabilizer::isWeakPartial)
        val best = bestOf(candidates) ?: return
        overlay.show(best)
    }

    private fun flushReadyStabilized() {
        val ready: List<CardStabilizer.StableResult>
        synchronized(stabilizerLock) {
            ready = stabilizer.drainReady(SystemClock.elapsedRealtime())
            scheduleNextFlushLocked()
        }
        persistStableResults(ready)
    }

    private fun scheduleNextFlushLocked() {
        stabilizerHandler.removeCallbacks(flushRunnable)
        val delay = stabilizer.nextDelayMs(SystemClock.elapsedRealtime()) ?: return
        stabilizerHandler.postDelayed(flushRunnable, delay)
    }

    private fun persistStableResults(results: List<CardStabilizer.StableResult>) {
        if (results.isEmpty()) return

        val emitted = results.mapNotNull { result ->
            val prepared = prepare(result.offer) ?: return@mapNotNull null
            prepared to result
        }
        if (emitted.isEmpty()) return

        bestOf(emitted.map { it.first })?.let {
            overlay.show(it)
            OfferNotifier.notify(appContext, it)
            repo.saveLatestCapture(OfferParser.humanSummary(it), it.rawText, it.captureMethod)
        }

        emitted.forEach { (offer, stabilization) ->
            if (stabilization.samples > 1 || stabilization.replacements > 0) {
                LocalLog.append(
                    appContext,
                    "CARD ESTABILIZADO · amostras=${stabilization.samples} · melhorias=${stabilization.replacements} · " +
                        "R$ ${offer.fare} · ${offer.offerType}/${offer.serviceType} · confiança=${offer.confidence}",
                )
            }
            persist(offer, updateLatest = false)
        }
        broadcast()
    }

    private fun bestOf(offers: List<RideOffer>): RideOffer? = offers.maxWithOrNull(
        compareBy<RideOffer> { verdictRank(it.verdict) }
            .thenBy { stabilizer.qualityScore(it) }
            .thenBy { it.perMinute ?: 0.0 }
            .thenBy { it.perKm ?: 0.0 },
    )

    private fun prepare(offer: RideOffer): RideOffer? {
        if (!OfferDeduplicator.shouldEmit(offer)) {
            logDuplicate(offer)
            return null
        }
        val journeyId = repo.currentJourneyId().takeIf { it.isNotBlank() } ?: offer.journeyId
        return offer.copy(journeyId = journeyId)
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
