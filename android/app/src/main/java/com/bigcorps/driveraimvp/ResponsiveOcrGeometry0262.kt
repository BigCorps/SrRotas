package com.srrotas.app

import kotlin.math.roundToInt

/**
 * Geometria relativa ao frame para celulares, tablets, rotação e multi-window.
 *
 * O 0.26.1 usava mínimos fixos que funcionavam bem no telefone de referência,
 * mas podiam ficar estreitos quando o mesmo card era renderizado em um frame
 * muito maior. Aqui o alcance cresce proporcionalmente e continua limitado para
 * não misturar Waze/Maps com a oferta.
 */
object ResponsiveOcrGeometry0262 {
    fun horizontalRadius(frameWidth: Int, strict: Boolean): Int {
        if (frameWidth <= 0) return 220
        val ratio = if (strict) 0.28 else 0.48
        val min = (frameWidth * 0.18).roundToInt().coerceAtLeast(180)
        val max = (frameWidth * if (strict) 0.36 else 0.58).roundToInt().coerceAtLeast(min)
        return (frameWidth * ratio).roundToInt().coerceIn(min, max)
    }

    fun paneRadius(frameWidth: Int, strict: Boolean): Int {
        if (frameWidth <= 0) return 230
        val ratio = if (strict) 0.30 else 0.50
        val min = (frameWidth * 0.19).roundToInt().coerceAtLeast(190)
        val max = (frameWidth * if (strict) 0.40 else 0.60).roundToInt().coerceAtLeast(min)
        return (frameWidth * ratio).roundToInt().coerceIn(min, max)
    }

    fun verticalRadius(frameHeight: Int): Int {
        if (frameHeight <= 0) return 300
        return (frameHeight * 0.41).roundToInt()
            .coerceIn(260, (frameHeight * 0.48).roundToInt().coerceAtLeast(260))
    }

    fun paneExtraY(frameHeight: Int): Int {
        if (frameHeight <= 0) return 260
        return (frameHeight * 0.32).roundToInt()
            .coerceIn(220, (frameHeight * 0.42).roundToInt().coerceAtLeast(220))
    }
}
