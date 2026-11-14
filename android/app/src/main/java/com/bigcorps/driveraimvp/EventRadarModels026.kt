package com.srrotas.app

data class EventRadarOpportunity026(
    val id: String,
    val type: String,
    val name: String,
    val venueName: String?,
    val address: String?,
    val startsAt: String,
    val expectedEndAt: String,
    val egressStartAt: String,
    val egressEndAt: String,
    val distanceKm: Double,
    val source: String,
    val confidence: Double,
    val sourceUrl: String?,
    val lat: Double? = null,
    val lng: Double? = null,
    val status: String? = null,
    val reviewStatus: String? = null,
)

data class EventRadarResult026(
    val opportunities: List<EventRadarOpportunity026>,
    val sourceStatus: String,
    val refreshedAt: String?,
    val receivedAtMs: Long = 0L,
)
