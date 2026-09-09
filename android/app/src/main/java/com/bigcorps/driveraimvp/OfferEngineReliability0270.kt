package com.srrotas.app

import android.content.Context
import android.os.SystemClock
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/**
 * 0.27.0-alpha2 — diagnóstico persistente do Offer Engine.
 *
 * Mantém a instrumentação da alpha1 e acrescenta:
 * - histórico circular das sessões anteriores;
 * - eventos de falha/recuperação, inclusive manuais;
 * - telemetria shadow baseada na distribuição de 38.771 registros históricos
 *   válidos, sem alterar parser, HUD ou verdict.
 *
 * Não persiste OCR bruto, screenshot, endereço ou coordenada.
 */
class OfferEngineReliability0270(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(
        PREFS,
        Context.MODE_PRIVATE,
    )

    private var active = false
    private var journeyPrefix = ""
    private var startedAt = 0L
    private var endedAt = 0L
    private var endReason = ""

    private var captureWidth = 0
    private var captureHeight = 0
    private var lastOcrWidth = 0
    private var lastOcrHeight = 0

    private var framesSeen = 0L
    private var sampledFrames = 0L
    private var detectorFirst = 0L
    private var detectorVisual = 0L
    private var detectorPeriodic = 0L
    private var detectorUnchanged = 0L
    private var maxChangedCells = 0
    private var maxAverageDelta = 0.0

    private var queuedFrames = 0L
    private var replacedQueuedFrames = 0L

    private var ocrStarted = 0L
    private var ocrCompleted = 0L
    private var ocrFailures = 0L
    private var totalOcrMs = 0L
    private var maxOcrMs = 0L

    private var candidateFrames = 0L
    private var rejectedCandidateFrames = 0L
    private var parsedFrames = 0L
    private var offersParsed = 0L
    private var ownAppFrames = 0L
    private var nonCandidateFrames = 0L

    private var candidateRejectStreak = 0
    private var maxCandidateRejectStreak = 0
    private var semanticGapActive = false
    private var semanticGapEpisodes = 0L

    private var watchdogSurfaceRearms = 0L
    private var watchdogWorkerRebuilds = 0L
    private var watchdogOcrResets = 0L
    private var manualRecoveries = 0L
    private var manualFailureMarks = 0L

    private var shadowOffersSeen = 0L
    private var shadowTailOffers = 0L
    private var shadowInconsistentOffers = 0L
    private val shadowSignalCounts = linkedMapOf<String, Long>()

    private var lastImageAt = 0L
    private var lastSampleAt = 0L
    private var lastOcrAt = 0L
    private var lastCandidateAt = 0L
    private var lastOfferAt = 0L
    private var lastPlatform = ""
    private var lastRouteReason = ""

    private var lastPersistElapsed = 0L

    @Synchronized
    fun begin(
        journeyId: String?,
        width: Int,
        height: Int,
    ) {
        archiveStoredSnapshot("next_session")
        resetInMemory()
        active = true
        journeyPrefix = journeyId?.take(8).orEmpty()
        startedAt = System.currentTimeMillis()
        captureWidth = width.coerceAtLeast(0)
        captureHeight = height.coerceAtLeast(0)
        appendEvent(
            type = "session_begin",
            detail = "capture=${captureWidth}x${captureHeight}",
        )
        persist(force = true)
    }

    @Synchronized
    fun captureResized(width: Int, height: Int) {
        captureWidth = width.coerceAtLeast(0)
        captureHeight = height.coerceAtLeast(0)
        appendEvent(
            type = "capture_resize",
            detail = "${captureWidth}x${captureHeight}",
        )
        persist()
    }

    @Synchronized
    fun frameSeen() {
        framesSeen++
        lastImageAt = System.currentTimeMillis()
    }

    @Synchronized
    fun sampled() {
        sampledFrames++
        lastSampleAt = System.currentTimeMillis()
    }

    @Synchronized
    fun detector(decision: FrameChangeDetector.Decision) {
        when (decision.reason) {
            FrameChangeDetector.Reason.FIRST -> detectorFirst++
            FrameChangeDetector.Reason.VISUAL_CHANGE -> detectorVisual++
            FrameChangeDetector.Reason.PERIODIC_RECOVERY -> detectorPeriodic++
            FrameChangeDetector.Reason.UNCHANGED -> detectorUnchanged++
        }
        if (decision.changedCells > maxChangedCells) {
            maxChangedCells = decision.changedCells
        }
        if (decision.averageDelta > maxAverageDelta) {
            maxAverageDelta = decision.averageDelta
        }
        persist()
    }

    @Synchronized
    fun queued(replacedPrevious: Boolean) {
        queuedFrames++
        if (replacedPrevious) replacedQueuedFrames++
        persist()
    }

    @Synchronized
    fun ocrStarted(width: Int, height: Int) {
        ocrStarted++
        lastOcrWidth = width.coerceAtLeast(0)
        lastOcrHeight = height.coerceAtLeast(0)
    }

    @Synchronized
    fun route(
        platform: String?,
        candidate: Boolean,
        ownApp: Boolean,
        reason: String,
        offers: Int,
    ) {
        val now = System.currentTimeMillis()
        lastPlatform = platform.orEmpty().take(24)
        lastRouteReason = reason.take(120)

        when {
            ownApp -> {
                ownAppFrames++
                candidateRejectStreak = 0
            }

            offers > 0 -> {
                parsedFrames++
                offersParsed += offers
                lastOfferAt = now
                candidateRejectStreak = 0
                semanticGapActive = false
            }

            candidate -> {
                candidateFrames++
                rejectedCandidateFrames++
                lastCandidateAt = now
                candidateRejectStreak++
                if (candidateRejectStreak > maxCandidateRejectStreak) {
                    maxCandidateRejectStreak = candidateRejectStreak
                }
            }

            else -> {
                nonCandidateFrames++
                candidateRejectStreak = 0
            }
        }

        updateSemanticGap(now)
        persist()
    }

    /**
     * Observação estatística somente. Não bloqueia oferta e não altera HUD.
     */
    @Synchronized
    fun shadowEvaluation(
        offerCount: Int,
        tailOfferCount: Int,
        inconsistentOfferCount: Int,
        signals: List<String>,
    ) {
        shadowOffersSeen += offerCount.coerceAtLeast(0)
        shadowTailOffers += tailOfferCount.coerceAtLeast(0)
        shadowInconsistentOffers += inconsistentOfferCount.coerceAtLeast(0)

        signals.forEach { signal ->
            val safe = signal.take(80)
            shadowSignalCounts[safe] =
                (shadowSignalCounts[safe] ?: 0L) + 1L
        }

        persist()
    }

    @Synchronized
    fun ocrCompleted(durationMs: Long, detectedOffers: Int) {
        ocrCompleted++
        val safe = durationMs.coerceAtLeast(0L)
        totalOcrMs += safe
        if (safe > maxOcrMs) maxOcrMs = safe
        lastOcrAt = System.currentTimeMillis()

        if (detectedOffers > 0 && lastOfferAt == 0L) {
            lastOfferAt = lastOcrAt
        }

        updateSemanticGap(lastOcrAt)
        persist()
    }

    @Synchronized
    fun ocrFailed() {
        ocrFailures++
        lastOcrAt = System.currentTimeMillis()
        appendEvent("ocr_failure", null)
        persist(force = true)
    }

    @Synchronized
    fun markFailure(source: String) {
        manualFailureMarks++
        appendEvent(
            type = "manual_failure_mark",
            detail = source.take(120),
        )
        persist(force = true)
    }

    @Synchronized
    fun recovery(kind: String, reason: String? = null) {
        when (kind) {
            "surface_rearm" -> watchdogSurfaceRearms++
            "worker_rebuild" -> watchdogWorkerRebuilds++
            "ocr_reset" -> watchdogOcrResets++
            "manual_recovery" -> manualRecoveries++
        }

        appendEvent(
            type = "recovery:$kind",
            detail = reason?.take(120),
        )
        persist(force = true)
    }

    @Synchronized
    fun checkpoint() {
        updateSemanticGap(System.currentTimeMillis())
        persist()
    }

    @Synchronized
    fun end(reason: String) {
        if (!active && startedAt == 0L) return

        active = false
        endedAt = System.currentTimeMillis()
        endReason = reason.take(80)
        updateSemanticGap(endedAt)
        appendEvent("session_end", endReason)
        persist(force = true)
    }

    private fun updateSemanticGap(now: Long) {
        val ocrRecentlyAlive =
            lastOcrAt > 0L &&
                now - lastOcrAt <= SEMANTIC_OCR_FRESH_MS

        val repeatedCandidateFailures =
            candidateRejectStreak >= SEMANTIC_REJECT_STREAK

        val noRecentOffer =
            lastOfferAt == 0L ||
                now - lastOfferAt >= SEMANTIC_NO_OFFER_MS

        val gap =
            ocrRecentlyAlive &&
                repeatedCandidateFailures &&
                noRecentOffer

        if (gap && !semanticGapActive) {
            semanticGapEpisodes++
            appendEvent(
                type = "semantic_gap_enter",
                detail = "reject_streak=$candidateRejectStreak",
            )
        }

        semanticGapActive = gap
    }

    private fun persist(force: Boolean = false) {
        val elapsed = SystemClock.elapsedRealtime()
        if (
            !force &&
            lastPersistElapsed > 0L &&
            elapsed - lastPersistElapsed < PERSIST_INTERVAL_MS
        ) {
            return
        }

        lastPersistElapsed = elapsed
        prefs.edit()
            .putString(KEY_LAST_SNAPSHOT, snapshotJson().toString())
            .apply()
    }

    private fun appendEvent(type: String, detail: String?) {
        val events = readArray(KEY_EVENTS)

        val item = JSONObject().apply {
            put("at", System.currentTimeMillis())
            put("type", type.take(80))
            put("journey_prefix", journeyPrefix)
            put("detail", detail ?: JSONObject.NULL)
            put("frames_seen", framesSeen)
            put("sampled_frames", sampledFrames)
            put("ocr_completed", ocrCompleted)
            put("offers_parsed", offersParsed)
            put("candidate_reject_streak", candidateRejectStreak)
            put("semantic_gap_active", semanticGapActive)
            put("last_platform", lastPlatform)
            put("last_route_reason", lastRouteReason)
        }

        events.put(item)

        prefs.edit()
            .putString(
                KEY_EVENTS,
                trimArray(events, MAX_EVENTS).toString(),
            )
            .apply()
    }

    private fun archiveStoredSnapshot(reason: String) {
        val raw =
            prefs.getString(KEY_LAST_SNAPSHOT, null)
                .orEmpty()

        if (raw.isBlank()) return

        val previous =
            runCatching { JSONObject(raw) }
                .getOrNull()
                ?: return

        val started =
            previous.optLong("started_at", 0L)

        if (started <= 0L) return

        val history = readArray(KEY_HISTORY)

        if (history.length() > 0) {
            val last =
                history.optJSONObject(history.length() - 1)
            if (last?.optLong("started_at", -1L) == started) {
                return
            }
        }

        previous.put("archive_reason", reason)
        history.put(previous)

        prefs.edit()
            .putString(
                KEY_HISTORY,
                trimArray(history, MAX_SESSIONS).toString(),
            )
            .apply()
    }

    private fun readArray(key: String): JSONArray {
        val raw =
            prefs.getString(key, null)
                .orEmpty()

        return runCatching {
            if (raw.isBlank()) JSONArray()
            else JSONArray(raw)
        }.getOrElse {
            JSONArray()
        }
    }

    private fun trimArray(
        source: JSONArray,
        maxItems: Int,
    ): JSONArray {
        val out = JSONArray()
        val start =
            (source.length() - maxItems)
                .coerceAtLeast(0)

        for (index in start until source.length()) {
            out.put(source.get(index))
        }

        return out
    }

    private fun resetInMemory() {
        active = false
        journeyPrefix = ""
        startedAt = 0L
        endedAt = 0L
        endReason = ""

        captureWidth = 0
        captureHeight = 0
        lastOcrWidth = 0
        lastOcrHeight = 0

        framesSeen = 0L
        sampledFrames = 0L
        detectorFirst = 0L
        detectorVisual = 0L
        detectorPeriodic = 0L
        detectorUnchanged = 0L
        maxChangedCells = 0
        maxAverageDelta = 0.0

        queuedFrames = 0L
        replacedQueuedFrames = 0L

        ocrStarted = 0L
        ocrCompleted = 0L
        ocrFailures = 0L
        totalOcrMs = 0L
        maxOcrMs = 0L

        candidateFrames = 0L
        rejectedCandidateFrames = 0L
        parsedFrames = 0L
        offersParsed = 0L
        ownAppFrames = 0L
        nonCandidateFrames = 0L

        candidateRejectStreak = 0
        maxCandidateRejectStreak = 0
        semanticGapActive = false
        semanticGapEpisodes = 0L

        watchdogSurfaceRearms = 0L
        watchdogWorkerRebuilds = 0L
        watchdogOcrResets = 0L
        manualRecoveries = 0L
        manualFailureMarks = 0L

        shadowOffersSeen = 0L
        shadowTailOffers = 0L
        shadowInconsistentOffers = 0L
        shadowSignalCounts.clear()

        lastImageAt = 0L
        lastSampleAt = 0L
        lastOcrAt = 0L
        lastCandidateAt = 0L
        lastOfferAt = 0L
        lastPlatform = ""
        lastRouteReason = ""

        lastPersistElapsed = 0L
    }

    private fun snapshotJson(): JSONObject {
        val avgOcr =
            if (ocrCompleted == 0L) {
                0.0
            } else {
                totalOcrMs.toDouble() /
                    ocrCompleted.toDouble()
            }

        val detectorAccepted =
            detectorFirst +
                detectorVisual +
                detectorPeriodic

        val parsedCandidateTotal =
            parsedFrames +
                rejectedCandidateFrames

        val parserSuccessRate =
            if (parsedCandidateTotal == 0L) {
                0.0
            } else {
                parsedFrames.toDouble() /
                    parsedCandidateTotal.toDouble()
            }

        return JSONObject().apply {
            put(
                "schema",
                "sr-offer-reliability-0270-alpha2",
            )
            put("active", active)
            put("journey_prefix", journeyPrefix)
            put("started_at", startedAt)
            put("ended_at", endedAt)
            put("end_reason", endReason)

            put("capture_width", captureWidth)
            put("capture_height", captureHeight)
            put(
                "capture_rgba_bytes_estimate",
                captureWidth.toLong() *
                    captureHeight.toLong() *
                    4L,
            )
            put("last_ocr_width", lastOcrWidth)
            put("last_ocr_height", lastOcrHeight)

            put("frames_seen", framesSeen)
            put("sampled_frames", sampledFrames)
            put("detector_first", detectorFirst)
            put("detector_visual", detectorVisual)
            put("detector_periodic", detectorPeriodic)
            put("detector_unchanged", detectorUnchanged)
            put("detector_accepted", detectorAccepted)
            put(
                "detector_max_changed_cells",
                maxChangedCells,
            )
            put(
                "detector_max_average_delta",
                String.format(
                    Locale.US,
                    "%.3f",
                    maxAverageDelta,
                ),
            )

            put("queued_frames", queuedFrames)
            put(
                "replaced_queued_frames",
                replacedQueuedFrames,
            )

            put("ocr_started", ocrStarted)
            put("ocr_completed", ocrCompleted)
            put("ocr_failures", ocrFailures)
            put(
                "ocr_average_ms",
                String.format(
                    Locale.US,
                    "%.1f",
                    avgOcr,
                ),
            )
            put("ocr_max_ms", maxOcrMs)

            put("candidate_frames", candidateFrames)
            put(
                "rejected_candidate_frames",
                rejectedCandidateFrames,
            )
            put("parsed_frames", parsedFrames)
            put("offers_parsed", offersParsed)
            put("own_app_frames", ownAppFrames)
            put(
                "non_candidate_frames",
                nonCandidateFrames,
            )
            put(
                "candidate_parse_success_rate",
                String.format(
                    Locale.US,
                    "%.4f",
                    parserSuccessRate,
                ),
            )
            put(
                "candidate_reject_streak",
                candidateRejectStreak,
            )
            put(
                "max_candidate_reject_streak",
                maxCandidateRejectStreak,
            )
            put(
                "semantic_gap_active",
                semanticGapActive,
            )
            put(
                "semantic_gap_episodes",
                semanticGapEpisodes,
            )

            put(
                "watchdog_surface_rearms",
                watchdogSurfaceRearms,
            )
            put(
                "watchdog_worker_rebuilds",
                watchdogWorkerRebuilds,
            )
            put(
                "watchdog_ocr_resets",
                watchdogOcrResets,
            )
            put(
                "manual_recoveries",
                manualRecoveries,
            )
            put(
                "manual_failure_marks",
                manualFailureMarks,
            )

            put(
                "shadow_offers_seen",
                shadowOffersSeen,
            )
            put(
                "shadow_tail_offers",
                shadowTailOffers,
            )
            put(
                "shadow_inconsistent_offers",
                shadowInconsistentOffers,
            )
            put(
                "shadow_signal_counts",
                JSONObject().apply {
                    shadowSignalCounts.forEach {
                            (key, value) ->
                        put(key, value)
                    }
                },
            )

            put("last_image_at", lastImageAt)
            put("last_sample_at", lastSampleAt)
            put("last_ocr_at", lastOcrAt)
            put("last_candidate_at", lastCandidateAt)
            put("last_offer_at", lastOfferAt)
            put("last_platform", lastPlatform)
            put(
                "last_route_reason",
                lastRouteReason,
            )

            put(
                "privacy",
                "Somente métricas técnicas; sem OCR bruto, screenshot, endereço ou coordenada.",
            )
        }
    }

    companion object {
        private const val PREFS =
            "sr_offer_engine_reliability_0270"

        private const val KEY_LAST_SNAPSHOT =
            "last_snapshot"

        private const val KEY_HISTORY =
            "recent_sessions"

        private const val KEY_EVENTS =
            "recent_events"

        private const val PERSIST_INTERVAL_MS =
            4_000L

        private const val MAX_SESSIONS =
            10

        private const val MAX_EVENTS =
            30

        private const val SEMANTIC_REJECT_STREAK =
            5

        private const val SEMANTIC_NO_OFFER_MS =
            15_000L

        private const val SEMANTIC_OCR_FRESH_MS =
            10_000L

        fun readLast(context: Context): JSONObject {
            val prefs =
                context.applicationContext
                    .getSharedPreferences(
                        PREFS,
                        Context.MODE_PRIVATE,
                    )

            val raw =
                prefs.getString(
                    KEY_LAST_SNAPSHOT,
                    null,
                ).orEmpty()

            val snapshot =
                runCatching {
                    if (raw.isBlank()) {
                        JSONObject()
                    } else {
                        JSONObject(raw)
                    }
                }.getOrElse {
                    JSONObject()
                }

            val history =
                runCatching {
                    JSONArray(
                        prefs.getString(
                            KEY_HISTORY,
                            "[]",
                        ) ?: "[]",
                    )
                }.getOrElse {
                    JSONArray()
                }

            val events =
                runCatching {
                    JSONArray(
                        prefs.getString(
                            KEY_EVENTS,
                            "[]",
                        ) ?: "[]",
                    )
                }.getOrElse {
                    JSONArray()
                }

            snapshot.put(
                "recent_sessions",
                history,
            )
            snapshot.put(
                "recent_events",
                events,
            )

            return snapshot
        }
    }
}
