package com.srrotas.app

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionConfig
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast

/** Ações provisórias de campo da RC3, disponíveis pelo botão de bug do HUD. */
object DiagnosticQuickActions0270 {
    fun reportFailure(context: Context) {
        val app = context.applicationContext
        RadarHudTrace024.markManualFailure(app, "hud_bug")
        val mark = FailureReportStore0270.mark(app, "hud_bug")
        Toast.makeText(
            app,
            "Falha #${mark.numberInJourney} registrada. Pode reportar outra imediatamente; exporte uma vez no fim.",
            Toast.LENGTH_LONG,
        ).show()
    }

    /** Reabre a autorização da captura preservando a jornada atual. */
    fun restartReading(context: Context) {
        CaptureRecoveryActivity0270.open(context)
    }

    fun exportDiagnostic(context: Context) {
        DiagnosticBundle.share(context)
    }
}

/**
 * Reautoriza MediaProjection sem criar nova jornada. É intencionalmente uma
 * Activity: Android 14+ exige consentimento novo quando a projeção anterior morre.
 */
class CaptureRecoveryActivity0270 : Activity() {
    companion object {
        private const val REQ_CAPTURE = 2710

        fun open(context: Context) {
            val intent = Intent(context, CaptureRecoveryActivity0270::class.java)
            if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }

    private lateinit var projectionManager: MediaProjectionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val currentJourney = SettingsRepository(this).currentJourneyId()
            .takeIf(String::isNotBlank)
            ?.let { LocalStore.get(this).journey(it) }
            ?.takeIf { it.endedAt == null }

        if (currentJourney == null) {
            Toast.makeText(this, "Não há jornada aberta para recuperar.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        projectionManager = getSystemService(MediaProjectionManager::class.java)
        val captureIntent =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                projectionManager.createScreenCaptureIntent(
                    MediaProjectionConfig.createConfigForUserChoice(),
                )
            } else {
                @Suppress("DEPRECATION")
                projectionManager.createScreenCaptureIntent()
            }

        Toast.makeText(
            this,
            "Reative a captura. Para Uber + 99 simultâneas, selecione a tela inteira.",
            Toast.LENGTH_LONG,
        ).show()
        @Suppress("DEPRECATION")
        startActivityForResult(captureIntent, REQ_CAPTURE)
    }

    @Deprecated("Mantido sem AndroidX")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQ_CAPTURE) return

        if (resultCode != RESULT_OK || data == null) {
            Toast.makeText(this, "Recuperação da leitura cancelada.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val service = Intent(this, MediaProjectionOcrService::class.java).apply {
            action = MediaProjectionOcrService.ACTION_START
            putExtra(MediaProjectionOcrService.EXTRA_RESULT_CODE, resultCode)
            putExtra(MediaProjectionOcrService.EXTRA_RESULT_DATA, data)
        }
        val failure = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(service)
            else startService(service)
        }.exceptionOrNull()

        if (failure == null) {
            Toast.makeText(this, "Leitura reativada na mesma jornada.", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(
                this,
                "Não foi possível reativar a leitura: ${failure.message ?: "erro do Android"}",
                Toast.LENGTH_LONG,
            ).show()
        }
        finish()
    }
}

/** Activity ponte para ação da notificação; abre o compartilhamento do JSON. */
class DiagnosticExportActivity0270 : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        runCatching { DiagnosticBundle.share(this) }
            .onFailure {
                Toast.makeText(
                    this,
                    "Não foi possível gerar o diagnóstico.",
                    Toast.LENGTH_SHORT,
                ).show()
            }
        Handler(Looper.getMainLooper()).postDelayed({ finish() }, 250L)
    }
}

/** Recebe o clique em Reportar falha da notificação de contingência. */
class DiagnosticActionReceiver0270 : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == DiagnosticNotification0270.ACTION_REPORT_FAILURE) {
            DiagnosticQuickActions0270.reportFailure(context)
        }
    }
}

/**
 * Observa a mudança da captura. Quando MediaProjection morre mas a jornada segue
 * ACTIVE, publica uma notificação própria que não depende do serviço de OCR.
 */
class CaptureDiagnosticReceiver0270 : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != AppSignals.ACTION_CAPTURE_UPDATED) return
        val pending = goAsync()
        Handler(Looper.getMainLooper()).postDelayed(
            {
                runCatching { DiagnosticNotification0270.sync(context.applicationContext) }
                pending.finish()
            },
            1_200L,
        )
    }
}

object DiagnosticNotification0270 {
    const val ACTION_REPORT_FAILURE = "com.srrotas.app.action.DIAGNOSTIC_REPORT_FAILURE"
    private const val CHANNEL_ID = "sr_rotas_projection"
    private const val NOTIFICATION_ID = 2704

    fun sync(context: Context) {
        val repo = SettingsRepository(context)
        val id = repo.currentJourneyId().takeIf(String::isNotBlank)
        val openJourney = id
            ?.let { LocalStore.get(context).journey(it) }
            ?.takeIf { it.endedAt == null }
        val activeState =
            openJourney != null &&
                JourneyCoordinator.snapshot(context).journeyState == JourneyOperationalState.ACTIVE

        if (repo.isProjectionActive() || !activeState) {
            cancel(context)
            return
        }
        showCaptureInterrupted(context)
    }

    fun cancel(context: Context) {
        runCatching {
            context.getSystemService(NotificationManager::class.java)
                .cancel(NOTIFICATION_ID)
        }
    }

    private fun showCaptureInterrupted(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Jornada ativa",
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = "Mantém as ferramentas da jornada e de diagnóstico acessíveis."
                    setShowBadge(false)
                },
            )
        }

        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val content = PendingIntent.getActivity(
            context,
            2704,
            Intent(context, MainActivity::class.java),
            flags,
        )
        val report = PendingIntent.getBroadcast(
            context,
            2705,
            Intent(context, DiagnosticActionReceiver0270::class.java)
                .setAction(ACTION_REPORT_FAILURE),
            flags,
        )
        val recover = PendingIntent.getActivity(
            context,
            2706,
            Intent(context, CaptureRecoveryActivity0270::class.java),
            flags,
        )
        val export = PendingIntent.getActivity(
            context,
            2707,
            Intent(context, DiagnosticExportActivity0270::class.java),
            flags,
        )

        val notification = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setContentTitle("Sr. Rotas — jornada ativa")
            .setContentText("Captura interrompida · jornada preservada. Reative a leitura.")
            .setContentIntent(content)
            .addAction(android.R.drawable.ic_menu_info_details, "Reportar falha", report)
            .addAction(android.R.drawable.ic_media_play, "Reativar leitura", recover)
            .addAction(android.R.drawable.ic_menu_share, "Diagnóstico", export)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_SERVICE)
            .build()

        runCatching { manager.notify(NOTIFICATION_ID, notification) }
    }
}
