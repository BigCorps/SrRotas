package com.srrotas.app.r3lab

import org.junit.Assert.*
import org.junit.Test

/**
 * Issue #6 — exclusividade real de screenshot/OCR/replay, com callbacks atrasados, timeout,
 * cancelamento e reinício. JVM pura (sem ML Kit): cada "tarefa" é um ticket que só a conclusão
 * real encerra; provamos que jamais coexistem duas tarefas.
 */
class R3LabConcurrencyTest {
    private var clock = 0L
    private val ex = R3Exclusivity({ clock }, stuckMs = 30_000L)
    private val discarded = mutableListOf<String>()
    private val live = R3LiveOcrScheduler<String>(ex) { item, reason -> discarded += "$item:$reason" }

    /** Invariante verificada a cada passo: nunca dois tickets vivos. */
    private val liveTickets = mutableSetOf<Long>()
    private fun begin(kind: R3Exclusivity.Kind, token: R3Exclusivity.ReplayToken? = null) = ex.tryBeginOcr(kind, token)?.also {
        assertTrue("two ML Kit tasks would coexist", liveTickets.isEmpty()); liveTickets += it.id
    }
    private fun complete(t: R3Exclusivity.OcrTicket): Boolean = ex.endOcr(t).also { if (it) liveTickets -= t.id }

    @Test fun stuckTaskFailsClosedForMoreThan31sAndNeverAllowsASecondTask() {
        val stuck = begin(R3Exclusivity.Kind.LIVE)!!
        clock = 29_999; assertFalse(ex.watchdog())
        clock = 31_000; assertTrue("watchdog poisons, never resets", ex.watchdog())
        assertTrue(ex.poisoned)
        assertFalse(ex.mayStartScreenshot())
        assertNull(begin(R3Exclusivity.Kind.LIVE))
        val replay = ex.reserveReplay()!!
        assertFalse(ex.replayReady(replay, screenshotInFlight = false))
        assertNull(begin(R3Exclusivity.Kind.REPLAY, replay))
        ex.releaseReplay(replay)
        live.enqueue("frame-late"); assertNull("no live OCR while poisoned", live.next())
        // A tarefa travada conclui de verdade bem depois (ex.: 95 s): só então o próximo pode começar.
        clock = 95_000; assertTrue(complete(stuck))
        assertFalse(ex.poisoned)
        val (item, ticket) = live.next()!!
        assertEquals("frame-late", item); assertTrue(complete(ticket))
    }

    @Test fun lateCallbackFromAnOldTaskNeverFinishesTheNewOne() {
        val t1 = begin(R3Exclusivity.Kind.LIVE)!!
        assertTrue(complete(t1))
        val t2 = begin(R3Exclusivity.Kind.LIVE)!!
        assertFalse("duplicated/late completion of t1", complete(t1))
        assertEquals(t2, ex.currentTask())
        assertNull(begin(R3Exclusivity.Kind.LIVE))
        assertTrue(complete(t2))
    }

    @Test fun replayWaitsForPendingScreenshotAndItsLateFramesStayQueued() {
        val lease = R3CaptureLease({ clock }, 5_000L)
        // Screenshot ao vivo começou ANTES da reserva do replay.
        val shot = synchronized(ex) { if (ex.mayStartScreenshot()) lease.start(1, 1) else null }!!
        val replay = ex.reserveReplay()!!
        assertFalse("no new live screenshot after reservation", ex.mayStartScreenshot())
        assertFalse(ex.replayReady(replay, lease.isBusy()))
        // O callback atrasado do screenshot chega DEPOIS da aquisição exclusiva e entrega um quadro.
        clock = 1_200
        assertTrue(lease.matches(shot)); lease.finish(shot)
        live.enqueue("late-frame")
        assertNull("late frame cannot start live OCR during replay", live.next())
        assertTrue(ex.replayReady(replay, lease.isBusy()))
        val replayTicket = begin(R3Exclusivity.Kind.REPLAY, replay)!!
        assertNull(begin(R3Exclusivity.Kind.LIVE))
        assertTrue(complete(replayTicket))
        assertNull("still reserved until released", live.next())
        assertTrue(ex.releaseReplay(replay))
        val (item, t) = live.next()!!
        assertEquals("late-frame", item); assertTrue(complete(t))
    }

    @Test fun replayTokenIsExclusiveAndAlwaysReleasable() {
        val a = ex.reserveReplay()!!
        assertNull("second replay cannot overlap", ex.reserveReplay())
        assertNull("foreign token cannot run OCR", begin(R3Exclusivity.Kind.REPLAY, R3Exclusivity.ReplayToken(999)))
        // Exceção/cancelamento no meio: o finally libera.
        try { error("cancelled") } catch (_: IllegalStateException) { } finally { assertTrue(ex.releaseReplay(a)) }
        assertFalse(ex.releaseReplay(a))
        assertFalse(ex.replayReserved())
        assertTrue(ex.mayStartScreenshot())
    }

