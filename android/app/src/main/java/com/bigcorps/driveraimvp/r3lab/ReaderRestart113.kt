package com.srrotas.app.r3lab

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Process
import com.srrotas.app.MainActivity

/**
 * Issue #10 — reinício seguro do leitor com OCR travado (fail-closed). Uma tarefa ML Kit que nunca
 * conclui só é liberada com a morte do processo; criar outro reconhecedor por cima seria um segundo
 * OCR concorrente. Por isso, a pedido explícito do motorista, o app é reiniciado por uma atividade
 * mínima em processo próprio (`:restart113`), que encerra o processo principal e reabre o Agora.
 * A jornada (SQLite + preferências) continua aberta; a Acessibilidade é religada pelo Android; o
 * Histórico não é tocado. Não há reinício automático.
 */
object ReaderRestart113 {
    const val EXTRA_PID = "sr_restart_main_pid"
    const val EXTRA_RESTARTED = "sr_reader_restarted_113"

    fun restart(activity: Activity) {
        val app = activity.applicationContext
        runCatching { R3LabTelemetry.persist(app, sync = true) }
        // O fallback "OCR travado" desta jornada é desfeito: após o reinício o R3 volta a ser o leitor
        // (o processo novo não tem a tarefa presa). Outros motivos de fallback são preservados.
        runCatching {
            val journeyId = com.srrotas.app.SettingsRepository(app).currentJourneyId()
            if (R3Mode.fallbackActive(app, journeyId) && R3Mode.fallback(app)?.reason == "ocr_stuck") R3Mode.clearFallback(app, sync = true)
        }
        // commit(): grava antes de o processo morrer.
        app.getSharedPreferences("reader_health_113", Context.MODE_PRIVATE).edit()
            .putLong("restart_requested_at", System.currentTimeMillis())
            .putInt("restart_requested_count", app.getSharedPreferences("reader_health_113", Context.MODE_PRIVATE).getInt("restart_requested_count", 0) + 1)
            .commit()
        activity.startActivity(Intent(activity, ReaderRestartActivity113::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra(EXTRA_PID, Process.myPid()))
        activity.finishAffinity()
    }
}

/** Roda em `:restart113`: encerra o processo principal e reabre o app no Agora. */
class ReaderRestartActivity113 : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val mainPid = intent.getIntExtra(ReaderRestart113.EXTRA_PID, -1)
        if (mainPid > 0 && mainPid != Process.myPid()) Process.killProcess(mainPid)
        startActivity(Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            .putExtra(MainActivity.EXTRA_BUBBLE_ACTION, MainActivity.BUBBLE_ACTION_NOW)
            .putExtra(ReaderRestart113.EXTRA_RESTARTED, true))
        finish()
        Runtime.getRuntime().exit(0)
    }
}
