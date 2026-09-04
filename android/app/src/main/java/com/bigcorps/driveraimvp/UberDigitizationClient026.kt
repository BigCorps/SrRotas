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

object UberDigitizationClient026 {
    private val executor = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    fun sync(
        context: Context,
        result: UberDigitizationResult026,
        onResult: ((Result<Unit>) -> Unit)? = null,
    ) {
        val app = context.applicationContext
        executor.execute {
            val settings = SettingsRepository(app).load()
            if (settings.deviceToken.isBlank() || settings.backendUrl.isBlank()) {
                main.post { onResult?.invoke(Result.success(Unit)) }
                return@execute
            }
            val body = when (result) {
                is UberDigitizationResult026.Session -> UberDigitizationJson026.session(result.value)
                is UberDigitizationResult026.Rides -> UberDigitizationJson026.rides(result.values)
            }
            val outcome = runCatching {
                request(
                    "${settings.backendUrl.trimEnd('/')}/api/v1/uber-digitization",
                    body,
                    settings.deviceToken,
                )
                val store = UberDigitizationStore026.get(app)
                when (result) {
                    is UberDigitizationResult026.Session -> store.markSessionSynced(result.value.sourceKey)
                    is UberDigitizationResult026.Rides -> result.values.forEach { store.markRideSynced(it.sourceKey) }
                }
                Unit
            }.onFailure {
                LocalLog.append(app, "Falha na digitalização Uber: ${it.message}")
            }
            main.post { onResult?.invoke(outcome) }
        }
    }

    private fun request(url: String, body: JSONObject, token: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 8_000
            readTimeout = 12_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("X-SrRotas-App-Version", BuildConfig.VERSION_NAME)
        }
        connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
        val status = connection.responseCode
        val stream = if (status in 200..299) connection.inputStream else connection.errorStream
        val text = stream?.use { BufferedReader(InputStreamReader(it)).readText() }.orEmpty()
        connection.disconnect()
        if (status !in 200..299) {
            val message = runCatching { JSONObject(text).optString("error") }.getOrDefault("")
            error(if (message.isBlank()) "HTTP $status" else message)
        }
        return text
    }
}
