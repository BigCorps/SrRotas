package com.srrotas.app

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class Field97RescanSafetyTest {
    private class Harness {
        val lease = ScreenshotRescanLeaseV1()
        var blocked: ScreenshotRescanBlockV1? = null
        var prepared = 0
        var transferred = 0
        val disposed = AtomicInteger()
        val delivered = AtomicInteger()
        var result: Result<Int>? = null
        fun submit(prepare: () -> Int = { prepared++; 1 },
                   executor: Executor = Executor { it.run() }, work: (Int) -> Int = { it }) =
            ScreenshotRescanExecutionV1.submit(
                acquire = { lease.acquireChecked { blocked } }, release = lease::complete,
                prepare = prepare, transfer = { transferred++ }, executor = executor,
                work = work, dispose = { disposed.incrementAndGet() },
                deliver = { result = it; delivered.incrementAndGet() },
            )
    }
    @Test fun activeJourneyNeverAutomaticallyPicksOrAcquires() {
        val h=Harness();h.blocked=ScreenshotRescanBlockV1.JOURNEY
        assertFalse(ScreenshotRescanGateV1.shouldAutoPick(h.blocked,false,false))
        assertEquals(h.blocked,h.submit());assertFalse(h.lease.busy)
        assertEquals(0,h.prepared);assertEquals(0,h.transferred)
    }
    @Test fun precheckDoesNotAcquireAndDescribesExclusion() {
        val h=Harness();assertNull(h.lease.precheck { null });assertFalse(h.lease.busy)
        h.lease.acquireChecked { null }
        assertEquals(ScreenshotRescanBlockV1.OTHER_OCR,h.lease.precheck { null })
        h.lease.complete();assertTrue(h.lease.foreignStart())
        assertEquals(ScreenshotRescanBlockV1.RESTART,h.lease.precheck { null })
    }
    @Test fun precheckIsRevalidatedAtExecution() {
        val h=Harness();assertNull(h.lease.precheck { h.blocked })
        h.blocked=ScreenshotRescanBlockV1.CAPTURE
        assertEquals(h.blocked,h.submit());assertFalse(h.lease.busy)
    }
    @Test fun settingsFailureReleasesLeaseWithoutTakingDisplayedImage() {
        val h=Harness();h.submit(prepare={error("settings")})
        assertFalse(h.lease.busy);assertTrue(h.lease.foreignStart())
        assertEquals(0,h.transferred);assertEquals(0,h.disposed.get());assertTrue(h.result!!.isFailure)
    }
    @Test fun schedulingFailureReleasesLeaseAndTransferredImage() {
        val h=Harness();h.submit(executor=Executor { error("rejected") })
        assertFalse(h.lease.busy);assertTrue(h.lease.foreignStart())
        assertEquals(1,h.disposed.get());assertEquals(1,h.delivered.get());assertTrue(h.result!!.isFailure)
    }
    @Test fun ocrFailureDisposesAndReleasesAfterActualWork() {
        val h=Harness();h.submit(work={assertTrue(h.lease.busy);error("ocr")})
        assertFalse(h.lease.busy);assertEquals(1,h.disposed.get());assertTrue(h.result!!.isFailure)
    }
    @Test fun evenCleanupFailureCannotRetainLease() {
        val lease=ScreenshotRescanLeaseV1()
        ScreenshotRescanExecutionV1.submit(
            {lease.acquireChecked { null }},lease::complete,{1},{},Executor { it.run() },{it},
            {error("dispose")},{},
        )
        assertFalse(lease.busy);assertTrue(lease.foreignStart())
    }
    @Test fun transferFailureReleasesTransferredImageAndLease() {
        val lease=ScreenshotRescanLeaseV1();var disposed=0;var failure=false
        ScreenshotRescanExecutionV1.submit(
            {lease.acquireChecked { null }},lease::complete,{1},{error("transfer")},Executor { it.run() },{it},
            {disposed++},{failure=it.isFailure},
        )
        assertTrue(failure);assertEquals(1,disposed);assertFalse(lease.busy)
    }
    @Test fun queuedButRejectedWorkerCannotRunAfterLeaseWasReleased() {
        val h=Harness();lateinit var queued:Runnable;var worked=false
        h.submit(executor=Executor {queued=it;error("rejected")},work={worked=true;it})
        assertFalse(h.lease.busy);queued.run()
        assertFalse(worked);assertEquals(1,h.disposed.get());assertEquals(1,h.delivered.get())
    }
    @Test fun executorStartingThenThrowingCannotReleaseRunningWorker() {
        val h=Harness();val started=CountDownLatch(1);val finish=CountDownLatch(1);val done=CountDownLatch(1)
        h.submit(executor=Executor { job ->
            Thread { try {job.run()} finally {done.countDown()} }.start()
            assertTrue(started.await(3,TimeUnit.SECONDS));error("late rejection")
        },work={started.countDown();check(finish.await(3,TimeUnit.SECONDS));it})
        try { assertTrue(h.lease.busy);assertFalse(h.lease.foreignStart());assertEquals(0,h.disposed.get()) }
        finally {finish.countDown()}
        assertTrue(done.await(3,TimeUnit.SECONDS));assertFalse(h.lease.busy)
        assertEquals(1,h.disposed.get());assertEquals(1,h.delivered.get())
    }
    @Test fun visualTimeoutAndInterruptCannotReleaseNativeWork() {
        val h=Harness();val started=CountDownLatch(1);val nativeFinish=CountDownLatch(1);val interruptedWait=CountDownLatch(1)
        val done=CountDownLatch(1);lateinit var thread: Thread
        h.submit(executor=Executor { job ->thread=Thread {try {job.run()} finally {done.countDown()}};thread.start() },work={
            started.countDown()
            ScreenshotRescanExecutionV1.awaitCompletion {
                try {nativeFinish.await()} catch(e:InterruptedException) {interruptedWait.countDown();throw e}
            }
            assertTrue(Thread.currentThread().isInterrupted);it
        })
        assertTrue(started.await(3,TimeUnit.SECONDS));thread.interrupt()
        try {
            assertTrue(interruptedWait.await(3,TimeUnit.SECONDS))
            assertTrue(h.lease.busy);assertFalse(h.lease.foreignStart());assertEquals(0,h.disposed.get())
        } finally {nativeFinish.countDown()}
        assertTrue(done.await(3,TimeUnit.SECONDS));assertFalse(h.lease.busy);assertEquals(1,h.disposed.get())
    }
    private fun source(name:String):String {
        val root=generateSequence(File(System.getProperty("user.dir") ?: ".").absoluteFile) { it.parentFile }
            .first { File(it,"app/build.gradle.kts").exists() }
        return File(root,"app/src/main/java/com/bigcorps/driveraimvp/$name.kt").readText()
    }
    @Test fun linkedAndManuallyPickedImagesAreViewableWithoutOcr() {
        val ui=source("OfferRescanActivityV1")
        assertTrue(ui.contains("if(linked != null) load"))
        assertTrue(ui.contains("ScreenshotRescanComparisonV1.original(original)"))
        val picker=ui.substringAfter("private fun pick()").substringBefore("@Deprecated")
        assertFalse(picker.contains("acquire"));assertFalse(picker.contains("enabled"))
        val load=ui.substringAfter("private fun load(").substringBefore("private fun scan()")
        assertFalse(load.contains("TextRecognition"));assertTrue(load.contains("activity.refreshSafety()"))
        assertTrue(ui.indexOf("val block = refreshSafety()") < ui.indexOf("if(linked != null) load"))
        assertFalse(ScreenshotRescanGateV1.shouldAutoPick(null,true,false))
        assertFalse(ScreenshotRescanGateV1.shouldAutoPick(null,false,true))
        assertTrue(ScreenshotRescanGateV1.shouldAutoPick(null,false,false))
    }
}
