package com.srrotas.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Cache local somente para disponibilidade imediata da janela flutuante. */
object MessagePresetStore023 {
    private const val PREFS = "sr_rotas_023_message_presets"
    private const val KEY_JSON = "messages_json"
    private const val KEY_SYNCED_AT = "synced_at"

    fun load(context: Context): List<MessageShortcut023> {
        val raw = context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_JSON, null)
            ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            val list = buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    add(
                        MessageShortcut023(
                            id = item.optString("id"),
                            order = item.optInt("order", index),
                            shortLabel = item.optString("shortLabel", (index + 1).toString()),
                            accessibilityLabel = if (item.isNull("accessibilityLabel")) null else item.optString("accessibilityLabel").takeIf(String::isNotBlank),
                            text = item.optString("text"),
                            colorToken = item.optString("colorToken", MessageShortcutRules023.colorFor(index)),
                            enabled = item.optBoolean("enabled", true),
                        ),
                    )
                }
            }
            MessageShortcutRules023.normalized(list)
        }.getOrDefault(emptyList())
    }

    fun visible(context: Context): List<MessageShortcut023> =
        MessageShortcutRules023.visible(load(context))

    fun save(context: Context, items: List<MessageShortcut023>) {
        val normalized = MessageShortcutRules023.normalized(items)
        val array = JSONArray()
        normalized.forEach { item ->
            array.put(
                JSONObject().apply {
                    put("id", item.id)
                    put("order", item.order)
                    put("shortLabel", item.shortLabel)
                    put("accessibilityLabel", item.accessibilityLabel ?: JSONObject.NULL)
                    put("text", item.text)
                    put("colorToken", item.colorToken)
                    put("enabled", item.enabled)
                },
            )
        }
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_JSON, array.toString())
            .putLong(KEY_SYNCED_AT, System.currentTimeMillis())
            .apply()
    }

    fun syncedAt(context: Context): Long =
        context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getLong(KEY_SYNCED_AT, 0L)
}
