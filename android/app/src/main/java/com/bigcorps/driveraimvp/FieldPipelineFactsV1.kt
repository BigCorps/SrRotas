package com.srrotas.app

import java.time.Instant

/** Facts only: never changes eligibility, parsing or geocoding. */
object FieldPipelineFactsV1 {
    fun coordinatesValid(lat: Double?, lng: Double?) =
        lat != null && lng != null && lat.isFinite() && lng.isFinite() && lat in -90.0..90.0 && lng in -180.0..180.0
    fun etaDeltaSeconds(eta: String?, now: Long): Long? =
        runCatching { (Instant.parse(eta).toEpochMilli() - now) / 1000L }.getOrNull()
    fun geocodeStage(status: String?) = when (status) {
        "resolved" -> "GEOCODE_RESOLVED"
        "partial" -> "GEOCODE_PARTIAL"
        else -> "GEOCODE_MISSING"
    }
    fun geocodeReason(status: String?) = when(status) {
        "resolved", "partial", "pending", "failed", "unresolved" -> status!!
        else -> "unknown"
    }
    fun retainedEpisodeIndices(updatedAt: List<Long>, now: Long, limit: Int, maxAge: Long): Set<Int> =
        updatedAt.indices.filter { now - updatedAt[it] in 0..maxAge }.takeLast(limit.coerceAtLeast(0)).toSet()
    // A marker over a blank surface can satisfy the diversity heuristic.
    fun cartographyProvenByNonblankBitmap() = false
    fun specBlock(offer: RideOffer?): String? = when {
        offer == null -> "source_offer_missing"
        offer.context == null -> "context_missing"
        !coordinatesValid(offer.context.destinationLat, offer.context.destinationLng) -> "destination_coordinates_missing_or_invalid"
        etaDeltaSeconds(offer.context.estimatedArrivalAt, 0L) == null -> "eta_missing_or_invalid"
        else -> null
    }
}
