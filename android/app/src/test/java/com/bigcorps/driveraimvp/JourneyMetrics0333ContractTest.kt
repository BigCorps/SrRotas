package com.srrotas.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** P1 0.33.3 — contrato de recuperação automática de odômetro/energia. */
class JourneyMetrics0333ContractTest {
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

    @Test fun metricsAlwaysWaitForCanonicalJourneySync() {
        val client = source("JourneyMetricsClient026.kt")
        assertTrue(client.contains("SyncCoordinator.sync(app) { coreResult ->"))
        assertTrue(client.contains("if (coreResult.skipped)"))
        assertTrue(client.contains("flushAfterCoreSync(app, onDone)"))

        val coreIndex = client.indexOf("SyncCoordinator.sync(app) { coreResult ->")
        val flushIndex = client.indexOf("flushAfterCoreSync(app, onDone)")
        assertTrue(coreIndex >= 0 && flushIndex > coreIndex)
    }

    @Test fun startupRecoversOldPendingMetricsWithoutSecondSyncImplementation() {
        val app = source("SrRotasApplication.kt")
        assertTrue(app.contains("JourneyMetricsClient026.syncPending(this)"))
        assertFalse(
            "Startup não deve disparar duas filas core concorrentes.",
            app.contains("SyncCoordinator.sync(this)"),
        )
    }

    @Test fun concurrentMetricFlushesAreCoalesced() {
        val client = source("JourneyMetricsClient026.kt")
        assertTrue(client.contains("AtomicBoolean(false)"))
        assertTrue(client.contains("CopyOnWriteArrayList<() -> Unit>()"))
        assertTrue(client.contains("compareAndSet(false, true)"))
        assertTrue(client.contains("running.set(false)"))
    }

    @Test fun diagnosticExposesOnlyQueueAndCounters() {
        val client = source("JourneyMetricsClient026.kt")
        val diagnostic = source("ReaderLabCombinedDiagnostic0270361.kt")
        assertTrue(client.contains("sr-journey-metrics-sync-0333-v1"))
        assertTrue(client.contains("pending_metrics"))
        assertTrue(client.contains("pending_energy"))
        assertTrue(client.contains("core_sync_before_metrics"))
        assertTrue(client.contains("exports_sensitive_metric_values"))
        assertTrue(diagnostic.contains("journey_metrics_sync_0333"))
        assertTrue(diagnostic.contains("JourneyMetricsClient026.toJson(context)"))

        val jsonStart = client.indexOf("fun toJson(context: Context)")
        val jsonEnd = client.indexOf("fun refreshDays", jsonStart)
        val jsonBlock = client.substring(jsonStart, jsonEnd)
        assertFalse(jsonBlock.contains("odometer_start_km"))
        assertFalse(jsonBlock.contains("odometer_end_km"))
        assertFalse(jsonBlock.contains("amount_paid"))
        assertFalse(jsonBlock.contains("fuel_type"))
    }
}
