package com.srrotas.app

import android.Manifest
import android.app.Application
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
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog
import org.robolectric.shadows.ShadowLooper
import java.time.Instant

/** vc109 — Oportunidades e Eventos visíveis sem corrida, motivos distintos, lugares e filtros reais. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class Radar109RegionTest {
    private fun texts(v: View): List<TextView> = when (v) { is TextView -> listOf(v); is ViewGroup -> (0 until v.childCount).flatMap { texts(v.getChildAt(it)) }; else -> emptyList() }

    @Test fun realEventTypesAreEligibleWithinTheArrivalWindow() {
        val now = Instant.parse("2026-11-03T20:00:00Z").toEpochMilli(); val eta = "2026-11-03T20:10:00Z"
        for (type in listOf("music", "sports", "theatre", "fair_convention", "family", "event")) {
            assertTrue(type, RadarContextualEventEligibilityV1.eligible(type, "Show", "2026-11-03T19:00:00Z", "2026-11-03T20:15:00Z", .8, eta, now))
        }
        assertFalse(RadarContextualEventEligibilityV1.eligible("", "Sem tipo", "2026-11-03T19:00:00Z", "2026-11-03T20:15:00Z", .8, eta, now))
        assertFalse(RadarContextualEventEligibilityV1.eligible("music", "Já terminou", "2026-11-03T17:00:00Z", "2026-11-03T18:00:00Z", .8, eta, now))
        assertFalse(RadarContextualEventEligibilityV1.eligible("music", "Sem confiança", "2026-11-03T19:00:00Z", "2026-11-03T20:15:00Z", 0.0, eta, now))
    }

    @Test fun eventLinesShowOriginTimeConfidenceAndValidity() {
        val e = EventRadarOpportunity026("1", "music", "Show Exemplo", "Arena Fictícia", "Rua Alfa, 10", "2026-11-03T22:00:00Z",
            "2026-11-04T01:00:00Z", "2026-11-04T00:40:00Z", "2026-11-04T02:15:00Z", 3.2, "ticketmaster", 0.82, null)
        val lines = RadarRegion109.eventLines(e, Instant.parse("2026-11-03T23:00:00Z").toEpochMilli()).joinToString("\n")
        assertTrue(lines, lines.contains("Fonte: ticketmaster · confiança 82%"))
        assertTrue(lines, lines.contains("19:00–22:00")); assertTrue(lines, lines.contains("válido até 23:15"))
        assertTrue(RadarRegion109.eventLines(e, Instant.parse("2026-11-04T03:00:00Z").toEpochMilli()).joinToString().contains("encerrado"))
    }

    @Test fun messagesDistinguishNoDataNoDestinationDisabledAndFailure() {
        assertTrue(RadarRegion109.failure("access_restricted").contains("acesso atual"))
        assertTrue(RadarRegion109.failure("HTTP 500").contains("servidor"))
        assertTrue(RadarRegion109.failure("Conecte sua conta.").contains("Conecte"))
        assertTrue(RadarRegion109.emptyEvents("source_not_configured").contains("não está configurada"))
        assertTrue(RadarRegion109.emptyEvents("ok").contains("Nenhum evento verificado"))
        val app = org.robolectric.RuntimeEnvironment.getApplication()
        assertTrue(RadarRegion109.idleReason(app).contains("Sem jornada ativa"))
    }

    @Test fun idleRadarOffersRegionQueryPlacesAndExplainsFailures() {
        val activity = Robolectric.buildActivity(MainActivity::class.java).get()
        shadowOf(activity.application).grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION)
        val lm = activity.getSystemService(LocationManager::class.java)
        shadowOf(lm).setProviderEnabled(LocationManager.NETWORK_PROVIDER, true)
        shadowOf(lm).setLastKnownLocation(LocationManager.NETWORK_PROVIDER, Location(LocationManager.NETWORK_PROVIDER).apply {
            latitude = -23.55; longitude = -46.63; time = System.currentTimeMillis() - 60_000
        })
        val panel = RadarContextualPanelV1(activity)
        val labels = texts(panel).map { it.text.toString() }
        assertTrue(labels.contains("Consultar minha região")); assertTrue(labels.contains("Meus lugares marcados"))
        assertTrue(labels.any { it.contains("Sem jornada ativa") })
        // No account on this device: both tabs show the friendly failure instead of a silent placeholder.
        texts(panel).first { it.text == "Consultar minha região" }.performClick()
        repeat(40) { ShadowLooper.idleMainLooper(); Thread.sleep(25) }
        texts(panel).first { it.text == "Oportunidades" }.performClick()
        assertTrue(texts(panel).map { it.text.toString() }.any { it.contains("Conecte sua conta") })
        texts(panel).first { it.text == "Áreas" }.performClick()
        assertTrue(texts(panel).map { it.text.toString() }.any { it.contains("Conecte sua conta") })
        // Manual places keep their sharing controls inside the contextual Radar.
        texts(panel).first { it.text == "Resumo" }.performClick()
        RadarPlaceStore027035.add(activity, "Ponto fictício", "Rua Alfa, 10", "", "shareable")
        texts(panel).first { it.text == "Meus lugares marcados" }.performClick()
        val dialog = texts(ShadowDialog.getLatestDialog().window!!.decorView).map { it.text.toString() }
        assertTrue(dialog.contains("+ Marcar lugar")); assertTrue(dialog.contains("COMPARTILHÁVEL")); assertTrue(dialog.contains("Compartilhar"))
        assertFalse(dialog.contains("Eventos próximos"))
    }

    @Test fun noHighProbabilityWithoutSamplesAndNoEmbeddedMap() {
        val src = java.io.File(generateSequence(java.io.File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
            .first { java.io.File(it, "app/build.gradle.kts").exists() }, "app/src/main/java/com/bigcorps/driveraimvp/RadarContextualPanelV1.kt").readText()
        // Region cards use the same presenter (potential gated by backend samples) and the external Maps button.
        assertTrue(src.substringAfter("private fun renderRegionOpportunities").contains("RadarContextualPresenterV1.map(value)"))
        assertFalse(src.contains("import org.maplibre")); assertFalse(src.contains("MapView("))
        assertThrows(ClassNotFoundException::class.java) { Class.forName("org.maplibre.android.maps.MapView") }
    }
}
