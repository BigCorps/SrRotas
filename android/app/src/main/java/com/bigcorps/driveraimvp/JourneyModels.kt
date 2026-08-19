package com.srrotas.app

import java.util.UUID

enum class JourneyOperationalState {
    NOT_STARTED,
    ACTIVE,
    PAUSED,
    ENDED,
}

enum class RideOperationalStatus {
    OFFERED,
    DOING_RIDE,
    COMPLETED,
    NOT_COMPLETED,
    CANCELLED,
}

data class JourneyStateEvent(
    val id: String = UUID.randomUUID().toString(),
    val journeyId: String,
    val eventType: String,
    val state: JourneyOperationalState,
    val occurredAt: String,
)

data class RideOutcome(
    val localOfferId: String,
    val journeyId: String,
    val status: RideOperationalStatus,
    val startedAt: String? = null,
    val completedAt: String? = null,
    val cancelledAt: String? = null,
    val correctedAt: String? = null,
    val source: String,
    val revision: Int = 1,
)

data class RegionalExposure(
    val id: String = UUID.randomUUID().toString(),
    val journeyId: String,
    val cell: String,
    val startedAt: String,
    val endedAt: String? = null,
    val durationSeconds: Long? = null,
    val closeReason: String? = null,
    val nextOfferLocalId: String? = null,
    val locationAccuracyM: Double? = null,
)

data class JourneyOperationalSnapshot(
    val journeyId: String?,
    val journeyState: JourneyOperationalState,
    val currentRide: RideOutcome?,
    val latestOffer: RideOffer?,
) {
    val isDoingRide: Boolean get() = currentRide?.status == RideOperationalStatus.DOING_RIDE
}
