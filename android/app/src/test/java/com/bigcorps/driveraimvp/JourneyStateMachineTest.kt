package com.srrotas.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JourneyStateMachineTest {
    @Test fun activeIdleCanObservePauseAndStartRide() {
        assertTrue(JourneyStateMachine.canObserveOffers(JourneyOperationalState.ACTIVE, false))
        assertTrue(JourneyStateMachine.canPause(JourneyOperationalState.ACTIVE, false))
        assertTrue(JourneyStateMachine.canStartRide(JourneyOperationalState.ACTIVE, false))
    }

    @Test fun doingRideStopsOfferObservationAndPause() {
        assertFalse(JourneyStateMachine.canObserveOffers(JourneyOperationalState.ACTIVE, true))
        assertFalse(JourneyStateMachine.canPause(JourneyOperationalState.ACTIVE, true))
    }

    @Test fun onlyPausedCanResume() {
        assertTrue(JourneyStateMachine.canResume(JourneyOperationalState.PAUSED))
        assertFalse(JourneyStateMachine.canResume(JourneyOperationalState.ACTIVE))
        assertFalse(JourneyStateMachine.canResume(JourneyOperationalState.ENDED))
    }
}
