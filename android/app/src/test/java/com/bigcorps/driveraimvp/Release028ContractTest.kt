package com.srrotas.app

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Contrato 0.28 para as regressões reais encontradas em campo. */
class Release028ContractTest {
    private val root: File by lazy {
        val cwd = File(System.getProperty("user.dir") ?: ".").absoluteFile
        val candidates = generateSequence(cwd) { it.parentFile }
            .flatMap { base -> sequenceOf(
                File(base, "app/src/main/java/com/bigcorps/driveraimvp"),
                File(base, "src/main/java/com/bigcorps/driveraimvp"),
                File(base, "android/app/src/main/java/com/bigcorps/driveraimvp"),
            ) }.toList()
        candidates.firstOrNull { File(it, "SrRotasApplication.kt").isFile }
            ?: error("Não foi possível localizar os fontes do Sr. Rotas. user.dir=${cwd.path}")
    }

    private fun source(name: String): String {
        val file = File(root, name)
        check(file.isFile) { "Fonte não encontrado: ${file.absolutePath}" }
        return file.readText()
    }

    @Test fun compactFloatingWindowRemainsAFirstClassSetting() {
        val settings = source("FloatingWindowSettingsActivity027034.kt")
        val bubble = source("JourneyBubbleController.kt")
        assertTrue(settings.contains("Usar painel compacto"))
        assertTrue(settings.contains("setCompactPanel(compactPanel.isChecked)"))
        assertTrue(bubble.contains("prefs.compactPanel()"))
        assertTrue(bubble.contains("private fun panelDp"))
    }

    @Test fun regionalSearchRemainsCollapsible() {
        val now = source("NowPanel027037.kt")
        assertTrue(now.contains("sr028_region_search_toggle"))
        assertTrue(now.contains("searchBody.visibility = if (expanded) View.VISIBLE else View.GONE"))
        assertTrue(now.contains("setSearchExpanded(false)"))
    }

    @Test fun integrityGateRunsBeforeHudAndPersistence() {
        val dispatcher = source("OfferDispatcher.kt")
        val diagnostic = source("ReaderLabCombinedDiagnostic0270361.kt")
        assertTrue(dispatcher.contains("OfferIntegrityGuard028.accept(appContext, offer, \"dispatch\")"))
        assertTrue(dispatcher.contains("OfferIntegrityGuard028.accept(appContext, it, \"submit_stabilized\")"))
        assertTrue(dispatcher.contains("OfferIntegrityGuard028.isPlausible(offer)"))
        assertTrue(diagnostic.contains("offer_integrity_028"))
    }
}
