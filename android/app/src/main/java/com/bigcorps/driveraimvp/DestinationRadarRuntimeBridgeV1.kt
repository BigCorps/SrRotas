package com.srrotas.app

/**
 * Liga o runtime ao renderer já existente do HUD sem criar outro WindowManager.
 */
object DestinationRadarRuntimeBridgeV1 : DestinationRadarRuntimeV1.Listener {
    override fun onAssistantSignal(
        result: RadarContextualResultV1,
        signal: DestinationRadarAssistantBridgeV1.Signal,
    ) {
        val context = DestinationRadarRuntimeV1.applicationContext() ?: return
        if (!RadarContextualIntegrationV1.assistantAllowed(context)) {
            RadarContextualDiagnosticV1.assistantRenderBlocked("stage_assistant_disabled")
            return
        }
        val spec = RadarDestinationContextV1.current(context)
        if (spec == null) {
            RadarContextualDiagnosticV1.assistantRenderBlocked("spec_missing_before_render")
            return
        }
        DestinationRadarAssistantRendererV1.show(context, spec, result, signal)
    }

    override fun onRadarUnavailable(reason: String) {
        DestinationRadarRuntimeV1.applicationContext()?.let {
            LocalLog.append(it, "RADAR contextual indisponível: $reason")
        }
    }
}
