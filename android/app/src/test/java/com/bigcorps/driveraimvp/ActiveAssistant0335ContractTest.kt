package com.srrotas.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** 0.33.5 — contrato canônico de UX do Assistente Ativo. */
class ActiveAssistant0335ContractTest {
    private val root: File by lazy {
        val cwd = File(System.getProperty("user.dir") ?: ".").absoluteFile
        val candidates = generateSequence(cwd) { it.parentFile }
            .flatMap { base ->
                sequenceOf(
                    File(base, "app/src/main/java/com/bigcorps/driveraimvp"),
                    File(base, "src/main/java/com/bigcorps/driveraimvp"),
                    File(base, "android/app/src/main/java/com/bigcorps/driveraimvp"),
                )
            }.toList()
        candidates.firstOrNull { File(it, "SrRotasApplication.kt").isFile }
            ?: error("Não foi possível localizar os fontes do Sr. Rotas. user.dir=${cwd.path}")
    }

    private fun source(name: String): String {
        val file = File(root, name)
        check(file.isFile) { "Fonte não encontrado: ${file.absolutePath}" }
        return file.readText()
    }

    @Test fun polishUsesExplicitIgnoreAndViewActions() {
        val polish = source("ActiveAssistantPolish0265.kt")
        assertTrue(polish.contains("label = \"IGNORAR\""))
        assertTrue(polish.contains("label = \"VER\""))
        assertTrue(polish.contains("FieldValidationPolish0265.openNowFromAssistant(context)"))
        assertFalse(polish.contains("Você está fazendo uma corrida?"))
        assertFalse(polish.contains("Quero uma dica de local"))
    }

    @Test fun outsideTouchNeverMarksRideState() {
        val polish = source("ActiveAssistantPolish0265.kt")
        val outsideStart = polish.indexOf("MotionEvent.ACTION_OUTSIDE")
        val outsideEnd = polish.indexOf("private fun actionButton", outsideStart)
        val outsideBlock = polish.substring(outsideStart, outsideEnd)
        assertTrue(outsideBlock.contains("dismiss()"))
        assertFalse(outsideBlock.contains("putString"))
        assertFalse(outsideBlock.contains("KEY_MANUAL_RIDE_OFFER"))
        assertFalse(outsideBlock.contains("suppressAsRide"))
    }

    @Test fun legacySilentSuppressionIsClearedOnInstall() {
        val polish = source("ActiveAssistantPolish0265.kt")
        assertTrue(polish.contains("LEGACY_MANUAL_RIDE_OFFER"))
        assertTrue(polish.contains(".remove(LEGACY_MANUAL_RIDE_OFFER)"))
    }

    @Test fun configuredDisplaySecondsAreActuallyUsed() {
        val engine = source("ActiveAssistant026.kt")
        assertTrue(engine.contains("assistantDisplaySeconds()"))
        assertTrue(engine.contains(".coerceIn(5, 30) * 1_000L"))
        assertFalse(
            "Assistente não deve ignorar a preferência visual usando timeout fixo.",
            engine.contains("main.postDelayed(hide, ActiveAssistantRules026.CARD_TIMEOUT_MS)"),
        )
    }

    @Test fun fallbackEngineAlsoUsesExplicitActions() {
        val engine = source("ActiveAssistant026.kt")
        assertTrue(engine.contains("actionButton(context, \"Ignorar\""))
        assertTrue(engine.contains("actionButton(context, \"Ver\""))
        assertFalse(engine.contains("actionButton(context, \"Estou em corrida\""))
    }
}
