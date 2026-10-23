package com.srrotas.app

import org.junit.Assert.*
import org.junit.Test
import java.io.File

class JourneyEndDialogStyleV1Test {
    private val root by lazy {
        generateSequence(File(System.getProperty("user.dir") ?: ".").absoluteFile){it.parentFile}
            .first { File(it,"app/build.gradle.kts").exists() }
    }
    private fun source(file:String)=File(root,"app/src/main/java/com/bigcorps/driveraimvp/$file.kt").readText()

    @Test fun bothExistingEndFlowsUseSameStylesAndIgnoreLabelWithoutChangingLifecycle() {
        val flow=source("JourneyFlow026")
        val preflight=source("JourneyPreflight027037")
        val style=source("JourneyEndDialogStyleV1")
        for(screen in listOf(flow,preflight)) {
            assertTrue(screen.contains(".setNeutralButton(\"Ignorar\", null)"))
            assertTrue(screen.contains(".setNegativeButton(\"Cancelar\", null)"))
            assertTrue(screen.contains(".setPositiveButton(\"Salvar e encerrar\", null)"))
            assertTrue(screen.contains("JourneyEndDialogStyleV1.apply(context, dialog)"))
            assertTrue(screen.contains("dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener {"))
        }
        assertTrue(flow.contains("ActiveAssistant026.stop(context)"))
        assertTrue(preflight.contains("onEnd()"))
        assertTrue(style.contains("setTextColor(Color.WHITE)"))
        assertTrue(style.contains("p.userGreen"))
        assertTrue(style.contains("setTextColor(p.red)"))
        assertTrue(style.contains("setTextColor(p.muted)"))
    }
}
