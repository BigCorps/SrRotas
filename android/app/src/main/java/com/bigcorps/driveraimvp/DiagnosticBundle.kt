package com.srrotas.app

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

object DiagnosticBundle {
    fun build(context: Context): String {
        val repo = SettingsRepository(context)
        val settings = repo.load()
        val store = LocalStore.get(context)
        val summary = JourneyCoordinator.currentSummary(context)
        return JSONObject().apply {
            put("schema", "sr-rotas-diagnostic-v1")
            put("generated_at", Instant.now().toString())
            put("app", JSONObject().apply {
                put("version_name", BuildConfig.VERSION_NAME); put("version_code", BuildConfig.VERSION_CODE); put("package", context.packageName)
            })
            put("device", JSONObject().apply {
                put("manufacturer", Build.MANUFACTURER); put("model", Build.MODEL); put("android_sdk", Build.VERSION.SDK_INT); put("android_release", Build.VERSION.RELEASE ?: "")
            })
            put("capture", JSONObject().apply {
                put("projection_active", repo.isProjectionActive()); put("latest_method", repo.latestMethod()); put("latest_summary", repo.latestSummary()); put("latest_raw_text", repo.latestRaw())
            })
            put("strategy", JSONObject().apply {
                put("min_per_km", settings.minPerKm); put("min_per_hour", settings.minPerHour); put("min_fare", settings.minFare); put("max_pickup_km", settings.maxPickupKm); put("min_profit", settings.minProfit); put("cost_per_km", settings.costPerKm)
            })
            put("sync", JSONObject().apply {
                put("backend_configured", settings.backendUrl.isNotBlank()); put("device_paired", settings.deviceToken.isNotBlank()); put("pending_offer_count", store.pendingOfferCount())
            })
            put("current_journey", summary?.toJson() ?: JSONObject.NULL)
            put("recent_offers", JSONArray().apply { store.recentOffers(20).forEach { put(it.toDiagnosticJson()) } })
            put("local_log", LocalLog.tail(context, 120))
            put("privacy_note", "Compartilhamento explícito. Nenhuma screenshot ou device_token é incluído. O texto OCR bruto pode conter conteúdo visível na tela.")
        }.toString(2)
    }

    fun share(context: Context) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Diagnóstico Sr. Rotas ${BuildConfig.VERSION_NAME}")
            putExtra(Intent.EXTRA_TEXT, build(context))
        }
        val chooser = Intent.createChooser(send, "Compartilhar diagnóstico do Sr. Rotas")
        if (context !is Activity) chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }

    private fun JourneySummary.toJson() = JSONObject().apply {
        put("id", journey.id); put("platform", journey.platform); put("started_at", journey.startedAt); put("ended_at", journey.endedAt ?: JSONObject.NULL); put("offer_count", offerCount); put("good_count", goodCount); put("regular_count", regularCount); put("bad_count", badCount); put("average_per_km", averagePerKm ?: JSONObject.NULL); put("average_per_hour", averagePerHour ?: JSONObject.NULL); put("estimated_profit_observed", estimatedProfitObserved ?: JSONObject.NULL)
    }

    private fun RideOffer.toDiagnosticJson() = JSONObject().apply {
        put("local_id", localId); put("journey_id", journeyId ?: JSONObject.NULL); put("observed_at", observedAt); put("platform", platform); put("capture_method", captureMethod); put("raw_text", rawText); put("fare", fare); put("pickup_km", pickupKm ?: JSONObject.NULL); put("trip_km", tripKm ?: JSONObject.NULL); put("total_km", totalKm ?: JSONObject.NULL); put("pickup_minutes", pickupMinutes ?: JSONObject.NULL); put("trip_minutes", tripMinutes ?: JSONObject.NULL); put("total_minutes", totalMinutes ?: JSONObject.NULL); put("per_km", perKm ?: JSONObject.NULL); put("per_hour", perHour ?: JSONObject.NULL); put("estimated_cost", estimatedCost ?: JSONObject.NULL); put("estimated_profit", estimatedProfit ?: JSONObject.NULL); put("verdict", verdict); put("confidence", confidence); put("offer_type", offerType); put("parser_version", parserVersion); put("dedupe_key", dedupeKey)
    }
}
