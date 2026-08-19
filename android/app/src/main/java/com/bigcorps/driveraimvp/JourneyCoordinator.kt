package com.srrotas.app

import android.content.Context
import android.content.Intent
import java.time.Instant

object JourneyCoordinator {
    @Synchronized
    fun startJourney(context: Context, platform: String = "uber"): JourneyRecord {
        val appContext = context.applicationContext
        val repo = SettingsRepository(appContext)
        val store = LocalStore.get(appContext)
        val currentId = repo.currentJourneyId()
        if (currentId.isNotBlank()) {
            val existing = store.journey(currentId)
            if (existing != null && repo.isProjectionActive()) {
                JourneyBubbleController.show(appContext)
                JourneyLocationRuntime.ensure(context)
                return existing
            }
            if (existing != null && existing.endedAt == null) {
                store.recordJourneyEvent(existing.id, "end", JourneyOperationalState.ENDED)
                store.endJourney(currentId, "recovered_before_new_session")?.let { BackendClient.endJourney(appContext, it) }
            }
            repo.clearCurrentJourney()
        }
        OfferDeduplicator.reset()
        val journey = store.startJourney(platform)
        repo.setCurrentJourney(journey.id, journey.startedAt)
        store.recordJourneyEvent(journey.id, "start", JourneyOperationalState.ACTIVE, journey.startedAt)
        BackendClient.startJourney(appContext, journey)
        JourneySyncClient.flush(appContext)
        JourneyBubbleController.show(appContext)
        JourneyLocationRuntime.ensure(context)
        LocalLog.append(appContext, "JORNADA 0.15 iniciada id=${journey.id} plataforma=${journey.platform}")
        return journey
    }

    @Synchronized
    fun pauseJourney(context: Context): Boolean {
        val app = context.applicationContext
        val id = SettingsRepository(app).currentJourneyId().takeIf { it.isNotBlank() } ?: return false
        val store = LocalStore.get(app)
        if (!JourneyStateMachine.canPause(store.currentJourneyState(id), store.currentDoingRide(id) != null)) return false
        store.closeExposure(id, "pause")
        store.recordJourneyEvent(id, "pause", JourneyOperationalState.PAUSED)
        JourneyLocationService.dispatch(app, JourneyLocationService.ACTION_PAUSE)
        JourneyBubbleController.refresh(app)
        JourneySyncClient.flush(app)
        LocalLog.append(app, "JORNADA pausada id=$id")
        return true
    }

    @Synchronized
    fun resumeJourney(context: Context): Boolean {
        val app = context.applicationContext
        val id = SettingsRepository(app).currentJourneyId().takeIf { it.isNotBlank() } ?: return false
        val store = LocalStore.get(app)
        if (!JourneyStateMachine.canResume(store.currentJourneyState(id))) return false
        store.recordJourneyEvent(id, "resume", JourneyOperationalState.ACTIVE)
        JourneyLocationRuntime.ensure(context)
        JourneyLocationService.dispatch(app, JourneyLocationService.ACTION_RESUME)
        JourneyBubbleController.refresh(app)
        JourneySyncClient.flush(app)
        LocalLog.append(app, "JORNADA retomada id=$id")
        return true
    }

    @Synchronized
    fun endJourney(context: Context, reason: String): JourneySummary? {
        val appContext = context.applicationContext
        val repo = SettingsRepository(appContext)
        val id = repo.currentJourneyId()
        if (id.isBlank()) return null
        val store = LocalStore.get(appContext)
        store.currentDoingRide(id)?.let { doing ->
            store.updateRideOutcome(doing.localOfferId, id, RideOperationalStatus.NOT_COMPLETED, "journey_end_auto")
        }
        store.closeExposure(id, "journey_end")
        JourneyLocationService.dispatch(appContext, JourneyLocationService.ACTION_STOP)
        // Encerra a captura antes de gravar ENDED para permitir que o serviço
        // descarregue qualquer card estabilizado que já estava pronto.
        runCatching { appContext.stopService(Intent(appContext, MediaProjectionOcrService::class.java)) }
        repo.setProjectionActive(false)
        if (store.currentJourneyState(id) != JourneyOperationalState.ENDED) store.recordJourneyEvent(id, "end", JourneyOperationalState.ENDED)
        JourneyBubbleController.hide(appContext)
        val summary = store.endJourney(id, reason)
        repo.clearCurrentJourney()
        OfferDeduplicator.reset()
        JourneySyncClient.flush(appContext)
        if (summary != null) {
            BackendClient.endJourney(appContext, summary)
            LocalLog.append(appContext, "JORNADA encerrada id=$id motivo=$reason ofertas=${summary.offerCount}")
        }
        return summary
    }

    fun currentSummary(context: Context): JourneySummary? {
        val id = SettingsRepository(context).currentJourneyId()
        return if (id.isBlank()) null else LocalStore.get(context).journeySummary(id)
    }

