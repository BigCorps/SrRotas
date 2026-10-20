package com.srrotas.app

import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** Field-only UX/diagnostic contract: no OCR or MediaProjection lifecycle changes. */
class Field99FeedbackContractTest {
    private val root by lazy {
        generateSequence(File(System.getProperty("user.dir") ?: ".").absoluteFile) { it.parentFile }
            .first { File(it,"app/build.gradle.kts").exists() }
    }
    private fun source(name: String) =
        File(root,"app/src/main/java/com/bigcorps/driveraimvp/$name.kt").readText()

    @Test fun versionIsNewerThanPreviouslyInstalledVc98() {
        val gradle=File(root,"app/build.gradle.kts").readText()
        assertTrue(gradle.contains("versionCode=101;versionName=\"0.33.24-field\""))
    }

    @Test fun assistantExpiredEtaIsReportedButNotAllowedToFetch() {
        val runtime=source("DestinationRadarRuntimeV1")
        val gate=runtime.substringAfter("val etaFresh = DestinationRadarFreshnessV1.blockReason")
        assertTrue(gate.contains("val temporalBlock = if(etaFresh) null"))
        assertTrue(gate.contains("if (!etaFresh && RadarContextualIntegrationV1.assistantAllowed(c))"))
        assertTrue(gate.contains("FieldPipelineTraceV1.event(c,spec.localOfferId,\"ASSISTANT_BLOCKED\",temporalBlock!!)"))
        assertTrue(gate.contains("RadarContextualDiagnosticV1.assistantRenderBlocked(temporalBlock)"))
        assertTrue(gate.contains("if(etaFresh &&"))
        assertTrue(runtime.contains("lastTemporalBlockKey=null"))
        assertTrue(source("DestinationRadarFreshnessV1").contains("return if (arrivalMs <= nowMs) \"eta_expired\" else null"))
        val context=source("RadarDestinationContextV1")
        assertTrue(context.contains("FieldPipelineFactsV1.specBlock(offer)"))
        assertFalse(runtime.contains("endJourney("))
    }

    @Test fun digitizationUsesBrandedSurfaceButPreservesAndroidConsentAndCapture() {
        val manifest=File(root,"app/src/main/AndroidManifest.xml").readText()
        assertTrue(manifest.contains("android:name=\".UberDigitizationActivity026\""))
        assertFalse(manifest.contains("android:name=\".UberDigitizationActivity026\" android:excludeFromRecents=\"true\" android:exported=\"false\" android:theme=\"@android:style/Theme.Translucent.NoTitleBar\""))
        val activity=source("UberDigitizationActivity026")
        assertTrue(activity.contains("val surface = SrUi023.screen(this)"))
        assertTrue(activity.contains("setContentView(surface)"))
        assertTrue(activity.contains("UiKit.applySafeArea(surface)"))
        assertTrue(activity.contains("manager.createScreenCaptureIntent()"))
        assertTrue(activity.contains("moveTaskToBack(true)"))
        assertTrue(activity.contains("UberDigitizationCaptureService026::class.java"))
        assertTrue(activity.contains("reviewHistoryScan(scanFile)"))
    }

    @Test fun rescanSimpleGalleryStillPreservesManualGateAndPreview() {
        val s=source("OfferRescanActivityV1")
        assertTrue(s.contains("Escolher imagem da galeria"))
        assertTrue(s.contains("Detalhes e opções técnicas"))
        assertTrue(s.contains("visibility=android.view.View.GONE"))
        assertTrue(s.contains("ScreenshotRescanGateV1.precheck(this)"))
        assertTrue(s.contains("ScreenshotRescanGateV1.acquire(this)"))
        assertTrue(s.contains("ScreenshotRescanGateV1.complete()"))
        assertTrue(s.contains("Intent.ACTION_OPEN_DOCUMENT"))
        assertTrue(s.contains("preview.setImageBitmap(image)"))
        assertTrue(s.contains("Revisar leitura"))
        assertTrue(s.contains("confirm.isEnabled = !operation && bitmap != null && block == null"))
        assertTrue(s.contains("ScreenshotRescanComparisonV1.describe(activity.original,fresh)"))
    }

    @Test fun radarErrorTabsDoNotInventLoadingOrDiscardRetry() {
        val s=source("RadarContextualPanelV1")
        assertTrue(s.contains("private var errorMessage: String? = null"))
        assertTrue(s.contains("errorMessage = message"))
        assertTrue(s.contains("errorMessage?.let(::renderError) ?: renderLoading(current)"))
        val error=s.substringAfter("private fun renderError(").substringBefore("private fun ensureMap(")
        assertTrue(error.contains("body.addView(tabRow())"))
        assertTrue(error.contains("Tentar novamente"))
        val refreshed=s.substringAfter("fun openDestination(").substringBefore("RadarContextualClientV1.fetch(")
        assertTrue(refreshed.contains("errorMessage = null"))
    }

    @Test fun oldAlphaNoticesRemovedFromUserFacingScreens() {
        val settings=source("SettingsHub023")
        val ai=source("AiMcpPanel")
        assertFalse(settings.contains("\"ALPHA 0.27\""))
        assertFalse(ai.contains("Nesta fase Alpha"))
        assertFalse(ai.contains("Alpha liberado para testes"))
    }
}
