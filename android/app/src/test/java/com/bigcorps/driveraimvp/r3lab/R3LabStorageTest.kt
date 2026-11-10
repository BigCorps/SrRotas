package com.srrotas.app.r3lab

import android.app.Application
import android.graphics.Bitmap
import android.graphics.Color
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.security.MessageDigest

/** Banco isolado + evidência: vínculo exato N:N, SHA-256 dos bytes, MISSING/PURGED sem troca de imagem. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class R3LabStorageTest {
    private val app get() = RuntimeEnvironment.getApplication()
    private val profiles = R3ProfileManager().also(R3DefaultProfiles::installDefaults)
    private lateinit var db: R3LabDb

    @Before fun setUp() { R3LabDb.resetForTest(); app.deleteDatabase(R3LabDb.NAME); db = R3LabDb.get(app) }
    @After fun tearDown() { R3LabDb.resetForTest(); app.deleteDatabase(R3LabDb.NAME); File(app.filesDir, "r3lab").deleteRecursively() }

    private fun frame(id: String, pkg: String = R3AllowedPackages.UBER, status: R3EvidenceStatus = R3EvidenceStatus.STAGED) {
        db.insertFrame(R3CaptureEnvelope(id, 1, 1, profiles.activeFor(pkg)!!, pkg, 3, 0, 1_000, R3SourceKind.WINDOW, intArrayOf(0, 0, 10, 10)), 10, 10, status, null)
    }
    private fun seen(frame: String, obs: String, fare: String) = R3Stabilizer.Seen(frame, obs, R3Candidate("uber",
        mapOf("fare" to R3FieldEvidence("fare", frame, obs, fare, R3FieldOrigin.OBSERVED, 0.9)), null, null, 0.9), 0)
    private fun stable(vararg s: R3Stabilizer.Seen) = R3Stabilizer.Stable(java.util.UUID.randomUUID().toString(), "uber", s.first(), s.drop(1),
        s.first().candidate.fields, true, null)

    @Test fun commitLinksExactlyTheContributingObservationsNtoN() {
        frame("f1"); frame("f2"); db.insertObservation("o1", "f1", "C", 10, 2, 100); db.insertObservation("o2", "f2", "C", 10, 1, 100)
        val a = db.commitOffer(stable(seen("f1", "o1", "37,27"), seen("f2", "o2", "37,27")), "C")!!
        val b = db.commitOffer(stable(seen("f1", "o1", "18,40")), "C")!! // mesmo quadro, outra oferta
        assertEquals(listOf("f1" to R3EvidenceRole.PRIMARY, "f2" to R3EvidenceRole.SUPPLEMENTAL), db.links(a).map { it.frameId to it.role })
        assertEquals(listOf("f1"), db.links(b).map { it.frameId })
        assertNotEquals(a, b)
    }

    @Test fun purgedOrForeignPackageFramesAreNeverLinked() {
        frame("f1"); db.insertObservation("o1", "f1", "C", 1, 1, 1)
        frame("f9", R3AllowedPackages.NINETY_NINE); db.insertObservation("o9", "f9", "C", 1, 1, 1)
        assertNull(db.commitOffer(stable(seen("f1", "o1", "1"), seen("f9", "o9", "1")), "C"))
        assertTrue(db.offers().isEmpty())
        assertTrue(db.updateImage("f1", R3EvidenceStatus.PURGED, null, null, "test"))
        assertNull(db.commitOffer(stable(seen("f1", "o1", "1")), "C"))
        assertFalse("PURGED is terminal", db.updateImage("f1", R3EvidenceStatus.READY, "x.webp", "a".repeat(64), null))
    }

    @Test fun storedBytesHaveVerifiableSha256AndMissingFileNeverOpensAnother() {
        val store = R3EvidenceStore(app, db)
        frame("f1"); frame("f2")
        val bmp = Bitmap.createBitmap(32, 24, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
        assertTrue(store.persist("f1", bmp)); assertTrue(store.persist("f2", bmp))
        val row = db.frame("f1")!!
        assertEquals(R3EvidenceStatus.READY, row.status)
        val bytes = File(store.dir, row.fileName!!).readBytes()
        assertEquals(MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }, row.sha256)
        assertEquals("sha256_ok", store.verifiedFile("f1").second)
        File(store.dir, row.fileName!!).delete()
        val (file, status) = store.verifiedFile("f1")
        assertNull(file); assertEquals("MISSING", status)
        assertEquals(R3EvidenceStatus.MISSING, db.frame("f1")!!.status)
        assertEquals("sha256_ok", store.verifiedFile("f2").second) // a outra imagem segue intacta e não é usada no lugar
        File(store.dir, db.frame("f2")!!.fileName!!).appendBytes(byteArrayOf(1))
        assertEquals("sha256_divergente", store.verifiedFile("f2").second)
    }

    @Test fun retentionPurgesUnlinkedFramesButKeepsLinkedOnes() {
        val store = R3EvidenceStore(app, db)
        frame("linked"); frame("orphan"); db.insertObservation("o1", "linked", "C", 1, 1, 1)
        val bmp = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
        store.persist("linked", bmp); store.persist("orphan", bmp)
        db.commitOffer(stable(seen("linked", "o1", "9")), "C")
        store.enforceRetention(nowMs = 1_000 + 601_000L)
        assertEquals(R3EvidenceStatus.PURGED, db.frame("orphan")!!.status)
        assertEquals(R3EvidenceStatus.READY, db.frame("linked")!!.status)
    }

    @Test fun groundTruthLabelsFeedPrecisionAndRecall() {
        frame("f1"); db.insertObservation("o1", "f1", "C", 1, 1, 1)
        val a = db.commitOffer(stable(seen("f1", "o1", "1")), "C")!!
        val b = db.commitOffer(stable(seen("f1", "o1", "2")), "C")!!
        db.setLabel(a, "TP"); db.setLabel(b, "FP"); db.addMissedOffer("uber")
        assertEquals(R3LabDb.Labels(1, 1, 1, 0), db.labels())
    }

    @Test fun operationalBuildsKeepR3Inert() {
        org.junit.Assume.assumeFalse("operational variants only", com.srrotas.app.BuildConfig.R3_LAB)
        val service = Robolectric.buildService(com.srrotas.app.DriverAccessibilityService::class.java).create().get()
        R3Supervisor.attach(service)
        assertFalse(R3Supervisor.isAttached())
        val root = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "app/build.gradle.kts").exists() }
        val manifest = File(root, "app/src/main/AndroidManifest.xml").readText()
        assertFalse("lab screens never ship in the operational manifest", manifest.contains("R3LabActivity") || manifest.contains("R3EvidenceViewerActivity"))
        assertTrue(File(root, "app/src/main/res/xml/accessibility_service_config.xml").readText().contains("android:packageNames=\"com.ubercab.driver\""))
        assertTrue(File(root, "app/src/r3lab/res/xml/accessibility_service_config.xml").readText().contains("com.ubercab.driver,com.app99.driver"))
        val gradle = File(root, "app/build.gradle.kts").readText()
        assertTrue(gradle.contains("applicationIdSuffix=\".r3lab\"") && gradle.contains("buildConfigField(\"boolean\",\"R3_LAB\",\"false\")"))
        // O laboratório nunca grava na base oficial nem chama o backend.
        File(root, "app/src/main/java/com/bigcorps/driveraimvp/r3lab").listFiles()!!.forEach { f ->
            val src = f.readText()
            listOf("LocalStore.get", "saveOffer(", "OfferDispatcher.", "JourneyCoordinator.", "supabase", "HttpURLConnection", "BackendClient", "JourneySyncClient").forEach {
                assertFalse("${f.name} uses $it", src.contains(it))
            }
        }
    }

    @Test fun labBuildNeverTalksToProductionBackend() {
        org.junit.Assume.assumeTrue("r3lab variant only", com.srrotas.app.BuildConfig.R3_LAB)
        val settings = com.srrotas.app.SettingsRepository(app).load()
        assertEquals("", settings.backendUrl); assertEquals("", settings.deviceToken)
        assertEquals("", com.srrotas.app.BuildConfig.ONESIGNAL_APP_ID)
        assertTrue(com.srrotas.app.BuildConfig.APPLICATION_ID.endsWith(".r3lab"))
    }

    @Test fun conflictParserFindsOperationalServices() {
        val (own, operational) = R3LabConflicts.parseEnabled(
            "com.srrotas.app/com.srrotas.app.DriverAccessibilityService:com.srrotas.app.r3lab/com.srrotas.app.DriverAccessibilityService", "com.srrotas.app.r3lab")
        assertTrue(own); assertEquals(listOf("com.srrotas.app"), operational)
    }
}
