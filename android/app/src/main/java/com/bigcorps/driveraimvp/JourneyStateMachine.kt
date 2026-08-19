package com.srrotas.app

object JourneyStateMachine {
    fun canPause(state: JourneyOperationalState, doingRide: Boolean): Boolean =
        state == JourneyOperationalState.ACTIVE && !doingRide

    fun canResume(state: JourneyOperationalState): Boolean = state == JourneyOperationalState.PAUSED

    fun canStartRide(state: JourneyOperationalState, doingRide: Boolean): Boolean =
        state == JourneyOperationalState.ACTIVE && !doingRide

    fun canObserveOffers(state: JourneyOperationalState, doingRide: Boolean): Boolean =
        state == JourneyOperationalState.ACTIVE && !doingRide
}
