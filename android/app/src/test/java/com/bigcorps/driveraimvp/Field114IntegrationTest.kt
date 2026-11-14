package com.srrotas.app

import android.app.Activity
import android.app.Application
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.srrotas.app.r3lab.R3Mode
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.RobolectricTestRunner

/** Combined surfaces must never clear an open journey or its reader recovery choice. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class Field114IntegrationTest {
    private fun texts(v: View): List<TextView> = when (v) {
        is TextView -> listOf(v)
        is ViewGroup -> (0 until v.childCount).flatMap { texts(v.getChildAt(it)) }
        else -> emptyList()
    }
    @Test fun radarVisualControlsPreserveTheOpenJourneyAndReaderFallback() {
        val app = RuntimeEnvironment.getApplication()
        val repo = SettingsRepository(app)
        repo.setCurrentJourney("integration-journey", "2030-02-20T19:00:00Z")
        R3Mode.setFallback(app, "integration-journey", "accessibility_disconnected")
        RadarContextualFlagsV1.disableAll(app)
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val controls = RadarContextualHomologationV1(activity, {}, {})
        for (label in listOf("Rollback visual", "Visual duas abas")) {
            texts(controls).first { it.text == label }.performClick()
            assertEquals("integration-journey", repo.currentJourneyId())
            assertEquals("accessibility_disconnected", R3Mode.fallback(app)!!.reason)
            assertTrue(R3Mode.fallbackActive(app, "integration-journey"))
            assertFalse(RadarContextualFlagsV1.runtimeEnabled(app))
            assertFalse(RadarContextualFlagsV1.assistantEnabled(app))
        }
        R3Mode.clearFallback(app); repo.clearCurrentJourney()
    }
    @Test fun historySelectionDoesNotConfirmRideOrDisturbReaderAndRadarChoice() {
        val app = RuntimeEnvironment.getApplication()
        val repo = SettingsRepository(app)
        repo.setCurrentJourney("integration-history", "2030-02-20T19:00:00Z")
        R3Mode.setFallback(app, "integration-history", "manual_recover_m1")
        RadarVisualLayoutV1.rememberTab(app, RadarTwoTabsRulesV1.Tab.EVENTS)
        val ride = UberCompletedRide026("integration-review", "2030-02-20T19:00:00Z", "2030-02-20T18:00:00Z",
            23.51, "uberx", "Rua de teste, 10", null, 0.9, 900, 6.2)
        HistoryReviewStore109.save(app, listOf(ride), 1, 1)
        val before = LocalStore.get(app).pendingRideOutcomes(250).size
        val activity = Robolectric.buildActivity(HistoryReviewActivity109::class.java).setup().get()
        val card = texts(activity.window.decorView).first { it.text.startsWith("Incluir nesta confirmação") }.parent as View
        assertFalse(card.isSelected); assertTrue(card.performClick())
        assertTrue(texts(activity.window.decorView).any { it.text == "Confirmar 1 registro(s)" })
        val selected = texts(activity.window.decorView).first { it.text.startsWith("✓ Selecionado") }.parent as View
        assertTrue(selected.isSelected)
        assertEquals(before, LocalStore.get(app).pendingRideOutcomes(250).size)
        assertEquals("integration-history", repo.currentJourneyId())
        assertTrue(R3Mode.fallbackActive(app, "integration-history"))
        assertEquals(RadarTwoTabsRulesV1.Tab.EVENTS, RadarVisualLayoutV1.initialTab(app, null, RadarTwoTabsFixtures.now))
        activity.finish(); HistoryReviewStore109.clear(app); R3Mode.clearFallback(app); repo.clearCurrentJourney()
    }
}
