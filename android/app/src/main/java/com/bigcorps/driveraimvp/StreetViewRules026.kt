package com.srrotas.app

object StreetViewRules026 {
    const val MIN_CONTEXT_CONFIDENCE = 0.55

    fun eligible(lat: Double?, lng: Double?, confidence: Double): Boolean {
        if (lat == null || lng == null) return false
        if (lat !in -90.0..90.0 || lng !in -180.0..180.0) return false
        if (lat == 0.0 && lng == 0.0) return false
        return confidence >= MIN_CONTEXT_CONFIDENCE
    }

    fun mapsUrl(lat: Double, lng: Double): String =
        "https://www.google.com/maps/@?api=1&map_action=pano&viewpoint=$lat%2C$lng"
}
