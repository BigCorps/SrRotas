package com.srrotas.app

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
import java.util.concurrent.atomic.AtomicBoolean

object BackendClient {
    data class AccountSession(
        val token: String,
        val email: String,
        val displayName: String,
        val driverId: String,
    )

    data class AccountProfile(
        val email: String,
        val displayName: String,
        val onboardingCompleted: Boolean,
        val legacy: Boolean,
    )


    data class AiAnswer(
        val answer: String,
        val model: String,
        val offerCount: Int,
        val inputTokens: Int?,
        val outputTokens: Int?,
        val totalTokens: Int?,
    )

    data class McpTokenInfo(
        val id: String,
        val name: String,
        val prefix: String,
        val createdAt: String,
        val lastUsedAt: String?,
    )

    data class McpTokenCreated(
        val id: String,
        val name: String,
        val prefix: String,
        val token: String,
        val endpoint: String,
    )


    data class BillingStatus(
        val subscriptionActive: Boolean,
        val subscriptionStatus: String?,
        val currentPeriodEnd: String?,
        val creditBalance: Int,
        val lifetimeGranted: Int,
        val lifetimeSpent: Int,
        val billingEnforcement: Boolean,
        val creditPacksAvailable: Boolean,
    )

    private val executor = Executors.newSingleThreadExecutor()
    private val flushRunning = AtomicBoolean(false)

    fun registerAccount(context: Context, email: String, password: String, displayName: String, onResult: (Result<AccountSession>) -> Unit) {
        val app = context.applicationContext
        val settings = SettingsRepository(app).load()
        executor.execute {
            val result = runCatching {
                require(email.trim().contains("@")) { "Informe um e-mail válido." }
                require(password.length >= 8) { "A senha precisa ter pelo menos 8 caracteres." }
                require(displayName.trim().length >= 2) { "Informe seu nome." }
                val response = request("POST", "${settings.backendUrl.trimEnd('/')}/api/v1/account/register", JSONObject().apply {
                    put("email", email.trim().lowercase())
                    put("password", password)
                    put("display_name", displayName.trim())
                    put("device_name", "${Build.MANUFACTURER} ${Build.MODEL}")
                })
                parseAccountSession(response).also {
                    SettingsRepository(app).saveAccountSession(it.token, it.email, it.displayName)
                }
            }
            Handler(Looper.getMainLooper()).post { onResult(result) }
        }
    }

    fun loginAccount(context: Context, email: String, password: String, onResult: (Result<AccountSession>) -> Unit) {
        val app = context.applicationContext
        val settings = SettingsRepository(app).load()
        executor.execute {
            val result = runCatching {
                require(email.trim().contains("@")) { "Informe um e-mail válido." }
                require(password.isNotBlank()) { "Informe sua senha." }
                val response = request("POST", "${settings.backendUrl.trimEnd('/')}/api/v1/account/login", JSONObject().apply {
                    put("email", email.trim().lowercase())
                    put("password", password)
                    put("device_name", "${Build.MANUFACTURER} ${Build.MODEL}")
                })
                parseAccountSession(response).also {
                    SettingsRepository(app).saveAccountSession(it.token, it.email, it.displayName)
                }
            }
            Handler(Looper.getMainLooper()).post { onResult(result) }
        }
    }

    fun fetchAccount(context: Context, onResult: (Result<AccountProfile>) -> Unit) {
        val app = context.applicationContext
        val settings = SettingsRepository(app).load()
        executor.execute {
            val result = runCatching {
                require(settings.deviceToken.isNotBlank()) { "Este aparelho não está conectado." }
                val response = request("GET", "${settings.backendUrl.trimEnd('/')}/api/v1/account/me", null, settings.deviceToken)
                val json = JSONObject(response)
                val profile = AccountProfile(
                    email = json.optString("email"),
                    displayName = json.optString("display_name").ifBlank { "Motorista" },
                    onboardingCompleted = json.optBoolean("onboarding_completed", false),
                    legacy = json.optBoolean("legacy", false),
                )
                SettingsRepository(app).updateAccountIdentity(profile.email, profile.displayName)
                profile
            }
            Handler(Looper.getMainLooper()).post { onResult(result) }
        }
    }

