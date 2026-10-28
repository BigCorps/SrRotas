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
        val nativeContent = history + source("PerformancePresentation") + source("StatisticsScreens105")
        for (label in listOf("Seu desempenho","Visão rápida do período","Tarifas de corridas concluídas",
            "Resultado estimado","Corridas confirmadas","Distância registrada",
            "Horas de jornadas encerradas","Tarifas por hora","Tarifas por km",
            "Ofertas e atividade","Comparar\\nperíodos","Fonte dos dados e limitações")) {
            assertTrue("Missing native Android content $label",nativeContent.contains(label))
        }
        assertTrue(Regex("""WebHandoff021\.open\(context,\s*"/app/estatisticas"\)""").containsMatchIn(history))
        assertTrue(history.contains("BuildConfig.VERSION_NAME"))
        assertTrue(history.contains("versionCode") == false)
    }

    @Test fun noObservedOffersArePromotedToActualRevenue() {
        val h=source("StatisticsScreens105").substringAfter("fun overview(").substringBefore("fun rowLink(")
        assertTrue(h.contains("s.snapshot?.metrics?.get(kpi.key)"))
        assertTrue(h.contains("reading?.display() ?: \"—\""))
        assertTrue(h.contains("s.offers?.summary"))
        assertFalse(h.contains("totalOfferedFare"))
        assertFalse(h.contains("estimatedTotalProfit"))
        assertFalse(h.contains("LocalAnalytics"))
        assertTrue(h.contains("summary.offerCount >= 20"))
    }

    @Test fun greenHaloIsVisibleEvenWhenNoRideIsActive() {
        val hud=source("JourneyBubbleController")
        assertTrue(hud.contains("GreenHaloDrawableV1(context, surface, !colorBlind)"))
        assertFalse(hud.contains("GreenHaloDrawableV1(context, surface, rideActive && !colorBlind)"))
        val gradle=File(root,"app/build.gradle.kts").readText()
        assertTrue(gradle.contains("versionCode=105;versionName=\"0.33.28-field\""))
    }
}
