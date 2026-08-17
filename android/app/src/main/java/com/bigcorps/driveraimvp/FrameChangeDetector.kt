package com.srrotas.app

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.abs

/**
 * Filtro leve e conservador. Só pula frames praticamente idênticos.
 * A deduplicação de ofertas continua sendo a proteção principal.
 */
class FrameChangeDetector(
    private val columns: Int = 12,
    private val rows: Int = 24,
    private val minAverageDelta: Double = 2.2,
) {
    private var previous: IntArray? = null

    fun shouldProcess(bitmap: Bitmap): Boolean {
        val current = signature(bitmap)
        val old = previous
        previous = current
        if (old == null || old.size != current.size) return true

        var totalDelta = 0L
        for (i in current.indices) totalDelta += abs(current[i] - old[i]).toLong()
        val average = totalDelta.toDouble() / current.size
        return average >= minAverageDelta
    }

    fun reset() { previous = null }

    private fun signature(bitmap: Bitmap): IntArray {
        val values = IntArray(columns * rows)
        var index = 0
        for (row in 0 until rows) {
            val y = ((row + 0.5) * bitmap.height / rows).toInt().coerceIn(0, bitmap.height - 1)
            for (column in 0 until columns) {
                val x = ((column + 0.5) * bitmap.width / columns).toInt().coerceIn(0, bitmap.width - 1)
                val color = bitmap.getPixel(x, y)
                val gray = (Color.red(color) * 30 + Color.green(color) * 59 + Color.blue(color) * 11) / 100
                values[index++] = gray
            }
        }
        return values
    }
}