    fun updateAccountProfile(context: Context, displayName: String, onboardingCompleted: Boolean, onResult: ((Result<Unit>) -> Unit)? = null) {
        val app = context.applicationContext
        val settings = SettingsRepository(app).load()
        if (settings.deviceToken.isBlank()) {
            onResult?.invoke(Result.success(Unit))
            return
        }
        executor.execute {
            val result = runCatching {
                request("PATCH", "${settings.backendUrl.trimEnd('/')}/api/v1/account/me", JSONObject().apply {
                    put("display_name", displayName.trim())
                    put("onboarding_completed", onboardingCompleted)
                }, settings.deviceToken)
                SettingsRepository(app).updateAccountIdentity(null, displayName)
                Unit
            }
            Handler(Looper.getMainLooper()).post { onResult?.invoke(result) }
        }
    }

    fun logoutAccount(context: Context, onResult: (Result<Unit>) -> Unit) {
        val app = context.applicationContext
        val settings = SettingsRepository(app).load()
        executor.execute {
            val result = runCatching {
                require(settings.deviceToken.isNotBlank()) { "Nenhuma sessão ativa." }
                request("POST", "${settings.backendUrl.trimEnd('/')}/api/v1/account/logout", JSONObject(), settings.deviceToken)
                SettingsRepository(app).clearAccountSession()
                Unit
            }
            Handler(Looper.getMainLooper()).post { onResult(result) }
        }
    }




    fun fetchBillingStatus(context: Context, onResult: (Result<BillingStatus>) -> Unit) {
        val app=context.applicationContext;val settings=SettingsRepository(app).load()
        executor.execute {
            val result=runCatching {
                require(settings.deviceToken.isNotBlank()){ "Aparelho sem sessão." }
                val response=request("GET","${settings.backendUrl.trimEnd('/')}/api/v1/billing/status",null,settings.deviceToken)
                val json=JSONObject(response);val sub=json.optJSONObject("subscription");val wallet=json.optJSONObject("wallet")?:JSONObject()
                BillingStatus(sub?.optBoolean("active",false)?:false,sub?.optString("status")?.takeIf{it.isNotBlank()},sub?.optString("current_period_end")?.takeIf{it.isNotBlank()},wallet.optInt("balance",0),wallet.optInt("lifetime_granted",0),wallet.optInt("lifetime_spent",0),json.optBoolean("billing_enforcement",false),json.optBoolean("credit_packs_available",false))
            }
            Handler(Looper.getMainLooper()).post{onResult(result)}
        }
    }

    fun askEnhanced(context: Context, question: String, days: Int, onResult: (Result<AiAnswer>) -> Unit) {
        val app = context.applicationContext
        val settings = SettingsRepository(app).load()
        executor.execute {
            val result = runCatching {
                require(settings.deviceToken.isNotBlank()) { "Aparelho sem sessão." }
                require(question.trim().length in 3..800) { "Pergunta inválida." }
                val response = request(
                    "POST",
                    "${settings.backendUrl.trimEnd('/')}/api/v1/ask",
                    JSONObject().apply {
                        put("question", question.trim())
                        put("days", days.coerceIn(1, 90))
                    },
                    settings.deviceToken,
                )
                val json = JSONObject(response)
                val usage = json.optJSONObject("usage")
                AiAnswer(
                    answer = json.optString("answer").ifBlank { "A IA não retornou texto." },
                    model = json.optString("model", "IA Sr. Rotas"),
                    offerCount = json.optInt("offer_count", 0),
                    inputTokens = usage?.optInt("input_tokens")?.takeIf { it > 0 },
                    outputTokens = usage?.optInt("output_tokens")?.takeIf { it > 0 },
                    totalTokens = usage?.optInt("total_tokens")?.takeIf { it > 0 },
                )
            }
            Handler(Looper.getMainLooper()).post { onResult(result) }
        }
    }

