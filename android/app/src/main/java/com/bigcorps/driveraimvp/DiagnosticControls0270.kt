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
import org.json.JSONObject

/** Ações de campo disponíveis pelo botão de bug do HUD. */
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

    fun restartReading(context: Context) {
        if (ReaderLab027036.mode(context) == ReaderLab027036.MODE_M2) {
            Toast.makeText(context, "M2 isolado usa Acessibilidade e não MediaProjection.", Toast.LENGTH_SHORT).show()
            return
        }
        CaptureRecoveryActivity0270.open(context)
    }

    /** RC3.7: existe um único exportador oficial de diagnóstico. */
    fun exportDiagnostic(context: Context) {
        ReaderLabCombinedDiagnostic0270361.share(context)
    }
}

/**
 * Reautoriza MediaProjection preservando a jornada M1/Comparativa atual.
 *
 * 0.31.1 registra pedido/autorização/cancelamento/falha. A retomada só é
 * considerada concluída quando o estado oficial voltar a projectionActive=true.
 */
class CaptureRecoveryActivity0270 : Activity() {
    companion object {
        private const val REQ_CAPTURE = 2710

        fun open(context: Context) {
            if (ReaderLab027036.mode(context) == ReaderLab027036.MODE_M2) {
                Toast.makeText(context, "M2 isolado: confira a Acessibilidade em vez da captura de tela.", Toast.LENGTH_SHORT).show()
                return
            }
            val intent = Intent(context, CaptureRecoveryActivity0270::class.java)
            if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }

    private lateinit var projectionManager: MediaProjectionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (ReaderLab027036.mode(this) == ReaderLab027036.MODE_M2) {
            Toast.makeText(this, "M2 isolado não usa MediaProjection.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        val currentJourney = SettingsRepository(this).currentJourneyId()
            .takeIf(String::isNotBlank)
            ?.let { LocalStore.get(this).journey(it) }
            ?.takeIf { it.endedAt == null }
        if (currentJourney == null) {
            Toast.makeText(this, "Não há jornada aberta para recuperar.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        CaptureResilience0311.markResumeRequested(this, "recovery_activity")
        projectionManager = getSystemService(MediaProjectionManager::class.java)
        val captureIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            projectionManager.createScreenCaptureIntent(MediaProjectionConfig.createConfigForUserChoice())
        } else {
            @Suppress("DEPRECATION")
            projectionManager.createScreenCaptureIntent()
        }
        Toast.makeText(
            this,
            "A jornada continua aberta. Autorize novamente a captura para retomar a leitura.",
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
            CaptureResilience0311.markResumeCancelled(this)
            Toast.makeText(this, "Retomada cancelada. A jornada continua aberta.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        CaptureResilience0311.markResumeAuthorized(this)
        val service = Intent(this, MediaProjectionOcrService::class.java).apply {
            action = MediaProjectionOcrService.ACTION_START
            putExtra(MediaProjectionOcrService.EXTRA_RESULT_CODE, resultCode)
            putExtra(MediaProjectionOcrService.EXTRA_RESULT_DATA, data)
        }
        val failure = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(service) else startService(service)
        }.exceptionOrNull()
        if (failure == null) {
            Toast.makeText(this, "Retomando leitura na mesma jornada…", Toast.LENGTH_SHORT).show()
        } else {
            CaptureResilience0311.markResumeFailed(this, "service_start_failed")
            Toast.makeText(this, "Não foi possível reativar a leitura: ${failure.message ?: "erro do Android"}", Toast.LENGTH_LONG).show()
        }
        finish()
    }
}

/** Activity ponte para o único diagnóstico combinado M1/M2. */
class DiagnosticExportActivity0270 : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        runCatching { ReaderLabCombinedDiagnostic0270361.share(this) }
            .onFailure { Toast.makeText(this, "Não foi possível gerar o diagnóstico.", Toast.LENGTH_SHORT).show() }
        Handler(Looper.getMainLooper()).postDelayed({ finish() }, 250L)
    }
}

class DiagnosticActionReceiver0270 : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == DiagnosticNotification0270.ACTION_REPORT_FAILURE) {
            DiagnosticQuickActions0270.reportFailure(context)
        }
    }
}

class CaptureDiagnosticReceiver0270 : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != AppSignals.ACTION_CAPTURE_UPDATED) return
        val pending = goAsync()
        Handler(Looper.getMainLooper()).postDelayed({
            runCatching {
                CaptureResilience0311.sync(context.applicationContext)
                DiagnosticNotification0270.sync(context.applicationContext)
            }
            pending.finish()
        }, 1_200L)
    }
}

/**
 * Notificação independente do FGS de captura.
 *
 * 0.33.2: recovery usa canal próprio de alta prioridade. O canal antigo
 * `sr_rotas_projection` é LOW e não pode ter sua importância elevada depois
 * que o Android o cria pela primeira vez. Se a MediaProjection morrer, esta
 * notificação continua fora do serviço OCR e leva diretamente à reautorização.
 */
object DiagnosticNotification0270 {
    const val ACTION_REPORT_FAILURE = "com.srrotas.app.action.DIAGNOSTIC_REPORT_FAILURE"

