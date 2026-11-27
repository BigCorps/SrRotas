package com.srrotas.app.r3lab

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Color
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

/** BR-08 and BR-10: isolated tests; no live Reader or real personal images. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class R3CaptureSafety1126Test {
    private val app get() = RuntimeEnvironment.getApplication()
    private lateinit var db: R3LabDb
    private val profiles = R3ProfileManager().also(R3DefaultProfiles::installDefaults)

    @Before fun setup() {
        R3LabDb.resetForTest()
        app.deleteDatabase(R3LabDb.NAME)
        db = R3LabDb.get(app)
    }

    @After fun teardown() {
        R3LabDb.resetForTest()
        app.deleteDatabase(R3LabDb.NAME)
        File(app.filesDir, "r3lab").deleteRecursively()
    }

    @Test fun onlyAnObservedCandidateWithExplicitImageConsentMayKeepAnUnparsedFrame() {
        val gate = R3UnparsedEvidenceGate
        assertTrue(gate.saveCandidateFrame(true, true, 0, true))
        assertFalse(gate.saveCandidateFrame(true, true, 0, false))
        assertFalse(gate.saveCandidateFrame(true, false, 0, true))
        assertFalse(gate.saveCandidateFrame(true, true, 1, true))
        assertFalse(gate.saveCandidateFrame(false, true, 0, true))
    }

    @Test fun aSingleTickErrorDoesNotTriggerFallbackAndSuccessfulTickResetsTheSequence() {
        val gate = R3TickFailureGate()
        assertFalse(gate.onFailure())
        gate.onSuccess()
        assertFalse(gate.onFailure())
        assertFalse(gate.onFailure())
        assertTrue(gate.onFailure())
        gate.reset()
        assertFalse(gate.onFailure())
    }

    @Test fun combinedDiagnosticIncludesOnlyRedactedCaptureCounters() {
        val cwd = File(System.getProperty("user.dir") ?: ".").absoluteFile
        val root = generateSequence(cwd) { it.parentFile }
            .first { File(it, "app/src/main/java/com/bigcorps/driveraimvp/ReaderLabCombinedDiagnostic0270361.kt").isFile }
        val source = File(root,
            "app/src/main/java/com/bigcorps/driveraimvp/ReaderLabCombinedDiagnostic0270361.kt").readText()
        assertTrue(source.contains("r3_capture_quality_1126"))
        assertTrue(source.contains("R3LabTelemetry.snapshot()"))
        assertTrue(source.contains("includeRawOcr=false"))
        assertFalse(source.contains("frame.bitmap"))
    }

    @Test fun unparsedFrameRemainsPrivateAndUnlinkedAndObeysOriginalRetention() {
        val frameId = "frame-unparsed-01"
        val observed = R3CaptureEnvelope(frameId, 1, 1,
            profiles.activeFor(R3AllowedPackages.UBER)!!,
            R3AllowedPackages.UBER, 3, 0, 1_000,
            R3SourceKind.WINDOW, intArrayOf(0, 0, 10, 10))
        db.insertFrameWithObservation(observed, 32, 24, R3EvidenceStatus.STAGED,
            null, "obs-unparsed-01", "C", 2, 0, 60)
        val image = Bitmap.createBitmap(32, 24, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.LTGRAY) }
        try {
            val store = R3EvidenceStore(app, db)
            assertTrue(store.persist(frameId, image))
            assertEquals(R3EvidenceStatus.READY, db.frame(frameId)?.status)
            assertEquals("sha256_ok", store.verifiedFile(frameId).second)
            assertTrue("No fake offer for this picture", db.offers().isEmpty())
            assertNull("No invented original screenshot link", db.officialLink("not-a-real-offer"))
            store.enforceRetention(nowMs = 1_000 + 601_000L)
            assertEquals(R3EvidenceStatus.PURGED, db.frame(frameId)?.status)
        } finally {
            image.recycle()
        }
    }
}
