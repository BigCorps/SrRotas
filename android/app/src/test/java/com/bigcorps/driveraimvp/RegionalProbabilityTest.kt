package com.srrotas.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RegionalProbabilityTest {
    @Test
    fun censoredShortIntervalIsNotCountedAsFailure() {
        val result = RegionalProbability.calculate(
            listOf(
                RegionalProbability.Sample(
                    durationSeconds = 120,
                    offerHit = false,
                ),
                RegionalProbability.Sample(
                    durationSeconds = 300,
                    offerHit = true,
                ),
                RegionalProbability.Sample(
                    durationSeconds = 900,
                    offerHit = false,
                ),
            ),
            horizonMinutes = 10,
        )

        assertEquals(2, result.eligibleIntervals)
        assertEquals(1, result.successes)
        assertNull(result.probabilityPct)
        assertEquals("insufficient", result.reliability)
    }

    @Test
    fun publishesPercentageOnlyAfterMinimumSample() {
        val samples = buildList {
            repeat(8) {
                add(
                    RegionalProbability.Sample(
                        durationSeconds = 300,
                        offerHit = true,
                    ),
                )
            }
            repeat(12) {
                add(
                    RegionalProbability.Sample(
                        durationSeconds = 700,
                        offerHit = false,
                    ),
                )
            }
        }

        val result =
            RegionalProbability.calculate(
                samples,
                horizonMinutes = 10,
            )

        assertEquals(20, result.eligibleIntervals)
        assertEquals(8, result.successes)
        assertEquals(40.0, result.probabilityPct ?: -1.0, 0.001)
        assertEquals("low", result.reliability)
    }
}
