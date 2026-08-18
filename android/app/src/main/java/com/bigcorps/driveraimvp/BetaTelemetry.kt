package com.srrotas.app

import android.content.Context
import android.os.Build
import org.json.JSONObject
import java.time.Instant
import java.util.UUID

object BetaTelemetry {
    private const val PREFS = "sr_rotas_beta"
    private const val PENDING_CRASH = "pending_crash_v1"
    private var installed = false

    fun install(context: Context) {
        if (installed) return
        installed = true
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                val stack = throwable.stackTrace
                    .take(10)
                    .joinToString("\n") { "${it.className}.${it.methodName}:${it.lineNumber}" }
                    .take(3500)

                val payload = JSONObject().apply {
                    put("event_id", UUID.randomUUID().toString())
                    put("occurred_at", Instant.now().toString())
                    put("exception_class", throwable.javaClass.name.take(180))
                    put("message", (throwable.message ?: "").take(320))
                    put("stack", stack)
                    put("thread", thread.name.take(80))
                    put("app_version", BuildConfig.VERSION_NAME)
                    put("version_code", BuildConfig.VERSION_CODE)
                    put("android_sdk", Build.VERSION.SDK_INT)
                    put("manufacturer", Build.MANUFACTURER.take(80))
                    put("model", Build.MODEL.take(100))
                }
                app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .edit()
                    .putString(PENDING_CRASH, payload.toString())
                    .commit()
            }
            previous?.uncaughtException(thread, throwable)
        }
    }

    fun flushPendingCrash(context: Context) {
        val app = context.applicationContext
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val raw = prefs.getString(PENDING_CRASH, null)?.takeIf(String::isNotBlank) ?: return
        if (SettingsRepository(app).load().deviceToken.isBlank()) return

        BetaClient.sendCrash(app, raw) { result ->
            result.onSuccess { prefs.edit().remove(PENDING_CRASH).apply() }
        }
    }

    fun hasPendingCrash(context: Context): Boolean =
        !context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(PENDING_CRASH, null)
            .isNullOrBlank()
}
