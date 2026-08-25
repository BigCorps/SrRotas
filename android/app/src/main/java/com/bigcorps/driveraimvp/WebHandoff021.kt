package com.srrotas.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/** Handoff Android -> Web sem colocar device_token na URL. */
object WebHandoff021 {
    private val executor = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    fun open(context: Context, targetPath: String = "/app/agora") {
        val app = context.applicationContext
        val settings = SettingsRepository(app).load()
        if (settings.deviceToken.isBlank()) {
            openBrowser(app, "${settings.backendUrl.trimEnd('/')}/app/entrar")
            return
        }

        executor.execute {
            val result = runCatching {
                val url = "${settings.backendUrl.trimEnd('/')}/api/v1/web/handoff"
                val body = JSONObject().apply { put("target_path", normalizeTarget(targetPath)) }
                val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 8000
                    readTimeout = 12000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    setRequestProperty("Accept", "application/json")
                    setRequestProperty("Authorization", "Bearer ${settings.deviceToken}")
                    setRequestProperty("X-SrRotas-App-Version", BuildConfig.VERSION_NAME)
                }
                connection.outputStream.use {
                    it.write(body.toString().toByteArray(Charsets.UTF_8))
                }
                val status = connection.responseCode
                val stream = if (status in 200..299) connection.inputStream else connection.errorStream
                val text = stream?.use { BufferedReader(InputStreamReader(it)).readText() }.orEmpty()
                connection.disconnect()
                if (status !in 200..299) error("HTTP $status")
                JSONObject(text).optString("handoff_url").takeIf { it.startsWith("https://") }
                    ?: error("handoff_url ausente")
            }

            main.post {
                result.onSuccess { openBrowser(app, it) }
                    .onFailure {
                        LocalLog.append(app, "Handoff Web 0.21 falhou: ${it.message}")
                        openBrowser(app, "${settings.backendUrl.trimEnd('/')}/app/entrar")
                    }
            }
        }
    }

    private fun normalizeTarget(value: String): String =
        value.takeIf { it.startsWith("/app") && !it.startsWith("//") }?.take(300) ?: "/app/agora"

    private fun openBrowser(context: Context, url: String) {
        context.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            },
        )
    }
}
