package com.srrotas.app

import android.app.Application
import android.widget.TextView
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

/**
 * vc109 — releitura: sempre um estado compreensível e uma trilha persistente para o campo.
 * Robolectric não executa ML Kit nem o processo `:rescan` real; o aparelho continua obrigatório.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class GalleryRescan109Test {
    private val root = generateSequence(File(System.getProperty("user.dir")).absoluteFile) { it.parentFile }
        .first { File(it, "app/build.gradle.kts").exists() }
    private fun src(name: String) = File(root, "app/src/main/java/com/bigcorps/driveraimvp/$name").readText()
    private fun texts(v: android.view.View): List<String> = when (v) {
        is TextView -> listOf(v.text.toString()); is android.view.ViewGroup -> (0 until v.childCount).flatMap { texts(v.getChildAt(it)) }; else -> emptyList()
    }

    @Test fun everyPhaseHasAStateAndEveryEndIsTerminal() {
        val activity = src("OfferRescanActivityV1.kt")
        listOf("PREPARANDO", "PRONTA", "PROCESSANDO", "CONCLUIDA", "SEM_RESULTADO", "BLOQUEADA", "FALHA").forEach {
            assertTrue(it, activity.contains("RescanState109.$it"))
        }
        // deliver always lands in a terminal state (success with/without card, or failure).
        val deliver = activity.substringAfter("deliver = { result ->")
        assertTrue(deliver.contains("RescanState109.SEM_RESULTADO") && deliver.contains("RescanState109.CONCLUIDA") && deliver.contains("RescanState109.FALHA"))
        // The exclusion lease is still released only by the real worker completion (frozen executor).
        assertTrue(activity.contains("release = { ScreenshotRescanGateV1.complete() }"))
        assertFalse(activity.substringAfter("override fun onDestroy()").contains("complete()"))
    }

    @Test fun traceIsPersistentBoundedAndExportedWithoutContent() {
        val app = RuntimeEnvironment.getApplication()
        repeat(45) { RescanTrace109.record(app, "PROCESSANDO", "step_$it") }
        RescanTrace109.record(app, "FALHA", "ocr_IllegalStateException")
        val json = RescanTrace109.toJson(app)
        assertEquals(40, json.getJSONArray("events").length())
        assertEquals("FALHA", json.getString("last_state"))
        assertTrue(src("ReaderLabCombinedDiagnostic0270361.kt").contains("RescanTrace109.toJson(context)"))
        val app2 = src("SrRotasApplication.kt").substringAfter("if (isReviewProcess) {").substringBefore("return")
        assertTrue(app2.contains("mlkit_init_ok") && app2.contains("mlkit_init_failed_"))
    }

    @Test fun openingTheScreenShowsAnExplicitState() {
        val activity = Robolectric.buildActivity(OfferRescanActivityV1::class.java).setup().get()
        val shown = texts(activity.window.decorView)
        assertTrue(shown.toString(), shown.any { it == RescanState109.AGUARDANDO.label || it == RescanState109.BLOQUEADA.label })
        // With an active journey the state is BLOCKED (never a silent status screen).
        activity.getSharedPreferences("driver_ai_settings", 0).edit().putString("current_journey_id", "journey-109").commit()
        val blocked = Robolectric.buildActivity(OfferRescanActivityV1::class.java).setup().get()
        assertTrue(texts(blocked.window.decorView).contains(RescanState109.BLOQUEADA.label))
        assertEquals("BLOQUEADA", RescanTrace109.toJson(blocked).getString("last_state"))
        activity.getSharedPreferences("driver_ai_settings", 0).edit().remove("current_journey_id").commit()
    }

    @Test fun noResultExplainsTheStage() {
        assertTrue(RescanDropText109.describe(OfferDropDiagnosis109.Result(OfferDropDiagnosis109.Stage.FARE, "x", 0, 1, 2, 0)).contains("tarifa"))
        assertEquals("", RescanDropText109.describe(null))
        val work = src("OfferRescanActivityV1.kt").substringAfter("work = { settings ->").substringBefore("dispose =")
        assertTrue(work.contains("UberSpatialParser0221.parseLines(") && work.contains("OfferContextCompletion109::complete"))
        assertEquals(1, Regex("TextRecognition\\.getClient\\(").findAll(src("OfferRescanActivityV1.kt")).count())
    }
}