    private const val CHANNEL_ID = "sr_rotas_capture_recovery_0332"
    private const val NOTIFICATION_ID = 2704
    private const val PREFS = "sr_capture_recovery_notification_0332"
    private const val KEY_VISIBLE_REQUESTED = "visible_requested"
    private const val KEY_LAST_POSTED_AT = "last_posted_at_ms"
    private const val KEY_LAST_CANCELLED_AT = "last_cancelled_at_ms"
    private const val KEY_POSTED_EPISODES = "posted_episodes"
    private const val KEY_CANCELLED_EPISODES = "cancelled_episodes"
    private const val KEY_FAILURES = "notify_failures"

    fun sync(context: Context) {
        CaptureResilience0311.sync(context)
        if (ReaderLab027036.mode(context) == ReaderLab027036.MODE_M2) {
            cancel(context)
            return
        }
        val repo = SettingsRepository(context)
        val id = repo.currentJourneyId().takeIf(String::isNotBlank)
        val openJourney = id?.let { LocalStore.get(context).journey(it) }?.takeIf { it.endedAt == null }
        val activeState =
            openJourney != null &&
                JourneyCoordinator.snapshot(context).journeyState == JourneyOperationalState.ACTIVE

        if (!activeState || !CaptureResilience0311.needsRecovery(context)) {
            cancel(context)
            return
        }
        showCaptureInterrupted(context)
    }

    fun cancel(context: Context) {
        val app = context.applicationContext
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val wasVisibleRequested = prefs.getBoolean(KEY_VISIBLE_REQUESTED, false)
        runCatching { app.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID) }
        if (wasVisibleRequested) {
            prefs.edit()
                .putBoolean(KEY_VISIBLE_REQUESTED, false)
                .putLong(KEY_LAST_CANCELLED_AT, System.currentTimeMillis())
                .putInt(KEY_CANCELLED_EPISODES, prefs.getInt(KEY_CANCELLED_EPISODES, 0) + 1)
                .apply()
        }
    }

    fun toJson(context: Context): JSONObject {
        val app = context.applicationContext
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val manager = app.getSystemService(NotificationManager::class.java)
        val active = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            runCatching { manager.activeNotifications.any { it.id == NOTIFICATION_ID } }.getOrDefault(false)
        } else {
            prefs.getBoolean(KEY_VISIBLE_REQUESTED, false)
        }
        return JSONObject().apply {
            put("schema", "sr-capture-recovery-notification-0332-v1")
            put("channel_id", CHANNEL_ID)
            put("importance", "high")
            put("notifications_enabled", runCatching { manager.areNotificationsEnabled() }.getOrDefault(false))
            put("visible_requested", prefs.getBoolean(KEY_VISIBLE_REQUESTED, false))
            put("notification_active", active)
            put("posted_episodes", prefs.getInt(KEY_POSTED_EPISODES, 0))
            put("cancelled_episodes", prefs.getInt(KEY_CANCELLED_EPISODES, 0))
            put("notify_failures", prefs.getInt(KEY_FAILURES, 0))
            put("last_posted_at_ms", prefs.getLong(KEY_LAST_POSTED_AT, 0L))
            put("last_cancelled_at_ms", prefs.getLong(KEY_LAST_CANCELLED_AT, 0L))
            put("fresh_consent_required", true)
            put("silent_projection_restart", false)
        }
    }

    private fun showCaptureInterrupted(context: Context) {
        val app = context.applicationContext
        val manager = app.getSystemService(NotificationManager::class.java)
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Sr. Rotas — recuperar captura",
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply {
                    description = "Avisa quando a leitura de ofertas parou durante uma jornada ativa."
                    setShowBadge(true)
                    enableVibration(false)
                    setSound(null, null)
                    setBypassDnd(false)
                },
            )
        }

        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val recover = PendingIntent.getActivity(
            app,
            2706,
            Intent(app, CaptureRecoveryActivity0270::class.java),
            flags,
        )
        val report = PendingIntent.getBroadcast(
            app,
            2705,
            Intent(app, DiagnosticActionReceiver0270::class.java)
                .setAction(ACTION_REPORT_FAILURE),
            flags,
        )
        val export = PendingIntent.getActivity(
            app,
            2707,
            Intent(app, DiagnosticExportActivity0270::class.java),
            flags,
        )

        val notification = Notification.Builder(app, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("Sr. Rotas — leitura pausada")
            .setContentText("A jornada continua. Toque para reativar a captura.")
            .setContentIntent(recover)
            .addAction(android.R.drawable.ic_media_play, "Retomar captura", recover)
            .addAction(android.R.drawable.ic_menu_share, "Diagnóstico", export)
            .addAction(android.R.drawable.ic_menu_info_details, "Reportar falha", report)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_ERROR)
            .setPriority(Notification.PRIORITY_HIGH)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .build()

        val firstInEpisode = !prefs.getBoolean(KEY_VISIBLE_REQUESTED, false)
        runCatching { manager.notify(NOTIFICATION_ID, notification) }
            .onSuccess {
                if (firstInEpisode) {
                    prefs.edit()
                        .putBoolean(KEY_VISIBLE_REQUESTED, true)
                        .putLong(KEY_LAST_POSTED_AT, System.currentTimeMillis())
                        .putInt(KEY_POSTED_EPISODES, prefs.getInt(KEY_POSTED_EPISODES, 0) + 1)
                        .apply()
                }
            }
            .onFailure {
                prefs.edit()
                    .putInt(KEY_FAILURES, prefs.getInt(KEY_FAILURES, 0) + 1)
                    .apply()
            }
    }
}
