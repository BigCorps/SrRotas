package com.srrotas.app

import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.os.Build
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

object DiagnosticBundle {
    fun build(
        context: Context,
        includeRawOcr: Boolean = false,
    ): String {
        val repo = SettingsRepository(context)
        val s = repo.load()
        val store = LocalStore.get(context)
        val summary = JourneyCoordinator.currentSummary(context)
        val hudPos = repo.loadHudPosition()
        val costProfile = runCatching {
            CostProfileStore.get(context).load()
        }.getOrNull()
        val costCalculation = costProfile?.let(CostCalculator::calculate)
        val reliability = OfferEngineReliability0270.readLast(context)
        val diagnosticOffers = store.recentOffers(5_000)
        val currentJourneyId = repo.currentJourneyId().trim()

        return JSONObject().apply {
            put("schema", "sr-rotas-diagnostic-v6")
            put("generated_at", Instant.now().toString())
            put(
                "app",
                JSONObject().apply {
                    put("version_name", BuildConfig.VERSION_NAME)
                    put("version_code", BuildConfig.VERSION_CODE)
                    put("package", context.packageName)
                },
            )
            put(
                "device",
                JSONObject().apply {
                    put("manufacturer", Build.MANUFACTURER)
                    put("model", Build.MODEL)
                    put("android_sdk", Build.VERSION.SDK_INT)
                    put("android_release", Build.VERSION.RELEASE ?: "")
                },
            )
            put(
                "onboarding",
                JSONObject().apply {
                    put("completed", s.onboardingCompleted)
                    put("step", s.onboardingStep)
                    put("display_name_set", s.driverDisplayName.isNotBlank())
                    put("account_connected", s.deviceToken.isNotBlank())
                    put("account_email_set", s.accountEmail.isNotBlank())
                    put("network_online", ConnectivityState.isOnline(context))
                },
            )
            put(
                "capture",
                JSONObject().apply {
                    put("projection_active", repo.isProjectionActive())
                    put("latest_method", repo.latestMethod())
                    put("latest_summary", repo.latestSummary())
                    put("latest_raw_length", repo.latestRaw().length)
                    if (includeRawOcr) {
                        put("latest_raw_text", repo.latestRaw())
                    }
                    put("private_screenshot_enabled", s.privateScreenshotEnabled)
                    put("private_screenshot_count", PrivateScreenshotStore.count(context))
                },
            )
            put("offer_engine_reliability_0270", reliability)
            put(
                "radar_hud_trace_024",
                RadarHudTrace024.diagnosticSnapshot(
                    context = context,
                    reliability = reliability,
                ),
            )
            put("failure_reports_0270", FailureReportStore0270.snapshot(context))
            put(
                "offer_stats_0270",
                offerStats(
                    offers = diagnosticOffers,
                    currentJourneyId = currentJourneyId,
                ),
            )
            put(
                "strategy",
                JSONObject().apply {
                    put("red_per_km_below", s.redPerKmBelow)
                    put("green_per_km_from", s.minPerKm)
                    put("red_per_hour_below", s.redPerHourBelow)
                    put("green_per_hour_from", s.minPerHour)
                    put("red_rating_below", s.redRatingBelow)
                    put("green_rating_from", s.goodRatingFrom)
                    put("red_per_minute_below", s.redPerMinuteBelow)
                    put("green_per_minute_from", s.minPerMinute)
                    put("min_fare", s.minFare)
                    put("max_pickup_km", s.maxPickupKm)
                    put("min_profit", s.minProfit)
                    put("cost_per_km", s.costPerKm)
                    put("cost_snapshot_source", repo.costSnapshot().source)
                    put("cost_snapshot_version", repo.costSnapshot().version)
                },
            )
            put(
                "cost_profile",
                if (costProfile == null || costCalculation == null) {
                    JSONObject.NULL
                } else {
                    JSONObject().apply {
                        put("configured", true)
                        put("vehicle_type", costProfile.vehicleType)
                        put("ownership_type", costProfile.ownershipType)
                        put("energy_mode", costProfile.energyMode)
                        put("monthly_work_km_source", costProfile.monthlyWorkKmSource)
                        put("calculation_version", costCalculation.version)
                        put("completeness", costCalculation.completeness)
                        put("effective_cost_per_km", costCalculation.effectiveCostPerKm)
                        put("variable_cost_per_km", costCalculation.variableCostPerKm)
                        put("fixed_cost_per_km", costCalculation.fixedCostPerKm)
                        put("missing_inputs", JSONArray(costCalculation.missingInputs))
                    }
                },
            )
            put(
                "hud",
                JSONObject().apply {
                    put("order", s.hudMetricOrder)
                    put("enabled", s.hudEnabledMetrics)
                    put("position", s.hudPosition)
                    put("theme", s.hudTheme)
                    put("card_size", s.hudCardSize)
                    put("opacity", s.hudOpacity)
                    put("font_size", s.hudFontSize)
                    put("tap_dismiss", s.hudDismissOnTap)
                    put("drag_enabled", s.hudDragEnabled)
                    put("color_blind", s.colorBlindMode)
                    put(
                        "custom_position",
                        if (hudPos == null) {
                            JSONObject.NULL
                        } else {
                            JSONObject().apply {
                                put("x", hudPos.first)
                                put("y", hudPos.second)
                            }
                        },
                    )
                },
            )
            put(
                "sync",
                JSONObject().apply {
                    put("backend_configured", s.backendUrl.isNotBlank())
                    put("device_paired", s.deviceToken.isNotBlank())
                    put("pending_offer_count", store.pendingOfferCount())
                    put("pending_context_count", store.pendingOfferContexts(250).size)
                    put("pending_journey_event_count", store.pendingJourneyEvents(250).size)
                    put("pending_ride_outcome_count", store.pendingRideOutcomes(250).size)
                    put("pending_exposure_count", store.pendingExposures(250).size)
                },
            )
            put("current_journey", summary?.toJson() ?: JSONObject.NULL)
            put(
                "recent_offers",
                JSONArray().apply {
                    store.recentOffers(25).forEach {
                        put(it.toDiagnosticJson(includeRawOcr))
                    }
                },
            )
            put(
                "field_validation_019",
                runCatching {
                    JSONObject(FieldValidationReporter.build(context))
                }.getOrElse {
                    JSONObject().apply {
                        put("error", it.message ?: "field_validation_failed")
                    }
                },
            )
            if (includeRawOcr) {
                // O log local pode conter trechos de contexto vistos na tela.
                // Ele só entra no modo detalhado explicitamente solicitado.
                put("local_log", LocalLog.tail(context, 150))
            } else {
                put("local_log_included", false)
            }
            put(
                "privacy_note",
                if (includeRawOcr) {
                    "Compartilhamento explícito detalhado. Inclui OCR bruto e log local solicitados pelo usuário; eles podem conter conteúdo visível na tela. Não inclui senha, token, e-mail completo ou chave MCP."
                } else {
                    "Compartilhamento explícito padrão. Inclui trace técnico anonimizado Radar/HUD, todos os reportes da jornada e agregados locais de ofertas; sem OCR bruto, log local, screenshot, senha, token, e-mail completo, chave MCP, endereço textual ou coordenadas exatas."
                },
            )
        }.toString(2)
    }

