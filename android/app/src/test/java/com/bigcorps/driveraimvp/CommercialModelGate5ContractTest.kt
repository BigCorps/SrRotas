package com.srrotas.app
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
class CommercialModelGate5ContractTest {
 private val sourceRoot:File by lazy{val cwd=File(System.getProperty("user.dir")?:".").absoluteFile;generateSequence(cwd){it.parentFile}.flatMap{base->sequenceOf(File(base,"app/src/main/java/com/bigcorps/driveraimvp"),File(base,"android/app/src/main/java/com/bigcorps/driveraimvp"))}.firstOrNull{File(it,"SrRotasApplication.kt").isFile}?:error("Fontes Android não encontradas.")}
 private fun source(name:String)=File(sourceRoot,name).readText()
 @Test fun billingViewUsesCopilotIntelligenceWithoutCredits(){val s=source("BillingStatusView.kt");assertTrue(s.contains("Copiloto e Inteligência"));assertTrue(s.contains("GRÁTIS"));assertTrue(s.contains("R$ 9,90 / 30 dias"));assertFalse(s.contains("Créditos de IA"));assertFalse(s.contains("Alpha liberado"))}
 @Test fun welcomeNoLongerPromisesAiCredits(){val s=source("WelcomeCarouselActivity.kt");assertTrue(s.contains("Copiloto grátis"));assertTrue(s.contains("7 dias de Sr. Rotas Inteligência"));assertFalse(s.contains("5 créditos temporários"))}
 @Test fun aiWrapperHidesLegacyCreditLabelWithoutWatcher(){val s=source("ConsolidatedAiPanel027037.kt");assertTrue(s.contains("hideLegacyCreditStatus"));assertTrue(s.contains("visibility = View.GONE"));assertFalse(s.contains("postDelayed"));assertFalse(s.contains("GlobalLayoutListener"))}
}