    fun listMcpTokens(context: Context, onResult: (Result<List<McpTokenInfo>>) -> Unit) {
        val app = context.applicationContext
        val settings = SettingsRepository(app).load()
        executor.execute {
            val result = runCatching {
                require(settings.deviceToken.isNotBlank()) { "Aparelho sem sessão." }
                val response = request("GET", "${settings.backendUrl.trimEnd('/')}/api/v1/mcp/tokens", null, settings.deviceToken)
                val json = JSONObject(response)
                val array = json.optJSONArray("tokens") ?: org.json.JSONArray()
                (0 until array.length()).mapNotNull { array.optJSONObject(it) }.map { item ->
                    McpTokenInfo(
                        id = item.optString("id"),
                        name = item.optString("name", "Integração"),
                        prefix = item.optString("token_prefix"),
                        createdAt = item.optString("created_at"),
                        lastUsedAt = item.optString("last_used_at").takeIf { it.isNotBlank() },
                    )
                }
            }
            Handler(Looper.getMainLooper()).post { onResult(result) }
        }
    }

    fun createMcpToken(context: Context, name: String, onResult: (Result<McpTokenCreated>) -> Unit) {
        val app = context.applicationContext
        val settings = SettingsRepository(app).load()
        executor.execute {
            val result = runCatching {
                require(settings.deviceToken.isNotBlank()) { "Aparelho sem sessão." }
                val response = request(
                    "POST",
                    "${settings.backendUrl.trimEnd('/')}/api/v1/mcp/tokens",
                    JSONObject().apply { put("name", name.trim().take(80).ifBlank { "Minha integração" }) },
                    settings.deviceToken,
                )
                val json = JSONObject(response)
                McpTokenCreated(
                    id = json.optString("id"),
                    name = json.optString("name"),
                    prefix = json.optString("token_prefix"),
                    token = json.optString("token").also { require(it.isNotBlank()) { "Servidor não retornou a chave." } },
                    endpoint = json.optString("endpoint").ifBlank { "${settings.backendUrl.trimEnd('/')}/mcp" },
                )
            }
            Handler(Looper.getMainLooper()).post { onResult(result) }
        }
    }

    fun revokeMcpToken(context: Context, tokenId: String, onResult: (Result<Unit>) -> Unit) {
        val app = context.applicationContext
        val settings = SettingsRepository(app).load()
        executor.execute {
            val result = runCatching {
                require(settings.deviceToken.isNotBlank()) { "Aparelho sem sessão." }
                require(tokenId.isNotBlank()) { "Chave inválida." }
                request(
                    "DELETE",
                    "${settings.backendUrl.trimEnd('/')}/api/v1/mcp/tokens?id=$tokenId",
                    null,
                    settings.deviceToken,
                )
                Unit
            }
            Handler(Looper.getMainLooper()).post { onResult(result) }
        }
    }

    fun fetchHistoryAnalytics(
        context: Context,
        days: Int,
        verdict: String?,
        serviceType: String?,
        offerType: String?,
        onResult: (Result<HistoryAnalytics>) -> Unit,
    ) {
        val app = context.applicationContext
        val settings = SettingsRepository(app).load()
        executor.execute {
            val result = runCatching {
                require(settings.deviceToken.isNotBlank()) { "Aparelho sem sessão." }
                val query = buildList {
                    add("days=${days.coerceIn(1, 90)}")
                    verdict?.takeIf(String::isNotBlank)?.let { add("verdict=$it") }
                    serviceType?.takeIf(String::isNotBlank)?.let { add("service_type=$it") }
                    offerType?.takeIf(String::isNotBlank)?.let { add("offer_type=$it") }
                }.joinToString("&")
                val response = request(
                    "GET",
                    "${settings.backendUrl.trimEnd('/')}/api/v1/analytics?$query",
                    null,
                    settings.deviceToken,
                )
                HistoryAnalytics.fromJson(JSONObject(response), "cloud")
            }
            Handler(Looper.getMainLooper()).post { onResult(result) }
        }
    }

