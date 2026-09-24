package com.srrotas.app

import org.json.JSONObject
import java.util.ArrayDeque
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 0.33 — gate em memória para evitar múltiplos screenshots da mesma oferta.
 *
 * Não decide oferta, não toca no Reader e não altera persistência oficial. Ele
 * apenas impede que frames consecutivos da mesma oferta gerem arquivos repetidos.
 */
internal object ScreenshotStorageGuard033 {
    const val DEDUPE_WINDOW_MS = 60_000L
    const val MAX_RECENT = 180

    data class Candidate(
        val platform: String,
        val fare: Double,
        val pickupKm: Double?,
        val tripKm: Double?,
        val totalKm: Double?,
    )

    data class Snapshot(
        val attempts: Long,
        val accepted: Long,
        val duplicateSkipped: Long,
        val recentEntries: Int,
    )

    private data class Seen(val candidate: Candidate, val at: Long)
    private val recent = ArrayDeque<Seen>()

    @Volatile private var attempts = 0L
    @Volatile private var accepted = 0L
    @Volatile private var duplicateSkipped = 0L

    @Synchronized
    fun allow(candidate: Candidate, nowMs: Long = System.currentTimeMillis()): Boolean {
        attempts++
        purge(nowMs)
        if (recent.any { sameOffer(it.candidate, candidate) }) {
            duplicateSkipped++
            return false
        }
        recent.addLast(Seen(candidate, nowMs))
        while (recent.size > MAX_RECENT) recent.removeFirst()
        accepted++
        return true
    }

    @Synchronized
    fun resetRuntime() {
        recent.clear()
        attempts = 0
        accepted = 0
        duplicateSkipped = 0
    }

    @Synchronized
    fun snapshot(): Snapshot = Snapshot(attempts, accepted, duplicateSkipped, recent.size)

    @Synchronized
    fun toJson(): JSONObject = snapshot().let { s ->
        JSONObject().apply {
            put("schema", "sr-screenshot-storage-033-v1")
            put("dedupe_window_ms", DEDUPE_WINDOW_MS)
            put("max_recent", MAX_RECENT)
            put("attempts", s.attempts)
            put("accepted", s.accepted)
            put("duplicate_skipped", s.duplicateSkipped)
            put("recent_entries", s.recentEntries)
            put("policy", "uma captura útil por oferta semanticamente equivalente dentro de 60s")
            put("reader_effect", false)
            put("privacy", "Somente contadores do gate; nenhum screenshot ou OCR é serializado no diagnóstico.")
        }
    }

    private fun purge(nowMs: Long) {
        while (recent.isNotEmpty() && nowMs - recent.first().at >= DEDUPE_WINDOW_MS) {
            recent.removeFirst()
        }
    }

    private fun sameOffer(a: Candidate, b: Candidate): Boolean {
        if (!a.platform.equals(b.platform, ignoreCase = true)) return false
        if (cents(a.fare) != cents(b.fare)) return false

        val tripClose = close(a.tripKm, b.tripKm, .20)
        val totalClose = close(a.totalKm, b.totalKm, .35)
        val pickupClose = close(a.pickupKm, b.pickupKm, .25)

        // Mesmo contrato de intenção do dedupe oficial: tarifa + geometria
        // suficientemente estável. Campos ausentes não forçam um segundo arquivo.
        if (tripClose && (totalClose || a.totalKm == null || b.totalKm == null)) return true
        if (totalClose && (pickupClose || a.pickupKm == null || b.pickupKm == null)) return true
        return tripClose && pickupClose
    }

    private fun close(a: Double?, b: Double?, tolerance: Double): Boolean =
        a != null && b != null && abs(a - b) <= tolerance

    private fun cents(value: Double): Int = (value * 100.0).roundToInt()
}
