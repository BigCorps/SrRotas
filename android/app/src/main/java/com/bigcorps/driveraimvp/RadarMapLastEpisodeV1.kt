package com.srrotas.app

import android.content.Context
import org.json.JSONObject

/** Whitelist persistida localmente: nunca recebe estado geográfico, bitmap ou texto externo. */
internal object RadarMapLastEpisodeV1 {
    private const val PREFS = "radar_last_map_episode_v1"
    private const val KEY = "episode"
    private val flags = setOf("mapview_created", "style_loaded", "map_fully_callback",
        "live_snapshot_requested", "live_snapshot_success", "live_snapshot_blank",
        "snapshotter_requested", "snapshotter_success", "snapshotter_failed",
        "visible_bitmap_shown", "fallback_text_shown")
    private fun prefs(context: Context) = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    @Synchronized fun begin(context: Context, demo: Boolean): Long {
        val id = maxOf(System.currentTimeMillis(), read(context).optLong("episode_started_at") + 1)
        val data = JSONObject().put("schema", "sr-radar-map-episode-v1")
            .put("app_version", BuildConfig.VERSION_NAME).put("episode_started_at", id)
            .put("source", if (demo) "demo" else "real")
        flags.forEach { data.put(it, false) }
        prefs(context).edit().putString(KEY, data.toString()).apply()
        return id
    }
    @Synchronized fun event(context: Context, episode: Long, name: String) {
        val data = read(context)
        if (data.optLong("episode_started_at") != episode) return
        when {
            name in flags -> data.put(name, true)
            name in setOf("snapshot_blank", "snapshot_error", "snapshot_timeout", "snapshotter_error",
                "snapshotter_timeout", "renderer_error", "load_error") -> data.put("last_error", name)
            name in setOf("released", "detached", "replaced", "hidden", "idle", "demo_exit", "real_open") -> data.put("released_reason", name)
            else -> return
        }
        prefs(context).edit().putString(KEY, data.toString()).apply()
    }
    @Synchronized fun read(context: Context): JSONObject = runCatching {
        JSONObject(prefs(context).getString(KEY, "{}") ?: "{}")
    }.getOrElse { JSONObject() }
}
