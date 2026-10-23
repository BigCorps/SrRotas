package com.srrotas.app

import java.util.concurrent.Executor
import java.util.concurrent.atomic.AtomicInteger

/** Owns the lease across preparation, submission and actual worker completion, never UI lifetime. */
internal object ScreenshotRescanExecutionV1 {
    fun <S, T> submit(
        acquire: () -> ScreenshotRescanBlockV1?, release: () -> Unit,
        prepare: () -> S, transfer: () -> Unit, executor: Executor,
        work: (S) -> T, dispose: () -> Unit, deliver: (Result<T>) -> Unit,
    ): ScreenshotRescanBlockV1? {
        val blocked = acquire()
        if (blocked != null) return blocked
        val state = AtomicInteger(0) // queued, running, finished
        var transferred = false
        fun cleanup() {
            try { if (transferred) dispose() } finally { release() }
        }
        try {
            val settings = prepare()
            transferred = true
            transfer() // caller immediately relinquishes its image reference
            executor.execute {
                if (!state.compareAndSet(0, 1)) return@execute
                val result = runCatching {
                    try { work(settings) }
                    finally { try { cleanup() } finally { state.set(2) } }
                }
                deliver(result)
            }
        } catch (error: Throwable) {
            // An executor may start a worker before throwing. Its running task still owns the lease.
            if (state.compareAndSet(0, 2)) {
                try { cleanup() } finally { deliver(Result.failure(error)) }
            }
        }
        return null
    }

    /** An interrupt is not proof that native ML Kit finished. Keep exclusion until completion. */
    fun <T> awaitCompletion(wait: () -> T): T {
        var interrupted = false
        try {
            while (true) {
                try { return wait() } catch (_: InterruptedException) { interrupted = true }
            }
        } finally { if (interrupted) Thread.currentThread().interrupt() }
    }
}
