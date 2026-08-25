package com.srrotas.app

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.SystemClock

class OfferDispatcher(
    context: Context,
) {
    companion object {
        private const val DUPLICATE_LOG_INTERVAL_MS =
            4_000L
    }

    private val appContext =
        context.applicationContext
    private val repo =
        SettingsRepository(appContext)
    private val overlay =
        OverlayController(appContext)
    private val localStore =
        LocalStore.get(appContext)
    private val stabilizer =
        CardStabilizer()
    private val stabilizerHandler =
        Handler(Looper.getMainLooper())
    private val stabilizerLock =
        Any()

    private var lastDuplicateLogAt =
        0L
    private var suppressedDuplicateLogs =
        0

    private val flushRunnable =
        Runnable {
            flushReadyStabilized()
        }

    fun saveDiagnostic(
        raw: String,
        method: String,
    ) {
        if (raw.isBlank()) return

        val trimmed =
            raw.take(12000)

        repo.saveLatestCapture(
            "Texto detectado; nenhum card válido confirmado.",
            trimmed,
            method,
        )

        LocalLog.append(
            appContext,
            "Captura $method (${trimmed.length} chars): " +
                trimmed
                    .replace(
                        '\n',
                        ' ',
                    )
                    .take(700),
        )

        broadcast()
    }

    fun dispatch(
        offer: RideOffer,
        showOverlay: Boolean = true,
    ): Boolean {
        if (
            !JourneyCoordinator
                .canObserveOffers(
                    appContext,
                )
        ) {
            discardPendingForBlockedState()
            overlay.hide()
            return false
        }

        val enriched =
            prepare(offer)
                ?: return false

        if (showOverlay) {
            overlay.show(enriched)
            OfferNotifier.notify(
                appContext,
                enriched,
            )
        }

        persist(
            enriched,
            updateLatest = true,
        )
        broadcast()
        return true
    }

    /**
     * Offer Engine v1 permanece congelado.
     * 0.18 adiciona metadados de custo ao redor dele.
     * 0.21 aplica limites de estratégia somente após o parser, sem alterar OCR,
     * métricas financeiras, dedupe ou estabilização.
     */
    fun submitStabilized(
        offers: List<RideOffer>,
    ) {
        if (offers.isEmpty()) return

        if (
            !JourneyCoordinator
                .canObserveOffers(
                    appContext,
                )
        ) {
            discardPendingForBlockedState()
            overlay.hide()
            return
        }

        val activeJourneyId =
            repo.currentJourneyId()
                .takeIf {
                    it.isNotBlank()
                }

        val staged =
            offers.map { offer ->
                if (
                    offer.journeyId == null &&
                    activeJourneyId != null
                ) {
                    offer.copy(
                        journeyId =
                            activeJourneyId,
                    )
                } else {
                    offer
                }
            }

        previewBest(staged)

        val ready:
            List<CardStabilizer.StableResult>

        synchronized(
            stabilizerLock,
        ) {
            ready =
                stabilizer.submit(
                    staged,
                    SystemClock
                        .elapsedRealtime(),
                )
            scheduleNextFlushLocked()
        }

        persistStableResults(
            ready,
        )
    }

    fun dispatchAll(
        offers: List<RideOffer>,
    ) {
        if (
            !JourneyCoordinator
                .canObserveOffers(
                    appContext,
                )
        ) {
            discardPendingForBlockedState()
            overlay.hide()
            return
        }

        persistStableResults(
            offers.map {
                CardStabilizer.StableResult(
                    it,
                    1,
                    0,
                )
            },
        )
    }

    fun flushStabilized() {
        stabilizerHandler
            .removeCallbacks(
                flushRunnable,
            )

        val pending =
            synchronized(
                stabilizerLock,
            ) {
                stabilizer.flushAll()
            }

        if (
            JourneyCoordinator
                .canObserveOffers(
                    appContext,
                )
        ) {
            persistStableResults(
                pending,
            )
        }
    }

    fun hideOverlay() =
        overlay.hide()

    private fun discardPendingForBlockedState() {
        stabilizerHandler
            .removeCallbacks(
                flushRunnable,
            )

        synchronized(
            stabilizerLock,
        ) {
            stabilizer.flushAll()
        }
    }

    private fun previewBest(
        offers: List<RideOffer>,
    ) {
        val candidates =
            offers.filterNot(
                stabilizer::isWeakPartial,
            )

        val best =
            bestOf(candidates)
                ?: return

        overlay.show(
            StrategyGuard021.apply(
                appContext,
                best,
            ),
        )
    }

    private fun flushReadyStabilized() {
        val ready:
            List<CardStabilizer.StableResult>

        synchronized(
            stabilizerLock,
        ) {
            ready =
                stabilizer.drainReady(
                    SystemClock
                        .elapsedRealtime(),
                )
            scheduleNextFlushLocked()
        }

        if (
            JourneyCoordinator
                .canObserveOffers(
                    appContext,
                )
        ) {
            persistStableResults(
                ready,
            )
        }
    }

    private fun scheduleNextFlushLocked() {
        stabilizerHandler
            .removeCallbacks(
                flushRunnable,
            )

        val delay =
            stabilizer.nextDelayMs(
                SystemClock
                    .elapsedRealtime(),
            ) ?: return

        stabilizerHandler
            .postDelayed(
                flushRunnable,
                delay,
            )
    }

    private fun persistStableResults(
        results:
            List<CardStabilizer.StableResult>,
    ) {
        if (
            results.isEmpty() ||
            !JourneyCoordinator
                .canObserveOffers(
                    appContext,
                )
        ) {
            return
        }

        val emitted =
            results.mapNotNull {
                    result,
                ->
                val prepared =
                    prepare(
                        result.offer,
                    )
                        ?: return@mapNotNull null

                prepared to result
            }

        if (emitted.isEmpty()) return

        bestOf(
            emitted.map {
                it.first
            },
        )?.let {
            overlay.show(it)
            OfferNotifier.notify(
                appContext,
                it,
            )
            repo.saveLatestCapture(
                OfferParser
                    .humanSummary(it),
                it.rawText,
                it.captureMethod,
            )
        }

        emitted.forEach {
                (
                    offer,
                    stabilization,
                ),
            ->
            if (
                stabilization.samples > 1 ||
                stabilization.replacements > 0
            ) {
                LocalLog.append(
                    appContext,
                    "CARD ESTABILIZADO · " +
                        "amostras=${stabilization.samples} · " +
                        "melhorias=${stabilization.replacements} · " +
                        "R$ ${offer.fare} · " +
                        "${offer.offerType}/${offer.serviceType} · " +
                        "confiança=${offer.confidence}",
                )
            }

            persist(
                offer,
                updateLatest = false,
            )
        }

        broadcast()
    }

    private fun bestOf(
        offers: List<RideOffer>,
    ): RideOffer? =
        offers.maxWithOrNull(
            compareBy<RideOffer> {
                verdictRank(
                    StrategyGuard021.apply(
                        appContext,
                        it,
                    ).verdict,
                )
            }
                .thenBy {
                    stabilizer
                        .qualityScore(it)
                }
                .thenBy {
                    it.perMinute ?: 0.0
                }
                .thenBy {
                    it.perKm ?: 0.0
                },
        )

    private fun prepare(
        offer: RideOffer,
    ): RideOffer? {
        if (
            !OfferDeduplicator
                .shouldEmit(offer)
        ) {
            logDuplicate(offer)
            return null
        }

        val journeyId =
            repo.currentJourneyId()
                .takeIf {
                    it.isNotBlank()
                }
                ?: offer.journeyId

        val configured =
            repo.costSnapshot()

        val arithmeticCost =
            if (
                offer.estimatedCost != null &&
                offer.totalKm != null &&
                offer.totalKm > 0.0
            ) {
                kotlin.math.round(
                    (
                        offer.estimatedCost /
                        offer.totalKm
                        ) * 10000.0,
                ) / 10000.0
            } else {
                null
            }

        val sameAsConfigured =
            arithmeticCost != null &&
                kotlin.math.abs(
                    arithmeticCost -
                        configured.costPerKm,
                ) <= 0.0002

        val costWrapped =
            offer.copy(
                journeyId = journeyId,
                costPerKmUsed =
                    arithmeticCost
                        ?: configured.costPerKm,
                costSource =
                    if (
                        arithmeticCost == null ||
                        sameAsConfigured
                    ) {
                        configured.source
                    } else {
                        "runtime_reconstructed"
                    },
                costProfileVersion =
                    if (
                        arithmeticCost == null ||
                        sameAsConfigured
                    ) {
                        configured.version
                    } else {
                        "parser_cost_snapshot"
                    },
                costProfileUpdatedAt =
                    if (
                        arithmeticCost == null ||
                        sameAsConfigured
                    ) {
                        configured.profileUpdatedAt
                            .takeIf {
                                it.isNotBlank()
                            }
                    } else {
                        null
                    },
            )

        return StrategyGuard021.apply(
            appContext,
            costWrapped,
        )
    }

    private fun persist(
        enriched: RideOffer,
        updateLatest: Boolean,
    ) {
        val summary =
            OfferParser
                .humanSummary(
                    enriched,
                )

        if (updateLatest) {
            repo.saveLatestCapture(
                summary,
                enriched.rawText,
                enriched.captureMethod,
            )
        }

        if (
            !localStore
                .saveOffer(
                    enriched,
                )
        ) {
            return
        }

        LocalLog.append(
            appContext,
            "OFERTA VÁLIDA ${enriched.offerType}/${enriched.serviceType} " +
                "confiança=${enriched.confidence}: " +
                summary
                    .replace(
                        '\n',
                        ' ',
                    ),
        )

        JourneyCoordinator
            .onOfferObserved(
                appContext,
                enriched,
            )

        BackendClient.sendOffer(
            appContext,
            enriched,
        )

        val initialContext =
            enriched.context

        // 0.21.1: continuidade no destino roda fora do caminho quente do OCR.
        // Se o contexto já veio resolvido, consulta imediatamente; quando ainda
        // precisa de geocoding, aguardamos abaixo para aproveitar destinationCell.
        if (
            initialContext != null &&
            initialContext.geocodeStatus != "pending"
        ) {
            requestDestinationContinuity(enriched)
        }

        if (
            initialContext
                ?.hasTextContext() ==
            true &&
            initialContext.geocodeStatus ==
            "pending"
        ) {
            OfferContextGeocoder
                .enrichAsync(
                    appContext,
                    enriched,
                ) { resolved ->
                    localStore
                        .saveOrUpdateContext(
                            enriched.localId,
                            resolved,
                            syncState = 0,
                        )

                    BackendClient
                        .sendOfferContext(
                            appContext,
                            enriched.localId,
                            enriched.dedupeKey,
                            resolved,
                        )

                    val resolvedOffer =
                        enriched.copy(context = resolved)

                    JourneyBubbleController
                        .refreshOffer(
                            appContext,
                        )

                    requestDestinationContinuity(resolvedOffer)

                    LocalLog.append(
                        appContext,
                        "CONTEXTO ${resolved.geocodeStatus}: " +
                            "${resolved.pickupLabel ?: "?"} → " +
                            "${resolved.destinationLabel ?: "?"}",
                    )
                }
        }
    }

    private fun requestDestinationContinuity(offer: RideOffer) {
        val ctx = offer.context ?: return
        val eta =
            ctx.estimatedArrivalAt
                ?: OfferContextEngine.estimatedArrivalAt(
                    offer.observedAt,
                    offer.totalMinutes,
                )
                ?: return

        if (
            ctx.destinationCell.isNullOrBlank() &&
            ctx.destinationLabel.isNullOrBlank()
        ) {
            return
        }

        // O ETA é calculado antes apenas para garantir que a consulta é válida;
        // o cliente reaproveita o mesmo contexto e continua assíncrono.
        if (eta.isBlank()) return

        DestinationContinuityClient0211.request(
            appContext,
            offer,
        ) { result ->
            result
                .onSuccess { insight ->
                    // Re-renderiza somente apresentação. Não altera parser,
                    // verdict, dedupe, jornada ou persistência da oferta.
                    // Nunca ressuscita uma oferta antiga sobre uma mais nova.
                    val latestId =
                        localStore.recentOffers(1)
                            .firstOrNull()
                            ?.localId
                    val ageMs =
                        runCatching {
                            java.time.Duration.between(
                                java.time.Instant.parse(offer.observedAt),
                                java.time.Instant.now(),
                            ).toMillis()
                        }.getOrDefault(Long.MAX_VALUE)
                    if (latestId == offer.localId && ageMs in 0..12_000L) {
                        overlay.show(offer, durationMs = 5_000L)
                    }
                    JourneyBubbleController.refreshOffer(appContext)
                    LocalLog.append(
                        appContext,
                        "DESTINO ${insight.regionLabel ?: "?"}: " +
                            DestinationContinuityPresentation0211.hudLabel(insight) +
                            " · ${insight.source}",
                    )
                }
                .onFailure { error ->
                    LocalLog.append(
                        appContext,
                        "Continuidade no destino indisponível: ${error.message}",
                    )
                }
        }
    }

    private fun logDuplicate(
        offer: RideOffer,
    ) {
        suppressedDuplicateLogs++

        val now =
            SystemClock
                .elapsedRealtime()

        if (
            now -
            lastDuplicateLogAt <
            DUPLICATE_LOG_INTERVAL_MS
        ) {
            return
        }

        val suppressed =
            (
                suppressedDuplicateLogs -
                1
                ).coerceAtLeast(0)

        val suffix =
            if (suppressed > 0) {
                " (+$suppressed repetidas suprimidas no log)"
            } else {
                ""
            }

        LocalLog.append(
            appContext,
            "OFERTA duplicada ignorada: " +
                "R$ ${offer.fare} " +
                "${offer.offerType}/${offer.serviceType}$suffix",
        )

        suppressedDuplicateLogs =
            0
        lastDuplicateLogAt =
            now
    }

    private fun verdictRank(
        v: String,
    ) =
        when (v) {
            "boa" -> 3
            "regular" -> 2
            else -> 1
        }

    private fun broadcast() {
        appContext.sendBroadcast(
            Intent(
                AppSignals.ACTION_CAPTURE_UPDATED,
            ).setPackage(
                appContext.packageName,
            ),
        )
    }
}
