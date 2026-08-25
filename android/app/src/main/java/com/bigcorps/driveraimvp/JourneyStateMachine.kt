package com.srrotas.app

object JourneyStateMachine {
    fun canPause(state: JourneyOperationalState, doingRide: Boolean): Boolean =
        state == JourneyOperationalState.ACTIVE && !doingRide

    fun canResume(state: JourneyOperationalState): Boolean = state == JourneyOperationalState.PAUSED

    fun canStartRide(state: JourneyOperationalState, doingRide: Boolean): Boolean =
        state == JourneyOperationalState.ACTIVE && !doingRide

    /** 0.21.1: seleção/estado de corrida nunca bloqueia o OCR. Só pausa/fim de jornada bloqueiam. */
    fun canObserveOffers(state: JourneyOperationalState, doingRide: Boolean): Boolean =
        state == JourneyOperationalState.ACTIVE
}