    fun share(context: Context) {
        val uri = DiagnosticShareProvider0270.prepare(
            context = context,
            content = build(context, includeRawOcr = false),
        )
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_SUBJECT, "Diagnóstico Sr. Rotas ${BuildConfig.VERSION_NAME}")
            putExtra(Intent.EXTRA_TEXT, "Diagnóstico técnico do Sr. Rotas em anexo.")
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newUri(context.contentResolver, "Diagnóstico Sr. Rotas", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(send, "Compartilhar diagnóstico do Sr. Rotas")
        chooser.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        if (context !is Activity) chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }

    private fun offerStats(
        offers: List<RideOffer>,
        currentJourneyId: String,
    ): JSONObject {
        val byPlatform = offers.groupingBy { it.platform.lowercase() }.eachCount()
        val current = if (currentJourneyId.isBlank()) {
            emptyList()
        } else {
            offers.filter { it.journeyId == currentJourneyId }
        }
        val complete = offers.count { offer ->
            offer.fare > 0.0 &&
                offer.totalKm != null && offer.totalKm > 0.0 &&
                offer.totalMinutes != null && offer.totalMinutes > 0 &&
                offer.perKm != null && offer.perHour != null
        }
        val currentComplete = current.count { offer ->
            offer.fare > 0.0 &&
                offer.totalKm != null && offer.totalKm > 0.0 &&
                offer.totalMinutes != null && offer.totalMinutes > 0 &&
                offer.perKm != null && offer.perHour != null
        }

        return JSONObject().apply {
            put("window_limit", 5_000)
            put("offers_in_window", offers.size)
            put("complete_financial_offers", complete)
            put("incomplete_financial_offers", offers.size - complete)
            put("geo_temporal_quality", geoTemporalQuality(offers))
            put(
                "by_platform",
                JSONObject().apply {
                    put("uber", byPlatform["uber"] ?: 0)
                    put("99", byPlatform["99"] ?: 0)
                    put("multi", byPlatform["multi"] ?: 0)
                    put(
                        "other",
                        byPlatform.entries
                            .filter { it.key !in setOf("uber", "99", "multi") }
                            .sumOf { it.value },
                    )
                },
            )
            put(
                "current_journey",
                JSONObject().apply {
                    if (currentJourneyId.isBlank()) put("journey_id", JSONObject.NULL)
                    else put("journey_id", currentJourneyId)
                    put("offers", current.size)
                    put("complete_financial_offers", currentComplete)
                    put("incomplete_financial_offers", current.size - currentComplete)
                    put("geo_temporal_quality", geoTemporalQuality(current))
                    val currentByPlatform = current.groupingBy { it.platform.lowercase() }.eachCount()
                    put("uber", currentByPlatform["uber"] ?: 0)
                    put("99", currentByPlatform["99"] ?: 0)
                    put("multi", currentByPlatform["multi"] ?: 0)
                },
            )
            put(
                "note",
                "Contagem local para diagnóstico; não equivale ao total histórico remoto quando houver mais de 5.000 ofertas.",
            )
        }
    }

