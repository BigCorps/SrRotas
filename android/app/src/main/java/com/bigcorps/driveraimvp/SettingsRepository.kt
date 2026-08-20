package com.srrotas.app

import android.content.Context

class SettingsRepository(
    context: Context,
) {
    companion object {
        const val DEFAULT_BACKEND_URL =
            "https://srrotas.com"
    }

    data class CostSnapshot(
        val costPerKm: Double,
        val source: String,
        val version: String,
        val profileUpdatedAt: String,
    )

    private val appContext =
        context.applicationContext
    private val prefs =
        appContext.getSharedPreferences(
            "driver_ai_settings",
            Context.MODE_PRIVATE,
        )

    init {
        migrateV05HudDefaults()
        migrateV06VisualDefaults()
        migrateV07OnboardingDefaults()
        migrateV12CanonicalDomain()
        migrateV0133VoiceDefaults()
        migrateV018CostSnapshot()
    }

    fun load(): DriverSettings =
        DriverSettings(
            backendUrl =
                prefs.getString(
                    "backend_url",
                    DEFAULT_BACKEND_URL,
                )?.ifBlank {
                    DEFAULT_BACKEND_URL
                } ?: DEFAULT_BACKEND_URL,
            deviceToken =
                prefs.getString(
                    "device_token",
                    "",
                ) ?: "",
            driverDisplayName =
                prefs.getString(
                    "driver_display_name",
                    "Motorista",
                )?.ifBlank {
                    "Motorista"
                } ?: "Motorista",
            accountEmail =
                prefs.getString(
                    "account_email",
                    "",
                ) ?: "",
            onboardingCompleted =
                prefs.getBoolean(
                    "onboarding_completed",
                    false,
                ),
            onboardingStep =
                prefs.getInt(
                    "onboarding_step",
                    0,
                ).coerceIn(0, 5),
            minPerKm =
                double(
                    "min_per_km",
                    1.80,
                ),
            redPerKmBelow =
                double(
                    "red_per_km_below",
                    1.45,
                ),
            minPerHour =
                double(
                    "min_per_hour",
                    35.0,
                ),
            redPerHourBelow =
                double(
                    "red_per_hour_below",
                    28.0,
                ),
            goodRatingFrom =
                double(
                    "good_rating_from",
                    4.85,
                ),
            redRatingBelow =
                double(
                    "red_rating_below",
                    4.70,
                ),
            minPerMinute =
                double(
                    "min_per_minute",
                    0.60,
                ),
            redPerMinuteBelow =
                double(
                    "red_per_minute_below",
                    0.48,
                ),
            minFare =
                double(
                    "min_fare",
                    0.0,
                ),
            maxPickupKm =
                double(
                    "max_pickup_km",
                    5.0,
                ),
            minProfit =
                double(
                    "min_profit",
                    0.0,
                ),
            minProfitPerHour =
                double(
                    "min_profit_per_hour",
                    0.0,
                ),
            redProfitPerHourBelow =
                double(
                    "red_profit_per_hour_below",
                    0.0,
                ),
            minProfitPercent =
                double(
                    "min_profit_percent",
                    0.0,
                ),
            redProfitPercentBelow =
                double(
                    "red_profit_percent_below",
                    0.0,
                ),
            costPerKm =
                double(
                    "cost_per_km",
                    0.85,
                ),
            ocrEnabled =
                prefs.getBoolean(
                    "ocr_enabled",
                    true,
                ),
            consentAccepted =
                prefs.getBoolean(
                    "consent_accepted",
                    false,
                ),
            hudMetricOrder =
                prefs.getString(
                    "hud_metric_order",
                    DriverSettings()
                        .hudMetricOrder,
                ) ?: DriverSettings()
                    .hudMetricOrder,
            hudEnabledMetrics =
                prefs.getString(
                    "hud_enabled_metrics",
                    DriverSettings()
                        .hudEnabledMetrics,
                ) ?: DriverSettings()
                    .hudEnabledMetrics,
            hudPosition =
                prefs.getString(
                    "hud_position",
                    "left",
                ) ?: "left",
            hudTheme =
                prefs.getString(
                    "hud_theme",
                    "auto",
                ) ?: "auto",
            hudCardSize =
                prefs.getString(
                    "hud_card_size",
                    "normal",
                ) ?: "normal",
            hudDismissOnTap =
                prefs.getBoolean(
                    "hud_dismiss_on_tap",
                    true,
                ),
            hudDragEnabled =
                prefs.getBoolean(
                    "hud_drag_enabled",
                    true,
                ),
            colorBlindMode =
                prefs.getBoolean(
                    "color_blind_mode",
                    false,
                ),
            hudOpacity =
                prefs.getInt(
                    "hud_opacity",
                    90,
                ).coerceIn(30, 100),
            hudFontSize =
                prefs.getInt(
                    "hud_font_size",
                    16,
                ).coerceIn(14, 24),
            textNotificationEnabled =
                prefs.getBoolean(
                    "text_notification_enabled",
                    false,
                ),
            voiceNotificationEnabled =
                prefs.getBoolean(
                    "voice_notification_enabled",
                    false,
                ),
            voiceFollowHudOrder =
                prefs.getBoolean(
                    "voice_follow_hud_order",
                    true,
                ),
            voiceMetricOrder =
                prefs.getString(
                    "voice_metric_order",
                    DriverSettings()
                        .voiceMetricOrder,
                ) ?: DriverSettings()
                    .voiceMetricOrder,
            voiceEnabledMetrics =
                prefs.getString(
                    "voice_enabled_metrics",
                    DriverSettings()
                        .voiceEnabledMetrics,
                ) ?: DriverSettings()
                    .voiceEnabledMetrics,
            privateScreenshotEnabled =
                prefs.getBoolean(
                    "private_screenshot_enabled",
                    false,
                ),
            collectiveStatsOptIn =
                prefs.getBoolean(
                    "collective_stats_opt_in",
                    false,
                ),
            defaultPassengerMessage =
                prefs.getString(
                    "default_passenger_message",
                    DriverSettings()
                        .defaultPassengerMessage,
                ) ?: DriverSettings()
                    .defaultPassengerMessage,
        )

    fun save(
        s: DriverSettings,
    ) {
        prefs.edit()
            .putString(
                "backend_url",
                s.backendUrl
                    .trim()
                    .trimEnd('/')
                    .ifBlank {
                        DEFAULT_BACKEND_URL
                    },
            )
            .putString(
                "device_token",
                s.deviceToken,
            )
            .putString(
                "driver_display_name",
                s.driverDisplayName
                    .trim()
                    .take(80)
                    .ifBlank {
                        "Motorista"
                    },
            )
            .putString(
                "account_email",
                s.accountEmail
                    .trim()
                    .lowercase()
                    .take(180),
            )
            .putBoolean(
                "onboarding_completed",
                s.onboardingCompleted,
            )
            .putInt(
                "onboarding_step",
                s.onboardingStep
                    .coerceIn(0, 5),
            )
            .putString(
                "min_per_km",
                s.minPerKm.toString(),
            )
            .putString(
                "red_per_km_below",
                s.redPerKmBelow
                    .toString(),
            )
            .putString(
                "min_per_hour",
                s.minPerHour.toString(),
            )
            .putString(
                "red_per_hour_below",
                s.redPerHourBelow
                    .toString(),
            )
            .putString(
                "good_rating_from",
                s.goodRatingFrom
                    .toString(),
            )
            .putString(
                "red_rating_below",
                s.redRatingBelow
                    .toString(),
            )
            .putString(
                "min_per_minute",
                s.minPerMinute
                    .toString(),
            )
            .putString(
                "red_per_minute_below",
                s.redPerMinuteBelow
                    .toString(),
            )
            .putString(
                "min_fare",
                s.minFare.toString(),
            )
            .putString(
                "max_pickup_km",
                s.maxPickupKm.toString(),
            )
            .putString(
                "min_profit",
                s.minProfit.toString(),
            )
            .putString(
                "min_profit_per_hour",
                s.minProfitPerHour
                    .toString(),
            )
            .putString(
                "red_profit_per_hour_below",
                s.redProfitPerHourBelow
                    .toString(),
            )
            .putString(
                "min_profit_percent",
                s.minProfitPercent
                    .toString(),
            )
            .putString(
                "red_profit_percent_below",
                s.redProfitPercentBelow
                    .toString(),
            )
            .putString(
                "cost_per_km",
                s.costPerKm.toString(),
            )
            .putBoolean(
                "ocr_enabled",
                s.ocrEnabled,
            )
            .putBoolean(
                "consent_accepted",
                s.consentAccepted,
            )
            .putString(
                "hud_metric_order",
                s.hudMetricOrder,
            )
            .putString(
                "hud_enabled_metrics",
                s.hudEnabledMetrics,
            )
            .putString(
                "hud_position",
                s.hudPosition,
            )
            .putString(
                "hud_theme",
                normalizeTheme(
                    s.hudTheme,
                ),
            )
            .putString(
                "hud_card_size",
                normalizeSize(
                    s.hudCardSize,
                ),
            )
            .putBoolean(
                "hud_dismiss_on_tap",
                s.hudDismissOnTap,
            )
            .putBoolean(
                "hud_drag_enabled",
                s.hudDragEnabled,
            )
            .putBoolean(
                "color_blind_mode",
                s.colorBlindMode,
            )
            .putInt(
                "hud_opacity",
                s.hudOpacity
                    .coerceIn(30, 100),
            )
            .putInt(
                "hud_font_size",
                s.hudFontSize
                    .coerceIn(14, 24),
            )
            .putBoolean(
                "text_notification_enabled",
                s.textNotificationEnabled,
            )
            .putBoolean(
                "voice_notification_enabled",
                s.voiceNotificationEnabled,
            )
            .putBoolean(
                "voice_follow_hud_order",
                s.voiceFollowHudOrder,
            )
            .putString(
                "voice_metric_order",
                HudPresentation
                    .normalizedVoiceOrder(
                        s.voiceMetricOrder,
                    )
                    .joinToString(","),
            )
            .putString(
                "voice_enabled_metrics",
                HudPresentation
                    .normalizedVoiceOrder(
                        s.voiceMetricOrder,
                    )
                    .filter {
                        it in
                            s.voiceEnabledMetrics
                                .split(',')
                                .map(
                                    String::trim,
                                )
                                .toSet()
                    }
                    .joinToString(","),
            )
            .putBoolean(
                "private_screenshot_enabled",
                s.privateScreenshotEnabled,
            )
            .putBoolean(
                "collective_stats_opt_in",
                s.collectiveStatsOptIn,
            )
            .putString(
                "default_passenger_message",
                s.defaultPassengerMessage
                    .take(600),
            )
            .apply()
    }

    fun costSnapshot(): CostSnapshot =
        CostSnapshot(
            costPerKm =
                double(
                    "cost_per_km",
                    0.85,
                ),
            source =
                prefs.getString(
                    "cost_snapshot_source",
                    "legacy_setting",
                ) ?: "legacy_setting",
            version =
                prefs.getString(
                    "cost_snapshot_version",
                    "legacy_pre_018",
                ) ?: "legacy_pre_018",
            profileUpdatedAt =
                prefs.getString(
                    "cost_profile_updated_at",
                    "",
                ) ?: "",
        )

    fun saveCostSnapshot(
        costPerKm: Double,
        source: String,
        version: String,
        profileUpdatedAt: String,
    ) {
        prefs.edit()
            .putString(
                "cost_per_km",
                costPerKm
                    .coerceAtLeast(0.0)
                    .toString(),
            )
            .putString(
                "cost_snapshot_source",
                source.take(80),
            )
            .putString(
                "cost_snapshot_version",
                version.take(80),
            )
            .putString(
                "cost_profile_updated_at",
                profileUpdatedAt
                    .take(80),
            )
            .apply()
    }

    fun saveAccountSession(
        token: String,
        email: String,
        displayName: String,
    ) {
        prefs.edit()
            .putString(
                "device_token",
                token,
            )
            .putString(
                "account_email",
                email.trim()
                    .lowercase(),
            )
            .putString(
                "driver_display_name",
                displayName
                    .trim()
                    .take(80)
                    .ifBlank {
                        "Motorista"
                    },
            )
            .apply()

        // Novo login/dispositivo deve receber o perfil pessoal antes
        // de começar uma jornada com um custo legado/local incorreto.
        CostProfileSync.refreshOrFlush(appContext)
    }

    fun updateAccountIdentity(
        email: String?,
        displayName: String?,
    ) {
        val edit =
            prefs.edit()
        if (email != null) {
            edit.putString(
                "account_email",
                email.trim()
                    .lowercase(),
            )
        }
        if (displayName != null) {
            edit.putString(
                "driver_display_name",
                displayName
                    .trim()
                    .take(80)
                    .ifBlank {
                        "Motorista"
                    },
            )
        }
        edit.apply()
    }

    fun clearAccountSession() {
        runCatching {
            CostProfileStore
                .get(appContext)
                .clear()
        }
        prefs.edit()
            .remove("device_token")
            .remove("account_email")
            .putBoolean(
                "onboarding_completed",
                false,
            )
            .putInt(
                "onboarding_step",
                1,
            )
            .putString(
                "cost_per_km",
                "0.85",
            )
            .putString(
                "cost_snapshot_source",
                "legacy_setting",
            )
            .putString(
                "cost_snapshot_version",
                "legacy_pre_018",
            )
            .putString(
                "cost_profile_updated_at",
                "",
            )
            .apply()
    }

    fun markOnboardingStep(
        step: Int,
    ) {
        prefs.edit()
            .putInt(
                "onboarding_step",
                step.coerceIn(0, 5),
            )
            .apply()
    }

    fun completeOnboarding() {
        prefs.edit()
            .putBoolean(
                "onboarding_completed",
                true,
            )
            .putInt(
                "onboarding_step",
                5,
            )
            .apply()
    }

    fun restartOnboarding() {
        prefs.edit()
            .putBoolean(
                "onboarding_completed",
                false,
            )
            .putInt(
                "onboarding_step",
                0,
            )
            .apply()
    }

    fun saveHudPosition(
        x: Int,
        y: Int,
    ) {
        prefs.edit()
            .putInt(
                "hud_custom_x",
                x,
            )
            .putInt(
                "hud_custom_y",
                y,
            )
            .putBoolean(
                "hud_custom_position",
                true,
            )
            .apply()
    }

    fun loadHudPosition():
        Pair<Int, Int>? {
        if (
            !prefs.getBoolean(
                "hud_custom_position",
                false,
            )
        ) {
            return null
        }
        return prefs.getInt(
            "hud_custom_x",
            0,
        ) to
            prefs.getInt(
                "hud_custom_y",
                0,
            )
    }

    fun resetHudPosition() {
        prefs.edit()
            .remove("hud_custom_x")
            .remove("hud_custom_y")
            .putBoolean(
                "hud_custom_position",
                false,
            )
            .apply()
    }

    private fun migrateV05HudDefaults() {
        if (
            prefs.getBoolean(
                "hud_v05_migrated",
                false,
            )
        ) {
            return
        }

        val oldOrder =
            "per_hour,rating,per_minute,per_km,profit_hour,profit_percent,profit"
        val oldEnabled =
            "per_hour,rating,per_minute,per_km"
        val edit =
            prefs.edit()

        if (
            prefs.getInt(
                "hud_font_size",
                13,
            ) <= 13
        ) {
            edit.putInt(
                "hud_font_size",
                16,
            )
        }

        if (
            (
                prefs.getString(
                    "hud_metric_order",
                    oldOrder,
                ) ?: oldOrder
                ) == oldOrder
        ) {
            edit.putString(
                "hud_metric_order",
                DriverSettings()
                    .hudMetricOrder,
            )
        }

        if (
            (
                prefs.getString(
                    "hud_enabled_metrics",
                    oldEnabled,
                ) ?: oldEnabled
                ) == oldEnabled
        ) {
            edit.putString(
                "hud_enabled_metrics",
                DriverSettings()
                    .hudEnabledMetrics,
            )
        }

        edit.putBoolean(
            "hud_v05_migrated",
            true,
        ).apply()
    }

    private fun migrateV06VisualDefaults() {
        if (
            prefs.getBoolean(
                "hud_v06_migrated",
                false,
            )
        ) {
            return
        }

        val font =
            prefs.getInt(
                "hud_font_size",
                16,
            )

        val size =
            when {
                font <= 15 ->
                    "compact"
                font >= 19 ->
                    "large"
                else ->
                    "normal"
            }

        val oldTheme =
            prefs.getString(
                "hud_theme",
                "light",
            ) ?: "light"

        val migratedTheme =
            if (
                oldTheme ==
                "green"
            ) {
                "dark"
            } else {
                oldTheme
            }

        prefs.edit()
            .putString(
                "hud_card_size",
                prefs.getString(
                    "hud_card_size",
                    size,
                ) ?: size,
            )
            .putString(
                "hud_theme",
                normalizeTheme(
                    migratedTheme,
                ),
            )
            .putBoolean(
                "hud_dismiss_on_tap",
                true,
            )
            .putBoolean(
                "hud_drag_enabled",
                true,
            )
            .putBoolean(
                "hud_v06_migrated",
                true,
            )
            .apply()
    }

    private fun migrateV07OnboardingDefaults() {
        if (
            prefs.getBoolean(
                "onboarding_v07_migrated",
                false,
            )
        ) {
            return
        }

        val alreadyPaired =
            !(
                prefs.getString(
                    "device_token",
                    "",
                ) ?: ""
                ).isBlank()

        prefs.edit()
            .putString(
                "driver_display_name",
                prefs.getString(
                    "driver_display_name",
                    "Motorista",
                ) ?: "Motorista",
            )
            .putString(
                "account_email",
                prefs.getString(
                    "account_email",
                    "",
                ) ?: "",
            )
            .putInt(
                "onboarding_step",
                if (alreadyPaired) {
                    2
                } else {
                    0
                },
            )
            .putBoolean(
                "onboarding_completed",
                false,
            )
            .putBoolean(
                "onboarding_v07_migrated",
                true,
            )
            .apply()
    }

    private fun migrateV12CanonicalDomain() {
        if (
            prefs.getBoolean(
                "domain_v12_migrated",
                false,
            )
        ) {
            return
        }

        val current =
            prefs.getString(
                "backend_url",
                DEFAULT_BACKEND_URL,
            )?.trim()
                ?.trimEnd('/')
                ?: DEFAULT_BACKEND_URL

        val migrated =
            when (current) {
                "https://sr-rotas.vercel.app",
                "https://www.srrotas.com" ->
                    DEFAULT_BACKEND_URL
                else ->
                    current.ifBlank {
                        DEFAULT_BACKEND_URL
                    }
            }

        prefs.edit()
            .putString(
                "backend_url",
                migrated,
            )
            .putBoolean(
                "domain_v12_migrated",
                true,
            )
            .apply()
    }

    private fun migrateV0133VoiceDefaults() {
        if (
            prefs.getBoolean(
                "voice_v0133_migrated",
                false,
            )
        ) {
            return
        }

        val defaults =
            DriverSettings()
        val edit =
            prefs.edit()

        if (
            !prefs.contains(
                "voice_follow_hud_order",
            )
        ) {
            edit.putBoolean(
                "voice_follow_hud_order",
                true,
            )
        }
        if (
            !prefs.contains(
                "voice_metric_order",
            )
        ) {
            edit.putString(
                "voice_metric_order",
                defaults.voiceMetricOrder,
            )
        }
        if (
            !prefs.contains(
                "voice_enabled_metrics",
            )
        ) {
            edit.putString(
                "voice_enabled_metrics",
                defaults.voiceEnabledMetrics,
            )
        }

        edit.putBoolean(
            "voice_v0133_migrated",
            true,
        ).apply()
    }

    private fun migrateV018CostSnapshot() {
        if (
            prefs.getBoolean(
                "cost_snapshot_v018_migrated",
                false,
            )
        ) {
            return
        }

        val edit =
            prefs.edit()

        if (
            !prefs.contains(
                "cost_snapshot_source",
            )
        ) {
            edit.putString(
                "cost_snapshot_source",
                "legacy_setting",
            )
        }
        if (
            !prefs.contains(
                "cost_snapshot_version",
            )
        ) {
            edit.putString(
                "cost_snapshot_version",
                "legacy_pre_018",
            )
        }
        if (
            !prefs.contains(
                "cost_profile_updated_at",
            )
        ) {
            edit.putString(
                "cost_profile_updated_at",
                "",
            )
        }

        edit.putBoolean(
            "cost_snapshot_v018_migrated",
            true,
        ).apply()
    }

    fun clearAllUserData() {
        runCatching {
            HistoricalImportStore
                .get(appContext)
                .clearAll()
        }
        runCatching {
            CostProfileStore
                .get(appContext)
                .clear()
        }
        prefs.edit()
            .clear()
            .apply()
    }

    private fun normalizeTheme(
        v: String,
    ) =
        when (v) {
            "light",
            "dark",
            "auto" ->
                v
            else ->
                "auto"
        }

    private fun normalizeSize(
        v: String,
    ) =
        when (v) {
            "compact",
            "normal",
            "large" ->
                v
            else ->
                "normal"
        }

    private fun double(
        key: String,
        fallback: Double,
    ) =
        prefs.getString(
            key,
            fallback.toString(),
        )?.toDoubleOrNull()
            ?: fallback

    fun saveDeviceToken(
        token: String,
    ) {
        prefs.edit()
            .putString(
                "device_token",
                token,
            )
            .apply()

        CostProfileSync.refreshOrFlush(appContext)
    }

    fun saveLatestCapture(
        summary: String,
        raw: String,
        method: String,
    ) {
        prefs.edit()
            .putString(
                "latest_summary",
                summary,
            )
            .putString(
                "latest_raw",
                raw.take(12000),
            )
            .putString(
                "latest_method",
                method,
            )
            .putLong(
                "latest_at_ms",
                System.currentTimeMillis(),
            )
            .apply()
    }

    fun latestSummary(): String =
        prefs.getString(
            "latest_summary",
            "Nenhuma oferta reconhecida ainda.",
        ) ?: "Nenhuma oferta reconhecida ainda."

    fun latestRaw(): String =
        prefs.getString(
            "latest_raw",
            "",
        ) ?: ""

    fun latestMethod(): String =
        prefs.getString(
            "latest_method",
            "",
        ) ?: ""

    fun setProjectionActive(
        active: Boolean,
    ) {
        prefs.edit()
            .putBoolean(
                "projection_active",
                active,
            )
            .apply()
    }

    fun isProjectionActive(): Boolean =
        prefs.getBoolean(
            "projection_active",
            false,
        )

    fun setCurrentJourney(
        id: String,
        startedAt: String,
    ) {
        prefs.edit()
            .putString(
                "current_journey_id",
                id,
            )
            .putString(
                "current_journey_started_at",
                startedAt,
            )
            .apply()
    }

    fun currentJourneyId(): String =
        prefs.getString(
            "current_journey_id",
            "",
        ) ?: ""

    fun currentJourneyStartedAt(): String =
        prefs.getString(
            "current_journey_started_at",
            "",
        ) ?: ""

    fun clearCurrentJourney() {
        prefs.edit()
            .remove(
                "current_journey_id",
            )
            .remove(
                "current_journey_started_at",
            )
            .apply()
    }
}
