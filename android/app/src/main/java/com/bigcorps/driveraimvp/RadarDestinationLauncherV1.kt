package com.srrotas.app

import android.app.ActivityOptions
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

/**
 * Único caminho de abertura do Radar a partir do HUD/overlay.
 *
 * O app targeta API 36. Android 14+ exige opt-in explícito para background
 * activity launch via PendingIntent; Android 15+ também exige opt-in do creator.
 * Este launcher só é chamado após ação explícita do usuário (toque em VER/CTA).
 */
object RadarDestinationLauncherV1 {
    private const val REQUEST_REAL = 4901
    private const val REQUEST_DEMO = 4902

    fun openRadar(context: Context, opportunityId: String? = null, source:String="hud_cta"): Boolean =
        open(context, demo = false, opportunityId = opportunityId, source=source)

    fun openDemo(context: Context): Boolean =
        open(context, demo = true, opportunityId = null, source="assistant_demo")

    private fun open(
        context: Context,
        demo: Boolean,
        opportunityId: String?,
        source:String,
    ): Boolean {
        val app = context.applicationContext
        val kind = if (demo) "demo" else "real"
        RadarContextualDiagnosticV1.launchAttempt(kind)

        val intent = Intent(app, MainActivity::class.java).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP,
            )
            putExtra(
                MainActivity.EXTRA_BUBBLE_ACTION,
                if (demo) MainActivity.BUBBLE_ACTION_RADAR_DEMO
                else MainActivity.BUBBLE_ACTION_RADAR,
            )
            putExtra("sr_radar_open_source",source)
            if (!opportunityId.isNullOrBlank()) {
                putExtra(MainActivity.EXTRA_RADAR_OPPORTUNITY_ID, opportunityId)
            }
        }

        val creatorOptions = ActivityOptions.makeBasic().apply {
            if (Build.VERSION.SDK_INT >= 34) {
                setPendingIntentCreatorBackgroundActivityStartMode(
                    ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED,
                )
            }
        }
        val pending = PendingIntent.getActivity(
            app,
            if (demo) REQUEST_DEMO else REQUEST_REAL,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            creatorOptions.toBundle(),
        )

        return runCatching {
            if (Build.VERSION.SDK_INT >= 34) {
                val senderOptions = ActivityOptions.makeBasic().apply {
                    setPendingIntentBackgroundActivityStartMode(
                        ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED,
                    )
                }
                pending.send(
                    app,
                    0,
                    null,
                    null,
                    null,
                    null,
                    senderOptions.toBundle(),
                )
            } else {
                pending.send()
            }
            RadarContextualDiagnosticV1.launchSent(kind)
            true
        }.getOrElse {
            RadarContextualDiagnosticV1.launchFailed(kind, it)
            LocalLog.append(app, "Radar launcher falhou ($kind): ${it.message}")
            false
        }
    }
}
