package com.srrotas.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CommercialModelGate5ContractTest {
    private val sourceRoot: File by lazy {
        val cwd = File(System.getProperty("user.dir") ?: ".").absoluteFile
        generateSequence(cwd) { it.parentFile }
            .flatMap { base ->
                sequenceOf(
                    File(base, "app/src/main/java/com/bigcorps/driveraimvp"),
                    File(base, "android/app/src/main/java/com/bigcorps/driveraimvp"),
                )
            }
            .firstOrNull { File(it, "SrRotasApplication.kt").isFile }
            ?: error("Fontes Android não encontradas.")
    }
    private fun source(name: String) = File(sourceRoot, name).readText()

    @Test fun billingViewUsesCopilotIntelligenceWithoutCredits() {
        val s = source("BillingStatusView.kt")
        assertTrue(s.contains("Copiloto e Inteligência"))
        assertTrue(s.contains("GRÁTIS"))
        assertTrue(s.contains("R$ 9,90 / 30 dias"))
        assertFalse(s.contains("Créditos de IA"))
        assertFalse(s.contains("Alpha liberado"))
    }

    @Test fun realOnboardingUsesCopilotAndIntelligenceContract() {
        val s = source("OnboardingActivity.kt")
        assertTrue(s.contains("COPILOTO GRÁTIS"))
        assertTrue(s.contains("7 dias de Inteligência"))
        assertTrue(s.contains("Não existe cobrança automática"))
        assertFalse(s.contains("5 créditos temporários"))
        assertFalse(s.contains("Tudo liberado para testar"))
    }

    @Test fun activeAiPanelNoLongerShowsCreditWallet() {
        val s = source("AiPanel023.kt")
        assertTrue(s.contains("Inteligência: consultando"))
        assertTrue(s.contains("Copiloto grátis"))
        assertFalse(s.contains("creditBalance"))
        assertFalse(s.contains("seus créditos de IA acabaram"))
    }

    @Test fun collectiveAndPersonalBaseVisualsMatchContract() {
        val s = source("SrUi023.kt")
        assertTrue(s.contains("GradientDrawable.Orientation.TL_BR"))
        assertTrue(s.contains("val personal = label.equals("Base pessoal""))
        assertTrue(s.contains("rounded(Color.TRANSPARENT, 11, p.blue, if (active) 2 else 1, context)"))
    }

    @Test fun headerKeepsReducedLogoLeftAndSectionRight() {
        val s = source("SrAppHeader023.kt")
        assertTrue(s.contains("else -> 128"))
        assertTrue(s.contains("ImageView.ScaleType.FIT_START"))
        assertTrue(s.contains("Gravity.END or Gravity.CENTER_VERTICAL"))
    }
}
