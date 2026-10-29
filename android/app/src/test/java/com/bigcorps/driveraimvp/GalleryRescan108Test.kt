package com.srrotas.app

import android.app.Application
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

/**
 * vc108 — releitura pela galeria.
 *
 * Causas demonstradas: (1) o MlKitInitProvider do ML Kit não declara processo e só roda no
 * processo principal; no `:rescan` `MlKitContext.getInstance()` lança "has not been initialized",
 * então todo OCR terminava na mensagem genérica de falha. (2) preferências em cache no processo
 * `:rescan` mantinham "Jornada ativa" depois do fim da jornada. Complemento: só campos ausentes,
 * após confirmação, revalidado no processo principal, com histórico auditável.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class GalleryRescan108Test {
    private val root = generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
        .first { File(it, "app/build.gradle.kts").exists() }
    private fun src(name: String) = File(root, "app/src/main/java/com/bigcorps/driveraimvp/$name").readText()
    private fun ctx(pickup: String? = null, destination: String? = null, confidence: Double = 0.95) =
        OfferContext(pickupLabel = pickup, destinationLabel = destination, contextConfidence = confidence, geocodeStatus = "pending")

    @Test fun mlKitIsInitializedInTheReviewProcessAndPreferencesAreReloaded() {
        // The library's MlKitInitProvider has no android:process, so only the default process gets it.
        val app = src("SrRotasApplication.kt")
        val review = app.substringAfter("if (isReviewProcess) {").substringBefore("return")
        assertTrue(review.contains("MlKitContext.initializeIfNeeded(this)"))
        val activity = src("OfferRescanActivityV1.kt")
        val refresh = activity.substringAfter("private fun refreshSafety()").substringBefore("ScreenshotRescanGateV1.precheck(this)")
        assertTrue(refresh.contains("RescanProcessStateV108.reload(this)"))
        assertTrue(activity.contains("status.text = \"Abrindo imagem…\""))
        assertEquals(1, Regex("TextRecognition\\.getClient\\(").findAll(activity).count())
        // Rotation/split no longer recreates the activity mid-OCR.
        val manifest = File(root, "app/src/main/AndroidManifest.xml").readText()
        assertTrue(manifest.contains("android:process=\":rescan\" android:configChanges=\"orientation|screenSize"))
        assertTrue(manifest.contains("<receiver android:name=\".RescanSupplementReceiverV108\" android:exported=\"false\" />"))
    }

    @Test fun everyTerminalStateHasAnObservableMessage() {
        assertTrue(ScreenshotRescanFailureV108.message(IllegalArgumentException("multiple_cards")).contains("mais de um card"))
        assertTrue(ScreenshotRescanFailureV108.message(IllegalStateException("MlKitContext has not been initialized")).contains("indisponível"))
        assertTrue(ScreenshotRescanFailureV108.message(OutOfMemoryError()).contains("grande"))
        assertTrue(ScreenshotRescanFailureV108.message(RuntimeException("x")).contains("RuntimeException"))
        val activity = src("OfferRescanActivityV1.kt")
        assertTrue(activity.contains("nenhuma oferta reconhecida nesta imagem"))
        assertTrue(activity.contains("ScreenshotRescanFailureV108.message(error)"))
    }

    @Test fun onlyMissingFieldsAreProposedWithEvidence() {
        val original = Fixtures108.offer("o1", 17.99, context = ctx(pickup = "Rua Alfa, 120"))
        val fresh = Fixtures108.offer("tmp", 17.99, context = ctx(pickup = "Rua Alfa, 999", destination = "Avenida Beta, 900"))
        val p = RescanSupplementV108.proposals(original, fresh)
        assertEquals(listOf(RescanSupplementV108.FIELD_DESTINATION), p.map { it.field })
        assertEquals("Avenida Beta, 900", p.single().value)
        // Different fare = not the same offer; weak context or swapped legs are not proposed.
        assertTrue(RescanSupplementV108.proposals(original, fresh.copy(fare = 18.99)).isEmpty())
        assertTrue(RescanSupplementV108.proposals(original, fresh.copy(context = fresh.context!!.copy(contextConfidence = 0.52))).isEmpty())
        assertTrue(RescanSupplementV108.proposals(original, fresh.copy(context = ctx(destination = "Rua Alfa, 120"))).isEmpty())
        assertTrue(RescanSupplementV108.proposals(null, fresh).isEmpty())
        // Both missing: both proposed.
        val bare = Fixtures108.offer("o2", 17.99)
        assertEquals(2, RescanSupplementV108.proposals(bare, fresh).size)
    }

    @Test fun mainProcessRevalidatesAppliesOnceAndKeepsAuditTrail() {
        val app = RuntimeEnvironment.getApplication()
        val store = LocalStore.get(app)
        val offer = Fixtures108.offer("o-apply", 22.40, context = ctx(pickup = "Rua Alfa, 120"))
        assertTrue(store.saveOffer(offer))
        store.saveOrUpdateContext(offer.localId, offer.context!!)
        val proposal = listOf(RescanSupplementV108.Proposal(RescanSupplementV108.FIELD_DESTINATION, "Avenida Beta, 900", "Destino"))
        assertTrue(RescanSupplementV108.request(app, offer.localId, proposal, "abc123", 0.95))
        val file = File(app.filesDir, "rescan-supplements").listFiles()!!.single()
        val applied = RescanSupplementV108.apply(app, file.name)!!
        assertEquals(listOf(RescanSupplementV108.FIELD_DESTINATION), applied.fields)
        assertFalse("request file is consumed", file.exists())
        val saved = store.contextForOffer(offer.localId)!!
        assertEquals("Rua Alfa, 120", saved.pickupLabel)
        assertEquals("Avenida Beta, 900", saved.destinationLabel)
        // Fare and times are untouched; the offer is not duplicated.
        val again = store.offerByLocalId(offer.localId)!!
        assertEquals(22.40, again.fare, 0.001); assertEquals(15, again.tripMinutes)
        val audit = RescanSupplementV108.history(app, offer.localId).single()
        assertTrue(audit.isNull("before")); assertEquals("Avenida Beta, 900", audit.getString("after"))
        assertEquals("abc123", audit.getString("image_fingerprint")); assertEquals(1, audit.getInt("revision"))
        // A second request never overwrites an existing value.
        RescanSupplementV108.request(app, offer.localId,
            listOf(RescanSupplementV108.Proposal(RescanSupplementV108.FIELD_DESTINATION, "Rua Outra, 1", "Destino")), null, 0.95)
        val second = RescanSupplementV108.apply(app, File(app.filesDir, "rescan-supplements").listFiles()!!.single().name)!!
        assertTrue(second.fields.isEmpty()); assertEquals(listOf(RescanSupplementV108.FIELD_DESTINATION), second.skipped)
        assertEquals("Avenida Beta, 900", store.contextForOffer(offer.localId)!!.destinationLabel)
        assertEquals(1, RescanSupplementV108.history(app, offer.localId).size)
    }

    @Test fun unknownOrTamperedRequestsDoNothing() {
        val app = RuntimeEnvironment.getApplication()
        assertNull(RescanSupplementV108.apply(app, "../../shared_prefs/x.json"))
        val dir = File(app.filesDir, "rescan-supplements").apply { mkdirs() }
        File(dir, "bad.json").writeText("{not json")
        assertNull(RescanSupplementV108.apply(app, "bad.json"))
        File(dir, "missing.json").writeText("""{"offer_id":"nope","fields":[{"field":"destination_label","value":"Avenida Beta, 900"}]}""")
        assertNull(RescanSupplementV108.apply(app, "missing.json"))
    }

    @Test fun reviewProcessStillHasNoDirectOfficialWrites() {
        val activity = src("OfferRescanActivityV1.kt")
        listOf("saveOffer(", "saveOrUpdateContext", "BackendClient", "Geocoder", "updateRideOutcome").forEach {
            assertFalse(it, activity.contains(it))
        }
        // The only write path is the explicit, confirmed request.
        assertTrue(activity.substringAfter("setPositiveButton(\"Completar\")").contains("RescanSupplementV108.request("))
    }
}
