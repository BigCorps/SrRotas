package com.srrotas.app

/**
 * Regras puras do watchdog da captura 0.25.0.
 *
 * Não decide se uma oferta é válida e não toca no estado da jornada. Serve apenas
 * para detectar dois travamentos técnicos recuperáveis enquanto a MediaProjection
 * continua autorizada: ausência de frames e OCR preso por tempo anormal.
 */
internal object CaptureHealthPolicy025 {
    const val CHECK_INTERVAL_MS = 4_000L
    const val NO_FRAME_TIMEOUT_MS = 12_000L
    const val OCR_STALL_TIMEOUT_MS = 18_000L
    const val RECOVERY_COOLDOWN_MS = 8_000L

    enum class Action {
        NONE,
        REARM_CAPTURE_SURFACE,
        RESET_OCR_PIPELINE,
    }

    data class Snapshot(
        val nowMs: Long,
        val projectionActive: Boolean,
        val journeyOwned: Boolean,
        val screenInteractive: Boolean,
        val lastImageSeenAtMs: Long,
        val ocrBusy: Boolean,
        val ocrStartedAtMs: Long,
        val lastRecoveryAtMs: Long,
    )

    fun decide(snapshot: Snapshot): Action {
        if (!snapshot.projectionActive || !snapshot.journeyOwned || !snapshot.screenInteractive) {
            return Action.NONE
        }
        if (snapshot.lastRecoveryAtMs > 0L &&
            snapshot.nowMs - snapshot.lastRecoveryAtMs < RECOVERY_COOLDOWN_MS
        ) {
            return Action.NONE
        }

        if (snapshot.ocrBusy && snapshot.ocrStartedAtMs > 0L &&
            snapshot.nowMs - snapshot.ocrStartedAtMs >= OCR_STALL_TIMEOUT_MS
        ) {
            return Action.RESET_OCR_PIPELINE
        }

        if (snapshot.lastImageSeenAtMs > 0L &&
            snapshot.nowMs - snapshot.lastImageSeenAtMs >= NO_FRAME_TIMEOUT_MS
        ) {
            return Action.REARM_CAPTURE_SURFACE
        }

        return Action.NONE
    }
}
