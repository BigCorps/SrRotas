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
import java.util.concurrent.atomic.AtomicBoolean

/** Sincroniza os atalhos da conta com um cache local. */
object MessagePresetClient023 {
    private const val DEFAULT_REFRESH_MS = 20_000L

    private val executor = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val refreshing = AtomicBoolean(false)

    @Volatile
    private var lastRefreshAttemptAt = 0L

    fun refreshIfDue(
        context: Context,
        force: Boolean = false,
        minIntervalMs: Long = DEFAULT_REFRESH_MS,
        callback: ((Result<List<MessageShortcut023>>) -> Unit)? = null,
    ) {
        val now = System.currentTimeMillis()
        if (
            !force &&
            now - lastRefreshAttemptAt <
            minIntervalMs.coerceAtLeast(5_000L)
        ) {
            callback?.invoke(
                Result.success(
                    MessagePresetStore023.load(context),
                ),
            )
            return
        }
        refresh(context, callback)
    }

    fun refresh(
        context: Context,
        callback: ((Result<List<MessageShortcut023>>) -> Unit)? = null,
    ) {
        val app = context.applicationContext
        val settings = SettingsRepository(app).load()
        lastRefreshAttemptAt = System.currentTimeMillis()

        if (settings.deviceToken.isBlank()) {
            callback?.invoke(
                Result.success(
                    MessagePresetStore023.load(app),
                ),
            )
            return
        }

        if (!refreshing.compareAndSet(false, true)) {
            callback?.invoke(
                Result.success(
                    MessagePresetStore023.load(app),
                ),
            )
            return
        }

        executor.execute {
            val result = runCatching {
                val connection = (
                    URL(
                        "${settings.backendUrl.trimEnd('/')}" +
                            "/api/v1/messages/presets",
                    ).openConnection() as HttpURLConnection
                    ).apply {
                    requestMethod = "GET"
                    connectTimeout = 8000
                    readTimeout = 12000
                    setRequestProperty("Accept", "application/json")
                    setRequestProperty(
                        "Authorization",
                        "Bearer ${settings.deviceToken}",
                    )
                    setRequestProperty(
                        "X-SrRotas-App-Version",
                        BuildConfig.VERSION_NAME,
                    )
                }
                val status = connection.responseCode
                val stream =
                    if (status in 200..299) {
                        connection.inputStream
                    } else {
                        connection.errorStream
                    }
                val text = stream?.use {
                    BufferedReader(InputStreamReader(it)).readText()
                }.orEmpty()
                connection.disconnect()
                if (status !in 200..299) error("HTTP $status")

                val json = JSONObject(text)
                parse(json.optJSONArray("messages"))
            }.onSuccess { items ->
                MessagePresetStore023.save(app, items)
            }

            refreshing.set(false)
            main.post { callback?.invoke(result) }
        }
    }

    fun push(
        context: Context,
        items: List<MessageShortcut023>,
        callback: ((Result<List<MessageShortcut023>>) -> Unit)? = null,
    ) {
        val app = context.applicationContext
        val settings = SettingsRepository(app).load()
        if (settings.deviceToken.isBlank()) {
            callback?.invoke(
                Result.failure(
                    IllegalStateException("Aparelho sem sessão."),
                ),
            )
            return
        }

        val payload = MessageShortcutRules023.normalized(items)
            .filter { it.text.isNotBlank() }

        executor.execute {
            val result = runCatching {
                val body = JSONObject().apply {
                    put(
                        "messages",
                        JSONArray().apply {
                            payload.forEach { item ->
                                put(
                                    JSONObject().apply {
                                        put("order", item.order)
                                        put("shortLabel", item.shortLabel)
                                        put(
                                            "accessibilityLabel",
                                            item.accessibilityLabel
                                                ?: JSONObject.NULL,
                                        )
                                        put("text", item.text)
                                        put("colorToken", item.colorToken)
                                        put("enabled", item.enabled)
                                    },
                                )
                            }
                        },
                    )
                }

                val connection = (
                    URL(
                        "${settings.backendUrl.trimEnd('/')}" +
                            "/api/v1/messages/presets",
                    ).openConnection() as HttpURLConnection
                    ).apply {
                    requestMethod = "PUT"
                    connectTimeout = 8000
                    readTimeout = 12000
                    doOutput = true
                    setRequestProperty(
                        "Content-Type",
                        "application/json; charset=utf-8",
                    )
                    setRequestProperty("Accept", "application/json")
                    setRequestProperty(
                        "Authorization",
                        "Bearer ${settings.deviceToken}",
                    )
                    setRequestProperty(
                        "X-SrRotas-App-Version",
                        BuildConfig.VERSION_NAME,
                    )
                }
                connection.outputStream.use {
                    it.write(body.toString().toByteArray(Charsets.UTF_8))
                }
                val status = connection.responseCode
                val stream =
                    if (status in 200..299) {
                        connection.inputStream
                    } else {
                        connection.errorStream
                    }
                val text = stream?.use {
                    BufferedReader(InputStreamReader(it)).readText()
                }.orEmpty()
                connection.disconnect()
                if (status !in 200..299) error("HTTP $status")

                val json = JSONObject(text)
                parse(json.optJSONArray("messages"))
            }.onSuccess { saved ->
                MessagePresetStore023.save(app, saved)
                MessagePresetStore023.clearLocal(app)
                lastRefreshAttemptAt = System.currentTimeMillis()
            }
            main.post { callback?.invoke(result) }
        }
    }

    private fun parse(
        array: JSONArray?,
    ): List<MessageShortcut023> {
        if (array == null) return emptyList()
        val items = buildList {
            for (index in 0 until array.length()) {
                val item =
                    array.optJSONObject(index) ?: continue
                add(
                    MessageShortcut023(
                        id = item.optString(
                            "id",
                            "slot-${index + 1}",
                        ),
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
                        enabled = item.optBoolean(
                            "enabled",
                            true,
                        ),
                    ),
                )
            }
        }
        return MessageShortcutRules023.normalized(items)
    }
}
