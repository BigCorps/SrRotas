package com.srrotas.app

import android.content.Context
import android.os.Build
import org.json.JSONObject
import java.time.Instant
import java.util.UUID

object BetaTelemetry {
    private const val PREFS = "sr_rotas_beta"
    private const val PENDING_CRASH = "pending_crash_v1"
    private const val LAST_CRASH = "last_crash_summary_v1"
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
                val raw = payload.toString()
                app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .edit()
                    .putString(PENDING_CRASH, raw)
                    .putString(LAST_CRASH, raw)
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

    /**
     * 0.33.2 — diagnóstico local sanitizado.
     *
     * Não exporta OCR, screenshot, endereço, coordenada ou conteúdo de tela.
     * O payload já nasce limitado no uncaughtExceptionHandler e aqui é
     * reconstruído por whitelist para impedir que futuras chaves extras vazem.
     */
    fun crashDiagnostic(context: Context): JSONObject {
        val prefs = context.applicationContext
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val pendingRaw = prefs.getString(PENDING_CRASH, null)
        val lastRaw = prefs.getString(LAST_CRASH, null)
        return JSONObject().apply {
            put("schema", "sr-crash-observability-0332-v1")
            put("pending", !pendingRaw.isNullOrBlank())
            put("pending_crash", sanitizeCrash(pendingRaw))
            put("last_crash", sanitizeCrash(lastRaw))
            put(
                "privacy",
                "Somente classe, mensagem limitada, stack limitada, thread e versão; sem OCR, screenshot, endereço, coordenada ou conteúdo de tela.",
            )
        }
    }

    private fun sanitizeCrash(raw: String?): Any {
        if (raw.isNullOrBlank()) return JSONObject.NULL
        val source = runCatching { JSONObject(raw) }.getOrNull() ?: return JSONObject.NULL
        return JSONObject().apply {
            listOf(
                "event_id",
                "occurred_at",
                "exception_class",
                "message",
                "stack",
                "thread",
                "app_version",
                "version_code",
                "android_sdk",
                "manufacturer",
                "model",
            ).forEach { key ->
                if (source.has(key) && !source.isNull(key)) put(key, source.opt(key))
            }
            val occurredAt = source.optString("occurred_at")
            val ageMs = runCatching {
                val at = Instant.parse(occurredAt).toEpochMilli()
                (System.currentTimeMillis() - at).coerceAtLeast(0L)
            }.getOrNull()
            if (ageMs != null) put("age_ms", ageMs)
        }
    }
}
