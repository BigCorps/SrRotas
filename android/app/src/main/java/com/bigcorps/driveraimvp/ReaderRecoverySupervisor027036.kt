package com.srrotas.app

import android.app.Application
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.SystemClock

/**
 * Supervisor do M1.
 *
 * 0.31.1: além da recuperação técnica enquanto a MediaProjection ainda existe,
 * sincroniza o estado de Capture Resilience. Se a projeção acabou, não tenta
 * reutilizar autorização; apenas mantém a jornada aberta e deixa UI/notificação
 * solicitarem novo consentimento do usuário.
 */
object ReaderRecoverySupervisor027036 {
    private const val CHECK_MS = 2_500L
    private const val UNHEALTHY_GRACE_MS = 15_000L
    private const val RECOVERY_COOLDOWN_MS = 35_000L
    private val main = Handler(Looper.getMainLooper())
    private var app: Application? = null
    private var unhealthySince = 0L
    private var lastRecovery = 0L

    fun install(application: Application) {
        if (app != null) return
        app = application
        main.post(checker)
    }

    private val checker = object : Runnable {
        override fun run() {
            val context = app ?: return
            runCatching {
                CaptureResilience0311.sync(context)

                if (!ReaderLab027036.m1Enabled(context)) {
                    unhealthySince = 0L
                    return@runCatching
                }
                ReaderLab027036.pollOfficialM1(context)
                val repo = SettingsRepository(context)
                val journeyId = repo.currentJourneyId().takeIf(String::isNotBlank)
                val projectionActive = repo.isProjectionActive()

                // Sem uma MediaProjection ativa o Android exige nova autorização.
                // Não enviamos ACTION_RECOVER para um serviço inexistente/token morto.
                if (journeyId == null || !projectionActive) {
                    unhealthySince = 0L
                    return@runCatching
                }

                val healthy = CaptureHealthState0263.isHealthy(context, journeyId, projectionActive)
                val now = SystemClock.elapsedRealtime()
                if (healthy) {
                    unhealthySince = 0L
                } else {
                    if (unhealthySince == 0L) unhealthySince = now
                    if (now - unhealthySince >= UNHEALTHY_GRACE_MS && now - lastRecovery >= RECOVERY_COOLDOWN_MS) {
                        lastRecovery = now
                        val intent = Intent(context, MediaProjectionOcrService::class.java).apply {
                            action = MediaProjectionOcrService.ACTION_RECOVER
                        }
                        runCatching { context.startService(intent) }
                            .onSuccess { LocalLog.append(context, "0.31.1 supervisor solicitou recuperação técnica do M1") }
                            .onFailure { LocalLog.append(context, "0.31.1 supervisor falhou ao recuperar M1: ${it.message}") }
                    }
                }
            }.onFailure { LocalLog.append(context, "0.31.1 supervisor ignorou falha isolada: ${it.message}") }
            main.postDelayed(this, CHECK_MS)
        }
    }
}
