package com.srrotas.app

import android.content.Context
import android.location.Location
import java.time.Instant

class RegionalExposureTracker(context: Context) {
    private val app = context.applicationContext
    private val repo = SettingsRepository(app)
    private val store = LocalStore.get(app)
    private var lastCell: String? = null
    private var lastAccuracyM: Double? = null

    fun onLocation(location: Location) {
        val journeyId = repo.currentJourneyId().takeIf { it.isNotBlank() } ?: return
        if (!eligible(journeyId)) {
            close("not_available")
            return
        }
        val cell = OfferContextEngine.geoCell(location.latitude, location.longitude) ?: return
        val accuracy = location.accuracy.toDouble().takeIf { it >= 0.0 }
        lastAccuracyM = accuracy
        val open = store.currentOpenExposure(journeyId)
        lastCell = cell
        if (open == null) {
            store.openExposure(journeyId, cell, accuracy)
            return
        }
        if (open.cell != cell) {
            store.closeExposure(journeyId, "cell_changed")
            JourneySyncClient.flush(app)
            store.openExposure(journeyId, cell, accuracy)
        }
    }

    fun onOfferObserved(localOfferId: String) {
        val journeyId = repo.currentJourneyId().takeIf { it.isNotBlank() } ?: return
        if (!eligible(journeyId)) return
        val open = store.currentOpenExposure(journeyId)
        if (open != null) {
            store.closeExposure(journeyId, "offer_observed", localOfferId)
            JourneySyncClient.flush(app)
            store.openExposure(journeyId, open.cell, lastAccuracyM ?: open.locationAccuracyM, Instant.now().toString())
        }
    }

    fun onRideStarted() = close("ride_started")
    fun onPause() = close("pause")
    fun onEnd() = close("journey_end")
    fun onLocationUnavailable() = close("location_unavailable")

    fun onRideFinished() {
        val journeyId = repo.currentJourneyId().takeIf { it.isNotBlank() } ?: return
        if (!eligible(journeyId)) return
        val cell = lastCell ?: return
        if (store.currentOpenExposure(journeyId) == null) store.openExposure(journeyId, cell, lastAccuracyM)
    }

    private fun close(reason: String) {
        val journeyId = repo.currentJourneyId().takeIf { it.isNotBlank() } ?: return
        if (store.closeExposure(journeyId, reason) != null) JourneySyncClient.flush(app)
    }

    private fun eligible(journeyId: String): Boolean =
        store.currentJourneyState(journeyId) == JourneyOperationalState.ACTIVE && store.currentDoingRide(journeyId) == null
}
