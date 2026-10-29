package com.srrotas.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class RadarContextualField2ContractTest {
    private val androidRoot: File by lazy {
        val cwd = File(System.getProperty("user.dir") ?: ".").absoluteFile

        generateSequence(cwd) { it.parentFile }
            .firstOrNull { base ->
                File(base, "app/build.gradle.kts").isFile &&
                    File(
                        base,
                        "app/src/main/java/com/bigcorps/driveraimvp/SrRotasApplication.kt",
                    ).isFile
            }
            ?: error("Raiz Android não encontrada.")
    }

    private val sourceRoot: File by lazy {
        File(
            androidRoot,
            "app/src/main/java/com/bigcorps/driveraimvp",
        )
    }

    private fun source(name: String) =
        File(sourceRoot, name).readText()

    @Test
    fun radarHasNoEmbeddedMapAndNoPaidMapKey() {
        val gradle = File(androidRoot, "app/build.gradle.kts").readText()
        val panel = source("RadarContextualPanelV1.kt")
        val external = source("RadarExternalMapsV1.kt")

        assertTrue(gradle.contains("versionCode=107"))
        assertTrue(gradle.contains("versionName=\"0.33.30-field\""))
        // vc107: no MapLibre/Google Maps SDK; locations open in the external app via a universal link.
        assertFalse(gradle.contains("org.maplibre"))
        assertFalse(gradle.contains("play-services-maps"))
        assertTrue(external.contains("OfferMaps.searchIntent("))
        assertFalse(panel.contains("google_maps_key"))
    }

    @Test
    fun operationalRefreshCannotKeepDemoAlive() {
        val panel = source("RadarContextualPanelV1.kt")

        assertTrue(panel.contains("fun refresh()"))
        assertTrue(panel.contains("leaveDemo()"))
        assertTrue(panel.contains("fun openDestination"))
        assertTrue(panel.contains("demoMode = false"))
    }

    @Test
    fun fieldDiagnosticProvesCurrentRideIdentityWithoutExportingCoordinates() {
        val diag = source("RadarContextualDiagnosticV1.kt")

        assertTrue(diag.contains("currentRide.localOfferId → LocalStore → RideOffer.context"))
        assertTrue(diag.contains("destinationLat/Lng presentes"))
        assertTrue(diag.contains("query_matches_current_ride"))
        assertTrue(diag.contains("assistant_reason"))
        assertTrue(diag.contains("demo_data_included"))
        assertTrue(diag.contains("exports_coordinates"))
        assertTrue(diag.contains("put(\"exports_coordinates\", false)"))
    }

    @Test
    fun combinedDiagnosticExportsRadarContextualBlock() {
        val combined = source("ReaderLabCombinedDiagnostic0270361.kt")
        assertTrue(combined.contains("\"radar_contextual_v1\""))
        assertTrue(combined.contains("RadarContextualDiagnosticV1.toJson(context)"))
    }
}
