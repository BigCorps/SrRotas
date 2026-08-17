package com.srrotas.app

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.abs

/**
 * Detector barato de mudança visual.
 *
 * A 0.5.1 ficou mais sensível a mudanças localizadas do card sem depender apenas
 * da média da tela inteira. Isso ajuda a reagir a ofertas curtas sem fazer OCR
 * repetido em frames realmente idênticos.
 */
class FrameChangeDetector(
    private val columns: Int = 16,
    private val rows: Int = 32,
    private val minAverageDelta: Double = 1.0,
    private val changedCellDelta: Int = 9,
    private val minChangedCells: Int = 6,
) {
    private var previous: IntArray? = null

    fun shouldProcess(bitmap: Bitmap): Boolean {
        val current = signature(bitmap)
        val old = previous
        previous = current
        if (old == null || old.size != current.size) return true

        var totalDelta = 0L
        var changedCells = 0
        for (i in current.indices) {
            val delta = abs(current[i] - old[i])
            totalDelta += delta.toLong()
            if (delta >= changedCellDelta) changedCells++
        }
        val average = totalDelta.toDouble() / current.size
        return average >= minAverageDelta || changedCells >= minChangedCells
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
