package com.srrotas.app

import android.content.Context

/** Cola operacional do Radar Contextual. */
object RadarContextualIntegrationV1 {
    private var lastJourneySignature: String? = null

    private fun journeySignature(context: Context): String {
        val snapshot = JourneyCoordinator.snapshot(context)
        return listOf(snapshot.journeyId ?: "", snapshot.journeyState.name,
            snapshot.currentRide?.localOfferId ?: "").joinToString("|")
    }
    fun syncRuntime(context: Context) {
        RadarContextualFlagsV1.migrateField93IfNeeded(context)
        if (RadarContextualFlagsV1.runtimeEnabled(context)) {
            DestinationRadarRuntimeV1.start(
                context.applicationContext,
                DestinationRadarRuntimeBridgeV1,
            )
        } else {
            DestinationRadarRuntimeV1.stop()
            DestinationRadarAssistantRendererV1.hide()
        }
    }

    fun onOperationalStateChanged(context: Context) {
        // Capture broadcasts are frequent: only an actual journey/ride transition
        // invalidates in-flight HTTP and the visible assistant.
        val currentSignature = journeySignature(context)
        if (currentSignature != lastJourneySignature) {
            lastJourneySignature = currentSignature
            DestinationRadarRuntimeV1.invalidatePending()
            DestinationRadarAssistantRendererV1.hide()
        }
        syncRuntime(context)
        if (RadarContextualFlagsV1.runtimeEnabled(context)) {
            DestinationRadarRuntimeV1.refreshNow()
        }
    }

    fun assistantAllowed(context: Context): Boolean =
        RadarContextualFlagsV1.runtimeEnabled(context) &&
            RadarContextualFlagsV1.assistantEnabled(context)

    fun rollback(context: Context) {
        RadarContextualFlagsV1.disableAll(context)
        DestinationRadarRuntimeV1.stop()
        DestinationRadarAssistantRendererV1.hide()
    }
}