    fun pair(context: Context, backendUrl: String, pairingCode: String, onResult: (Result<String>) -> Unit) {
        executor.execute {
            val result = runCatching {
                require(backendUrl.isNotBlank()) { "Informe a URL do backend." }
                require(pairingCode.isNotBlank()) { "Informe o código de pareamento." }
                val response = request("POST", "${backendUrl.trim().trimEnd('/')}/api/v1/pair", JSONObject().apply {
                    put("code", pairingCode.trim()); put("device_name", "${Build.MANUFACTURER} ${Build.MODEL}")
                })
                val token = JSONObject(response).optString("device_token")
                require(token.isNotBlank()) { "Backend não retornou device_token." }
                SettingsRepository(context).saveDeviceToken(token)
                token
            }
            Handler(Looper.getMainLooper()).post { onResult(result) }
        }
    }

    fun ask(context: Context, question: String, onResult: (Result<String>) -> Unit) {
        val settings = SettingsRepository(context).load()
        executor.execute {
            val result = runCatching {
                require(settings.backendUrl.isNotBlank()) { "Configure a URL do backend." }
                require(settings.deviceToken.isNotBlank()) { "Conecte o aparelho primeiro." }
                require(question.trim().length >= 3) { "Digite uma pergunta." }
                val response = request("POST", "${settings.backendUrl.trimEnd('/')}/api/v1/ask", JSONObject().apply { put("question", question.trim()) }, settings.deviceToken)
                JSONObject(response).optString("answer").ifBlank { "O backend não retornou uma resposta." }
            }
            Handler(Looper.getMainLooper()).post { onResult(result) }
        }
    }

    fun sendOffer(context: Context, offer: RideOffer) { val app = context.applicationContext; executor.execute { sendOfferNow(app, offer) } }

    fun flushPendingOffers(context: Context) {
        val app = context.applicationContext
        if (!flushRunning.compareAndSet(false, true)) return
        executor.execute {
            try {
                val settings = SettingsRepository(app).load()
                if (settings.backendUrl.isBlank() || settings.deviceToken.isBlank()) return@execute
                val store = LocalStore.get(app)
                store.pendingJourneyStarts(20).forEach { syncJourneyStartNow(app, it) }
                store.pendingOffers(50).forEach { sendOfferNow(app, it) }
                store.pendingJourneyEnds(20).forEach { syncJourneyEndNow(app, it) }
            } finally { flushRunning.set(false) }
        }
    }

    fun startJourney(context: Context, journey: JourneyRecord) {
        val app = context.applicationContext
        executor.execute { syncJourneyStartNow(app, journey) }
    }

    fun endJourney(context: Context, summary: JourneySummary) {
        val app = context.applicationContext
        executor.execute {
            syncJourneyStartNow(app, summary.journey)
            syncJourneyEndNow(app, summary.journey)
        }
    }

    private fun syncJourneyStartNow(context: Context, journey: JourneyRecord): Boolean {
        val s = SettingsRepository(context).load(); if (s.backendUrl.isBlank() || s.deviceToken.isBlank()) return false
        return runCatching {
            request("POST", "${s.backendUrl.trimEnd('/')}/api/v1/journeys", JSONObject().apply {
                put("action", "start"); put("journey_id", journey.id); put("platform", journey.platform); put("started_at", journey.startedAt)
            }, s.deviceToken)
            LocalStore.get(context).markJourneyStartSynced(journey.id); true
        }.onFailure { LocalLog.append(context, "Falha ao sincronizar início da jornada: ${it.message}") }.getOrDefault(false)
    }

