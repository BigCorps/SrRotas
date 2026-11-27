package com.srrotas.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** Field report 2026-11-26: BR-01 / BR-15 invariants. */
class FieldReport1126UiContractTest {
    private val sourceRoot by lazy {
        val current = File(System.getProperty("user.dir") ?: ".").absoluteFile
        generateSequence(current) { it.parentFile }
            .first { File(it, "app/src/main/java/com/bigcorps/driveraimvp/AiPanel023.kt").isFile }
    }
    private fun read(path: String) = File(sourceRoot, path).readText()

    @Test fun microphoneIsThemeAwareVectorAndNotEmoji() {
        val ui = read("app/src/main/java/com/bigcorps/driveraimvp/AiPanel023.kt")
        val icon = read("app/src/main/res/drawable/sr23_ic_mic_minimal.xml")
        assertTrue(ui.contains("setImageResource(R.drawable.sr23_ic_mic_minimal)"))
        assertTrue(ui.contains("imageTintList = ColorStateList.valueOf(SrUi023.palette(context).ink)"))
        assertTrue(ui.contains("contentDescription = \"Ditar pergunta pelo microfone\""))
        assertFalse(ui.contains("text = \"🎙\""))
        assertTrue(icon.contains("android:viewportWidth=\"24\""))
    }

    @Test fun selectAllAppliesSameEligibilityGateAsExistingConservativeSelection() {
        val source = read("app/src/main/java/com/bigcorps/driveraimvp/HistoryReviewActivity109.kt")
        assertTrue(source.contains("chip(\"Selecionar todos\", false)"))
        assertTrue(source.contains("entries.filter { it.selectedByDefault }.forEach"))
        assertTrue(source.contains("val selectedByDefault get() = !cancelled && !duplicate && complete && confidence != Confidence.BAIXA"))
        assertTrue(source.contains("Incompletos, cancelados, duplicados e de baixa confiança ficam fora da seleção automática."))
        assertTrue(source.contains("confirmSelection()"))
        assertTrue(source.contains("val chosen = entries.filter { it.ride.sourceKey in selected }.map { it.ride }"))
    }
}
