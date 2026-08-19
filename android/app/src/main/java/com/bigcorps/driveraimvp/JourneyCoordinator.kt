package com.srrotas.app

import android.content.Context
import android.content.Intent
import java.time.Instant
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.abs

object JourneyCoordinator {
    private const val OFFER_BURST_WINDOW_MS = 15_000L

    @Volatile private var runtimeHydrated = false
    @Volatile private var runtimeJourneyId: String? = null
    @Volatile private var runtimeState: JourneyOperationalState =
        JourneyOperationalState.NOT_STARTED
    @Volatile private var runtimeRide: RideOutcome? = null
    @Volatile private var runtimeLatestOffer: RideOffer? = null

    // 0.17: o Radar pode persistir vários cards no mesmo instante. Para
    // exposição/tempo de espera isso é uma única chegada de oportunidade.
    @Volatile private var lastExposureBurstAtMs = Long.MIN_VALUE
    @Volatile private var lastExposureBurstCell: String? = null

    private val runtimeLock = Any()

    private val postOfferExecutor =
        Executors.newSingleThreadScheduledExecutor { runnable ->
            Thread(runnable, "SrRotasJourneyPost").apply {
                priority =
                    (Thread.NORM_PRIORITY - 1)
                        .coerceAtLeast(Thread.MIN_PRIORITY)
                isDaemon = true
            }
        }

    fun hydrateRuntime(context: Context) {
        if (runtimeHydrated) return
        synchronized(runtimeLock) {
            if (runtimeHydrated) return
            val app = context.applicationContext
            val repo = SettingsRepository(app)
            val id =
                repo.currentJourneyId()
                    .takeIf { it.isNotBlank() }

            if (id == null) {
                runtimeJourneyId = null
                runtimeState =
                    JourneyOperationalState.NOT_STARTED
                runtimeRide = null
                runtimeLatestOffer = null
                runtimeHydrated = true
                return
            }

            val store = LocalStore.get(app)
            runtimeJourneyId = id
            runtimeState =
                store.currentJourneyState(id)
            runtimeRide =
                store.currentDoingRide(id)
            runtimeLatestOffer =
                store.recentOffers(1).firstOrNull()
            runtimeHydrated = true
        }
    }

    private fun setRuntime(
        journeyId: String?,
        state: JourneyOperationalState,
        ride: RideOutcome? = runtimeRide,
        latestOffer: RideOffer? = runtimeLatestOffer,
    ) {
        synchronized(runtimeLock) {
            runtimeJourneyId = journeyId
            runtimeState = state
            runtimeRide = ride
            runtimeLatestOffer = latestOffer
            runtimeHydrated = true
        }
    }

    private fun resetOfferBurst() {
        synchronized(runtimeLock) {
            lastExposureBurstAtMs = Long.MIN_VALUE
            lastExposureBurstCell = null
        }
    }

    @Synchronized
    fun startJourney(
        context: Context,
        platform: String = "uber",
    ): JourneyRecord {
        val appContext = context.applicationContext
        val repo = SettingsRepository(appContext)
        val store = LocalStore.get(appContext)
        val currentId = repo.currentJourneyId()

        if (currentId.isNotBlank()) {
            val existing = store.journey(currentId)
            if (
                existing != null &&
                repo.isProjectionActive()
            ) {
                setRuntime(
                    journeyId = existing.id,
                    state =
                        store.currentJourneyState(
                            existing.id,
                        ),
                    ride =
                        store.currentDoingRide(
                            existing.id,
                        ),
                    latestOffer =
                        store.recentOffers(1)
                            .firstOrNull(),
                )
                JourneyBubbleController.show(
                    appContext,
                )
                JourneyLocationRuntime.ensure(
                    context,
                )
                return existing
            }

            if (
                existing != null &&
                existing.endedAt == null
            ) {
                store.recordJourneyEvent(
                    existing.id,
                    "end",
                    JourneyOperationalState.ENDED,
                )
                store.endJourney(
                    currentId,
                    "recovered_before_new_session",
                )?.let {
                    BackendClient.endJourney(
                        appContext,
                        it,
                    )
                }
            }
            repo.clearCurrentJourney()
        }

        OfferDeduplicator.reset()
        resetOfferBurst()

        val journey =
            store.startJourney(platform)

        repo.setCurrentJourney(
            journey.id,
            journey.startedAt,
        )
        store.recordJourneyEvent(
            journey.id,
            "start",
            JourneyOperationalState.ACTIVE,
            journey.startedAt,
        )
        setRuntime(
            journey.id,
            JourneyOperationalState.ACTIVE,
            ride = null,
            latestOffer = null,
        )

        BackendClient.startJourney(
            appContext,
            journey,
        )
        JourneySyncClient.flush(appContext)
        JourneyBubbleController.show(appContext)
        JourneyLocationRuntime.ensure(context)

        LocalLog.append(
            appContext,
            "JORNADA 0.17 iniciada id=${journey.id} plataforma=${journey.platform}",
        )
        return journey
    }

