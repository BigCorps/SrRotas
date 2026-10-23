package com.srrotas.app

/** Amostra fixa; não depende de Android, cor do estilo ou OCR. */
internal object RadarMapBitmapHealthV1 {
    fun nonblank(width: Int, height: Int, pixel: (Int, Int) -> Int): Boolean {
        if (width <= 0 || height <= 0) return false
        val buckets = mutableMapOf<Int, Int>()
        var opaque = 0
        var min = 255
        var max = 0
        for (y in 0 until 12) for (x in 0 until 12) {
            val c = pixel(((x + 0.5) * width / 12).toInt().coerceAtMost(width - 1),
                ((y + 0.5) * height / 12).toInt().coerceAtMost(height - 1))
            if ((c ushr 24) < 128) continue
            opaque++
            val r = c ushr 16 and 255; val g = c ushr 8 and 255; val b = c and 255
            val light = (r * 30 + g * 59 + b * 11) / 100
            min = minOf(min, light); max = maxOf(max, light)
            val bucket = (r / 8 shl 10) or (g / 8 shl 5) or (b / 8)
            buckets[bucket] = (buckets[bucket] ?: 0) + 1
        }
        return opaque >= 130 && max - min >= 12 && buckets.size >= 4 &&
            (buckets.values.maxOrNull() ?: 144) * 100 < opaque * 94
    }
}