    /**
     * Qualidade da matéria-prima para inteligência regional/temporal.
     * Não altera parser, HUD, verdict nem decide se uma oferta deve aparecer.
     * É apenas telemetria local para medir se novas coletas chegam com contexto
     * suficiente para os motores estatísticos.
     */
    private fun geoTemporalQuality(offers: List<RideOffer>): JSONObject {
        fun validObservedAt(offer: RideOffer): Boolean =
            runCatching { Instant.parse(offer.observedAt) }.isSuccess

        val withValidTime = offers.count(::validObservedAt)
        val withPickupLabel = offers.count { !it.context?.pickupLabel.isNullOrBlank() }
        val withDestinationLabel = offers.count { !it.context?.destinationLabel.isNullOrBlank() }
        val withBothLabels = offers.count {
            !it.context?.pickupLabel.isNullOrBlank() &&
                !it.context?.destinationLabel.isNullOrBlank()
        }
        val withPickupCell = offers.count { !it.context?.pickupCell.isNullOrBlank() }
        val withDestinationCell = offers.count { !it.context?.destinationCell.isNullOrBlank() }
        val withBothCells = offers.count {
            !it.context?.pickupCell.isNullOrBlank() &&
                !it.context?.destinationCell.isNullOrBlank()
        }
        val resolved = offers.count { it.context?.geocodeStatus == "resolved" }
        val highConfidence = offers.count { (it.context?.contextConfidence ?: 0.0) >= 0.90 }
        val destinationContinuityReady = offers.count { offer ->
            validObservedAt(offer) &&
                !offer.context?.destinationCell.isNullOrBlank()
        }
        val routeIntelligenceReady = offers.count { offer ->
            val ctx = offer.context
            validObservedAt(offer) &&
                ctx != null &&
                !ctx.pickupCell.isNullOrBlank() &&
                !ctx.destinationCell.isNullOrBlank() &&
                ctx.contextConfidence >= 0.90 &&
                ctx.geocodeStatus == "resolved"
        }

        return JSONObject().apply {
            put("offers", offers.size)
            put("valid_observed_at", withValidTime)
            put("with_pickup_label", withPickupLabel)
            put("with_destination_label", withDestinationLabel)
            put("with_both_labels", withBothLabels)
            put("with_pickup_cell", withPickupCell)
            put("with_destination_cell", withDestinationCell)
            put("with_both_cells", withBothCells)
            put("resolved_contexts", resolved)
            put("high_confidence_contexts", highConfidence)
            put("destination_continuity_ready", destinationContinuityReady)
            put("route_intelligence_ready", routeIntelligenceReady)
            put(
                "definition",
                "route_intelligence_ready = horário válido + célula de embarque + célula de destino + contexto >= 0.90 + geocode resolvido",
            )
            put(
                "note",
                "Probabilidade de receber nova oferta usa também zone_exposures (célula GPS do motorista + tempo disponível), que é uma fonte separada deste contador.",
            )
        }
    }

