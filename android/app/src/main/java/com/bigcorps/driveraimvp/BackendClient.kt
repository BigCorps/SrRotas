package com.bigcorps.driveraimvp

import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

object BackendClient {
    private val executor = Executors.newSingleThreadExecutor()

    fun pair(
        context: Context,
        backendUrl: String,
        pairingCode: String,
        onResult: (Result<String>) -> Unit,
    ) {
        executor.execute {
            val result = runCatching {
                require(backendUrl.isNotBlank()) { "Informe a URL do backend." }
                require(pairingCode.isNotBlank()) { "Informe o código de pareamento." }
                val body = JSONObject().apply {
                    put("code", pairingCode.trim())
                    put("device_name", "${Build.MANUFACTURER} ${Build.MODEL}")
                }
                val response = request(
                    method = "POST",
                    url = "${backendUrl.trim().trimEnd('/')}/api/v1/pair",
                    body = body,
                )
                val json = JSONObject(response)
                val token = json.optString("device_token")
                require(token.isNotBlank()) { "Backend não retornou device_token." }
                SettingsRepository(context).saveDeviceToken(token)
                token
            }
            Handler(Looper.getMainLooper()).post { onResult(result) }
        }
    }

    fun ask(
        context: Context,
        question: String,
        onResult: (Result<String>) -> Unit,
    ) {
        val settings = SettingsRepository(context).load()
        executor.execute {
            val result = runCatching {
                require(settings.backendUrl.isNotBlank()) { "Configure a URL do backend." }
                require(settings.deviceToken.isNotBlank()) { "Pareie o aparelho primeiro." }
                require(question.trim().length >= 3) { "Digite uma pergunta." }
                val body = JSONObject().apply { put("question", question.trim()) }
                val response = request(
                    method = "POST",
                    url = "${settings.backendUrl.trimEnd('/')}/api/v1/ask",
                    body = body,
                    bearer = settings.deviceToken,
                )
                JSONObject(response).optString("answer").ifBlank { "O backend não retornou uma resposta." }
            }
            Handler(Looper.getMainLooper()).post { onResult(result) }
        }
    }

    fun sendOffer(context: Context, offer: RideOffer) {
        val settings = SettingsRepository(context).load()
        if (settings.backendUrl.isBlank() || settings.deviceToken.isBlank()) return

        executor.execute {
            runCatching {
                request(
                    method = "POST",
                    url = "${settings.backendUrl.trimEnd('/')}/api/v1/offers",
                    body = offer.toJson(),
                    bearer = settings.deviceToken,
                )
            }.onFailure {
                LocalLog.append(context, "Falha ao enviar oferta: ${it.message}")
            }
        }
    }

    private fun request(
        method: String,
        url: String,
        body: JSONObject,
        bearer: String? = null,
    ): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 8000
            readTimeout = 12000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "application/json")
            if (!bearer.isNullOrBlank()) setRequestProperty("Authorization", "Bearer $bearer")
        }

        connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
        val status = connection.responseCode
        val stream = if (status in 200..299) connection.inputStream else connection.errorStream
        val text = stream?.use { input ->
            BufferedReader(InputStreamReader(input)).readText()
        } ?: ""
        connection.disconnect()
        if (status !in 200..299) error("HTTP $status: $text")
        return text
    }
}
