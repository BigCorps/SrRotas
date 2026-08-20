package com.srrotas.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FieldValidationAssessmentTest {
    @Test
    fun probabilityViolationIsARealFailure() {
        val checks = FieldValidationAssessment.evaluate(
            FieldValidationFacts(
                offers = 50,
                closedExposures = 30,
                probabilityGuardrailViolations = 1,
            ),
        )

        val guardrail = checks.first { it.id == "probability_guardrail" }
        assertEquals(FieldValidationStatus.FAIL, guardrail.status)
    }

    @Test
    fun healthySyncRequiresEmptyQueues() {
        val checks = FieldValidationAssessment.evaluate(
            FieldValidationFacts(
                paired = true,
                online = true,
                pendingOffers = 0,
                pendingContexts = 0,
                pendingJourneyEvents = 0,
                pendingRideOutcomes = 0,
                pendingExposures = 0,
            ),
        )

        val sync = checks.first { it.id == "sync_queue" }
        assertEquals(FieldValidationStatus.PASS, sync.status)
    }

    @Test
    fun pendingSyncProducesWarning() {
        val checks = FieldValidationAssessment.evaluate(
            FieldValidationFacts(
                paired = true,
                online = true,
                pendingOffers = 3,
                pendingExposures = 2,
            ),
        )

        val sync = checks.first { it.id == "sync_queue" }
        assertEquals(FieldValidationStatus.WARN, sync.status)
        assertTrue(sync.detail.contains("5"))
    }

    @Test
    fun radarRemainsManualEvenWhenDataExists() {
        val checks = FieldValidationAssessment.evaluate(
            FieldValidationFacts(
                offers = 20,
                radarOffers = 8,
            ),
        )

        val radar = checks.first { it.id == "radar_card_mix" }
        assertEquals(FieldValidationStatus.MANUAL, radar.status)
    }

    @Test
    fun costProfileNotConfiguredIsNotAFakePass() {
        val checks = FieldValidationAssessment.evaluate(
            FieldValidationFacts(
                costConfigured = false,
                offersWithCostSnapshot = 12,
            ),
        )

        val costs = checks.first { it.id == "cost_profile" }
        assertEquals(FieldValidationStatus.MANUAL, costs.status)
    }
}
