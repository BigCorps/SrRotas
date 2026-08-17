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
        val repo = SettingsRepository(context); val s = repo.load(); val store = LocalStore.get(context); val summary = JourneyCoordinator.currentSummary(context); val hudPos = repo.loadHudPosition()
        return JSONObject().apply {
            put("schema", "sr-rotas-diagnostic-v2"); put("generated_at", Instant.now().toString())
            put("app", JSONObject().apply { put("version_name", BuildConfig.VERSION_NAME); put("version_code", BuildConfig.VERSION_CODE); put("package", context.packageName) })
            put("device", JSONObject().apply { put("manufacturer", Build.MANUFACTURER); put("model", Build.MODEL); put("android_sdk", Build.VERSION.SDK_INT); put("android_release", Build.VERSION.RELEASE ?: "") })
            put("onboarding", JSONObject().apply {
                put("completed", s.onboardingCompleted); put("step", s.onboardingStep); put("display_name_set", s.driverDisplayName.isNotBlank())
                put("account_connected", s.deviceToken.isNotBlank()); put("account_email_set", s.accountEmail.isNotBlank()); put("network_online", ConnectivityState.isOnline(context))
            })
            put("capture", JSONObject().apply { put("projection_active", repo.isProjectionActive()); put("latest_method", repo.latestMethod()); put("latest_summary", repo.latestSummary()); put("latest_raw_text", repo.latestRaw()); put("private_screenshot_enabled", s.privateScreenshotEnabled); put("private_screenshot_count", PrivateScreenshotStore.count(context)) })
            put("strategy", JSONObject().apply { put("red_per_km_below", s.redPerKmBelow); put("green_per_km_from", s.minPerKm); put("red_per_hour_below", s.redPerHourBelow); put("green_per_hour_from", s.minPerHour); put("red_rating_below", s.redRatingBelow); put("green_rating_from", s.goodRatingFrom); put("red_per_minute_below", s.redPerMinuteBelow); put("green_per_minute_from", s.minPerMinute); put("min_fare", s.minFare); put("max_pickup_km", s.maxPickupKm); put("min_profit", s.minProfit); put("cost_per_km", s.costPerKm) })
            put("hud", JSONObject().apply {
                put("order", s.hudMetricOrder); put("enabled", s.hudEnabledMetrics); put("position", s.hudPosition); put("theme", s.hudTheme); put("card_size", s.hudCardSize); put("opacity", s.hudOpacity); put("font_size", s.hudFontSize); put("tap_dismiss", s.hudDismissOnTap); put("drag_enabled", s.hudDragEnabled); put("color_blind", s.colorBlindMode)
                put("custom_position", if (hudPos == null) JSONObject.NULL else JSONObject().apply { put("x", hudPos.first); put("y", hudPos.second) })
            })
            put("sync", JSONObject().apply { put("backend_configured", s.backendUrl.isNotBlank()); put("device_paired", s.deviceToken.isNotBlank()); put("pending_offer_count", store.pendingOfferCount()) })
            put("current_journey", summary?.toJson() ?: JSONObject.NULL); put("recent_offers", JSONArray().apply { store.recentOffers(25).forEach { put(it.toDiagnosticJson()) } }); put("local_log", LocalLog.tail(context, 150))
            put("privacy_note", "Compartilhamento explícito. Nenhuma senha, screenshot, device_token, e-mail completo nem código de pareamento é incluído. O texto OCR bruto pode conter conteúdo visível na tela.")
        }.toString(2)
    }

    fun share(context: Context) {
        val send = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_SUBJECT, "Diagnóstico Sr. Rotas ${BuildConfig.VERSION_NAME}"); putExtra(Intent.EXTRA_TEXT, build(context)) }
        val chooser = Intent.createChooser(send, "Compartilhar diagnóstico do Sr. Rotas"); if (context !is Activity) chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); context.startActivity(chooser)
    }

    private fun JourneySummary.toJson() = JSONObject().apply { put("id", journey.id); put("platform", journey.platform); put("started_at", journey.startedAt); put("ended_at", journey.endedAt ?: JSONObject.NULL); put("offer_count", offerCount); put("good_count", goodCount); put("regular_count", regularCount); put("bad_count", badCount); put("average_per_km", averagePerKm ?: JSONObject.NULL); put("average_per_hour", averagePerHour ?: JSONObject.NULL); put("estimated_profit_observed", estimatedProfitObserved ?: JSONObject.NULL) }
    private fun RideOffer.toDiagnosticJson() = JSONObject().apply { put("local_id", localId); put("journey_id", journeyId ?: JSONObject.NULL); put("observed_at", observedAt); put("platform", platform); put("capture_method", captureMethod); put("raw_text", rawText); put("fare", fare); put("pickup_km", pickupKm ?: JSONObject.NULL); put("trip_km", tripKm ?: JSONObject.NULL); put("total_km", totalKm ?: JSONObject.NULL); put("pickup_minutes", pickupMinutes ?: JSONObject.NULL); put("trip_minutes", tripMinutes ?: JSONObject.NULL); put("total_minutes", totalMinutes ?: JSONObject.NULL); put("per_km", perKm ?: JSONObject.NULL); put("per_hour", perHour ?: JSONObject.NULL); put("per_minute", perMinute ?: JSONObject.NULL); put("estimated_cost", estimatedCost ?: JSONObject.NULL); put("estimated_profit", estimatedProfit ?: JSONObject.NULL); put("profit_per_hour", profitPerHour ?: JSONObject.NULL); put("profit_percent", profitPercent ?: JSONObject.NULL); put("passenger_rating", passengerRating ?: JSONObject.NULL); put("advertised_per_km", advertisedPerKm ?: JSONObject.NULL); put("service_type", serviceType); put("verdict", verdict); put("confidence", confidence); put("offer_type", offerType); put("parser_version", parserVersion); put("dedupe_key", dedupeKey) }
}
