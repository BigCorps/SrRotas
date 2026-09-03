package com.srrotas.app

import android.content.Context
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

object UberDigitizationClient026 {
    private val executor = Executors.newSingleThreadExecutor()
    fun sync(context: Context, result: UberDigitizationResult026) {
        val app=context.applicationContext
        executor.execute {
            val s=SettingsRepository(app).load(); if(s.deviceToken.isBlank()||s.backendUrl.isBlank()) return@execute
            val body=when(result){ is UberDigitizationResult026.Session->UberDigitizationJson026.session(result.value); is UberDigitizationResult026.Rides->UberDigitizationJson026.rides(result.values) }
            runCatching { request("${s.backendUrl.trimEnd('/')}/api/v1/uber-digitization", body, s.deviceToken) }
                .onSuccess {
                    val store=UberDigitizationStore026.get(app)
                    when(result){ is UberDigitizationResult026.Session->store.markSessionSynced(result.value.sourceKey); is UberDigitizationResult026.Rides->result.values.forEach{store.markRideSynced(it.sourceKey)} }
                }
                .onFailure { LocalLog.append(app,"Falha na digitalização Uber: ${it.message}") }
        }
    }
    private fun request(url:String, body:JSONObject, token:String):String {
        val c=(URL(url).openConnection() as HttpURLConnection).apply{requestMethod="POST";connectTimeout=8000;readTimeout=12000;doOutput=true;setRequestProperty("Content-Type","application/json; charset=utf-8");setRequestProperty("Authorization","Bearer $token")}
        c.outputStream.use{it.write(body.toString().toByteArray(Charsets.UTF_8))}; val status=c.responseCode; val stream=if(status in 200..299)c.inputStream else c.errorStream; val text=stream?.use{BufferedReader(InputStreamReader(it)).readText()}.orEmpty(); c.disconnect(); if(status !in 200..299) error("HTTP $status"); return text
    }
}