    private fun JourneySummary.toJson() = JSONObject().apply {
        put("id", journey.id)
        put("platform", journey.platform)
        put("started_at", journey.startedAt)
        put("ended_at", journey.endedAt ?: JSONObject.NULL)
        put("offer_count", offerCount)
        put("good_count", goodCount)
        put("regular_count", regularCount)
        put("bad_count", badCount)
        put("average_per_km", averagePerKm ?: JSONObject.NULL)
        put("average_per_hour", averagePerHour ?: JSONObject.NULL)
        put("estimated_profit_observed", estimatedProfitObserved ?: JSONObject.NULL)
    }

    private fun RideOffer.toDiagnosticJson(includeRawOcr: Boolean) = JSONObject().apply {
        put("local_id", localId)
        put("journey_id", journeyId ?: JSONObject.NULL)
        put("observed_at", observedAt)
        put("platform", platform)
        put("capture_method", captureMethod)
        put("raw_text_length", rawText.length)
        if (includeRawOcr) put("raw_text", rawText)
        put("fare", fare)
        put("pickup_km", pickupKm ?: JSONObject.NULL)
        put("trip_km", tripKm ?: JSONObject.NULL)
        put("total_km", totalKm ?: JSONObject.NULL)
        put("pickup_minutes", pickupMinutes ?: JSONObject.NULL)
        put("trip_minutes", tripMinutes ?: JSONObject.NULL)
        put("total_minutes", totalMinutes ?: JSONObject.NULL)
        put("per_km", perKm ?: JSONObject.NULL)
        put("per_hour", perHour ?: JSONObject.NULL)
        put("per_minute", perMinute ?: JSONObject.NULL)
        put("estimated_cost", estimatedCost ?: JSONObject.NULL)
        put("estimated_profit", estimatedProfit ?: JSONObject.NULL)
        put("profit_per_hour", profitPerHour ?: JSONObject.NULL)
        put("profit_percent", profitPercent ?: JSONObject.NULL)
        put("passenger_rating", passengerRating ?: JSONObject.NULL)
        put("advertised_per_km", advertisedPerKm ?: JSONObject.NULL)
        put("service_type", serviceType)
        put("verdict", verdict)
        put("confidence", confidence)
        put("offer_type", offerType)
        put("parser_version", parserVersion)
        put("dedupe_key", dedupeKey)
        put("cost_per_km_used", costPerKmUsed ?: JSONObject.NULL)
        put("cost_source", costSource ?: JSONObject.NULL)
        put("cost_profile_version", costProfileVersion ?: JSONObject.NULL)
        context?.let { ctx ->
            put("context", JSONObject().apply {
                put("has_pickup_label", !ctx.pickupLabel.isNullOrBlank())
                put("has_destination_label", !ctx.destinationLabel.isNullOrBlank())
                put("pickup_cell", ctx.pickupCell ?: JSONObject.NULL)
                put("destination_cell", ctx.destinationCell ?: JSONObject.NULL)
                put("estimated_arrival_at", ctx.estimatedArrivalAt ?: JSONObject.NULL)
                put("context_confidence", ctx.contextConfidence)
                put("geocode_status", ctx.geocodeStatus)
                put("geocode_source", ctx.geocodeSource ?: JSONObject.NULL)
                put("context_version", ctx.contextVersion)
                put("source_type", ctx.sourceType)
                put("time_source", ctx.timeSource)
            })
        }
    }
}
