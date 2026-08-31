package com.srrotas.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.max

/**
 * Cache de mensagens rápidas.
 *
 * 0.24 separa a base sincronizada da personalização local feita no APK:
 * uma atualização GET do backend nunca apaga silenciosamente a edição local.
 */
object MessagePresetStore023 {
    private const val PREFS = "sr_rotas_023_message_presets"
    private const val KEY_JSON = "messages_json"
    private const val KEY_SYNCED_AT = "synced_at"
    private const val KEY_LOCAL_JSON = "messages_local_024"
    private const val KEY_LOCAL_UPDATED_AT = "local_updated_at_024"

    fun load(context: Context): List<MessageShortcut023> {
        val prefs = prefs(context)
        val local = prefs.getString(KEY_LOCAL_JSON, null)
        if (!local.isNullOrBlank()) {
            return decode(local)
        }
        return decode(prefs.getString(KEY_JSON, null))
    }

    /** Base recebida da conta, ignorando personalização local. */
    fun base(context: Context): List<MessageShortcut023> =
        decode(prefs(context).getString(KEY_JSON, null))

    fun visible(context: Context): List<MessageShortcut023> =
        MessageShortcutRules023.visible(load(context))

    /**
     * Mantém a assinatura histórica: este método é usado pelo cliente GET para
     * gravar a base remota. Não remove KEY_LOCAL_JSON.
     */
    fun save(
        context: Context,
        items: List<MessageShortcut023>,
    ) {
        prefs(context)
            .edit()
            .putString(KEY_JSON, encode(items))
            .putLong(KEY_SYNCED_AT, System.currentTimeMillis())
            .apply()
    }

    fun saveLocal(
        context: Context,
        items: List<MessageShortcut023>,
    ) {
        prefs(context)
            .edit()
            .putString(
                KEY_LOCAL_JSON,
                encode(MessagePresetEditorRules024.sanitizeEditorItems(items)),
            )
            .putLong(KEY_LOCAL_UPDATED_AT, System.currentTimeMillis())
            .apply()
    }

    fun clearLocal(context: Context) {
        prefs(context)
            .edit()
            .remove(KEY_LOCAL_JSON)
            .putLong(KEY_LOCAL_UPDATED_AT, System.currentTimeMillis())
            .apply()
    }

    fun hasLocalOverride(context: Context): Boolean =
        !prefs(context).getString(KEY_LOCAL_JSON, null).isNullOrBlank()

    /**
     * JourneyBubbleController já observa syncedAt(). Retornamos a maior versão
     * visual para que uma edição local também redesenhe o trilho.
     */
    fun syncedAt(context: Context): Long {
        val prefs = prefs(context)
        return max(
            prefs.getLong(KEY_SYNCED_AT, 0L),
            prefs.getLong(KEY_LOCAL_UPDATED_AT, 0L),
        )
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(
            PREFS,
            Context.MODE_PRIVATE,
        )

    private fun encode(items: List<MessageShortcut023>): String {
        val array = JSONArray()
        MessageShortcutRules023.normalized(items).forEach { item ->
            array.put(
                JSONObject().apply {
                    put("id", item.id)
                    put("order", item.order)
                    put("shortLabel", item.shortLabel)
                    put(
                        "accessibilityLabel",
                        item.accessibilityLabel ?: JSONObject.NULL,
                    )
                    put("text", item.text)
                    put("colorToken", item.colorToken)
                    put("enabled", item.enabled)
                },
            )
        }
        return array.toString()
    }

    private fun decode(raw: String?): List<MessageShortcut023> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            val list = buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    add(
                        MessageShortcut023(
                            id = item.optString("id"),
                            order = item.optInt("order", index),
                            shortLabel = item.optString(
                                "shortLabel",
                                (index + 1).toString(),
                            ),
                            accessibilityLabel =
                                if (item.isNull("accessibilityLabel")) {
                                    null
                                } else {
                                    item.optString("accessibilityLabel")
                                        .takeIf(String::isNotBlank)
                                },
                            text = item.optString("text"),
                            colorToken = item.optString(
                                "colorToken",
                                MessageShortcutRules023.colorFor(index),
                            ),
                            enabled = item.optBoolean("enabled", true),
                        ),
                    )
                }
            }
            MessageShortcutRules023.normalized(list)
        }.getOrDefault(emptyList())
    }
}
