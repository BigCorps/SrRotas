package com.srrotas.app

/**
 * Regras puras do watchdog de captura.
 *
 * 0.26.3: o watchdog passa a reconhecer também morte da thread de captura e
 * falta de progresso do OCR mesmo quando frames continuam chegando.
 */
internal object CaptureHealthPolicy025 {
    const val CHECK_INTERVAL_MS = 4_000L
    const val NO_FRAME_TIMEOUT_MS = 12_000L
    const val OCR_STALL_TIMEOUT_MS = 18_000L
    const val OCR_NO_PROGRESS_TIMEOUT_MS = 24_000L
    const val RECOVERY_COOLDOWN_MS = 8_000L

    enum class Action {
        NONE,
        REARM_CAPTURE_SURFACE,
        RESET_OCR_PIPELINE,
        REBUILD_CAPTURE_WORKER,
    }

    data class Snapshot(
        val nowMs: Long,
        val projectionActive: Boolean,
        val journeyOwned: Boolean,
        val screenInteractive: Boolean,
        val workerAlive: Boolean,
        val lastImageSeenAtMs: Long,
        val lastOcrCompletedAtMs: Long,
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

        if (!snapshot.workerAlive) {
            return Action.REBUILD_CAPTURE_WORKER
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

        if (snapshot.lastImageSeenAtMs > 0L &&
            snapshot.nowMs - snapshot.lastImageSeenAtMs < NO_FRAME_TIMEOUT_MS &&
            snapshot.lastOcrCompletedAtMs > 0L &&
            snapshot.nowMs - snapshot.lastOcrCompletedAtMs >= OCR_NO_PROGRESS_TIMEOUT_MS
        ) {
            return Action.RESET_OCR_PIPELINE
        }

        return Action.NONE
    }
}