    @Synchronized
    fun pauseJourney(
        context: Context,
    ): Boolean {
        val app = context.applicationContext
        hydrateRuntime(app)
        val id =
            runtimeJourneyId ?: return false
        val store = LocalStore.get(app)

        if (
            !JourneyStateMachine.canPause(
                runtimeState,
                runtimeRide != null,
            )
        ) {
            return false
        }

        store.closeExposure(id, "pause")
        store.recordJourneyEvent(
            id,
            "pause",
            JourneyOperationalState.PAUSED,
        )
        setRuntime(
            id,
            JourneyOperationalState.PAUSED,
        )
        resetOfferBurst()

        JourneyLocationService.dispatch(
            app,
            JourneyLocationService.ACTION_PAUSE,
        )
        JourneyBubbleController.refresh(app)
        JourneySyncClient.flush(app)

        LocalLog.append(
            app,
            "JORNADA pausada id=$id",
        )
        return true
    }

    @Synchronized
    fun resumeJourney(
        context: Context,
    ): Boolean {
        val app = context.applicationContext
        hydrateRuntime(app)
        val id =
            runtimeJourneyId ?: return false
        val store = LocalStore.get(app)

        if (
            !JourneyStateMachine.canResume(
                runtimeState,
            )
        ) {
            return false
        }

        store.recordJourneyEvent(
            id,
            "resume",
            JourneyOperationalState.ACTIVE,
        )
        setRuntime(
            id,
            JourneyOperationalState.ACTIVE,
        )
        resetOfferBurst()

        JourneyLocationRuntime.ensure(context)
        JourneyLocationService.dispatch(
            app,
            JourneyLocationService.ACTION_RESUME,
        )
        JourneyBubbleController.refresh(app)
        JourneySyncClient.flush(app)

        LocalLog.append(
            app,
            "JORNADA retomada id=$id",
        )
        return true
    }

    @Synchronized
    fun endJourney(
        context: Context,
        reason: String,
    ): JourneySummary? {
        val appContext =
            context.applicationContext
        hydrateRuntime(appContext)

        val repo =
            SettingsRepository(appContext)
        val id =
            runtimeJourneyId
                ?: repo.currentJourneyId()
                    .takeIf { it.isNotBlank() }
                ?: return null
        val store =
            LocalStore.get(appContext)

        store.currentDoingRide(id)?.let { doing ->
            store.updateRideOutcome(
                doing.localOfferId,
                id,
                RideOperationalStatus.NOT_COMPLETED,
                "journey_end_auto",
            )
        }

        store.closeExposure(
            id,
            "journey_end",
        )

        JourneyLocationService.dispatch(
            appContext,
            JourneyLocationService.ACTION_STOP,
        )

        runCatching {
            appContext.stopService(
                Intent(
                    appContext,
                    MediaProjectionOcrService::class.java,
                ),
            )
        }

        repo.setProjectionActive(false)

        if (
            store.currentJourneyState(id) !=
            JourneyOperationalState.ENDED
        ) {
            store.recordJourneyEvent(
                id,
                "end",
                JourneyOperationalState.ENDED,
            )
        }

        val summary =
            store.endJourney(id, reason)

        setRuntime(
            null,
            JourneyOperationalState.ENDED,
            ride = null,
            latestOffer = runtimeLatestOffer,
        )
        resetOfferBurst()

        JourneyBubbleController.hide(
            appContext,
        )
        repo.clearCurrentJourney()
        OfferDeduplicator.reset()
        JourneySyncClient.flush(appContext)

        if (summary != null) {
            BackendClient.endJourney(
                appContext,
                summary,
            )
            LocalLog.append(
                appContext,
                "JORNADA encerrada id=$id motivo=$reason ofertas=${summary.offerCount}",
            )
        }
        return summary
    }

