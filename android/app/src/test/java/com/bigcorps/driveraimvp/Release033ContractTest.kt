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

    @Test
    fun screenshotStorageIsGuardedAndBounded() {
        val s = read("app/src/main/java/com/bigcorps/driveraimvp/PrivateScreenshotStore.kt")
        assertTrue(s.contains("ScreenshotStorageGuard033.allow"))
        assertTrue(s.contains("MAX_PRIVATE_FILES = 30"))
        assertTrue(s.contains("MAX_VISIBLE_FILES = 180"))
        assertTrue(s.contains("JPEG_QUALITY = 72"))
        assertTrue(s.contains("cropDiagnosticBitmap"))
    }

    @Test
    fun developJourneyStatusIsCompact() {
        val n = read("app/src/main/java/com/bigcorps/driveraimvp/NowPanel027037.kt")
        val s = read("app/src/main/java/com/bigcorps/driveraimvp/DevelopStatus033.kt")
        assertTrue(n.contains("DevelopStatus033.label"))
        assertTrue(s.contains("allOk -> \"OK ✓ — \$reader\""))
        assertTrue(s.contains("else -> \"M1/2.0\""))
        assertFalse(n.contains("active -> ReaderLab027036.modeLabel"))
    }

    @Test
    fun reader2ConsensusStillHasNoOfficialEffect() {
        val c = read("app/src/main/java/com/bigcorps/driveraimvp/Reader2Consensus0321.kt")
        assertTrue(c.contains("controlled_hybrid_effect\", false"))
        listOf("LocalStore", "BackendClient", "OfferDispatcher", "saveOffer(", "sendOffer(")
            .forEach { assertFalse(c.contains(it)) }
    }

    @Test
    fun continuityIsSingleSourceAndTracksCurrentHead() {
        val gradle = read("app/build.gradle.kts")
        val continuity = read("../README-CONTINUIDADE.md")

        val versionName = Regex("""versionName="([^"]+)"""")
            .find(gradle)?.groupValues?.get(1)
            ?: error("versionName não encontrado no build.gradle.kts")
        val versionCode = Regex("""versionCode=(\d+)""")
            .find(gradle)?.groupValues?.get(1)
            ?: error("versionCode não encontrado no build.gradle.kts")
        val currentVersion = "$versionName / versionCode $versionCode"

        assertTrue(continuity.contains("SINGLE_SOURCE_OF_TRUTH: true"))
        assertTrue(continuity.contains("CURRENT_HEAD_STAGE:"))
        assertTrue(continuity.contains("HEAD atual: `$currentVersion`"))
        assertTrue(continuity.contains("Base Android homologada: `0.33.6-field / versionCode 83`"))
        assertTrue(continuity.contains("Field validada mais recente:"))
        assertFalse(continuity.contains("Roadmap mestre:"))
    }
}
