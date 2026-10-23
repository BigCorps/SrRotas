package com.srrotas.app

import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Prevent Web-only UI changes being represented as Android feature releases. */
class Field103NativePerformanceTest {
    private val root by lazy {
        generateSequence(File(System.getProperty("user.dir") ?: ".").absoluteFile) { it.parentFile }
            .first { File(it,"app/build.gradle.kts").exists() }
    }
    private fun source(n:String)=File(root,"app/src/main/java/com/bigcorps/driveraimvp/$n.kt").readText()

    @Test fun actualAndroidStatisticsSurfaceShowsSeuDesempenho() {
        val activity=source("ConsolidatedMainActivity027037")
        val history=source("HistoryPanel")
        assertTrue(activity.contains("statisticsPanel = HistoryPanel(this)"))
        for (label in listOf("Seu desempenho","Visão rápida do período","Tarifas de corridas concluídas",
            "Resultado estimado","Corridas confirmadas","Distância registrada",
            "Horas de jornadas encerradas","Tarifas por hora","Tarifas por km",
            "Ofertas e atividade","Comparar períodos","Por que algumas taxas estão indisponíveis?")) {
            assertTrue("Missing native Android content $label",history.contains(label))
        }
        assertTrue(history.contains("WebHandoff021.open(context,\"/app/estatisticas\")"))
        assertTrue(history.contains("BuildConfig.VERSION_NAME"))
        assertTrue(history.contains("versionCode") == false)
    }

    @Test fun noObservedOffersArePromotedToActualRevenue() {
        val h=source("HistoryPanel").substringAfter("private fun performanceOverview(").substringBefore("private fun sectionHeading(")
        assertTrue(h.contains("val missing = \"—\""))
        assertTrue(h.contains("Exige ofertas vinculadas a corridas confirmadas"))
        assertTrue(h.contains("data.summary"))
        assertFalse(h.contains("s.totalOfferedFare"))
        assertTrue(h.contains("count>=20"))
    }

    @Test fun greenHaloIsVisibleEvenWhenNoRideIsActive() {
        val hud=source("JourneyBubbleController")
        assertTrue(hud.contains("GreenHaloDrawableV1(context, surface, !colorBlind)"))
        assertFalse(hud.contains("GreenHaloDrawableV1(context, surface, rideActive && !colorBlind)"))
        val gradle=File(root,"app/build.gradle.kts").readText()
        assertTrue(gradle.contains("versionCode=103;versionName=\"0.33.26-field\""))
    }
}
