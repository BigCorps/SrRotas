package com.srrotas.app

import android.app.Application
import android.graphics.Bitmap
import com.srrotas.app.r3lab.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class OriginalCaptureLookupTest {
    private val app get() = RuntimeEnvironment.getApplication()
    private lateinit var db: R3LabDb
    @Before fun setup() { R3LabDb.resetForTest(); app.deleteDatabase(R3LabDb.NAME); db = R3LabDb.get(app); PrivateScreenshotIndexV1.clear(app) }
    @After fun cleanup() { R3LabDb.resetForTest(); app.deleteDatabase(R3LabDb.NAME); File(app.filesDir, "r3lab").deleteRecursively(); File(app.filesDir, "private-offer-captures").deleteRecursively(); PrivateScreenshotIndexV1.clear(app) }
    private fun linked(id: String, error: String? = null): R3EvidenceStore {
        val profile = R3ProfileManager().also(R3DefaultProfiles::installDefaults).activeFor(R3AllowedPackages.UBER)!!
        db.insertFrameWithObservation(R3CaptureEnvelope(id, 1, 1, profile, R3AllowedPackages.UBER, 3, 0, 1_000,
            R3SourceKind.WINDOW, null), 10, 10, if (error == null) R3EvidenceStatus.STAGED else R3EvidenceStatus.FAILED,
            error, "obs-$id", "R3", 5, 1, 100)
        assertTrue(db.linkOfficial("offer-$id", id, "obs-$id"))
        return R3EvidenceStore(app, db)
    }
    private fun ready(id: String): R3EvidenceStore {
        val store = linked(id)
        val bitmap = Bitmap.createBitmap(10, 10, Bitmap.Config.ARGB_8888)
        assertTrue(store.persist(id, bitmap)); bitmap.recycle(); return store
    }
    @Test fun exactIdFindsOnlyItsOwnFrameRegardlessOfOtherOffersOrCaptureMethodLabel() {
        repeat(50) { ready("$it") }
        for (i in listOf(0, 24, 49)) {
            val result = OriginalCaptureLookupV1.resolve(app, "offer-$i")
            assertEquals("$i", result.frameId); assertEquals("obs-$i", result.observationId)
            assertEquals(1_000L, result.capturedAtMs); assertNotNull(result.file)
            assertEquals(db.frame("$i")!!.sha256, result.sha256)
        }
        assertNull(OriginalCaptureLookupV1.resolve(app, "offer-50").file)
        assertNull(OriginalCaptureLookupV1.resolve(app, "offer-4").file?.takeIf { it.name == "49.webp" })
        assertNull(OriginalCaptureLookupV1.resolve(app, "").file)
    }
    @Test fun missingTamperedPurgedAndUnconsentedImagesExplainAbsenceNeverFallback() {
        ready("good")
        ready("missing"); File(R3EvidenceStore(app, db).dir, "missing.webp").delete()
        ready("tampered"); File(R3EvidenceStore(app, db).dir, "tampered.webp").writeText("changed")
        val purged = ready("purged"); purged.purge("purged", "retention_days")
        linked("off", "consent_off"); linked("revoked", "consent_revoked")
        for ((id, text) in mapOf("missing" to "não está mais disponível", "tampered" to "não confere",
            "purged" to "retenção", "off" to "desativado", "revoked" to "revogado")) {
            val result = OriginalCaptureLookupV1.resolve(app, "offer-$id")
            assertNull(result.file); assertNull(result.sha256); assertTrue(result.reason, result.reason.contains(text))
            assertFalse(result.reason.contains(app.filesDir.path))
        }
        assertNotNull(OriginalCaptureLookupV1.resolve(app, "offer-good").file)
    }
    @Test fun legacyExactIndexWithoutOriginalDigestIsNeverClaimedVerifiedOrReassociated() {
        val dir = File(app.filesDir, "private-offer-captures").apply { mkdirs() }
        val file = File(dir, "legacy.jpg").apply { writeText("legacy bytes without saved digest") }
        PrivateScreenshotIndexV1.saved(app, "legacy-id", file)
        val result = OriginalCaptureLookupV1.resolve(app, "legacy-id")
        assertNull(result.file); assertTrue(result.reason.contains("hash de origem"))
        assertNull(OriginalCaptureLookupV1.resolve(app, "other-id").file)
        assertTrue(OriginalCaptureLookupV1.DIGITIZED_REASON.contains("sem imagem original vinculada"))
    }
    @Test fun storageAccessFailureIsReportedWithoutPathOrBreakingHistory() {
        ready("path"); db.updateImage("path", R3EvidenceStatus.READY, "../../private.txt", "a".repeat(64), null)
        val result = OriginalCaptureLookupV1.resolve(app, "offer-path")
        assertNull(result.file); assertFalse(result.reason.contains("private.txt")); assertFalse(result.reason.contains("/"))
    }
    private fun texts(v: android.view.View): List<android.widget.TextView> = when (v) {
        is android.widget.TextView -> listOf(v)
        is android.view.ViewGroup -> (0 until v.childCount).flatMap { texts(v.getChildAt(it)) }
        else -> emptyList()
    }
    private fun awaitViewer(activity: android.app.Activity) {
        val deadline = System.nanoTime() + 5_000_000_000L
        while (texts(activity.window.decorView).any { it.text == "Consultando a captura original…" } && System.nanoTime() < deadline) {
            Thread.sleep(10); org.robolectric.shadows.ShadowLooper.idleMainLooper()
        }
        assertFalse("viewer must finish without blocking UI", texts(activity.window.decorView).any { it.text == "Consultando a captura original…" })
    }
    @Test fun historyShowsAnExactIdActionEvenForAnOfferWithoutReadyR3Image() {
        val offer = Fixtures108.offer("history-no-image", 20.0)
        LocalStore.get(app).saveOffer(offer)
        val activity = org.robolectric.Robolectric.buildActivity(android.app.Activity::class.java).setup().get()
        val panel = RideHistoryPanel027035(activity); activity.setContentView(panel); panel.refresh()
        texts(panel).first { it.text == "Ver captura original" }.performClick()
        val intent = org.robolectric.Shadows.shadowOf(activity).nextStartedActivity
        assertEquals(offer.localId, intent.getStringExtra(R3EvidenceViewerActivity.EXTRA_OFFICIAL))
        assertEquals(R3EvidenceViewerActivity::class.java.name, intent.component!!.className)
        activity.finish()
    }
    @Test fun viewerExplainsAbsenceAndSurvivesRotationWithTheSameExactIdentity() {
        val intent = android.content.Intent(app, R3EvidenceViewerActivity::class.java)
            .putExtra(R3EvidenceViewerActivity.EXTRA_OFFICIAL, "not-linked")
        val controller = org.robolectric.Robolectric.buildActivity(R3EvidenceViewerActivity::class.java, intent).setup()
        var activity = controller.get(); awaitViewer(activity)
        assertTrue(texts(activity.window.decorView).any { it.text == OriginalCaptureLookupV1.UNAVAILABLE })
        assertTrue(activity.window.attributes.flags and android.view.WindowManager.LayoutParams.FLAG_SECURE != 0)
        controller.recreate(); activity = controller.get(); awaitViewer(activity)
        assertEquals("not-linked", activity.intent.getStringExtra(R3EvidenceViewerActivity.EXTRA_OFFICIAL))
        assertTrue(texts(activity.window.decorView).any { it.text == OriginalCaptureLookupV1.UNAVAILABLE })
        controller.pause().stop().destroy()
    }
    @Test fun viewerShowsTheVerifiedFullIdentifiersAndDigestWithoutOcr() {
        ready("view")
        val intent = android.content.Intent(app, R3EvidenceViewerActivity::class.java)
            .putExtra(R3EvidenceViewerActivity.EXTRA_OFFICIAL, "offer-view")
        val controller = org.robolectric.Robolectric.buildActivity(R3EvidenceViewerActivity::class.java, intent).setup()
        val activity = controller.get(); awaitViewer(activity)
        val labels = texts(activity.window.decorView).map { it.text.toString() }
        assertTrue(labels.any { it.contains("Oferta offer-view · quadro view · observação obs-view") })
        assertTrue(labels.any { it == "SHA-256: ${db.frame("view")!!.sha256}" })
        controller.pause().stop().destroy()
    }

}
