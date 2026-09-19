package com.srrotas.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Contrato estático para impedir retorno do padrão patch-sobre-patch. */
class ArchitectureContract027037Test {
    private val root: File by lazy {
        val cwd = File(System.getProperty("user.dir")).absoluteFile
        val candidates =
            generateSequence(cwd) { it.parentFile }
                .flatMap { base ->
                    sequenceOf(
                        File(base, "app/src/main/java/com/bigcorps/driveraimvp"),
                        File(base, "src/main/java/com/bigcorps/driveraimvp"),
                        File(base, "android/app/src/main/java/com/bigcorps/driveraimvp"),
                    )
                }
                .toList()

        candidates.firstOrNull { File(it, "SrRotasApplication.kt").isFile }
            ?: error(
                "Não foi possível localizar os fontes do Sr. Rotas. user.dir=${cwd.path}; " +
                    "candidatos=${candidates.joinToString { it.path }}",
            )
    }

    private fun source(name: String): String {
        val file = File(root, name)
        check(file.isFile) { "Fonte não encontrado: ${file.absolutePath}" }
        return file.readText()
    }

    @Test
    fun applicationDoesNotInstallLegacyVisualPolishes() {
        val app = source("SrRotasApplication.kt")
        listOf(
            "NowPanelPolish0262.install",
            "FieldValidationPolish0263.install",
            "FieldValidationPolish0264.install",
            "FieldValidationPolish0265.install",
            "BubbleRuntimePolish0265.install",
            "ReleasePolish0270.install",
            "Rc35UiPolish027035.install",
            "Rc36ClosingPolish027036.install",
            "Rc361FieldFixes0270361.install",
        ).forEach { assertFalse("Runtime legado reintroduzido: $it", app.contains(it)) }
    }

    @Test
    fun mainUsesConsolidatedShell() {
        assertTrue(source("MainActivity.kt").contains("ConsolidatedMainActivity027037"))
    }

    @Test
    fun nowHasNoLegacyRadarCounterOrTicker() {
        val now = source("NowPanel027037.kt")
        assertFalse(now.contains("Visualizar Radar"))
        assertFalse(now.contains("regiões em destaque"))
        assertFalse(now.contains("postDelayed"))
        assertFalse(now.contains("Handler("))
        assertTrue(now.contains("Pesquisar região"))
    }

    @Test
    fun readerDefaultsToM1AndHistoryIsReversible() {
        assertTrue(source("ReaderLab027036.kt").contains("getString(\"mode\", MODE_M1)"))
        assertTrue(source("RideHistoryPanel027035.kt").contains("RideOperationalStatus.NOT_COMPLETED"))
    }

    @Test
    fun diagnosticsUseCombinedExporter() {
        assertTrue(source("SettingsPanel027037.kt").contains("ReaderLabCombinedDiagnostic0270361.share"))
        assertTrue(source("DiagnosticControls0270.kt").contains("ReaderLabCombinedDiagnostic0270361.share"))
    }

    @Test
    fun settingsRefreshDoesNotReuseDetachedChildViews() {
        val settings = source("SettingsPanel027037.kt")
        assertFalse(
            "SettingsPanel voltou a reter readerStatus entre refreshes; isso causa 'child already has a parent'.",
            settings.contains("private val readerStatus"),
        )
    }

    @Test
    fun compatibilityPolishesStaySmall() {
        listOf(
            "NowPanelPolish0262.kt",
            "FieldValidationPolish0263.kt",
            "FieldValidationPolish0264.kt",
            "FieldValidationPolish0265.kt",
            "BubbleRuntimePolish0265.kt",
            "ReleasePolish0270.kt",
            "Rc35UiPolish027035.kt",
            "Rc36ClosingPolish027036.kt",
            "Rc361FieldFixes0270361.kt",
            "Rc36MainOverlay027036.kt",
        ).forEach { name ->
            assertTrue("$name voltou a acumular implementação", File(root, name).length() < 4_000L)
        }
    }
}
