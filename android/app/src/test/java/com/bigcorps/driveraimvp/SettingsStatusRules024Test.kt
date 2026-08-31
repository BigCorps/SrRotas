package com.srrotas.app

import org.junit.Assert.assertEquals
import org.junit.Test

class SettingsStatusRules024Test {
    @Test
    fun greenWhenEverythingReady() {
        val result = SettingsStatusRules024.evaluate(
            SettingsStatusRules024.Input(
                overlayOk = true,
                locationOk = true,
                captureOk = true,
                ocrEnabled = true,
                onboardingCompleted = true,
                journeyActive = false,
            ),
        )
        assertEquals(SettingsStatusRules024.Level.GREEN, result.level)
        assertEquals("Sr. Rotas está pronto", result.title)
    }

    @Test
    fun yellowForSinglePendingItem() {
        val result = SettingsStatusRules024.evaluate(
            SettingsStatusRules024.Input(
                overlayOk = true,
                locationOk = false,
                captureOk = true,
                ocrEnabled = true,
                onboardingCompleted = true,
                journeyActive = false,
            ),
        )
        assertEquals(SettingsStatusRules024.Level.YELLOW, result.level)
    }

    @Test
    fun redForMultiplePendingItems() {
        val result = SettingsStatusRules024.evaluate(
            SettingsStatusRules024.Input(
                overlayOk = false,
                locationOk = false,
                captureOk = true,
                ocrEnabled = true,
                onboardingCompleted = true,
                journeyActive = false,
            ),
        )
        assertEquals(SettingsStatusRules024.Level.RED, result.level)
    }
}