    fun currentSummary(
        context: Context,
    ): JourneySummary? {
        hydrateRuntime(context)
        val id =
            runtimeJourneyId ?: return null
        return LocalStore.get(context)
            .journeySummary(id)
    }

    fun currentOperationalState(
        context: Context,
    ): JourneyOperationalState {
        hydrateRuntime(context)
        return runtimeState
    }

    fun snapshot(
        context: Context,
    ): JourneyOperationalSnapshot {
        hydrateRuntime(context)
        return JourneyOperationalSnapshot(
            journeyId = runtimeJourneyId,
            journeyState = runtimeState,
            currentRide = runtimeRide,
            latestOffer = runtimeLatestOffer,
        )
    }

    /**
     * Caminho quente do OCR: somente estado em memória.
     */
    fun canObserveOffers(
        context: Context,
    ): Boolean {
        hydrateRuntime(context)
        return JourneyStateMachine
            .canObserveOffers(
                runtimeState,
                runtimeRide?.status ==
                    RideOperationalStatus.DOING_RIDE,
            )
    }

    /**
     * O Offer Engine continua gravando TODAS as ofertas válidas.
     *
     * Para o denominador estatístico, porém, vários cards do mesmo Radar em
     * poucos segundos representam uma única chegada de oportunidade.
     * A decisão de cortar exposição roda fora do callback OCR.
     */
    fun onOfferObserved(
        context: Context,
        offer: RideOffer,
    ) {
        val app = context.applicationContext
        hydrateRuntime(app)

        synchronized(runtimeLock) {
            runtimeLatestOffer = offer
        }

        val shouldConsiderExposure =
            runtimeState ==
                JourneyOperationalState.ACTIVE &&
                runtimeRide?.status !=
                RideOperationalStatus.DOING_RIDE

        JourneyBubbleController
            .refreshOffer(app)

        postOfferExecutor.schedule(
            {
                val journeyId =
                    offer.journeyId
                        ?.takeIf {
                            it.isNotBlank()
                        }
                        ?: return@schedule

                val store =
                    LocalStore.get(app)

                // Cada card continua tendo seu próprio estado OFFERED.
                store.ensureOfferedOutcome(
                    offer,
                )

                if (shouldConsiderExposure) {
                    store.currentOpenExposure(
                        journeyId,
                    )?.let { open ->
                        val observedMs =
                            runCatching {
                                Instant.parse(
                                    offer.observedAt,
                                ).toEpochMilli()
                            }.getOrDefault(
                                System.currentTimeMillis(),
                            )

                        val isSameBurst =
                            synchronized(
                                runtimeLock,
                            ) {
                                val sameCell =
                                    lastExposureBurstCell ==
                                        open.cell
                                val near =
                                    lastExposureBurstAtMs !=
                                        Long.MIN_VALUE &&
                                        abs(
                                            observedMs -
                                                lastExposureBurstAtMs,
                                        ) <=
                                        OFFER_BURST_WINDOW_MS

                                if (!(sameCell && near)) {
                                    lastExposureBurstCell =
                                        open.cell
                                    lastExposureBurstAtMs =
                                        observedMs
                                    false
                                } else {
                                    true
                                }
                            }

                        if (!isSameBurst) {
                            store.closeExposure(
                                journeyId =
                                    journeyId,
                                reason =
                                    "offer_observed",
                                nextOfferLocalId =
                                    offer.localId,
                                endedAt =
                                    offer.observedAt,
                            )
                            store.openExposure(
                                journeyId =
                                    journeyId,
                                cell =
                                    open.cell,
                                accuracyM =
                                    open.locationAccuracyM,
                                startedAt =
                                    offer.observedAt,
                            )
                        } else {
                            LocalLog.append(
                                app,
                                "OFERTA 0.17 no mesmo burst · exposição não recortada · ${offer.localId.take(8)}",
                            )
                        }
                    }
                }

                JourneySyncClient.flush(app)

                LocalLog.append(
                    app,
                    "OFERTA 0.17 pós-processada estado=OFFERED id=${offer.localId.take(8)} jornada=${journeyId.take(8)}",
                )
            },
            180L,
            TimeUnit.MILLISECONDS,
        )
    }