    private fun syncJourneyEndNow(context: Context, journey: JourneyRecord): Boolean {
        if (journey.endedAt.isNullOrBlank()) return false
        val s = SettingsRepository(context).load(); if (s.backendUrl.isBlank() || s.deviceToken.isBlank()) return false
        return runCatching {
            request("POST", "${s.backendUrl.trimEnd('/')}/api/v1/journeys", JSONObject().apply {
                put("action", "end"); put("journey_id", journey.id); put("ended_at", journey.endedAt); put("end_reason", journey.endReason ?: "user_or_system")
            }, s.deviceToken)
            LocalStore.get(context).markJourneyEndSynced(journey.id); true
        }.onFailure { LocalLog.append(context, "Falha ao sincronizar fim da jornada: ${it.message}") }.getOrDefault(false)
    }

    fun syncPreferences(context: Context) {
        val app = context.applicationContext
        executor.execute {
            val s = SettingsRepository(app).load(); if (s.backendUrl.isBlank() || s.deviceToken.isBlank()) return@execute
            runCatching {
                request("POST", "${s.backendUrl.trimEnd('/')}/api/v1/preferences", JSONObject().apply {
                    put("min_per_km", s.minPerKm); put("red_per_km_below", s.redPerKmBelow); put("min_per_hour", s.minPerHour); put("red_per_hour_below", s.redPerHourBelow); put("good_rating_from", s.goodRatingFrom); put("red_rating_below", s.redRatingBelow); put("min_per_minute", s.minPerMinute); put("red_per_minute_below", s.redPerMinuteBelow); put("min_fare", s.minFare); put("max_pickup_km", s.maxPickupKm); put("min_profit", s.minProfit); put("min_profit_per_hour", s.minProfitPerHour); put("red_profit_per_hour_below", s.redProfitPerHourBelow); put("min_profit_percent", s.minProfitPercent); put("red_profit_percent_below", s.redProfitPercentBelow); put("cost_per_km", s.costPerKm); put("timezone", "America/Sao_Paulo")
                }, s.deviceToken)
            }.onFailure { LocalLog.append(app, "Falha ao sincronizar estratégia: ${it.message}") }
        }
    }

    private fun sendOfferNow(context: Context, offer: RideOffer): Boolean {
        val s = SettingsRepository(context).load(); if (s.backendUrl.isBlank() || s.deviceToken.isBlank()) return false
        return runCatching {
            request("POST", "${s.backendUrl.trimEnd('/')}/api/v1/offers", offer.toJson(), s.deviceToken)
            LocalStore.get(context).markOfferSynced(offer.localId); true
        }.onFailure { LocalLog.append(context, "Falha ao enviar oferta: ${it.message}") }.getOrDefault(false)
    }

    private fun parseAccountSession(response: String): AccountSession {
        val json = JSONObject(response)
        val token = json.optString("device_token")
        require(token.isNotBlank()) { "Backend não retornou uma sessão do aparelho." }
        return AccountSession(
            token = token,
            email = json.optString("email"),
            displayName = json.optString("display_name").ifBlank { "Motorista" },
            driverId = json.optString("driver_id"),
        )
    }

    private fun request(method: String, url: String, body: JSONObject?, bearer: String? = null): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method; connectTimeout = 8000; readTimeout = 12000
            doOutput = body != null
            setRequestProperty("Content-Type", "application/json; charset=utf-8"); setRequestProperty("Accept", "application/json"); setRequestProperty("X-SrRotas-App-Version", BuildConfig.VERSION_NAME)
            if (!bearer.isNullOrBlank()) setRequestProperty("Authorization", "Bearer $bearer")
        }
        if (body != null) connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
        val status = connection.responseCode
        val stream = if (status in 200..299) connection.inputStream else connection.errorStream
        val text = stream?.use { BufferedReader(InputStreamReader(it)).readText() } ?: ""
        connection.disconnect()
        if (status !in 200..299) {
            val message = runCatching { JSONObject(text).optString("message").ifBlank { JSONObject(text).optString("error") } }.getOrDefault("")
            error(if (message.isBlank()) "HTTP $status" else message)
        }
        return text
    }
}
