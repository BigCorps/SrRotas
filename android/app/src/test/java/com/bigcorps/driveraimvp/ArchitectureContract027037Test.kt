package com.srrotas.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Contrato estático para impedir retorno do padrão patch-sobre-patch. */
class ArchitectureContract027037Test {
    private val root: File by lazy {
        val cwd = File(System.getProperty("user.dir") ?: ".").absoluteFile
        val candidates = generateSequence(cwd) { it.parentFile }
            .flatMap { base -> sequenceOf(
                File(base, "app/src/main/java/com/bigcorps/driveraimvp"),
                File(base, "src/main/java/com/bigcorps/driveraimvp"),
                File(base, "android/app/src/main/java/com/bigcorps/driveraimvp"),
            ) }.toList()
        candidates.firstOrNull { File(it, "SrRotasApplication.kt").isFile }
            ?: error("Não foi possível localizar os fontes do Sr. Rotas. user.dir=${cwd.path}; candidatos=${candidates.joinToString { it.path }}")
    }

    private fun source(name: String): String {
        val file = File(root, name)
        check(file.isFile) { "Fonte não encontrado: ${file.absolutePath}" }
        return file.readText()
    }

    @Test fun applicationDoesNotInstallLegacyVisualPolishes() {
        val app = source("SrRotasApplication.kt")
        listOf("NowPanelPolish0262.install","FieldValidationPolish0263.install","FieldValidationPolish0264.install","FieldValidationPolish0265.install","BubbleRuntimePolish0265.install","ReleasePolish0270.install","Rc35UiPolish027035.install","Rc36ClosingPolish027036.install","Rc361FieldFixes0270361.install")
            .forEach { assertFalse("Runtime legado reintroduzido: $it", app.contains(it)) }
    }

    @Test fun mainUsesConsolidatedShell() { assertTrue(source("MainActivity.kt").contains("ConsolidatedMainActivity027037")) }

    @Test fun nowHasNoLegacyRadarCounterOrTicker() {
        val now = source("NowPanel027037.kt")
        assertFalse(now.contains("Visualizar Radar")); assertFalse(now.contains("regiões em destaque")); assertFalse(now.contains("postDelayed")); assertFalse(now.contains("Handler(")); assertTrue(now.contains("Pesquisar região"))
    }

    @Test fun readerDefaultsToM1AndHistoryIsReversible() {
        assertTrue(source("ReaderLab027036.kt").contains("getString(\"mode\", MODE_M1)"))
        assertTrue(source("RideHistoryPanel027035.kt").contains("RideOperationalStatus.NOT_COMPLETED"))
    }

    @Test fun diagnosticsUseCombinedExporter() {
        assertTrue(source("SettingsPanel027037.kt").contains("ReaderLabCombinedDiagnostic0270361.share"))
        assertTrue(source("DiagnosticControls0270.kt").contains("ReaderLabCombinedDiagnostic0270361.share"))
    }

    @Test fun settingsRefreshDoesNotReuseDetachedChildViews() {
        assertFalse("SettingsPanel voltou a reter readerStatus entre refreshes.", source("SettingsPanel027037.kt").contains("private val readerStatus"))
    }

    @Test fun onboardingPersistsTermsConsentBeforeCompletion() {
        val onboarding = source("OnboardingActivity.kt")
        assertTrue("Onboarding precisa persistir consentAccepted=true ao concluir os Termos.", onboarding.contains("consentAccepted=true"))
        assertTrue("A caixa de Termos deve refletir consentimento já persistido.", onboarding.contains("isChecked=s.consentAccepted"))
    }

    @Test fun responsiveChromeProtectsNarrowAndLargeFontLayouts() {
        val header = source("SrAppHeader023.kt")
        val nav = source("SrBottomNav023.kt")
        val onboarding = source("OnboardingActivity.kt")
        val back = source("ConfigurationBackOverlay0212.kt")
        val responsive = source("ResponsiveUi027038.kt")

        assertTrue("Header deve reservar o slot do botão Voltar.", header.contains("backSlotWidthPx"))
        assertTrue("Header deve autoajustar texto em largura limitada.", header.contains("autoSizeSingleLine"))
        assertTrue("Bottom nav deve autoajustar os rótulos.", nav.contains("autoSizeSingleLine"))
        assertTrue("Onboarding deve reservar o botão Voltar.", onboarding.contains("backSlotWidthPx"))
        assertTrue("Onboarding deve autoajustar títulos.", onboarding.contains("autoSizeHeader"))
        assertTrue("Overlay deve expor reserva de espaço compartilhada.", back.contains("fun backSlotWidthPx"))
        assertTrue("Contrato responsivo deve medir screenWidthDp.", responsive.contains("fun screenWidthDp"))
    }

    @Test fun canonicalScreensRemainVerticallyScrollable() {
        val scrollableScreens = listOf(
            "NowPanel027037.kt",
            "RideHistoryPanel027035.kt",
            "RadarPanel027035.kt",
            "SettingsPanel027037.kt",
        )
        scrollableScreens.forEach { name ->
            val src = source(name).replace(Regex("\\s+"), "")
            assertTrue("$name deve permanecer um ScrollView.", src.contains(":ScrollView("))
        }
        assertTrue(source("AiPanel023.kt").contains("ScrollView(context)"))
        assertTrue(source("OnboardingActivity.kt").contains("ScrollView(this)"))
    }

    @Test fun responsiveWidthMathNeverExceedsAvailableWidth() {
        val src = source("ResponsiveLayoutMath027038.kt")
        assertTrue(src.contains("available.coerceAtMost"))
        assertTrue(src.contains("safeScreen - margin * 2"))
    }

    @Test fun compatibilityPolishesStaySmall() {
        listOf("NowPanelPolish0262.kt","FieldValidationPolish0263.kt","FieldValidationPolish0264.kt","FieldValidationPolish0265.kt","BubbleRuntimePolish0265.kt","ReleasePolish0270.kt","Rc35UiPolish027035.kt","Rc36ClosingPolish027036.kt","Rc361FieldFixes0270361.kt","Rc36MainOverlay027036.kt")
            .forEach { name -> assertTrue("$name voltou a acumular implementação", File(root, name).length() < 4_000L) }
    }
}
