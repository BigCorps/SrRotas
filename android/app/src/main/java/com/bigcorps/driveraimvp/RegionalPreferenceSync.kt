package com.srrotas.app

import android.content.Context
import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

object RegionalPreferenceSync {
    private val executor = Executors.newSingleThreadExecutor()

    fun fetch(context: Context, onResult: (Result<Boolean>) -> Unit) {
        val app = context.applicationContext
        executor.execute {
            val result = runCatching {
                val settings = SettingsRepository(app).load()
                require(settings.deviceToken.isNotBlank()) { "Aparelho sem sessão." }

                val text = request(
                    "GET",
                    "${settings.backendUrl.trimEnd('/')}/api/v1/preferences",
                    null,
                    settings.deviceToken,
                )
                val enabled = JSONObject(text)
                    .optJSONObject("preferences")
                    ?.optBoolean("collective_stats_opt_in", false)
                    ?: false

                val repo = SettingsRepository(app)
                repo.save(repo.load().copy(collectiveStatsOptIn = enabled))
                enabled
            }
            Handler(Looper.getMainLooper()).post { onResult(result) }
        }
    }

    fun set(context: Context, enabled: Boolean, onResult: (Result<Boolean>) -> Unit) {
        val app = context.applicationContext
        executor.execute {
            val result = runCatching {
                val repo = SettingsRepository(app)
                val settings = repo.load()
                require(settings.deviceToken.isNotBlank()) { "Aparelho sem sessão." }

                val text = request(
                    "POST",
                    "${settings.backendUrl.trimEnd('/')}/api/v1/preferences",
                    JSONObject().apply { put("collective_stats_opt_in", enabled) },
                    settings.deviceToken,
                )

                val saved = JSONObject(text)
                    .optJSONObject("preferences")
                    ?.optBoolean("collective_stats_opt_in", enabled)
                    ?: enabled

                repo.save(repo.load().copy(collectiveStatsOptIn = saved))
                saved
            }
            Handler(Looper.getMainLooper()).post { onResult(result) }
        }
    }

    private fun request(method: String, url: String, body: JSONObject?, bearer: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 8_000
            readTimeout = 12_000
            doOutput = body != null
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Authorization", "Bearer $bearer")
            setRequestProperty("X-SrRotas-App-Version", BuildConfig.VERSION_NAME)
        }

        if (body != null) {
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
        }

        val status = connection.responseCode
        val stream = if (status in 200..299) connection.inputStream else connection.errorStream
        val text = stream?.use { BufferedReader(InputStreamReader(it)).readText() } ?: ""
        connection.disconnect()

        if (status !in 200..299) {
            val message = runCatching {
                JSONObject(text).optString("error").ifBlank { JSONObject(text).optString("message") }
            }.getOrDefault("")
            error(if (message.isBlank()) "HTTP $status" else message)
        }
        return text
    }
}
