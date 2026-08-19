package com.srrotas.app

import android.content.Context

class JourneyUiPreferences(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("sr_rotas_journey_ui", Context.MODE_PRIVATE)

    fun position(): Pair<Int, Int> = prefs.getInt("bubble_x", 18) to prefs.getInt("bubble_y", 260)

    fun savePosition(x: Int, y: Int) {
        prefs.edit().putInt("bubble_x", x.coerceAtLeast(0)).putInt("bubble_y", y.coerceAtLeast(0)).apply()
    }

    fun sizeDp(): Int = prefs.getInt("bubble_size_dp", 58).coerceIn(46, 76)

    fun cycleSize(): Int {
        val next = when (sizeDp()) {
            in 0..52 -> 58
            in 53..62 -> 70
            else -> 48
        }
        prefs.edit().putInt("bubble_size_dp", next).apply()
        return next
    }

    fun opacityPercent(): Int = prefs.getInt("bubble_opacity", 90).coerceIn(60, 100)

    fun cycleOpacity(): Int {
        val next = when (opacityPercent()) {
            in 0..74 -> 85
            in 75..92 -> 100
            else -> 70
        }
        prefs.edit().putInt("bubble_opacity", next).apply()
        return next
    }
}
