package com.srrotas.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.security.MessageDigest

/** Runs in regular Android JVM unit tests, even when CI cloned with --depth=1.
 * Git blob IDs are source-content digests, not runtime dependency checks.
 */
class Field98ImmutableSourceContractTest {
    private val root by lazy {
        generateSequence(File(System.getProperty("user.dir") ?: ".").absoluteFile) { it.parentFile }
            .first { File(it, "app/build.gradle.kts").exists() }
    }

    private fun gitBlobSha(file: File): String {
        val bytes = file.readBytes()
        val digest = MessageDigest.getInstance("SHA-1")
        digest.update("blob ${bytes.size}\u0000".toByteArray(Charsets.UTF_8))
        digest.update(bytes)
        return digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    private fun expectFrozen(entries: Map<String, String>) {
        val source = File(root, "app/src/main/java/com/bigcorps/driveraimvp")
        for ((name, sha) in entries) {
            val file = File(source, name)
            assertTrue("Missing frozen source: $name", file.isFile)
            assertEquals("Frozen source changed: $name", sha, gitBlobSha(file))
        }
    }

    @Test fun readerInventoryStillContainsExactly18ProtectedSources() {
        val src = File(root, "app/src/main/java/com/bigcorps/driveraimvp")
        val protectedNames = Regex("^(MediaProjectionOcrService|UberM1TemporalRecoveryV1|DriverPlatformOfferRouter|UberScreenGate|UberSpatialParser0221|MoneyRoleResolver030|OfferParser|OfferDeduplicator|OfferIntegrityGate.*|OfferAdmission.*|ReaderLab027036|Reader2.*|ShadowOfferRecovery.*)\\.kt$")
        val expected = listOf(
            "DriverPlatformOfferRouter.kt", "MediaProjectionOcrService.kt",
            "MoneyRoleResolver030.kt", "OfferAdmissionGate029.kt", "OfferAdmissionGate030.kt",
            "OfferDeduplicator.kt", "OfferIntegrityGate027033.kt", "OfferParser.kt",
            "Reader2Accumulator032.kt", "Reader2Consensus0321.kt", "Reader2MoneyShadow030.kt",
            "Reader2Parallel031.kt", "Reader2Shadow030.kt", "ReaderLab027036.kt",
            "ShadowOfferRecovery027033.kt", "UberM1TemporalRecoveryV1.kt",
            "UberScreenGate.kt", "UberSpatialParser0221.kt",
        ).sorted()
        val current = src.listFiles().orEmpty().asSequence().filter { it.isFile }
            .map { it.name }.filter { protectedNames.matches(it) }.sorted().toList()
        assertEquals("Reader inventory drifted (new or missing protected source)", expected, current)
    }

    @Test fun reader17MatchLastValidatedReader() {
        // Reader vc92 inventory excludes Service: lifecycle fixes were approved after vc92.
        expectFrozen(linkedMapOf(
            "DriverPlatformOfferRouter.kt" to "16ebd5e51d3b9a62afd8e3d216484d87f423106c",
            "MoneyRoleResolver030.kt" to "13bb035da63a7799d94076532dc1caf3fd4cb629",
            "OfferAdmissionGate029.kt" to "deb0fb5225cef9d914e5f4cf902332b946a7d1c6",
            "OfferAdmissionGate030.kt" to "85a368af62fd35784c29afb7d096df4ce14f4f98",
            "OfferDeduplicator.kt" to "9efb67fd2bbf5e7f6f2ca3bf404c33a0f848e094",
            "OfferIntegrityGate027033.kt" to "887d137cefc5ea3161790db4e8ff5285516ff5d6",
            "OfferParser.kt" to "c461798a9110787ab5df0af73fc547cac280b9b7" // reviewed vc102 time-integrity exception,
            "Reader2Accumulator032.kt" to "987fbeb431c30c78920ddba37f35b8c5d20757e8",
            "Reader2Consensus0321.kt" to "0e4dc3afd9480b904c52709bf281bfd29f82ee5e",
            "Reader2MoneyShadow030.kt" to "0f29b9a253b4affb1a188df8bfb762b49f1ece73",
            "Reader2Parallel031.kt" to "25f6d5cea7656f7b9e513d8f7963a3ffccd2b69c",
            "Reader2Shadow030.kt" to "8fcbe4683efbe7c5ffe39dc5f41577d53438e34a",
            "ReaderLab027036.kt" to "349959ead236102443d6926d27c3560fa92bc46c",
            "ShadowOfferRecovery027033.kt" to "c50a27d2d3018d8c2cce0c6db41eaa3747102582",
            "UberM1TemporalRecoveryV1.kt" to "fcf97ee23dda4e34e47f5ffb3e8eb9298b4fe861",
            "UberScreenGate.kt" to "d6cc769f48a11392390b5b709e0a4a799b53b60d",
            "UberSpatialParser0221.kt" to "a9e95bb0ce006a70dc6f318def616f94d6d4f148",
        ))
    }

    @Test fun captureRadarAndRescan18MatchVc97() {
        expectFrozen(linkedMapOf(
            "MediaProjectionOcrService.kt" to "3e93bdf3afe7ce0cc11869cb84c3130c2e144126",
            "FieldCaptureLifecycleV1.kt" to "0df9d9dac4038b04d23349fc7883bfdb40e3bfc3",
            "CaptureResilience0311.kt" to "e5fbd645e6bca712972a5f638eabb995110c434e",
            "RadarMiniMapViewV1.kt" to "1fe411a5e1f2de1c614e0ba6834d16f52b5a1685",
            "RadarMapBitmapSurfaceV1.kt" to "509e68b7dd2cf58e5efd935a64e1c1463c37e2d4",
            "RadarMapBitmapHealthV1.kt" to "03ccbab57516d6fbbaac2dfbf5c7fed660ae8942",
            "RadarMapLastEpisodeV1.kt" to "0458c2ead0013791f2d3b52ff81fb5bf43ff7db3",
            "RadarMapRenderGateV1.kt" to "1df4272f01dfd55f68789ddb93ad9be82118b409",
            "RadarContextualClientV1.kt" to "f839140885259dbd80dba6e04041df9ee214a961",
            "ScreenshotRescanGateV1.kt" to "0e1969b7234c7a3993b3381937097b86803e212c" // reviewed vc102 gallery exception,
            "ScreenshotRescanExecutionV1.kt" to "385fae576f0f6828a3931f2c9397f5a82940cf55",
            "ScreenshotRescanImageV1.kt" to "f74d70b30029beaaffeb3c5cbb9549d325cd88a4",
            "PrivateScreenshotIndexV1.kt" to "47425bb9c91b06d3cdab01e36208e137c184b58d",
            "PrivateScreenshotStore.kt" to "8f759d16b332be906afd662a5a124c39720a3d1c",
            "JourneyStateMachine.kt" to "e1359001392d54b65a035f632019aa1dfd2df0e9",
            "ReportSelection0211.kt" to "dc0b5bbe1b9967e063fe2476f7830a59e5ab279d",
            "RadarContextualFlagsV1.kt" to "9ed2ceb47366282d35399bc6d133e58394932648",
            "RadarDestinationLauncherV1.kt" to "9f35649f11a04ce6555d6182a6f8bb1fb54e8cc8",
        ))
    }
}
