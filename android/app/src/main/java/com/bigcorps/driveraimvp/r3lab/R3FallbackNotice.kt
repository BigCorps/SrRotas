package com.srrotas.app.r3lab

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.os.Build

/**
 * vc112: aviso de fallback R3 → M1. O Android exige nova autorização de captura para cada sessão de
 * MediaProjection (Android 14+); por isso o M1 só começa quando o motorista toca e autoriza.
 */
object R3FallbackNotice {
    private const val CHANNEL = "sr_r3_fallback"
    private const val ID = 15190

    fun message(reason: String) = "Leitor R3 indisponível (${reasonText(reason)}). Toque para ativar a leitura M1 desta jornada."

    fun reasonText(reason: String) = when (reason) {
        "accessibility_disconnected" -> "Acessibilidade desligada"
        "ocr_stuck" -> "OCR travado"
        "screenshot_failures" -> "capturas falhando"
        "no_screenshot_with_target" -> "sem capturas da janela Uber/99"
        else -> reason
    }

    fun show(context: Context, reason: String) {
        val app = context.applicationContext
        val nm = app.getSystemService(NotificationManager::class.java)
        val blocked = when {
            nm == null -> "no_manager"
            Build.VERSION.SDK_INT >= 33 && app.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED -> "permission_denied"
            !nm.areNotificationsEnabled() -> "notifications_disabled"
            else -> null
        }
        runCatching { android.widget.Toast.makeText(app, message(reason), android.widget.Toast.LENGTH_LONG).show() }
        if (blocked != null || nm == null) { ReaderHeartbeat113.notice(app, "fallback_$reason", false, blocked); return }
        if (Build.VERSION.SDK_INT >= 26) nm.createNotificationChannel(NotificationChannel(CHANNEL, "Leitor de ofertas", NotificationManager.IMPORTANCE_HIGH))
        // Issue #10: abre a recuperação DESTA jornada (nunca a ação ambígua de "Iniciar").
        val intent = JourneyReaderRecoveryActivity113.intent(app)
        val pending = PendingIntent.getActivity(app, ID, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val builder = if (Build.VERSION.SDK_INT >= 26) android.app.Notification.Builder(app, CHANNEL) else @Suppress("DEPRECATION") android.app.Notification.Builder(app)
        val posted = runCatching {
            nm.notify(ID, builder.setSmallIcon(android.R.drawable.stat_notify_error).setContentTitle("Sr. Rotas — leitor de ofertas")
                .setContentText(message(reason)).setStyle(android.app.Notification.BigTextStyle().bigText(message(reason)))
                .setContentIntent(pending).setAutoCancel(true).build())
        }.isSuccess
        ReaderHeartbeat113.notice(app, "fallback_$reason", posted, if (posted) null else "notify_failed")
    }
}
