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
import java.util.concurrent.atomic.AtomicBoolean

object CostProfileSync {
    private val executor =
        Executors.newSingleThreadExecutor()
    private val running =
        AtomicBoolean(false)

    data class SyncResult(
        val configured: Boolean,
        val profile: CostProfile?,
        val calculation: CostCalculation?,
        val source: String,
    )

    fun refreshOrFlush(
        context: Context,
        onResult:
            ((Result<SyncResult>) -> Unit)? = null,
    ) {
        val app =
            context.applicationContext

        if (
            SettingsRepository(app)
                .load()
                .deviceToken
                .isBlank()
        ) {
            return
        }

        if (!running.compareAndSet(false, true)) {
            return
        }

        executor.execute {
            val result =
                runCatching {
                    val repo =
                        SettingsRepository(app)
                    val settings =
                        repo.load()

                    require(
                        settings.deviceToken
                            .isNotBlank(),
                    ) {
                        "Aparelho sem sessão."
                    }

                    val store =
                        CostProfileStore.get(app)
                    val pending =
                        store.pending()

                    if (pending != null) {
                        pushNow(
                            app,
                            pending,
                        )
                    } else {
                        fetchNow(app)
                    }
                }

            running.set(false)
            onResult?.let { callback ->
                Handler(
                    Looper.getMainLooper(),
                ).post {
                    callback(result)
                }
            }
        }
    }

    fun push(
        context: Context,
        profile: CostProfile,
        onResult:
            ((Result<SyncResult>) -> Unit)? = null,
    ) {
        val app =
            context.applicationContext

        executor.execute {
            val result =
                runCatching {
                    pushNow(
                        app,
                        profile,
                    )
                }

            onResult?.let { callback ->
                Handler(
                    Looper.getMainLooper(),
                ).post {
                    callback(result)
                }
            }
        }
    }

    private fun pushNow(
        context: Context,
        profile: CostProfile,
    ): SyncResult {
        val repo =
            SettingsRepository(context)
        val settings =
            repo.load()

        require(
            settings.deviceToken.isNotBlank(),
        ) {
            "Aparelho sem sessão."
        }

        val response =
            request(
                method = "PUT",
                url =
                    "${settings.backendUrl.trimEnd('/')}/api/v1/costs",
                body = profile.toJson(),
                bearer = settings.deviceToken,
            )

        val parsed =
            parseResponse(response)

        parsed.profile?.let {
            CostProfileStore.get(context)
                .save(
                    it,
                    syncState = 1,
                )
        }

        parsed.calculation?.let { calc ->
            repo.saveCostSnapshot(
                costPerKm =
                    calc.effectiveCostPerKm,
                source =
                    calc.costSource,
                version =
                    calc.version,
                profileUpdatedAt =
                    parsed.profile
                        ?.updatedAt
                        .orEmpty(),
            )
        }

        return parsed.copy(
            source = "push",
        )
    }

    private fun fetchNow(
        context: Context,
    ): SyncResult {
        val repo =
            SettingsRepository(context)
        val settings =
            repo.load()

        require(
            settings.deviceToken.isNotBlank(),
        ) {
            "Aparelho sem sessão."
        }

        val response =
            request(
                method = "GET",
                url =
                    "${settings.backendUrl.trimEnd('/')}/api/v1/costs",
                body = null,
                bearer = settings.deviceToken,
            )

        val parsed =
            parseResponse(response)

        if (
            parsed.configured &&
            parsed.profile != null
        ) {
            CostProfileStore.get(context)
                .save(
                    parsed.profile,
                    syncState = 1,
                )

            parsed.calculation?.let {
                repo.saveCostSnapshot(
                    costPerKm =
                        it.effectiveCostPerKm,
                    source =
                        it.costSource,
                    version =
                        it.version,
                    profileUpdatedAt =
                        parsed.profile
                            .updatedAt,
                )
            }
        }

        return parsed.copy(
            source = "fetch",
        )
    }

    private fun parseResponse(
        text: String,
    ): SyncResult {
        val root =
            JSONObject(text)

        val configured =
            root.optBoolean(
                "configured",
                false,
            )

        if (!configured) {
            return SyncResult(
                configured = false,
                profile = null,
                calculation = null,
                source = "server",
            )
        }

        val profileJson =
            root.optJSONObject("profile")
                ?: error(
                    "Servidor não retornou o perfil de custos.",
                )

        val profile =
            CostProfile.fromJson(
                profileJson,
            )

        // O mesmo motor determinístico roda no Android.
        // O servidor devolve os componentes para conferência,
        // mas o app recalcula a memória localmente.
        val calculation =
            CostCalculator.calculate(
                profile,
            )

        return SyncResult(
            configured = true,
            profile = profile,
            calculation = calculation,
            source = "server",
        )
    }

    private fun request(
        method: String,
        url: String,
        body: JSONObject?,
        bearer: String,
    ): String {
        val connection =
            (
                URL(url)
                    .openConnection()
                    as HttpURLConnection
                ).apply {
                requestMethod = method
                connectTimeout = 8_000
                readTimeout = 12_000
                doOutput = body != null
                setRequestProperty(
                    "Content-Type",
                    "application/json; charset=utf-8",
                )
                setRequestProperty(
                    "Accept",
                    "application/json",
                )
                setRequestProperty(
                    "Authorization",
                    "Bearer $bearer",
                )
                setRequestProperty(
                    "X-SrRotas-App-Version",
                    BuildConfig.VERSION_NAME,
                )
            }

        if (body != null) {
            connection.outputStream.use {
                it.write(
                    body.toString()
                        .toByteArray(
                            Charsets.UTF_8,
                        ),
                )
            }
        }

        val status =
            connection.responseCode
        val stream =
            if (status in 200..299) {
                connection.inputStream
            } else {
                connection.errorStream
            }

        val text =
            stream?.use {
                BufferedReader(
                    InputStreamReader(it),
                ).readText()
            } ?: ""

        connection.disconnect()

        if (status !in 200..299) {
            val message =
                runCatching {
                    JSONObject(text)
                        .optString("error")
                        .ifBlank {
                            JSONObject(text)
                                .optString(
                                    "message",
                                )
                        }
                }.getOrDefault("")

            error(
                if (message.isBlank()) {
                    "HTTP $status"
                } else {
                    message
                },
            )
        }

        return text
    }
}
