package com.srrotas.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

/** Local bounded journal, distinct from remote telemetry and ActiveAssistant026.
 * Calls copy only facts; disk IO never runs on the Reader worker. */
object FieldPipelineTraceV1 {
    private const val PREFS = "field_pipeline_trace_v1"
    private const val MAX_EPISODES = 12
    private const val MAX_EVENTS = 64
    private const val RETENTION_MS = 7 * 24 * 60 * 60_000L
    private val dropped = AtomicLong()
    private val disk = Executors.newSingleThreadScheduledExecutor()
    private var flushScheduled = false
    private val executor = ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS,
        ArrayBlockingQueue<Runnable>(128), java.util.concurrent.RejectedExecutionHandler { task, pool ->
            dropped.incrementAndGet()
            pool.queue.poll()
            if(!pool.queue.offer(task)) dropped.incrementAndGet()
        })
    private val lock = Any()
    private var episodes: JSONArray? = null
    private val reasons = setOf("observed_after_official_save", "inferred_from_accepted_capture", "missing", "present",
        "resolved", "partial", "pending", "failed", "unresolved", "unknown", "source_offer_missing", "context_missing",
        "destination_coordinates_missing_or_invalid", "eta_missing_or_invalid", "no_current_ride", "ready", "selected",
        "runtime", "ui", "cache", "http_success", "fetch_failed", "zero_reason_not_exposed_by_backend",
        "returned_opportunity_pois_not_catalogue_count", "backend_strong", "discovery_opportunity_available",
        "region_signal_generated", "payload_incompleto", "current_ride_changed", "backend_not_eligible_no_opportunities",
        "cooldown_mesma_corrida", "cooldown_mesma_oportunidade", "outside_region_eta_window", "outside_discovery_eta_window",
        "overlay_permission_missing", "hud_host_indisponivel", "hud_host_not_attached", "hud_card_not_attached",
        "flags_or_stale_result", "host_and_card_attached", "accepted_current_ride_click", "stale_click", "stale_render",
        "surface_ready", "bitmap_visible_cartography_unverified", "assistant_disabled", "surface_requested", "ui_disabled", "surface_not_ready", "stale_current_ride", "no_strong_opportunity",
        "hud_attached_visibility_pending", "card_not_measured_or_hidden", "stale_result", "http_error",
        "http_unauthorized", "network_failed", "payload_invalid", "request_precondition_failed",
        "latest_offer_snapshot", "persisted_recent_offer")

    fun event(context: Context, offerId: String?, stage: String, reason: String, value: Long? = null) {
        if (offerId.isNullOrBlank() || !BuildConfig.VERSION_NAME.contains("field")) return
        val app = context.applicationContext
        val safeReason = reason.takeIf { it in reasons } ?: "unknown"
        val safeStage = stage.takeIf { it in stages } ?: return
        val now = System.currentTimeMillis()
        executor.execute { runCatching {
            // Hashing/formatting also stays off the Reader worker.
            val key = MessageDigest.getInstance("SHA-256").digest(offerId.toByteArray())
                .take(12).joinToString("") { (it.toInt() and 255).toString(16).padStart(2,'0') }
            synchronized(lock) {
            val list = load(app, System.currentTimeMillis())
            val index = (0 until list.length()).firstOrNull { list.getJSONObject(it).optString("offer_ref") == key }
            val episode = if(index == null) JSONObject().put("offer_ref", key).put("events", JSONArray())
                else list.getJSONObject(index).also { list.remove(index) }
            list.put(episode) // LRU: active rides are not evicted merely because they were created first.
            episode.put("updated_at", maxOf(episode.optLong("updated_at",0L),now))
            val events = episode.getJSONArray("events")
            // Repeated polling doesn't erase historical transition evidence.
            val last = events.optJSONObject(events.length() - 1)
            if (last?.optString("stage") != safeStage || last.optString("reason") != safeReason ||
                (value != null && last.optLong("value", Long.MIN_VALUE) != value)) {
                events.put(JSONObject().put("at", now).put("stage", safeStage).put("reason", safeReason).apply {
                    if (value != null) put("value", value)
                })
                while (events.length() > MAX_EVENTS) events.remove(0)
            }
            // Last state by layer is retained even when the bounded event ring rolls over.
            val layers = episode.optJSONObject("layers") ?: JSONObject().also { episode.put("layers", it) }
            layers.put(safeStage, JSONObject().put("at", now).put("reason", safeReason).apply { if(value != null) put("value", value) })
            trim(list, System.currentTimeMillis())
            if(!flushScheduled) {
                flushScheduled = true
                disk.schedule({ synchronized(lock) {
                    runCatching {
                        app.getSharedPreferences(PREFS, 0).edit().putString("episodes", episodes.toString()).apply()
                    }
                    flushScheduled = false
                } },500,TimeUnit.MILLISECONDS)
            }
        } } }
    }

    private val stages = setOf("OCR_CAPTURED", "OFFER_PARSED", "PICKUP_TEXT_PRESENT", "DESTINATION_TEXT_PRESENT",
        "GEOCODE_RESOLVED", "GEOCODE_PARTIAL", "GEOCODE_MISSING", "DESTINATION_COORDINATES_VALID", "DESTINATION_CELL_PRESENT",
        "PARSER_CONFIDENCE_PERCENT", "RIDE_SELECTED", "RADAR_SPEC_READY", "RADAR_SPEC_BLOCKED", "ETA_DELTA_SECONDS",
        "RADAR_FETCH_STARTED", "RADAR_FETCH_SUCCESS", "RADAR_FETCH_FAILED", "RADAR_POIS_COUNT", "RADAR_OPPORTUNITIES_COUNT",
        "BASELINE_SAMPLES", "BASELINE_PROBABILITY_PERCENT", "ASSISTANT_ELIGIBLE", "ASSISTANT_BLOCKED", "ASSISTANT_RENDER_ATTEMPT",
        "ASSISTANT_VISIBLE", "ASSISTANT_VISIBLE_BLOCKED", "ASSISTANT_VIEW_CLICKED", "RADAR_OPENED", "RADAR_OPENED_BLOCKED",
        "MAP_BITMAP_VISIBLE", "MAP_DATA_EMPTY", "RADAR_HTTP_STATUS", "BACKEND_ASSISTANT_ELIGIBLE",
        "BACKEND_ASSISTANT_BLOCKED", "ASSISTANT_HOST_ATTACHED", "RADAR_OPEN_REQUESTED", "RADAR_SPEC_SOURCE")

    private fun load(context: Context, now: Long): JSONArray = (episodes ?: runCatching {
        JSONArray(context.getSharedPreferences(PREFS, 0).getString("episodes", "[]"))
    }.getOrDefault(JSONArray()).also { episodes = it }).also { trim(it, now) }

    private fun trim(list: JSONArray, now: Long) {
        val keep = FieldPipelineFactsV1.retainedEpisodeIndices(
            (0 until list.length()).map { list.optJSONObject(it)?.optLong("updated_at") ?: 0L },
            now,MAX_EPISODES,RETENTION_MS)
        for (i in list.length()-1 downTo 0) if(i !in keep) list.remove(i)
    }

    fun snapshot(context: Context): JSONObject = synchronized(lock) {
        JSONObject().put("schema", "field-pipeline-trace-v1")
            .put("assistant", "destination_contextual_only")
            .put("traditional_assistant", "see_existing_active_assistant_diagnostic_and_history")
            .put("ocr_capture_evidence", "inferred_only_for_persisted_capture_offers_not_rejected_frames")
            .put("cartography_verified", false).put("max_episodes", MAX_EPISODES).put("max_events_per_episode", MAX_EVENTS)
            .put("dropped_queue_events", dropped.get()).put("disk_debounce_ms", 500)
            .put("retention_days", 7).put("episodes", JSONArray(load(context, System.currentTimeMillis()).toString()))
    }

    fun offer(context: Context, offer: RideOffer) {
        if(offer.captureMethod.startsWith("media-projection") || offer.captureMethod.startsWith("accessibility"))
            event(context, offer.localId, "OCR_CAPTURED", "inferred_from_accepted_capture")
        event(context, offer.localId, "OFFER_PARSED", "observed_after_official_save")
        event(context, offer.localId, "PARSER_CONFIDENCE_PERCENT", "observed_after_official_save", (offer.confidence * 100).toLong())
        context(context, offer.localId, offer.context)
    }

    fun context(app: Context, id: String, c: OfferContext?) {
        event(app,id,"PICKUP_TEXT_PRESENT",if(c?.pickupLabel.isNullOrBlank()) "missing" else "present")
        event(app,id,"DESTINATION_TEXT_PRESENT",if(c?.destinationLabel.isNullOrBlank()) "missing" else "present")
        event(app,id,FieldPipelineFactsV1.geocodeStage(c?.geocodeStatus),FieldPipelineFactsV1.geocodeReason(c?.geocodeStatus))
        event(app,id,"DESTINATION_COORDINATES_VALID",if(FieldPipelineFactsV1.coordinatesValid(c?.destinationLat,c?.destinationLng)) "present" else "missing")
        event(app,id,"DESTINATION_CELL_PRESENT",if(c?.destinationCell.isNullOrBlank()) "missing" else "present")
    }

    fun result(context: Context, id: String?, result: RadarContextualResultV1, reason: String) {
        event(context,id,"RADAR_FETCH_SUCCESS",reason)
        event(context,id,if(result.assistant.eligible) "BACKEND_ASSISTANT_ELIGIBLE" else "BACKEND_ASSISTANT_BLOCKED",result.assistant.reason)
        event(context,id,"BASELINE_SAMPLES","present",result.baseline.samples.toLong())
        result.baseline.probabilityPct?.let { event(context,id,"BASELINE_PROBABILITY_PERCENT","present",it.toLong()) }
        event(context,id,"RADAR_POIS_COUNT","returned_opportunity_pois_not_catalogue_count",result.opportunities.map { it.poiId }.distinct().size.toLong())
        event(context,id,"RADAR_OPPORTUNITIES_COUNT",if(result.opportunities.isEmpty()) "zero_reason_not_exposed_by_backend" else "present",result.opportunities.size.toLong())
        if(result.opportunities.isEmpty()) event(context,id,"MAP_DATA_EMPTY","zero_reason_not_exposed_by_backend")
    }
}
