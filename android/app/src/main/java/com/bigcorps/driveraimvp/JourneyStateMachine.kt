package com.srrotas.app

object JourneyStateMachine {
    fun canPause(state: JourneyOperationalState, doingRide: Boolean): Boolean =
        state == JourneyOperationalState.ACTIVE && !doingRide

    fun canResume(state: JourneyOperationalState): Boolean = state == JourneyOperationalState.PAUSED

    fun canStartRide(state: JourneyOperationalState, doingRide: Boolean): Boolean =
        state == JourneyOperationalState.ACTIVE && !doingRide

    enum class RideSelection { REJECT, SAME, START, REPLACE }

    fun explicitRideSelection(
        state: JourneyOperationalState, sameJourney: Boolean,
        currentId: String?, selectedId: String, selectedStatus: RideOperationalStatus?,
        allowReplace: Boolean = false,
    ): RideSelection = when {
        state != JourneyOperationalState.ACTIVE || !sameJourney -> RideSelection.REJECT
        currentId == selectedId -> RideSelection.SAME
        selectedStatus != null && selectedStatus != RideOperationalStatus.OFFERED -> RideSelection.REJECT
        currentId == null -> RideSelection.START
        allowReplace -> RideSelection.REPLACE
        else -> RideSelection.REJECT
    }

    /** 0.21.1: seleção/estado de corrida nunca bloqueia o OCR. Só pausa/fim de jornada bloqueiam. */
    fun canObserveOffers(state: JourneyOperationalState, doingRide: Boolean): Boolean =
        state == JourneyOperationalState.ACTIVE
}
