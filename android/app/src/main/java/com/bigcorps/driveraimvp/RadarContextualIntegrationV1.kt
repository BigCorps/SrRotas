package com.srrotas.app

import android.content.Context

/** Cola operacional do Radar Contextual. */
object RadarContextualIntegrationV1 {
    fun syncRuntime(context: Context) {
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