    @Test fun replayOcrTimeoutKeepsTicketUntilRealCompletion() {
        val r = ex.reserveReplay()!!
        val t = begin(R3Exclusivity.Kind.REPLAY, r)!!
        // O chamador desistiu de esperar (timeout) e liberou a reserva — a tarefa continua viva.
        ex.releaseReplay(r)
        live.enqueue("f")
        assertNull("live OCR must wait for the abandoned replay task", live.next())
        clock = 31_000; assertTrue(ex.watchdog())
        clock = 40_000; assertTrue(complete(t))
        assertNotNull(live.next())
    }

    @Test fun shutdownCancelAndRestartReleaseEverythingWithoutOwnerlessBitmaps() {
        val lease = R3CaptureLease({ clock }, 5_000L)
        val oldShot = lease.start(serviceGeneration = 1, profileGeneration = 1)!!
        live.enqueue("a"); live.enqueue("b")
        assertEquals(listOf("a:superseded"), discarded)
        // detach: cancela o screenshot da geração 1 e descarta pendentes (bitmaps liberados).
        lease.cancelService(1); live.clear("stopped")
        assertEquals(listOf("a:superseded", "b:stopped"), discarded)
        assertFalse("late onSuccess of the cancelled screenshot is stale", lease.matches(oldShot))
        assertFalse(lease.finish(oldShot))
        // Reinício (geração 2) funciona normalmente.
        val newShot = lease.start(serviceGeneration = 2, profileGeneration = 1)!!
        assertTrue(lease.matches(newShot))
        assertFalse("old callback cannot release the new screenshot", lease.finish(oldShot))
        assertTrue(lease.finish(newShot))
        // Escrita adiada só com o serviço vigente e o laboratório ainda ativo.
        val on = R3LabConsent(labEnabled = true, accessibilityDisclosureAccepted = true, imageRetentionConsent = true)
        assertTrue(R3LabGate.mayPersistObservation(2, 2, on))
        assertFalse("after detach/revocation", R3LabGate.mayPersistObservation(1, 2, on))
        assertFalse("after opt-out", R3LabGate.mayPersistObservation(2, 2, on.copy(labEnabled = false)))
    }

    @Test fun ocrInFlightAcrossRestartStillBlocksTheNewServiceUntilItReallyEnds() {
        val t = begin(R3Exclusivity.Kind.LIVE)!!
        // detach/attach não zera a tarefa: o coordenador é do processo.
        live.enqueue("new-service-frame")
        assertNull(live.next())
        assertTrue(complete(t))
        assertNotNull(live.next())
    }

    @Test fun supervisorNeverResetsALiveTaskAndReplayGoesThroughTheCoordinator() {
        val root = generateSequence(java.io.File("").absoluteFile) { it.parentFile }.first { java.io.File(it, "app/build.gradle.kts").exists() }
        val dir = java.io.File(root, "app/src/main/java/com/bigcorps/driveraimvp/r3lab")
        val sup = java.io.File(dir, "R3Supervisor.kt").readText()
        val tick = sup.substringAfter("private fun tick()").substringBefore("private fun selectWindows")
        assertFalse("watchdog must not recreate the recognizer", tick.contains("TextRecognition.getClient"))
        assertFalse(sup.contains("mlkitInFlight") || sup.contains("set(0)"))
        assertTrue(sup.contains("exclusivity.watchdog()") && sup.contains("exclusivity.endOcr(ticket)"))
        assertTrue("screenshot start is atomic with the coordinator", sup.contains("synchronized(exclusivity)"))
        val activity = java.io.File(dir, "R3LabActivity.kt").readText()
        assertTrue(activity.contains("R3Supervisor.replayRecognize("))
        assertFalse("replay never creates its own unmanaged recognizer", activity.contains("TextRecognition.getClient"))
        // Revisão #6: replay vencido não deixa quadro ao vivo pendente para sempre, e a tarefa é dona da sua cópia.
        val replay = sup.substringAfter("fun replayRecognize").substringBefore("private fun scheduleTick")
        val listener = replay.substringAfter("addOnCompleteListener")
        assertTrue(listener.contains("exclusivity.endOcr(ticket)") && listener.contains("pumpOcr()") && listener.contains("recycle(owned)"))
        assertTrue(replay.indexOf("TextRecognition.getClient") < replay.indexOf("tryBeginOcr(R3Exclusivity.Kind.REPLAY"))
        assertTrue(tick.contains("if (scheduler.hasPending()) pumpOcr()"))
    }

    @Test fun pendingLiveFrameStartsOnceAnExpiredReplayTaskReallyCompletes() {
        val r = ex.reserveReplay()!!
        val t = begin(R3Exclusivity.Kind.REPLAY, r)!!
        live.enqueue("waiting-frame")
        ex.releaseReplay(r)                 // o chamador desistiu (timeout)
        assertNull(live.next())             // pump no finally do replay: ainda sem ticket
        assertTrue(complete(t))             // conclusão real → listener chama pumpOcr
        assertEquals("waiting-frame", live.next()!!.first)
    }
}
