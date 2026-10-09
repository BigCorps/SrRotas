package com.srrotas.app

import android.app.ActivityManager
import android.content.Context
import android.widget.Toast

/** Conservative process-wide exclusion; no changes to live workers. Once a foreign OCR
 * has been requested, its outstanding ML Kit tasks cannot be proven drained: require a
 * manually restarted process. Never release a rescan lease on a UI timeout/destroy. */
internal class ScreenshotRescanLeaseV1 {
    var foreignRequested = false; private set
    var busy = false; private set
    @Synchronized fun foreignStart(): Boolean {
        if(busy) return false
        foreignRequested = true
        return true
    }
    @Synchronized fun acquire(blocked: Boolean): Boolean {
        if(blocked || busy || foreignRequested) return false
        busy = true
        return true
    }
    @Synchronized fun complete() { busy = false }
}

object ScreenshotRescanGateV1 {
    private val lease = ScreenshotRescanLeaseV1()
    fun enabled(context: Context) = BuildConfig.VERSION_NAME.contains("field") &&
        context.getSharedPreferences("field_rescan_v1",0).getBoolean("enabled",true)
    fun setEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences("field_rescan_v1",0).edit().putBoolean("enabled",enabled).apply()
    }
    fun allowForeignStart(context: Context): Boolean {
        if(lease.foreignStart()) return true
        Toast.makeText(context,"Aguarde o Foto / Rescan terminar antes de iniciar leitura.",Toast.LENGTH_LONG).show()
        return false
    }
    fun seed(context: Context) {
        if(SettingsRepository(context).currentJourneyId().isNotBlank()) lease.foreignStart()
    }
    @Suppress("DEPRECATION")
    private fun liveBlocked(context: Context): Boolean {
        val repo = SettingsRepository(context)
        return repo.isProjectionActive() || repo.currentJourneyId().isNotBlank() ||
            ReaderLab027036.mode(context) != ReaderLab027036.MODE_M1 ||
            context.getSystemService(ActivityManager::class.java).getRunningServices(Int.MAX_VALUE).any {
                it.service.className.endsWith("MediaProjectionOcrService") ||
                    it.service.className.endsWith("UberDigitizationCaptureService026")
            }
    }
    fun acquire(context: Context): Boolean = lease.acquire(!enabled(context) || liveBlocked(context))
    fun complete() = lease.complete()
    const val BLOCK_MESSAGE = "Rescan não iniciou: encerre a jornada, use M1 e reinicie o app manualmente antes do rescan. Nenhuma captura foi interrompida."
}
