package com.srrotas.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Guard da UI aprovada; o motor e os parsers do Reader não são alterados. */
class ConversationalAiV1ContractTest {
    private val root: File by lazy {
        val cwd = File(System.getProperty("user.dir") ?: ".").absoluteFile
        generateSequence(cwd) { it.parentFile }
            .flatMap { base -> sequenceOf(
                File(base, "app/src/main/java/com/bigcorps/driveraimvp"),
                File(base, "android/app/src/main/java/com/bigcorps/driveraimvp"),
            ) }
            .first { File(it, "BackendClient.kt").isFile }
    }
    private fun file(name: String) = File(root, name).readText()

    @Test fun followUpUsesOnlyTheBackendContext() {
        val backend = file("BackendClient.kt")
        val ui = file("AiPanel023.kt")
        assertTrue(backend.contains("previousContext: JSONObject? = null"))
        assertTrue(backend.contains("put(\"context\", JSONObject(previousContext.toString()))"))
        assertTrue(backend.contains("structuredContext = json.optJSONObject(\"context\")"))
        assertTrue(ui.contains("conversationContext = response.structuredContext"))
        assertTrue(ui.contains("conversationContext = null"))
        assertFalse(ui.contains("localStorage"))
    }

    @Test fun discreteHeadComesFromOfficialHeaderWithoutLogoOrEnlargement() {
        val ui = file("AiPanel023.kt")
        val shell = file("ConsolidatedAiPanel027037.kt")
        assertTrue(ui.contains("R.drawable.sr0265_header_light"))
        assertTrue(ui.contains("Bitmap.createBitmap(bitmap, 0, 0, cropWidth"))
        assertTrue(ui.contains("mascotHead(84)"))
        assertTrue(ui.contains("mascotHead(34)"))
        assertFalse(shell.contains("enlargeMascotOnce"))
        assertFalse(shell.contains("sr0265_ai_mascot"))
    }

    @Test fun answersUseRealEvidenceAndNeverShowModelOrTokens() {
        val ui = file("AiPanel023.kt")
        assertTrue(ui.contains("evidenceSummary(response)"))
        assertTrue(ui.contains("Entenda o resultado"))
        assertTrue(ui.contains("response.metrics"))
        assertTrue(ui.contains("response.limitations"))
        assertTrue(ui.contains("Nova conversa"))
        assertFalse(ui.contains("response.model"))
        assertFalse(ui.contains("response.totalTokens"))
    }

    @Test fun dictationIsUserInitiatedAndRequiresExplicitConfirmation() {
        val ui = file("AiPanel023.kt")
        assertTrue(ui.contains("requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO)"))
        assertTrue(ui.contains("Texto reconhecido. Confira e toque em Enviar."))
        assertTrue(ui.contains("SpeechRecognizer.createSpeechRecognizer(context)"))
        assertFalse(ui.contains("onResults(results: Bundle?) { ask()"))
    }
}
