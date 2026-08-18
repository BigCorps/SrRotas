package com.srrotas.app

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.onesignal.OneSignal
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object PushManager {
    private const val PREFS = "sr_rotas_push"
    private const val KEY_DRIVER_ID = "driver_id"
    @Volatile private var initialized = false
    @Volatile private var identityFetchRunning = false

    fun initialize(context: Context) {
        if (initialized) return
        val appId = BuildConfig.ONESIGNAL_APP_ID.trim()
        if (!isUuid(appId)) {
            LocalLog.append(context, "OneSignal aguardando ONESIGNAL_APP_ID.")
            return
        }
        runCatching {
            OneSignal.initWithContext(context.applicationContext, appId)
            initialized = true
            LocalLog.append(context, "OneSignal inicializado.")
        }.onFailure {
            LocalLog.append(context, "Falha ao inicializar OneSignal: ${it.message}")
        }
    }

    fun isConfigured(context: Context): Boolean {
        initialize(context)
        return initialized
    }

    fun rememberedDriverId(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_DRIVER_ID, "") ?: ""

    fun onSignedIn(context: Context, driverId: String, preferences: BackendClient.NotificationPreferences? = null) {
        if (driverId.isBlank()) return
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY_DRIVER_ID, driverId).apply()
        sync(context, preferences)
    }

    fun ensureIdentity(context: Context) {
        initialize(context)
        if (!initialized) return
        val settings = SettingsRepository(context).load()
        if (settings.deviceToken.isBlank()) {
            logout(context)
            return
        }
        val known = rememberedDriverId(context)
        if (known.isNotBlank()) {
            sync(context, null)
            BackendClient.fetchNotificationPreferences(context) { result ->
                result.onSuccess { sync(context, it) }
            }
            return
        }
        if (identityFetchRunning) return
        identityFetchRunning = true
        BackendClient.fetchAccount(context) { result ->
            identityFetchRunning = false
            result.onSuccess { profile ->
                onSignedIn(context, profile.driverId)
                BackendClient.fetchNotificationPreferences(context) { prefs ->
                    prefs.onSuccess { sync(context, it) }
                }
            }
        }
    }

    fun sync(context: Context, preferences: BackendClient.NotificationPreferences?) {
        initialize(context)
        if (!initialized) return
        val driverId = rememberedDriverId(context)
        if (driverId.isBlank()) return

        val settings = SettingsRepository(context).load()
        val strategy = when {
            settings.minPerKm >= 2.15 && settings.minPerHour >= 42.0 -> "conservador"
            settings.minPerKm <= 1.55 && settings.minPerHour <= 31.0 -> "volume"
            settings.minPerKm in 1.70..1.95 && settings.minPerHour in 33.0..38.0 -> "equilibrado"
            else -> "personalizado"
        }

        runCatching {
            OneSignal.login(driverId)
            val tags = mutableMapOf(
                "platform" to "android",
                "app_version" to BuildConfig.VERSION_NAME,
                "paired" to settings.deviceToken.isNotBlank().toString(),
                "strategy" to strategy,
                "sync_pending" to (LocalStore.get(context).pendingOfferCount() > 0).toString(),
            )
            preferences?.let {
                tags["pref_operational"] = it.operationalEnabled.toString()
                tags["pref_journey_summary"] = it.journeySummaryEnabled.toString()
                tags["pref_sync_alerts"] = it.syncAlertsEnabled.toString()
                tags["pref_product_updates"] = it.productUpdatesEnabled.toString()
            }
            OneSignal.User.addTags(tags)
        }.onFailure {
            LocalLog.append(context, "Falha ao sincronizar identidade OneSignal: ${it.message}")
        }
    }

    fun requestPermission(context: Context, onResult: (Result<Boolean>) -> Unit) {
        initialize(context)
        if (!initialized) {
            onResult(Result.failure(IllegalStateException("onesignal_not_configured")))
            return
        }
        CoroutineScope(Dispatchers.IO).launch {
            val result = runCatching {
                OneSignal.Notifications.requestPermission(true)
                OneSignal.Notifications.permission
            }
            Handler(Looper.getMainLooper()).post { onResult(result) }
        }
    }

    fun permissionGranted(context: Context): Boolean {
        initialize(context)
        return initialized && runCatching { OneSignal.Notifications.permission }.getOrDefault(false)
    }

    fun logout(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY_DRIVER_ID).apply()
        if (!initialized) return
        runCatching { OneSignal.logout() }
    }

    private fun isUuid(value: String) =
        Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-5][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$").matches(value)
}
