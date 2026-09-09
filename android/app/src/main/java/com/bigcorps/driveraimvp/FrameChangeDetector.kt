package com.srrotas.app

import android.graphics.Bitmap
import android.graphics.Color
import android.os.SystemClock
import kotlin.math.abs

/**
 * Detector barato de mudança visual.
 *
 * 0.27.0-alpha1 preserva exatamente a decisão de processamento da 0.26.5,
 * porém passa a expor o motivo técnico da decisão para diagnóstico de campo.
 *
 * NÃO altera thresholds.
 */
class FrameChangeDetector(
    private val columns: Int = 16,
    private val rows: Int = 32,
    private val minAverageDelta: Double = 1.0,
    private val changedCellDelta: Int = 9,
    private val minChangedCells: Int = 6,
    private val forceAfterMs: Long = HudReliabilityRules0261.FORCE_OCR_AFTER_MS,
) {
    enum class Reason {
        FIRST,
        VISUAL_CHANGE,
        PERIODIC_RECOVERY,
        UNCHANGED,
    }

    data class Decision(
        val process: Boolean,
        val reason: Reason,
        val averageDelta: Double,
        val changedCells: Int,
    )

    private var previous: IntArray? = null
    private var lastAcceptedAtMs: Long = 0L

    fun shouldProcess(
        bitmap: Bitmap,
        nowMs: Long = SystemClock.elapsedRealtime(),
    ): Boolean = evaluate(bitmap, nowMs).process

    fun evaluate(
        bitmap: Bitmap,
        nowMs: Long = SystemClock.elapsedRealtime(),
    ): Decision {
        val current = signature(bitmap)
        val old = previous
        previous = current

        if (old == null || old.size != current.size) {
            lastAcceptedAtMs = nowMs
            return Decision(
                process = true,
                reason = Reason.FIRST,
                averageDelta = 0.0,
                changedCells = current.size,
            )
        }

        var totalDelta = 0L
        var changedCells = 0
        for (i in current.indices) {
            val delta = abs(current[i] - old[i])
            totalDelta += delta.toLong()
            if (delta >= changedCellDelta) changedCells++
        }
        val average = totalDelta.toDouble() / current.size
        val visualChange =
            average >= minAverageDelta || changedCells >= minChangedCells
        val periodicRecovery = HudReliabilityRules0261.forceOcrDue(
            lastAcceptedAtMs,
            nowMs,
            forceAfterMs,
        )

        return when {
            visualChange -> {
                lastAcceptedAtMs = nowMs
                Decision(
                    process = true,
                    reason = Reason.VISUAL_CHANGE,
                    averageDelta = average,
                    changedCells = changedCells,
                )
            }
            periodicRecovery -> {
                lastAcceptedAtMs = nowMs
                Decision(
                    process = true,
                    reason = Reason.PERIODIC_RECOVERY,
                    averageDelta = average,
                    changedCells = changedCells,
                )
            }
            else -> Decision(
                process = false,
                reason = Reason.UNCHANGED,
                averageDelta = average,
                changedCells = changedCells,
            )
        }
    }

    fun reset() {
        previous = null
        lastAcceptedAtMs = 0L
    }

    private fun signature(bitmap: Bitmap): IntArray {
        val values = IntArray(columns * rows)
        var index = 0
        for (row in 0 until rows) {
            val y = ((row + 0.5) * bitmap.height / rows)
                .toInt()
                .coerceIn(0, bitmap.height - 1)
            for (column in 0 until columns) {
                val x = ((column + 0.5) * bitmap.width / columns)
                    .toInt()
                    .coerceIn(0, bitmap.width - 1)
                val color = bitmap.getPixel(x, y)
                val gray =
                    (Color.red(color) * 30 +
                        Color.green(color) * 59 +
                        Color.blue(color) * 11) / 100
                values[index++] = gray
            }
        }
        return values
    }
}
