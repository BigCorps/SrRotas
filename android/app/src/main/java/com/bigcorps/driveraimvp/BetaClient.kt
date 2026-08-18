package com.srrotas.app

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

object BetaClient {
    private val executor = Executors.newSingleThreadExecutor()

    data class FeedbackPayload(
        val category: String,
        val severity: String,
        val message: String,
        val checklistCompleted: Int,
        val checklistTotal: Int,
    )

    fun sendFeedback(context: Context, payload: FeedbackPayload, onResult: (Result<Unit>) -> Unit) {
        val app = context.applicationContext
        executor.execute {
            val result = runCatching {
                val settings = SettingsRepository(app).load()
                require(settings.deviceToken.isNotBlank()) { "Conecte sua conta antes de enviar feedback." }
                require(payload.message.trim().length in 4..1600) { "Descreva o feedback com pelo menos 4 caracteres." }

                request(
                    "${settings.backendUrl.trimEnd('/')}/api/v1/beta/feedback",
                    settings.deviceToken,
                    JSONObject().apply {
                        put("category", payload.category)
                        put("severity", payload.severity)
                        put("message", payload.message.trim())
                        put("checklist_completed", payload.checklistCompleted)
                        put("checklist_total", payload.checklistTotal)
                        put("app_version", BuildConfig.VERSION_NAME)
                        put("version_code", BuildConfig.VERSION_CODE)
                        put("android_sdk", Build.VERSION.SDK_INT)
                        put("manufacturer", Build.MANUFACTURER.take(80))
                        put("model", Build.MODEL.take(100))
                    }
                )
                Unit
            }
            Handler(Looper.getMainLooper()).post { onResult(result) }
        }
    }

    fun sendCrash(context: Context, rawCrash: String, onResult: (Result<Unit>) -> Unit) {
        val app = context.applicationContext
        executor.execute {
            val result = runCatching {
                val settings = SettingsRepository(app).load()
                require(settings.deviceToken.isNotBlank()) { "Sem sessão." }
                request(
                    "${settings.backendUrl.trimEnd('/')}/api/v1/beta/crash",
                    settings.deviceToken,
                    JSONObject(rawCrash)
                )
                Unit
            }
            Handler(Looper.getMainLooper()).post { onResult(result) }
        }
    }

    private fun request(url: String, token: String, body: JSONObject): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 12_000
            readTimeout = 20_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Authorization", "Bearer $token")
        }
        connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
        val status = connection.responseCode
        val text = runCatching {
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            stream?.bufferedReader()?.use { it.readText() } ?: ""
        }.getOrDefault("")
        connection.disconnect()

        if (status !in 200..299) {
            val message = runCatching {
                val json = JSONObject(text)
                json.optString("message").ifBlank { json.optString("error") }
            }.getOrDefault("").ifBlank { "HTTP $status" }
            throw IllegalStateException(message)
        }
        return text
    }
}
