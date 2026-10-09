package com.srrotas.app

import android.app.ActivityManager
import android.content.Context
import android.widget.Toast

/** Conservative process-wide exclusion; no changes to live workers. Once a foreign OCR
 * has been requested, its outstanding ML Kit tasks cannot be proven drained: require a
 * manually restarted process. Never release a rescan lease on a UI timeout/destroy. */
internal enum class ScreenshotRescanBlockV1(val message: String) {
    DISABLED("Rescan OCR desativado."),
    JOURNEY("Jornada ativa: somente visualização; encerre a jornada antes da releitura."),
    CAPTURE("Captura em andamento: somente visualização; nenhuma captura foi interrompida."),
    OTHER_OCR("Outro OCR em andamento: aguarde sua conclusão. Se travar, reinicie o app manualmente."),
    RESTART("Reinício de processo necessário: após encerrar a jornada, force a parada do app nas configurações Android e reabra para reler."),
    MODE("Releitura disponível somente em M1."),
    UNKNOWN("Não foi possível verificar a segurança do OCR; somente visualização."),
}

internal class ScreenshotRescanLeaseV1 {
    var foreignRequested = false; private set
    var busy = false; private set
    @Synchronized fun foreignStart(): Boolean {
        if(busy) return false
        foreignRequested = true
        return true
    }
    @Synchronized fun precheck(check: () -> ScreenshotRescanBlockV1?): ScreenshotRescanBlockV1? =
        check() ?: when {
            busy -> ScreenshotRescanBlockV1.OTHER_OCR
            foreignRequested -> ScreenshotRescanBlockV1.RESTART
            else -> null
        }
    @Synchronized fun acquireChecked(check: () -> ScreenshotRescanBlockV1?): ScreenshotRescanBlockV1? {
        val blocked = precheck(check)
        if (blocked == null) busy = true
        return blocked
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
    private fun safety(context: Context): ScreenshotRescanBlockV1? = try {
        val repo = SettingsRepository(context)
        when {
            !enabled(context) -> ScreenshotRescanBlockV1.DISABLED
            repo.currentJourneyId().isNotBlank() -> ScreenshotRescanBlockV1.JOURNEY
            repo.isProjectionActive() ||
                context.getSystemService(ActivityManager::class.java).getRunningServices(Int.MAX_VALUE).any {
                    it.service.className.endsWith("MediaProjectionOcrService") ||
                        it.service.className.endsWith("UberDigitizationCaptureService026")
                } -> ScreenshotRescanBlockV1.CAPTURE
            ReaderLab027036.mode(context) != ReaderLab027036.MODE_M1 -> ScreenshotRescanBlockV1.MODE
            else -> null
        }
    } catch (_: Exception) { ScreenshotRescanBlockV1.UNKNOWN }
    internal fun precheck(context: Context) = lease.precheck { safety(context) }
    internal fun acquire(context: Context) = lease.acquireChecked { safety(context) }
    fun complete() = lease.complete()
    internal fun shouldAutoPick(block: ScreenshotRescanBlockV1?, linked: Boolean, restoring: Boolean) =
        block == null && !linked && !restoring
}