    fun currentOperationalState(context: Context): JourneyOperationalState {
        val id = SettingsRepository(context).currentJourneyId().takeIf { it.isNotBlank() } ?: return JourneyOperationalState.NOT_STARTED
        return LocalStore.get(context).currentJourneyState(id)
    }

    fun snapshot(context: Context): JourneyOperationalSnapshot {
        val app = context.applicationContext
        val id = SettingsRepository(app).currentJourneyId().takeIf { it.isNotBlank() }
        val store = LocalStore.get(app)
        val state = id?.let(store::currentJourneyState) ?: JourneyOperationalState.NOT_STARTED
        return JourneyOperationalSnapshot(id, state, id?.let(store::currentDoingRide), store.recentOffers(1).firstOrNull())
    }

    fun canObserveOffers(context: Context): Boolean {
        val snapshot = snapshot(context)
        return JourneyStateMachine.canObserveOffers(snapshot.journeyState, snapshot.isDoingRide)
    }

    @Synchronized
    fun onOfferObserved(context: Context, offer: RideOffer) {
        val app = context.applicationContext
        val journeyId = offer.journeyId?.takeIf { it.isNotBlank() } ?: return
        val store = LocalStore.get(app)
        store.ensureOfferedOutcome(offer)
        if (store.currentJourneyState(journeyId) == JourneyOperationalState.ACTIVE && store.currentDoingRide(journeyId) == null) {
            store.currentOpenExposure(journeyId)?.let { open ->
                store.closeExposure(journeyId, "offer_observed", offer.localId)
                store.openExposure(journeyId, open.cell, open.locationAccuracyM)
            }
        }
        JourneyBubbleController.refresh(app)
        JourneySyncClient.flush(app)
        LocalLog.append(app, "OFERTA 0.15 registrada estado=OFFERED id=${offer.localId.take(8)} jornada=${journeyId.take(8)}")
    }

    @Synchronized
    fun markDoingRide(context: Context, localOfferId: String, source: String): RideOutcome? {
        val app = context.applicationContext
        val repo = SettingsRepository(app)
        val journeyId = repo.currentJourneyId().takeIf { it.isNotBlank() } ?: return null
        val store = LocalStore.get(app)
        if (!JourneyStateMachine.canStartRide(store.currentJourneyState(journeyId), store.currentDoingRide(journeyId) != null)) return null
        val offer = store.recentOffers(100).firstOrNull { it.localId == localOfferId } ?: return null
        if (offer.journeyId != journeyId) return null
        store.currentDoingRide(journeyId)?.takeIf { it.localOfferId != localOfferId }?.let {
            store.updateRideOutcome(it.localOfferId, journeyId, RideOperationalStatus.NOT_COMPLETED, "replaced_by_new_ride")
        }
        val outcome = store.updateRideOutcome(localOfferId, journeyId, RideOperationalStatus.DOING_RIDE, source)
        store.closeExposure(journeyId, "ride_started")
        JourneyLocationService.dispatch(app, JourneyLocationService.ACTION_RIDE_STARTED)
        JourneyBubbleController.refresh(app)
        JourneySyncClient.flush(app)
        return outcome
    }

    @Synchronized
    fun completeCurrentRide(context: Context, source: String = "bubble"): RideOutcome? = finishCurrentRide(context, RideOperationalStatus.COMPLETED, source)

    @Synchronized
    fun cancelCurrentRide(context: Context, source: String = "bubble"): RideOutcome? = finishCurrentRide(context, RideOperationalStatus.CANCELLED, source)

    private fun finishCurrentRide(context: Context, status: RideOperationalStatus, source: String): RideOutcome? {
        val app = context.applicationContext
        val id = SettingsRepository(app).currentJourneyId().takeIf { it.isNotBlank() } ?: return null
        val store = LocalStore.get(app)
        val doing = store.currentDoingRide(id) ?: return null
        val outcome = store.updateRideOutcome(doing.localOfferId, id, status, source, Instant.now().toString())
        JourneyLocationService.dispatch(app, JourneyLocationService.ACTION_RIDE_FINISHED)
        JourneyBubbleController.refresh(app)
        JourneySyncClient.flush(app)
        return outcome
    }

    @Synchronized
    fun correctRide(context: Context, localOfferId: String, status: RideOperationalStatus): RideOutcome? {
        val app = context.applicationContext
        val store = LocalStore.get(app)
        val offer = store.recentOffers(500).firstOrNull { it.localId == localOfferId } ?: return null
        val journeyId = offer.journeyId?.takeIf { it.isNotBlank() } ?: return null
        val outcome = store.updateRideOutcome(localOfferId, journeyId, status, "history")
        val currentJourney = SettingsRepository(app).currentJourneyId()
        if (journeyId == currentJourney) JourneyLocationService.dispatch(app, JourneyLocationService.ACTION_RIDE_FINISHED)
        JourneyBubbleController.refresh(app)
        JourneySyncClient.flush(app)
        return outcome
    }
}
