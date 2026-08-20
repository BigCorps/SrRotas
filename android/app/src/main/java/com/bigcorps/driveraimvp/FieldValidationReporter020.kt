package com.srrotas.app

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.util.Locale

/**
 * Relatório específico da validação 0.20.
 * Mantém apenas dados agregados; não inclui OCR, screenshot, endereço,
 * coordenadas exatas, e-mail, token ou segredo.
 */
object FieldValidationReporter020 {
    fun build(context: Context): String {
        val facts =
            FieldValidationCollector.collect(context)
        val checks =
            FieldValidationAssessment
                .evaluate(facts)
                .filterNot {
                    it.id ==
                        "historical_import"
                }
        val performance =
            FieldValidationSession.current(
                context,
            ) ?: FieldValidationSession.last(
                context,
            )
        val visibleManual =
            FieldValidationManualChecklist.items
                .filterNot {
                    it.id ==
                        "historical_import"
                }

        return JSONObject().apply {
            put(
                "schema",
                "sr-rotas-field-validation-v0.20",
            )
            put(
                "generated_at",
                Instant.now().toString(),
            )
            put(
                "app_version",
                BuildConfig.VERSION_NAME,
            )
            put(
                "version_code",
                BuildConfig.VERSION_CODE,
            )

            put(
                "device",
                JSONObject().apply {
                    put(
                        "manufacturer",
                        Build.MANUFACTURER
                            .take(80),
                    )
                    put(
                        "model",
                        Build.MODEL.take(100),
                    )
                    put(
                        "android_sdk",
                        Build.VERSION.SDK_INT,
                    )
                    put(
                        "android_release",
                        Build.VERSION.RELEASE
                            ?: "",
                    )
                },
            )

            put(
                "facts",
                JSONObject().apply {
                    put("offers", facts.offers)
                    put(
                        "exclusive_offers",
                        facts.exclusiveOffers,
                    )
                    put(
                        "radar_offers",
                        facts.radarOffers,
                    )
                    put(
                        "offers_with_context",
                        facts.offersWithContext,
                    )
                    put(
                        "offers_with_destination_cell",
                        facts.offersWithDestinationCell,
                    )
                    put(
                        "resolved_contexts",
                        facts.resolvedContexts,
                    )
                    put(
                        "closed_exposures",
                        facts.closedExposures,
                    )
                    put(
                        "journey_events",
                        facts.journeyEvents,
                    )
                    put(
                        "ride_outcomes",
                        facts.rideOutcomes,
                    )
                    put(
                        "completed_rides",
                        facts.completedRides,
                    )
                    put(
                        "cost_configured",
                        facts.costConfigured,
                    )
                    put(
                        "offers_with_cost_snapshot",
                        facts.offersWithCostSnapshot,
                    )
                    put(
                        "probability_ready_cells",
                        facts.probabilityReadyCells,
                    )
                    put(
                        "probability_guardrail_violations",
                        facts.probabilityGuardrailViolations,
                    )
                    put(
                        "pending_total",
                        facts.pendingTotal,
                    )
                    put(
                        "pending_offers",
                        facts.pendingOffers,
                    )
                    put(
                        "pending_contexts",
                        facts.pendingContexts,
                    )
                    put(
                        "pending_journey_events",
                        facts.pendingJourneyEvents,
                    )
                    put(
                        "pending_ride_outcomes",
                        facts.pendingRideOutcomes,
                    )
                    put(
                        "pending_exposures",
                        facts.pendingExposures,
                    )
                    put(
                        "online",
                        facts.online,
                    )
                    put(
                        "paired",
                        facts.paired,
                    )
                    put(
                        "overlay_allowed",
                        facts.overlayAllowed,
                    )
                    put(
                        "coarse_location_allowed",
                        facts.coarseLocationAllowed,
                    )
                    put(
                        "pending_crash",
                        facts.pendingCrash,
                    )
                    put(
                        "sync_coordinator_running",
                        SyncCoordinator.isRunning(),
                    )
                },
            )

            put(
                "checks",
                JSONArray().apply {
                    checks.forEach { check ->
                        put(
                            JSONObject().apply {
                                put(
                                    "id",
                                    check.id,
                                )
                                put(
                                    "title",
                                    check.title,
                                )
                                put(
                                    "status",
                                    check.status.name
                                        .lowercase(
                                            Locale.ROOT,
                                        ),
                                )
                                put(
                                    "detail",
                                    check.detail,
                                )
                            },
                        )
                    }
                },
            )

            put(
                "manual",
                JSONObject().apply {
                    val completed =
                        visibleManual.count {
                            FieldValidationManualStore
                                .isChecked(
                                    context,
                                    it.id,
                                )
                        }
                    put(
                        "completed",
                        completed,
                    )
                    put(
                        "total",
                        visibleManual.size,
                    )
                    put(
                        "items",
                        JSONArray().apply {
                            visibleManual.forEach { item ->
                                put(
                                    JSONObject().apply {
                                        put(
                                            "id",
                                            item.id,
                                        )
                                        put(
                                            "checked",
                                            FieldValidationManualStore
                                                .isChecked(
                                                    context,
                                                    item.id,
                                                ),
                                        )
                                    },
                                )
                            }
                        },
                    )
                },
            )

            put(
                "performance",
                performance?.toJson()
                    ?: JSONObject.NULL,
            )

            put(
                "privacy_note",
                "Relatório 0.20 agregado. Não inclui token, e-mail, screenshot, OCR bruto, log bruto, endereço textual nem coordenadas exatas.",
            )
        }.toString(2)
    }

    fun share(context: Context) {
        val send =
            Intent(
                Intent.ACTION_SEND,
            ).apply {
                type = "text/plain"
                putExtra(
                    Intent.EXTRA_SUBJECT,
                    "Validação Sr. Rotas ${BuildConfig.VERSION_NAME}",
                )
                putExtra(
                    Intent.EXTRA_TEXT,
                    build(context),
                )
            }

        val chooser =
            Intent.createChooser(
                send,
                "Compartilhar validação 0.20",
            )

        if (context !is Activity) {
            chooser.addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK,
            )
        }

        context.startActivity(chooser)
    }

    private fun FieldPerformanceSample.toJson():
        JSONObject =
        JSONObject().apply {
            put("running", running)
            put(
                "started_at",
                startedAt,
            )
            put(
                "elapsed_minutes",
                elapsedMinutes,
            )
            put(
                "process_cpu_minutes",
                processCpuMinutes,
            )
            put(
                "cpu_to_elapsed_pct",
                cpuToElapsedPct,
            )
            put(
                "process_continuous",
                processContinuous,
            )
            putNullable(
                "start_battery_pct",
                startBatteryPct,
            )
            putNullable(
                "current_battery_pct",
                currentBatteryPct,
            )
            putNullable(
                "battery_drop_pct",
                batteryDropPct,
            )
            putNullable(
                "battery_drop_per_hour",
                batteryDropPerHour,
            )
            put(
                "start_pss_mb",
                startPssMb,
            )
            put(
                "current_pss_mb",
                currentPssMb,
            )
            put(
                "thermal_status",
                thermalStatus,
            )
            putNullable(
                "battery_temperature_c",
                batteryTemperatureC,
            )
        }

    private fun JSONObject.putNullable(
        key: String,
        value: Any?,
    ) {
        if (value == null) {
            put(key, JSONObject.NULL)
        } else {
            put(key, value)
        }
    }
}
