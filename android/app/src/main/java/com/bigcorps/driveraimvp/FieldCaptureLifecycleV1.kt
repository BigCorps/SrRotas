package com.srrotas.app

internal object FieldCaptureLifecycleV1 {
    fun shouldAcknowledgeForeground(consentAuthorized: Boolean, projectionExists: Boolean) =
        consentAuthorized && !projectionExists
    fun reuseExisting(forceFresh: Boolean, exists: Boolean, sameJourney: Boolean) =
        !forceFresh && exists && sameJourney
    fun freshAuthorizationValid(journeyOpen: Boolean, resultAuthorized: Boolean, hasResultData: Boolean) =
        journeyOpen && resultAuthorized && hasResultData
    fun shouldStopAfterRejectedFresh(rejected: Boolean, projectionExists: Boolean) =
        rejected && !projectionExists
    fun currentCallback(callbackProjection: Any, currentProjection: Any?) =
        callbackProjection === currentProjection
}

/** Somente counters locais em memória; nunca guarda token, imagem ou conteúdo de tela. */
internal object FieldCaptureRecoveryDiagnosticV1 {
    private val counts = linkedMapOf(
        "technical_recovery_requested" to 0, "technical_recovery_health_restored" to 0,
        "fresh_projection_requested" to 0, "fresh_projection_authorized" to 0,
        "fresh_projection_replaced" to 0, "fresh_projection_started" to 0,
        "stale_projection_callback_ignored" to 0, "fresh_projection_rejected_no_session" to 0,
    )
    private var awaitingTechnicalHealth = false
    @Synchronized fun record(event: String) {
        if (event !in counts) return
        counts[event] = counts.getValue(event) + 1
        if (event == "technical_recovery_requested") awaitingTechnicalHealth = true
        if (event == "fresh_projection_requested") awaitingTechnicalHealth = false
    }
    @Synchronized fun health(healthy: Boolean) {
        if (healthy && awaitingTechnicalHealth) {
            awaitingTechnicalHealth = false
            record("technical_recovery_health_restored")
        }
    }
    @Synchronized fun snapshot(): Map<String, Int> = counts.toMap()
}
