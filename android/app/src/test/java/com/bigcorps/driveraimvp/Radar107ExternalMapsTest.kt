package com.srrotas.app

import android.Manifest
import android.app.Application
import android.content.Intent
import android.location.Location
import android.location.LocationManager
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast

/** vc107: Radar without embedded cartography; locations open only in the external Google Maps. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class Radar107ExternalMapsTest {
    private fun texts(v: View): List<TextView> = when (v) {
        is TextView -> listOf(v); is ViewGroup -> (0 until v.childCount).flatMap { texts(v.getChildAt(it)) }; else -> emptyList()
    }
    private fun all(v: View): List<View> = listOf(v) + if (v is ViewGroup) (0 until v.childCount).flatMap { all(v.getChildAt(it)) } else emptyList()

    @Test fun validCoordinatesWinAndAddressIsTheFallback() {
        val byCoords = RadarExternalMapsV1.intent(-23.561, -46.656, "Endereço A")!!
        assertEquals(Intent.ACTION_VIEW, byCoords.action)
        assertEquals("https://www.google.com/maps/search/?api=1&query=-23.561000%2C-46.656000", byCoords.dataString)
        assertNull("universal link: the Maps app when installed, otherwise the browser", byCoords.`package`)
        val byLabel = RadarExternalMapsV1.intent(Double.NaN, null, "Endereço de destino A")!!
        assertTrue(byLabel.dataString!!.endsWith("query=Endere%C3%A7o%20de%20destino%20A"))
        assertNull(RadarExternalMapsV1.intent(0.0, 0.0, null))
        assertNull(RadarExternalMapsV1.intent(91.0, 10.0, " "))
        assertFalse(RadarExternalMapsV1.validCoordinate(0.0, 0.0))
    }

    @Test fun opensExternallyAndHandlesMissingAppsWithoutCrashing() {
        val app = RuntimeEnvironment.getApplication()
        assertTrue(RadarExternalMapsV1.open(app, -23.5, -46.6, null))
        val started = shadowOf(app).nextStartedActivity
        assertTrue(started.dataString!!.startsWith("https://www.google.com/maps/search/"))
        assertTrue(started.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
        // No Google Maps and no browser installed.
        shadowOf(app).checkActivities(true)
        assertFalse(RadarExternalMapsV1.open(app, -23.5, -46.6, null))
        assertTrue(ShadowToast.getTextOfLatestToast().contains("Nenhum aplicativo de mapas"))
        assertFalse("nothing without a location", RadarExternalMapsV1.open(app, null, null, null))
    }

    @Test fun radarSurfaceNeverCreatesAMapAndNeverOpensMapsByItself() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).get()
        shadowOf(activity.application).grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION)
        val lm = activity.getSystemService(LocationManager::class.java)
        shadowOf(lm).setProviderEnabled(LocationManager.NETWORK_PROVIDER, true)
        shadowOf(lm).setLastKnownLocation(LocationManager.NETWORK_PROVIDER, Location(LocationManager.NETWORK_PROVIDER).apply {
            latitude = -23.55; longitude = -46.63; time = System.currentTimeMillis() - 60_000
        })
        val panel = RadarContextualPanelV1(activity)
        // Entering the Radar renders text only: no MapView/SurfaceView/TextureView, no external start.
        val views = all(panel)
        assertTrue(views.none { it.javaClass.name.contains("maplibre", true) || it is android.view.SurfaceView || it is android.view.TextureView })
        assertNull(shadowOf(activity.application).nextStartedActivity)
        val labels = texts(panel).map { it.text.toString() }
        assertTrue(labels.contains("Resumo")); assertFalse(labels.contains("Mapa"))
        val button = texts(panel).first { it.text == "Ver no Google Maps" }
        button.performClick()
        val started = shadowOf(activity.application).nextStartedActivity ?: shadowOf(activity).nextStartedActivity
        assertTrue(started.dataString!!.contains("query=-23.550000%2C-46.630000"))
        // DEMO data never offers an external location (each maps button is gated on !demoMode).
        val source = java.io.File(generateSequence(java.io.File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
            .first { java.io.File(it, "app/build.gradle.kts").exists() }, "app/src/main/java/com/bigcorps/driveraimvp/RadarContextualPanelV1.kt").readText()
        assertEquals(3, Regex("!demoMode && RadarExternalMapsV1.available").findAll(source).count())
    }

    @Test fun mapLibreIsNotOnTheClasspath() {
        assertThrows(ClassNotFoundException::class.java) { Class.forName("org.maplibre.android.maps.MapView") }
    }
}
