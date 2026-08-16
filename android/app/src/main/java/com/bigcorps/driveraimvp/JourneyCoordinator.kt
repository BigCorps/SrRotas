package com.srrotas.app

import android.content.Context

object JourneyCoordinator {
    @Synchronized
    fun startJourney(context: Context, platform: String = "uber"): JourneyRecord {
        val appContext = context.applicationContext
        val repo = SettingsRepository(appContext)
        val store = LocalStore.get(appContext)
        val currentId = repo.currentJourneyId()
        if (currentId.isNotBlank()) {
            val existing = store.journey(currentId)
            if (existing != null && repo.isProjectionActive()) return existing
            if (existing != null && existing.endedAt == null) {
                store.endJourney(currentId, "recovered_before_new_session")?.let { BackendClient.endJourney(appContext, it) }
            }
            repo.clearCurrentJourney()
        }
        val journey = store.startJourney(platform)
        repo.setCurrentJourney(journey.id, journey.startedAt)
        BackendClient.startJourney(appContext, journey)
        LocalLog.append(appContext, "JORNADA iniciada id=${journey.id} plataforma=${journey.platform}")
        return journey
    }

    @Synchronized
    fun endJourney(context: Context, reason: String): JourneySummary? {
        val appContext = context.applicationContext
        val repo = SettingsRepository(appContext)
        val id = repo.currentJourneyId()
        if (id.isBlank()) return null
        val summary = LocalStore.get(appContext).endJourney(id, reason)
        repo.clearCurrentJourney()
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
}
