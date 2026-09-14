package com.srrotas.app

import android.content.Context
import android.os.SystemClock

/** Rate-limit para marcadores automáticos de falha sem gerar centenas de reports. */
object ReaderAutoFailure027033 {
    private const val GLOBAL_COOLDOWN_MS = 8_000L
    private const val SAME_REASON_COOLDOWN_MS = 30_000L

    private var lastAnyAt = 0L
    private val lastReasonAt = linkedMapOf<String, Long>()

    @Synchronized
    fun mark(context: Context, reason: String): Boolean {
        val clean = reason.trim().take(90).ifBlank { "unknown" }
        val now = SystemClock.elapsedRealtime()
        val previous = lastReasonAt[clean] ?: 0L
        if (now - lastAnyAt < GLOBAL_COOLDOWN_MS || now - previous < SAME_REASON_COOLDOWN_MS) return false

        lastAnyAt = now
        lastReasonAt[clean] = now
        if (lastReasonAt.size > 32) {
            val oldest = lastReasonAt.minByOrNull { it.value }?.key
            if (oldest != null) lastReasonAt.remove(oldest)
        }
        val report = FailureReportStore0270.mark(context, "auto:$clean")
        LocalLog.append(
            context,
            "Falha automática RC3.3 #${report.numberInJourney} · $clean",
        )
        return true
    }

    @Synchronized
    fun reset() {
        lastAnyAt = 0L
        lastReasonAt.clear()
    }
}
