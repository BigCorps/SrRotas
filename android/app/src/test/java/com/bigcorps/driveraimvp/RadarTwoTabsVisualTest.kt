package com.srrotas.app

import android.app.Activity
import android.app.Application
import android.content.Context
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class RadarTwoTabsVisualTest {
    private fun texts(v: View): List<TextView> = when (v) { is TextView -> listOf(v); is ViewGroup -> (0 until v.childCount).flatMap { texts(v.getChildAt(it)) }; else -> emptyList() }
    private fun labels(v: View) = texts(v).map { it.text.toString() }
    private fun activity() = Robolectric.buildActivity(Activity::class.java).setup().get()
    private fun panel(a: Activity, data: RadarTwoTabsFixtures.Data): RadarTwoTabsPanelV1 {
        val view = RadarTwoTabsPanelV1(a, data) { RadarTwoTabsFixtures.now }
        a.setContentView(view)
        view.measure(View.MeasureSpec.makeMeasureSpec(800, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(1200, View.MeasureSpec.EXACTLY))
        view.layout(0, 0, 800, 1200); view.setSurfaceResumed(true); ShadowLooper.idleMainLooper()
        return view
    }
    @Test fun categoriesFollowExplicitSourceTypesAndMockPalette() {
        val expected = mapOf("music" to "Show", "sports" to "Esporte", "fair_convention" to "Feira",
            "event" to "Evento", "theatre" to "Teatro", "festival" to "Festival", "mall" to "Shopping",
            "hospital" to "Hospital", "hotel" to "Hotel")
        expected.forEach { (source, label) -> assertEquals(label, RadarTwoTabsVisualV1.category(source).label) }
        assertEquals(0xFF7C3AED.toInt(), RadarTwoTabsVisualV1.category("music").color)
        assertEquals(0xFF23854B.toInt(), RadarTwoTabsVisualV1.category("sports").color)
        assertEquals("Família", RadarTwoTabsVisualV1.category("family").label)
        assertEquals("Local", RadarTwoTabsVisualV1.category("venue").label)
        assertEquals("Local", RadarTwoTabsVisualV1.category("unrecognized").label)
    }
    @Test fun selectedTabUsesPaleBlueAndReadableThemeSpecificInk() {
        val a = activity()
        Strategy021Store.saveAppTheme(a, "light")
        assertEquals(0xFFE6F3FF.toInt(), RadarTwoTabsVisualV1.tabFill(a))
        assertEquals(SrUi023.palette(a).navy, RadarTwoTabsVisualV1.tabInk(a))
        Strategy021Store.saveAppTheme(a, "dark")
        assertTrue(Color.red(RadarTwoTabsVisualV1.tabInk(a)) > Color.red(RadarTwoTabsVisualV1.tabFill(a)))
    }
    @Test fun withoutValidRideEventsIsInitialButManualChoiceSurvivesRefreshAndRecreation() {
        val a = activity(); val data = RadarTwoTabsFixtures.Data().apply { current = null }
        val view = panel(a, data)
        assertTrue(texts(view).first { it.text == "Eventos em geral" }.isSelected)
        texts(view).first { it.text == "Próximas do destino" }.performClick()
        view.refresh()
        assertTrue(texts(view).first { it.text == "Próximas do destino" }.isSelected)
        assertEquals(RadarTwoTabsRulesV1.Tab.DESTINATION, RadarVisualLayoutV1.initialTab(a, null, RadarTwoTabsFixtures.now))
    }
    @Test fun activeRideBadgeRequiresConfirmedFreshDestination() {
        val a = activity(); val data = RadarTwoTabsFixtures.Data()
        val view = panel(a, data)
        assertTrue(labels(view).contains("Corrida ativa"))
        assertTrue(labels(view).contains("Oportunidades próximas do destino"))
        data.current = data.current!!.copy(eta = "2030-02-20T19:00:00Z")
        view.refresh()
        assertFalse(labels(view).contains("Corrida ativa"))
        assertTrue(labels(view).contains("ETA vencida ou destino indisponível"))
    }
    @Test fun proximityBadgeRequiresActualFreshSortedResults() {
        val a = activity(); val data = RadarTwoTabsFixtures.Data().apply { current = null; defer = true }
        val view = panel(a, data)
        assertFalse(labels(view).contains("Por proximidade"))
        data.eventDone!!(Result.success(RadarTwoTabsFixtures.sixEvents()))
        assertTrue(labels(view).contains("Por proximidade"))
        assertTrue(labels(view).contains("Eventos próximos"))
        assertTrue(labels(view).contains("6 eventos verificados"))
        data.defer = false; data.eventReply = Result.success(RadarTwoTabsFixtures.events().copy(opportunities = emptyList()))
        view.refresh(); assertFalse(labels(view).contains("Por proximidade"))
    }
    @Test fun diagnosticOpensTheExistingExportActivityOnlyAfterTap() {
        val a = activity(); val view = panel(a, RadarTwoTabsFixtures.Data())
        assertNull(shadowOf(a).nextStartedActivity)
        texts(view).first { it.text == "Diagnóstico" }.performClick()
        val started = shadowOf(a).nextStartedActivity ?: shadowOf(a.application).nextStartedActivity
        assertEquals(DiagnosticExportActivity0270::class.java.name, started.component!!.className)
    }
    @Test fun visualRollbackDoesNotModifyRuntimeFlagsAndUiFlagCannotOverrideIt() {
        val a = activity()
        RadarContextualFlagsV1.enableAssistant(a)
        var changed = 0
        val controls = RadarContextualHomologationV1(a, { changed++ }, {})
        texts(controls).first { it.text == "Rollback visual" }.performClick()
        assertFalse(RadarVisualLayoutV1.enabled(a))
        assertTrue(RadarContextualFlagsV1.uiEnabled(a)); assertTrue(RadarContextualFlagsV1.runtimeEnabled(a))
        assertTrue(RadarContextualFlagsV1.assistantEnabled(a))
        texts(controls).first { it.text == "Visual duas abas" }.performClick()
        assertTrue(RadarVisualLayoutV1.enabled(a)); assertEquals(2, changed)
        assertTrue(RadarContextualFlagsV1.runtimeEnabled(a)); assertTrue(RadarContextualFlagsV1.assistantEnabled(a))
    }
    @Test fun freshInstallShowsNewVisualWithoutEnablingAutomaticFeatures() {
        val a = activity()
        a.getSharedPreferences("sr_radar_visual_v1", Context.MODE_PRIVATE).edit().clear().commit()
        RadarContextualFlagsV1.disableAll(a)
        assertTrue(RadarVisualLayoutV1.enabled(a))
        assertFalse(RadarContextualFlagsV1.uiEnabled(a)); assertFalse(RadarContextualFlagsV1.runtimeEnabled(a))
        assertFalse(RadarContextualFlagsV1.assistantEnabled(a))
    }
    @Test fun manualEntryPathsUseVisualControlAndKeepCurrentRideResolution() {
        val root = listOf(java.io.File("."), java.io.File("android"), java.io.File(".."))
            .first { java.io.File(it, "app/build.gradle.kts").isFile }
        val shell = java.io.File(root, "app/src/main/java/com/bigcorps/driveraimvp/ConsolidatedMainActivity027037.kt").readText()
        val entry = java.io.File(root, "app/src/main/java/com/bigcorps/driveraimvp/RadarDestinationEntryV1.kt").readText()
        assertFalse(shell.contains("RadarContextualFlagsV1.uiEnabled(this)"))
        assertTrue(shell.contains("current?.localOfferId!=request.spec.localOfferId || !RadarVisualLayoutV1.enabled(this)"))
        assertTrue(shell.contains("spec != null && RadarVisualLayoutV1.enabled(this)"))
        assertTrue(entry.contains("!RadarVisualLayoutV1.enabled(context)"))
        assertTrue(entry.substringAfter("setOnClickListener").contains("RadarDestinationContextV1.current(context)"))
    }

}