    @Synchronized
    fun markDoingRide(
        context: Context,
        localOfferId: String,
        source: String,
    ): RideOutcome? {
        val app = context.applicationContext
        hydrateRuntime(app)
        val journeyId =
            runtimeJourneyId ?: return null
        val store = LocalStore.get(app)

        if (
            !JourneyStateMachine.canStartRide(
                runtimeState,
                runtimeRide != null,
            )
        ) {
            return null
        }

        val offer =
            store.recentOffers(100)
                .firstOrNull {
                    it.localId == localOfferId
                } ?: return null

        if (
            offer.journeyId != journeyId
        ) {
            return null
        }

        store.currentDoingRide(
            journeyId,
        )
            ?.takeIf {
                it.localOfferId !=
                    localOfferId
            }
            ?.let {
                store.updateRideOutcome(
                    it.localOfferId,
                    journeyId,
                    RideOperationalStatus
                        .NOT_COMPLETED,
                    "replaced_by_new_ride",
                )
            }

        val outcome =
            store.updateRideOutcome(
                localOfferId,
                journeyId,
                RideOperationalStatus.DOING_RIDE,
                source,
            )

        synchronized(runtimeLock) {
            runtimeRide = outcome
        }
        resetOfferBurst()

        store.closeExposure(
            journeyId,
            "ride_started",
        )
        JourneyLocationService.dispatch(
            app,
            JourneyLocationService.ACTION_RIDE_STARTED,
        )
        JourneyBubbleController.refresh(app)
        JourneySyncClient.flush(app)

        return outcome
    }

    @Synchronized
    fun completeCurrentRide(
        context: Context,
        source: String = "bubble",
    ): RideOutcome? =
        finishCurrentRide(
            context,
            RideOperationalStatus.COMPLETED,
            source,
        )

    @Synchronized
    fun cancelCurrentRide(
        context: Context,
        source: String = "bubble",
    ): RideOutcome? =
        finishCurrentRide(
            context,
            RideOperationalStatus.CANCELLED,
            source,
        )

    private fun finishCurrentRide(
        context: Context,
        status: RideOperationalStatus,
        source: String,
    ): RideOutcome? {
        val app = context.applicationContext
        hydrateRuntime(app)
        val id =
            runtimeJourneyId ?: return null
        val store = LocalStore.get(app)

        val doing =
            runtimeRide
                ?: store.currentDoingRide(id)
                ?: return null

        val outcome =
            store.updateRideOutcome(
                doing.localOfferId,
                id,
                status,
                source,
                Instant.now().toString(),
            )

        synchronized(runtimeLock) {
            runtimeRide = null
        }
        resetOfferBurst()

        JourneyLocationService.dispatch(
            app,
            JourneyLocationService.ACTION_RIDE_FINISHED,
        )
        JourneyBubbleController.refresh(app)
        JourneySyncClient.flush(app)

        return outcome
    }

    @Synchronized
    fun correctRide(
        context: Context,
        localOfferId: String,
        status: RideOperationalStatus,
    ): RideOutcome? {
        val app = context.applicationContext
        hydrateRuntime(app)

        val store =
            LocalStore.get(app)

        val offer =
            store.recentOffers(500)
                .firstOrNull {
                    it.localId ==
                        localOfferId
                } ?: return null

        val journeyId =
            offer.journeyId
                ?.takeIf {
                    it.isNotBlank()
                } ?: return null

        val outcome =
            store.updateRideOutcome(
                localOfferId,
                journeyId,
                status,
                "history",
            )

        if (
            journeyId ==
            runtimeJourneyId
        ) {
            synchronized(runtimeLock) {
                runtimeRide =
                    if (
                        status ==
                        RideOperationalStatus.DOING_RIDE
                    ) {
                        outcome
                    } else {
                        runtimeRide
                            ?.takeIf {
                                it.localOfferId !=
                                    localOfferId
                            }
                    }
            }

            val action =
                if (
                    status ==
                    RideOperationalStatus.DOING_RIDE
                ) {
                    resetOfferBurst()
                    JourneyLocationService
                        .ACTION_RIDE_STARTED
                } else {
                    resetOfferBurst()
                    JourneyLocationService
                        .ACTION_RIDE_FINISHED
                }

            JourneyLocationService.dispatch(
                app,
                action,
            )
        }

        JourneyBubbleController.refresh(app)
        JourneySyncClient.flush(app)
        return outcome
    }
}
