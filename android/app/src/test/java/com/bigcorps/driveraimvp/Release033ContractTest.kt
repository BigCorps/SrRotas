package com.srrotas.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class Release033ContractTest {
    private fun read(path: String): String {
        val candidates = listOf(File(path), File("../$path"), File("../../$path"))
        return candidates.firstOrNull { it.exists() }?.readText()
            ?: error("Arquivo não encontrado no teste: $path")
    }

    @Test fun screenshotStorageIsGuardedAndBounded() {
        val store = read("app/src/main/java/com/bigcorps/driveraimvp/PrivateScreenshotStore.kt")
        assertTrue(store.contains("ScreenshotStorageGuard033.allow"))
        assertTrue(store.contains("MAX_PRIVATE_FILES = 30"))
        assertTrue(store.contains("MAX_VISIBLE_FILES = 180"))
        assertTrue(store.contains("JPEG_QUALITY = 72"))
        assertTrue(store.contains("cropDiagnosticBitmap"))
    }

    @Test fun developJourneyStatusIsCompact() {
        val now = read("app/src/main/java/com/bigcorps/driveraimvp/NowPanel027037.kt")
        val status = read("app/src/main/java/com/bigcorps/driveraimvp/DevelopStatus033.kt")
        assertTrue(now.contains("DevelopStatus033.label"))
        // O texto final é composto por estado + readerLabel; valide o contrato real,
        // não uma concatenação literal inexistente no fonte.
        assertTrue(status.contains("allOk -> \"OK ✓ — \$reader\""))
        assertTrue(status.contains("else -> \"M1/2.0\""))
        assertTrue(status.contains("ReaderLab027036.MODE_COMPARE -> \"M1/M2/2.0\""))
        assertFalse(now.contains("active -> ReaderLab027036.modeLabel"))
    }

    @Test fun darkInputsRemainPaletteAware() {
        val ui = read("app/src/main/java/com/bigcorps/driveraimvp/UiKit.kt")
        val preflight = read("app/src/main/java/com/bigcorps/driveraimvp/JourneyPreflight027037.kt")
        assertTrue(ui.contains("setTextColor(palette(context).ink)"))
        assertTrue(ui.contains("setHintTextColor(if (dark)"))
        assertTrue(preflight.contains("setTextColor(SrUi023.palette(context).ink)"))
        assertTrue(preflight.contains("setHintTextColor(SrUi023.palette(context).muted)"))
    }

    @Test fun reader2ConsensusStillHasNoOfficialEffect() {
        val consensus = read("app/src/main/java/com/bigcorps/driveraimvp/Reader2Consensus0321.kt")
        assertTrue(consensus.contains("controlled_hybrid_effect\", false"))
        listOf("LocalStore", "BackendClient", "OfferDispatcher", "saveOffer(", "sendOffer(").forEach {
            assertFalse("Consensus ganhou side effect proibido: $it", consensus.contains(it))
        }
    }

    @Test fun roadmapAllowsParallelProductTrack() {
        val roadmap = read("../ROADMAP-CANONICO.md")
        assertTrue(roadmap.contains("CURRENT_STAGE: 0.33.0 Field — Release Prep Pack 1"))
        assertTrue(roadmap.contains("trilhas paralelas"))
        assertTrue(roadmap.contains("0.34.x Navigation Pack"))
    }
}
