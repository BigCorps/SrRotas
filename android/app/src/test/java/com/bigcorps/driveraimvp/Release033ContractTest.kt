package com.srrotas.app
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
class Release033ContractTest {
 private fun read(path:String):String{val candidates=listOf(File(path),File("../$path"),File("../../$path"));return candidates.firstOrNull{it.exists()}?.readText()?:error("Arquivo não encontrado no teste: $path")}
 @Test fun screenshotStorageIsGuardedAndBounded(){val s=read("app/src/main/java/com/bigcorps/driveraimvp/PrivateScreenshotStore.kt");assertTrue(s.contains("ScreenshotStorageGuard033.allow"));assertTrue(s.contains("MAX_PRIVATE_FILES = 30"));assertTrue(s.contains("MAX_VISIBLE_FILES = 180"));assertTrue(s.contains("JPEG_QUALITY = 72"));assertTrue(s.contains("cropDiagnosticBitmap"))}
 @Test fun developJourneyStatusIsCompact(){val n=read("app/src/main/java/com/bigcorps/driveraimvp/NowPanel027037.kt");val s=read("app/src/main/java/com/bigcorps/driveraimvp/DevelopStatus033.kt");assertTrue(n.contains("DevelopStatus033.label"));assertTrue(s.contains("allOk -> \"OK ✓ — $reader\"".replace("$reader","\\$reader")));assertTrue(s.contains("else -> \"M1/2.0\""));assertFalse(n.contains("active -> ReaderLab027036.modeLabel"))}
 @Test fun reader2ConsensusStillHasNoOfficialEffect(){val c=read("app/src/main/java/com/bigcorps/driveraimvp/Reader2Consensus0321.kt");assertTrue(c.contains("controlled_hybrid_effect\", false"));listOf("LocalStore","BackendClient","OfferDispatcher","saveOffer(","sendOffer(").forEach{assertFalse(c.contains(it))}}
 @Test fun roadmapTracksCurrentFieldAndHeadSemantically(){val r=read("../ROADMAP-CANONICO.md");val c=read("../README-CONTINUIDADE.md");assertTrue(r.contains("CANONICAL_VERSION:"));assertTrue(r.contains("CURRENT_HEAD_STAGE:"));assertTrue(r.contains("0.33.8-field / versionCode 85"));assertTrue(r.contains("0.33.6-field / versionCode 83 — HOMOLOGADA"));assertTrue(r.contains("Módulos independentes podem avançar em paralelo"));assertTrue(c.contains("Base Android homologada: `0.33.6-field / versionCode 83`"));assertTrue(c.contains("HEAD 0.33.8"))}
}
