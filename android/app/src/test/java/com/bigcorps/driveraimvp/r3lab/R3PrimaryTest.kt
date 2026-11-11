package com.srrotas.app.r3lab

import android.app.Application
import android.graphics.Rect
import com.srrotas.app.DriverSettings
import com.srrotas.app.SpatialOcrLine
import com.srrotas.app.UberSpatialParser0221
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

/**
 * vc112 (issue #7) — R3 como Reader principal com M1 de reserva. Fixtures redigidas a partir do OCR
 * REAL das capturas Uber de 28/10 (SM-X626B, 1288×1599): mesma geometria e mesmos números lidos,
 * endereços trocados por fictícios.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class R3PrimaryTest {
    private val app get() = RuntimeEnvironment.getApplication()
    private val settings = DriverSettings(costPerKm = 0.82)
    private val profile = R3ProfileManager().also(R3DefaultProfiles::installDefaults).activeFor(R3AllowedPackages.UBER)!!
    private fun l(l: Int, t: Int, r: Int, b: Int, text: String) = SpatialOcrLine(text, Rect(l, t, r, b))

    /** Caso 174120 como lido pelo ML Kit (2ª passada): "8min2.4km" sem separador. */
    private fun card(pickup: String = "* 8min2.4km", header: String? = "47 min * 12.9 km - Est. de R$2,89/km") = listOfNotNull(
        l(69, 698, 396, 780, "R$ 37,27"),
        header?.let { l(66, 803, 844, 847, it) },
        l(84, 879, 502, 923, "o +R$ 3,50 incluído"),
        l(80, 1010, 466, 1050, pickup),
        l(139, 1079, 1025, 1122, "Av. Exemplo Um, Bairro Fictício, São Paulo"),
        l(157, 1152, 371, 1197, "x 5,00 (4)"),
        l(65, 1257, 629, 1306, "O 39 minutos - 10.5 km"),
        l(142, 1335, 1043, 1380, "Rua Exemplo Dois, 76, Bairro Seis, São Paulo"),
        l(464, 1457, 824, 1495, "Tenho interesse"),
    )
    private fun interpret(lines: List<SpatialOcrLine>) = R3PrimaryInterpreter.interpret(profile, lines,
        runCatching { UberSpatialParser0221.parseLines(lines, lines.joinToString("\n") { it.text }, settings, 1288, 1599) }.getOrDefault(emptyList()),
        settings, 1288, "frame-1", "obs-1")

    @Before fun clean() { app.getSharedPreferences("r3_mode", 0).edit().clear().commit(); R3LabDb.resetForTest(); app.deleteDatabase(R3LabDb.NAME) }
    @After fun tearDown() { R3LabDb.resetForTest(); app.deleteDatabase(R3LabDb.NAME) }

    // ---------------------------------------------------------------- interpretação
    @Test fun noSeparatorBetweenMinAndKmIsReadAndValidatedByTheOfficialParser() {
        val r = interpret(card())
        val ok = r.candidates.single { it.accepted }.offer!!
        assertEquals(37.27, ok.fare, 0.001); assertEquals(8, ok.pickupMinutes); assertEquals(2.4, ok.pickupKm!!, 0.001)
        assertEquals(39, ok.tripMinutes); assertEquals(10.5, ok.tripKm!!, 0.001)
    }

    @Test fun lostDecimalIsRejectedWithItsOwnReasonNeverRepaired() {
        val r = interpret(card(pickup = "*& 8min:24km"))
        assertTrue(r.candidates.none { it.accepted })
        assertEquals("legs_decimal_lost", r.candidates.single().rejection)
    }

    @Test fun withoutTheHeaderTheImpossibleSpeedIsStillRejected() {
        // 24 km em 8 min (180 km/h): o mesmo guard físico do dispatcher barra já na interpretação.
        val r = interpret(card(pickup = "*& 8min:24km", header = null))
        assertTrue(r.candidates.none { it.accepted })
        assertTrue(r.candidates.any { it.rejection == "guard028:pickup_speed_outlier" })
    }

    @Test fun singleLegIsIncompleteNeverAnOffer() {
        val r = interpret(card().filterNot { it.text.startsWith("O 39") })
        assertTrue(r.candidates.none { it.accepted })
    }

    @Test fun officialAndProfileDisagreeingOnTheSameCardIsRejected() {
        val lines = card()
        val official = UberSpatialParser0221.parseLines(lines, lines.joinToString("\n") { it.text }, settings, 1288, 1599).single()
        val tampered = official.copy(pickupMinutes = 9)
        val r = R3PrimaryInterpreter.interpret(profile, lines, listOf(tampered), settings, 1288, "f", "o")
        assertEquals("official_profile_conflict", r.candidates.single().rejection)
        assertNull(r.candidates.single().offer)
    }

    @Test fun officialResultIsPreferredAndProfileOnlyFillsCardsWithoutOfficialOffer() {
        val lines = card()
        val official = UberSpatialParser0221.parseLines(lines, lines.joinToString("\n") { it.text }, settings, 1288, 1599)
        val r = R3PrimaryInterpreter.interpret(profile, lines, official, settings, 1288, "f", "o")
        if (official.isNotEmpty()) assertEquals("official", r.source) else assertEquals("profile", r.source)
        assertEquals(1, r.candidates.count { it.accepted })
    }

    @Test fun rowAssemblerJoinsPiecesOfTheSameVisualLineOnly() {
        val rows = R3RowAssembler.rows(listOf(l(60, 1000, 200, 1044, "& 8 min"), l(215, 1002, 400, 1046, "• 2.4 km"), l(60, 1080, 600, 1124, "Av. Exemplo")))
        assertEquals(listOf("& 8 min • 2.4 km", "Av. Exemplo"), rows.map { it.text })
    }

    // ---------------------------------------------------------------- modo, saúde e fallback
    @Test fun eligibilityKeepsM1ForWhoeverDidNotOptIn() {
        assertTrue(R3Mode.eligible(36, R3Mode.PRIMARY, disclosure = true, serviceEnabled = true))
        assertFalse(R3Mode.eligible(29, R3Mode.PRIMARY, true, true))
        assertFalse(R3Mode.eligible(36, R3Mode.M1_FORCE, true, true))
        assertFalse(R3Mode.eligible(36, R3Mode.PRIMARY, disclosure = false, serviceEnabled = true))
        assertFalse(R3Mode.eligible(36, R3Mode.PRIMARY, disclosure = true, serviceEnabled = false))
        if (!com.srrotas.app.BuildConfig.R3_LAB) assertFalse("not opted in → hooks never load the R3 engine", R3Mode.hooksEnabled(app))
    }

    @Test fun fallbackHoldsOnlyForTheJourneyWhereItHappenedAndManualRollbackClearsIt() {
        R3Mode.setFallback(app, "journey-A", "ocr_stuck")
        assertTrue(R3Mode.fallbackActive(app, "journey-A"))
        assertFalse("a new journey tries R3 again", R3Mode.fallbackActive(app, "journey-B"))
        R3Mode.select(app, R3Mode.M1_FORCE)
        assertEquals(R3Mode.M1_FORCE, R3Mode.selected(app))
        assertNull(R3Mode.fallback(app))
        R3Mode.select(app, R3Mode.PRIMARY); assertEquals(R3Mode.PRIMARY, R3Mode.selected(app))
    }

    @Test fun healthFallsBackOnCaptureFailuresButNeverOnParserRejections() {
        val h = R3Health(failLimit = 3, silentMs = 25_000)
        h.targetsVisible(0, true); h.screenshotOk(0)
        assertNull("parser rejections are not health signals", h.fallbackReason(10_000, ocrPoisoned = false))
        repeat(3) { h.screenshotFailed() }
        assertEquals("screenshot_failures", h.fallbackReason(10_000, false))
        h.reset(); assertEquals("ocr_stuck", h.fallbackReason(0, ocrPoisoned = true))
        h.reset(); h.targetsVisible(0, true)
        assertNull(h.fallbackReason(20_000, false)); assertEquals("no_screenshot_with_target", h.fallbackReason(26_000, false))
        h.reset(); h.targetsVisible(0, false); assertNull(h.fallbackReason(60_000, false))
    }

    // ---------------------------------------------------------------- evidência oficial
    @Test fun officialLinkIsExactAndRefusesObservationFromAnotherFrame() {
        val db = R3LabDb.get(app)
        fun frame(id: String) = db.insertFrameWithObservation(R3CaptureEnvelope(id, 1, 1, profile, R3AllowedPackages.UBER, 3, 0, 1_000,
            R3SourceKind.WINDOW, null), 10, 10, R3EvidenceStatus.FAILED, "consent_off", "obs-$id", "R3", 5, 1, 100)
        frame("f1"); frame("f2")
        assertTrue(db.linkOfficial("offer-1", "f1", "obs-f1"))
        assertFalse("observation of another frame", db.linkOfficial("offer-2", "f1", "obs-f2"))
        assertEquals(R3LabDb.OfficialLink("offer-1", "f1", "obs-f1"), db.officialLink("offer-1"))
        assertNull(db.officialLink("offer-2"))
        assertNull("never by value/time", db.officialLink("offer-3"))
    }

    @Test fun labDatabaseUpgradesAdditivelyToV2() {
        R3LabDb.resetForTest(); app.deleteDatabase(R3LabDb.NAME)
        val f = app.getDatabasePath(R3LabDb.NAME).apply { parentFile?.mkdirs() }
        android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(f, null).use { d ->
            d.execSQL("CREATE TABLE r3_frame (frame_id TEXT PRIMARY KEY, captured_at_ms INTEGER NOT NULL, source_package TEXT NOT NULL, window_id INTEGER, source_kind TEXT NOT NULL, bounds TEXT, profile_id TEXT NOT NULL, profile_generation INTEGER NOT NULL, profile_sha TEXT NOT NULL, operation_token INTEGER NOT NULL, service_generation INTEGER NOT NULL, width INTEGER NOT NULL, height INTEGER NOT NULL, storage_status TEXT NOT NULL, file_name TEXT, sha256 TEXT, last_error TEXT)")
            d.execSQL("CREATE TABLE r3_observation (observation_id TEXT PRIMARY KEY, frame_id TEXT NOT NULL, mode TEXT NOT NULL, line_count INTEGER NOT NULL, candidate_count INTEGER NOT NULL, ocr_ms INTEGER NOT NULL, created_at_ms INTEGER NOT NULL)")
            d.execSQL("INSERT INTO r3_frame VALUES ('old','1','com.ubercab.driver',1,'WINDOW',null,'p',1,'s',1,1,1,1,'READY','old.webp','${"a".repeat(64)}',null)")
            d.execSQL("INSERT INTO r3_observation VALUES ('old-obs','old','C',1,1,1,1)")
            d.version = 1
        }
        val db = R3LabDb.get(app)
        assertEquals(R3EvidenceStatus.READY, db.frame("old")!!.status) // nada apagado
        assertTrue(db.linkOfficial("offer-x", "old", "old-obs"))
    }

    // ---------------------------------------------------------------- caminho oficial (guards de fonte)
    @Test fun onlyValidatedOffersReachTheOfficialDispatcherAndLinkHappensAfterSave() {
        val root = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "app/build.gradle.kts").exists() }
        val src = File(root, "app/src/main/java/com/bigcorps/driveraimvp")
        val sup = File(src, "r3lab/R3Supervisor.kt").readText()
        val submit = sup.substringAfter("private fun submitOfficial").substringBefore("private fun onOfficialPersisted")
        assertTrue(submit.contains("filter { it.accepted }") && submit.contains(".submitStabilized(offers)"))
        assertFalse(sup.contains("LocalStore.get(") || sup.contains("saveOffer("))
        val dispatcher = File(src, "OfferDispatcher.kt").readText().substringAfter("private fun persist(")
        assertTrue("onPersisted only after a successful saveOffer",
            dispatcher.indexOf("onPersisted?.let") > dispatcher.indexOf("persist_duplicate_key"))
        val main = File(src, "ConsolidatedMainActivity027037.kt").readText()
        val r3Start = main.substringAfter("private fun startR3PrimaryJourney()").substringBefore("fun recoverCurrentJourneyCapture")
        assertFalse("R3 journey never asks for MediaProjection", r3Start.contains("requestCapturePermission"))
        assertTrue(main.contains("!com.srrotas.app.r3lab.R3Mode.effectivePrimary(this)) content.post { recoverCurrentJourneyCapture() }"))
        assertTrue(File(src, "NowPanel027037.kt").readText().contains("!r3Primary && CaptureResilience0311.needsRecovery"))
        assertTrue(File(src, "DiagnosticControls0270.kt").readText().contains("R3Mode.effectivePrimary(context)"))
        assertTrue("rescan slot also waits for R3 OCR", File(src, "JourneyRescanSlot111.kt").readText().contains("externalInFlight()"))
    }

    // ---------------------------------------------------------------- regressões da revisão (vc112)
    @Test fun primaryModeGateLetsAnEligibleUserCapture() {
        // P0 da revisão: no APK principal o gate devolvia NOT_LAB_BUILD para todos.
        val consent = R3LabConsent(labEnabled = true, accessibilityDisclosureAccepted = true)
        val env = R3LabEnvironment(36, labBuild = true /* labBuild() || primary */, accessibilityServiceEnabled = true, screenOn = true,
            officialM1CaptureActive = false, operationalAppVisible = false, anotherOcrRunning = false, targetPackage = R3AllowedPackages.UBER,
            operationalM1StopConfirmed = true)
        assertNull(R3LabGate.canCapture(consent, env))
        val src = File(generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "app/build.gradle.kts").exists() },
            "app/src/main/java/com/bigcorps/driveraimvp/r3lab/R3Supervisor.kt").readText()
        assertTrue(src.contains("labBuild = labBuild() || primary"))
        assertTrue("M1 active still blocks R3 in primary mode", R3LabGate.canCapture(consent, env.copy(officialM1CaptureActive = true)) == R3LabBlock.OFFICIAL_READER_ACTIVE)
    }

    @Test fun dotDecimalFareIsNotReadAsThousands() {
        assertEquals(37.27, R3PrimaryInterpreter.money("37.27")!!, 0.001)
        assertEquals(37.27, R3PrimaryInterpreter.money("37,27")!!, 0.001)
        assertEquals(1234.56, R3PrimaryInterpreter.money("1.234,56")!!, 0.001)
    }

    @Test fun labReaderModesExcludeR3() {
        app.getSharedPreferences("sr_reader_lab_027036", 0).edit().clear().commit()
        com.srrotas.app.ReaderLab027036.setMode(app, com.srrotas.app.ReaderLab027036.MODE_COMPARE)
        assertFalse(R3Mode.primaryEligible(app))
        com.srrotas.app.ReaderLab027036.setMode(app, com.srrotas.app.ReaderLab027036.MODE_M1)
    }
}
