package com.srrotas.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ExposureQueueRepair0338ContractTest {
    private val root: File by lazy {
        val cwd = File(System.getProperty("user.dir") ?: ".").absoluteFile
        generateSequence(cwd) { it.parentFile }
            .flatMap { base ->
                sequenceOf(
                    File(base, "app/src/main/java/com/bigcorps/driveraimvp"),
                    File(base, "android/app/src/main/java/com/bigcorps/driveraimvp"),
                )
            }
            .firstOrNull { File(it, "ExposureQueueRepair0338.kt").isFile }
            ?: error("Fontes Android não encontradas.")
    }

    private fun source(name: String) = File(root, name).readText()

    @Test
    fun permanentInvalidExposureIsQuarantinedNotDeleted() {
        val repair = source("ExposureQueueRepair0338.kt")
        assertTrue(repair.contains("invalid_exposure_fields"))
        assertTrue(repair.contains("invalid_exposure_window"))
        assertTrue(repair.contains("put(\"sync_state\", 2)"))
        assertTrue(repair.contains("deletes_local_rows"))
        assertFalse(repair.contains("delete(\"local_zone_exposure\""))
        assertFalse(repair.contains("delete from local_zone_exposure"))
    }

    @Test
    fun repairRunsBeforeCanonicalSyncAndIsObservable() {
        val app = source("SrRotasApplication.kt")
        val sync = source("JourneySyncClient.kt")
        val diagnostic = source("ReaderLabCombinedDiagnostic0270361.kt")
        assertTrue(app.contains("ExposureQueueRepair0338.run(this, force=true)"))
        assertTrue(sync.contains("ExposureQueueRepair0338.run(app)"))
        assertTrue(sync.indexOf("ExposureQueueRepair0338.run(app)") < sync.indexOf("SyncCoordinator.sync(app)"))
        assertTrue(diagnostic.contains("exposure_queue_repair_0338"))
        assertTrue(diagnostic.contains("ExposureQueueRepair0338.toJson(context)"))
    }

    @Test
    fun pendingCrashFlushesAfterSuccessfulAccessResolution() {
        val access = source("AccessResolver10B.kt")
        assertTrue(access.contains("BetaTelemetry.flushPendingCrash(app)"))
        assertTrue(access.indexOf("KEY_SUCCESS") < access.indexOf("BetaTelemetry.flushPendingCrash(app)"))
    }
}
