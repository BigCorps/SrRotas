package com.srrotas.app

import android.app.Application
import android.graphics.Bitmap
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.shadows.ShadowLooper
import org.robolectric.annotation.Config
import java.io.File

/**
 * vc111 P1 — releitura com jornada ativa. Diagnóstico 34: `rescan_trace_109` registrou
 * BLOQUEADA/JOURNEY em todas as tentativas durante a jornada (só funcionou depois de encerrar).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class JourneyRescan111Test {
    private val app get() = RuntimeEnvironment.getApplication()
    @Before fun setUp() { OfficialOcrHold111.resetForTest(); File(app.filesDir, "journey_rescan_slot_111.json").delete() }
    @After fun tearDown() { OfficialOcrHold111.resetForTest(); SettingsRepository(app).setCurrentJourney("", "") }

    @Test fun onlyBetweenRidesAndNeverDuringHistoryScan() {
        assertNull(JourneyRescanSlot111.decide(rescanEnabled = true, rideInProgress = false, historyScanActive = false))
        assertEquals(JourneyRescanSlot111.Denial.RIDE_IN_PROGRESS, JourneyRescanSlot111.decide(true, true, false))
        assertEquals(JourneyRescanSlot111.Denial.HISTORY_SCAN, JourneyRescanSlot111.decide(true, false, true))
        assertEquals(JourneyRescanSlot111.Denial.DISABLED, JourneyRescanSlot111.decide(false, false, false))
        assertTrue(JourneyRescanSlot111.Denial.RIDE_IN_PROGRESS.message.contains("pendente"))
    }

    @Test fun holdSkipsOfficialFramesBrieflyAndReleasesWithoutLosingTheReference() {
        val detector = FrameChangeDetector()
        val a = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.WHITE) }
        val b = Bitmap.createBitmap(64, 64, Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.BLACK) }
        assertTrue(detector.evaluate(a, 1_000).process)
        OfficialOcrHold111.hold(JourneyRescanSlot111.HOLD_MAX_MS, now = 1_000)
        assertFalse("changed frame is held while the rescan OCR runs", detector.evaluate(b, 1_500).process)
        assertFalse("hold never exceeds its limit", OfficialOcrHold111.active(1_000 + JourneyRescanSlot111.HOLD_MAX_MS + 1))
        OfficialOcrHold111.release(2_000)
        assertTrue("next changed frame is processed normally after release", detector.evaluate(b, 2_100).process)
        assertTrue(OfficialOcrHold111.framesHeld >= 1)
    }

    @Test fun grantWaitsForTheOfficialOcrInFlightThenReleases() {
        OfficialOcrHold111.onTrace("FRAME_CAPTURED") // OCR oficial em curso
        JourneyRescanSlot111.onRequest(app, "nonce-0001")
        assertNotEquals("granted", JourneyRescanSlot111.read(app)?.state)
        assertTrue(OfficialOcrHold111.active())
        OfficialOcrHold111.onTrace("OCR_OK")
        ShadowLooper.idleMainLooper(200, java.util.concurrent.TimeUnit.MILLISECONDS)
        assertEquals("granted", JourneyRescanSlot111.read(app)!!.state)
        assertTrue(JourneyRescanSlot111.grantValid(app))
        // Gate no :rescan: com jornada ativa e concessão vigente, a releitura é liberada.
        SettingsRepository(app).setCurrentJourney("journey-111", "2026-11-05T10:00:00Z")
        assertNull(ScreenshotRescanGateV1.precheck(app))
        JourneyRescanSlot111.finish(app, "nonce-0001", "done")
        assertFalse(OfficialOcrHold111.active())
        assertEquals(ScreenshotRescanBlockV1.JOURNEY, ScreenshotRescanGateV1.precheck(app))
    }

    @Test fun journeyWithoutGrantStillBlocksAndDeadlineReleasesTheReader() {
        SettingsRepository(app).setCurrentJourney("journey-111", "2026-11-05T10:00:00Z")
        assertEquals(ScreenshotRescanBlockV1.JOURNEY, ScreenshotRescanGateV1.precheck(app))
        JourneyRescanSlot111.onRequest(app, "nonce-0002")
        ShadowLooper.idleMainLooper(JourneyRescanSlot111.HOLD_MAX_MS + 50, java.util.concurrent.TimeUnit.MILLISECONDS)
        assertFalse("never a permanent pause of the official Reader", OfficialOcrHold111.active())
        assertEquals("released", JourneyRescanSlot111.read(app)!!.state)
    }

    @Test fun pendingImageStaysPrivateUntilProcessed() {
        val bmp = Bitmap.createBitmap(20, 20, Bitmap.Config.ARGB_8888)
        PendingRescanImage111.save(app, bmp)
        assertTrue(PendingRescanImage111.file(app).exists())
        assertTrue(PendingRescanImage111.file(app).absolutePath.startsWith(app.filesDir.absolutePath))
        PendingRescanImage111.clear(app); assertFalse(PendingRescanImage111.file(app).exists())
    }

    @Test fun receiverIsExplicitNotExportedAndRescanUiUsesTheSlot() {
        val root = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "app/build.gradle.kts").exists() }
        val manifest = File(root, "app/src/main/AndroidManifest.xml").readText()
        assertTrue(manifest.contains(".JourneyRescanReceiver111\" android:exported=\"false\""))
        val ui = File(root, "app/src/main/java/com/bigcorps/driveraimvp/OfferRescanActivityV1.kt").readText()
        assertTrue(ui.contains("JourneyRescanSlot111.request(") && ui.contains("JourneyRescanSlot111.release("))
        // Nenhuma segunda MediaProjection / captura.
        listOf("getMediaProjection", "createVirtualDisplay", "createScreenCaptureIntent").forEach {
            assertFalse(it, File(root, "app/src/main/java/com/bigcorps/driveraimvp/JourneyRescanSlot111.kt").readText().contains(it))
        }
    }
}
