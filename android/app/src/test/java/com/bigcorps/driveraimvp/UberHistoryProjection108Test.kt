package com.srrotas.app

import android.app.Application
import android.os.Looper
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File
import java.time.Instant

/**
 * vc108 — histórico Uber pela MediaProjection oficial: nenhum consentimento, projeção ou OCR
 * novo; consumidor limitado do texto já reconhecido; mesma revisão; dedup entre quadros e leituras.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class UberHistoryProjection108Test {
    private val root = generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
        .first { File(it, "app/build.gradle.kts").exists() }
    private fun src(name: String) = File(root, "app/src/main/java/com/bigcorps/driveraimvp/$name").readText()

    private fun card(fare: String, line: String, a: String = "Rua Alfa, Bairro Um - São Paulo - SP", b: String = "Avenida Beta, Bairro Dois - São Paulo - SP") =
        "R$ $fare\n$line\n$a\n$b"
    private val header = "Ganhos\nqui., 29 de out.\n"

    @After fun tearDown() { UberHistoryProjectionTap108.cancel(RuntimeEnvironment.getApplication()) }

    @Test fun tapNeverOwnsAProjectionAndIsInertWhenInactive() {
        val tap = src("UberHistoryProjectionTap108.kt")
        listOf("getMediaProjection", "createVirtualDisplay", "ImageReader", "TextRecognition", "createScreenCaptureIntent").forEach {
            assertFalse(it, tap.contains(it))
        }
        // The official service is untouched; its OCR text reaches the tap through the existing trace call.
        assertTrue(src("MediaProjectionOcrService.kt").contains("RadarHudTrace024.recordOcr("))
        val trace = src("RadarHudTrace024.kt").substringAfter("fun recordOcr(").substringBefore("fun recordRoute(")
        assertTrue(trace.contains("runCatching { UberHistoryProjectionTap108.offer(rawText) }"))
        // The activity uses the official projection before asking for a new consent.
        val activity = src("UberDigitizationActivity026.kt").substringAfter("private fun requestCapture()")
        assertTrue(activity.indexOf("UberHistoryProjectionTap108.officialProjectionActive(this)") < activity.indexOf("createScreenCaptureIntent()"))
        assertFalse(UberHistoryProjectionTap108.isActive())
        UberHistoryProjectionTap108.offer("R$ 10,00 qualquer texto de oferta longo o bastante")
        assertEquals(0, UberHistoryProjectionTap108.progress().frames)
    }

    @Test fun withoutOfficialProjectionTheLegitimateConsentFlowIsKept() {
        val app = RuntimeEnvironment.getApplication()
        assertEquals(UberHistoryProjectionTap108.Start.NO_OFFICIAL_PROJECTION, UberHistoryProjectionTap108.start(app))
        assertEquals(UberHistoryProjectionTap108.Start.NO_OFFICIAL_PROJECTION, UberHistoryProjectionTap108.start(app, officialActive = false))
        assertTrue(src("UberDigitizationActivity026.kt").contains("manager.createScreenCaptureIntent()"))
    }

    @Test fun scrollFramesAreDedupedFinishedAndHandedToTheExistingReview() {
        val app = RuntimeEnvironment.getApplication()
        assertEquals(UberHistoryProjectionTap108.Start.STARTED, UberHistoryProjectionTap108.start(app, officialActive = true))
        assertEquals(UberHistoryProjectionTap108.Start.ALREADY_ACTIVE, UberHistoryProjectionTap108.start(app, officialActive = true))
        val f1 = header + card("23,51", "UberX · 12 min 48 segundos · 3.57 km · 16:34") + "\n" + card("10,19", "UberX · 4 min 18 segundos · 0.74 km · 11:35")
        val f2 = header + card("10,19", "UberX · 4 min 18 segundos · 0.74 km · 11:35") + "\n" + card("31,00", "UberX · 20 min 2 segundos · 9.10 km · 09:02")
        UberHistoryProjectionTap108.accept(f1)
        UberHistoryProjectionTap108.accept(f1) // identical frame (no scroll) is ignored
        UberHistoryProjectionTap108.accept(f2)
        val progress = UberHistoryProjectionTap108.progress()
        assertEquals(2, progress.frames); assertEquals(3, progress.ridesSeen)
        assertTrue(UberHistoryProjectionTap108.finish(app))
        assertFalse(UberHistoryProjectionTap108.isActive())
        val started = shadowOf(app).nextStartedActivity
        assertEquals(UberDigitizationActivity026::class.java.name, started.component!!.className)
        val file = File(app.cacheDir, started.getStringExtra(UberDigitizationActivity026.EXTRA_SCAN_FILE)!!)
        val json = JSONObject(file.readText())
        assertEquals("official_projection_v108", json.getString("source"))
        val frames = json.getJSONArray("frames").let { a -> (0 until a.length()).map { a.getString(it) } }
        val rides = UberHistoryScanAccumulator0262.parseFrames(frames.map(UberDigitizationText0265::normalize)).rides
        assertEquals(3, rides.size)
        file.delete()
    }

    @Test fun boundedSamplingTimeoutAndCancel() {
        val app = RuntimeEnvironment.getApplication()
        UberHistoryProjectionTap108.start(app, officialActive = true)
        // Main-thread sampling: two OCR results inside the minimum interval keep only the first.
        UberHistoryProjectionTap108.offer(header + card("12,00", "UberX · 5 min 0 segundos · 1.00 km · 08:00"))
        UberHistoryProjectionTap108.offer(header + card("13,00", "UberX · 6 min 0 segundos · 1.10 km · 08:10"))
        val deadline = System.currentTimeMillis() + 5000
        while (UberHistoryProjectionTap108.progress().frames < 1 && System.currentTimeMillis() < deadline) Thread.sleep(10)
        Thread.sleep(100)
        assertEquals(1, UberHistoryProjectionTap108.progress().frames)
        // Cancel discards without review.
        UberHistoryProjectionTap108.cancel(app)
        assertFalse(UberHistoryProjectionTap108.finish(app))
        assertNull(shadowOf(app).nextStartedActivity)
        // Timeout finishes the scan by itself.
        UberHistoryProjectionTap108.start(app, officialActive = true)
        UberHistoryProjectionTap108.accept(header + card("14,00", "UberX · 7 min 0 segundos · 1.20 km · 08:20"))
        shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(UberHistoryProjectionTap108.MAX_DURATION_MS + 10))
        assertFalse(UberHistoryProjectionTap108.isActive())
        assertNotNull(shadowOf(app).nextStartedActivity)
    }

    @Test fun divergentReadingsOfTheSameCardAreOneRide() {
        val a = header + card("23,51", "UberX · 12 min 48 segundos · 3.57 km · 16:34")
        val b = header + card("23,51", "UberX · 12 min 4 segundos · 3.51 km · 16:34") // OCR misread
        val c = header + card("23,51", "UberX · 12 min 48 segundos · 3.57 km · 18:02") // another ride, same fare
        val rides = UberHistoryScanAccumulator0262.parseFrames(listOf(a, b, c), Instant.parse("2026-10-20T22:00:00Z")).rides
        assertEquals(2, rides.size)
        val cancelled = header + "R$ 0,00\nCancelada pelo passageiro\nUberX · 16:40"
        val mixed = UberHistoryScanAccumulator0262.parseFrames(listOf(a, cancelled), Instant.parse("2026-10-20T22:00:00Z")).rides
        assertTrue(mixed.size >= 1)
        assertTrue(mixed.none { it.rideStatus == UberCompletedRide026.STATUS_CANCELLED && kotlin.math.abs(it.fare - 23.51) < 0.01 })
    }

    @Test fun savingTheSameRideFromAnotherReadingCountsAsDuplicate() {
        val app = RuntimeEnvironment.getApplication()
        val store = UberDigitizationStore026.get(app)
        val r1 = UberCompletedRide026("uber-ride-a", "2026-10-20T22:00:00Z", "2026-10-20T19:34:00Z", 23.51, "uberx", null, null, 0.9, 768, 3.57)
        val r2 = r1.copy(sourceKey = "uber-ride-b", durationSeconds = 724, distanceKm = 3.51, occurredAt = "2026-10-20T19:34:30Z")
        assertEquals(1 to 0, store.saveRides(listOf(r1)))
        assertEquals(0 to 1, store.saveRides(listOf(r2)))
        val other = r1.copy(sourceKey = "uber-ride-c", occurredAt = "2026-10-20T21:02:00Z")
        assertEquals(1 to 0, store.saveRides(listOf(other)))
        // Coverage vs the session summary never claims more than what was read.
        val cov = UberHistoryCoverage108.compute(listOf(r2, other), store.ridesBetween("2026-10-20T00:00:00Z", "2026-10-21T00:00:00Z"), 24)
        assertEquals(2, cov.completed); assertEquals(2, cov.alreadySaved); assertEquals(22, cov.missing)
    }
}
