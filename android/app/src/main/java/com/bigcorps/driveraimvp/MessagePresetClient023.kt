package com.srrotas.app

import android.content.Context
import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/** Sincroniza os atalhos da conta com um cache local. */
object MessagePresetClient023 {
    private val executor = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    fun refresh(
        context: Context,
        callback: ((Result<List<MessageShortcut023>>) -> Unit)? = null,
    ) {
        val app = context.applicationContext
        val settings = SettingsRepository(app).load()
        if (settings.deviceToken.isBlank()) {
            callback?.invoke(Result.success(MessagePresetStore023.load(app)))
            return
        }

        executor.execute {
            val result = runCatching {
                val connection = (
                    URL("${settings.backendUrl.trimEnd('/')}/api/v1/messages/presets")
                        .openConnection() as HttpURLConnection
                    ).apply {
                    requestMethod = "GET"
                    connectTimeout = 8000
                    readTimeout = 12000
                    setRequestProperty("Accept", "application/json")
                    setRequestProperty("Authorization", "Bearer ${settings.deviceToken}")
                    setRequestProperty("X-SrRotas-App-Version", BuildConfig.VERSION_NAME)
                }
                val status = connection.responseCode
                val stream = if (status in 200..299) connection.inputStream else connection.errorStream
                val text = stream?.use { BufferedReader(InputStreamReader(it)).readText() }.orEmpty()
                connection.disconnect()
                if (status !in 200..299) error("HTTP $status")

                val json = JSONObject(text)
                parse(json.optJSONArray("messages"))
            }.onSuccess { items ->
                MessagePresetStore023.save(app, items)
            }
            main.post { callback?.invoke(result) }
        }
    }

    private fun parse(array: JSONArray?): List<MessageShortcut023> {
        if (array == null) return emptyList()
        val items = buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                add(
                    MessageShortcut023(
                        id = item.optString("id", "slot-${index + 1}"),
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
        return MessageShortcutRules023.normalized(items)
    }
}
