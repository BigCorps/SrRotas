package com.srrotas.app.r3lab

import android.content.Context
import android.os.Build
import android.provider.Settings
import com.srrotas.app.BuildConfig

/**
 * vc112 (issue #7) — seleção do Reader no APK principal. Leve de propósito: só lê preferências; o
 * motor R3 (R3Supervisor) só é carregado quando o R3 é elegível.
 *
 *  - R3_PRIMARY (padrão escolhido): captura por Acessibilidade, SEM MediaProjection, quando elegível:
 *    Android 11+, aviso de Acessibilidade aceito e serviço do Sr. Rotas ativo. Senão o app segue em M1
 *    como sempre (quem não concedeu Acessibilidade não perde nada).
 *  - M1_FORCE: rollback manual, sem reinstalar — volta ao MediaProjection.
 *  - Fallback M1: falha de saúde do R3 durante a jornada (serviço desligado, OCR travado, screenshots
 *    falhando) marca fallback para AQUELA jornada; o app pede a autorização de captura do M1 pela UI
 *    (nunca reusa token morto). Rejeição de parser NÃO é falha de saúde.
 */
object R3Mode {
    const val PRIMARY = "R3_PRIMARY"
    const val M1_FORCE = "M1_FORCE"
    private const val PREFS = "r3_mode"

    /** Verdadeiro só depois que o R3Supervisor foi carregado (o desligamento não o carrega à toa). */
    @Volatile var supervisorLoaded = false

    private fun p(c: Context) = c.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun selected(c: Context): String = if (p(c).getString("mode", PRIMARY) == M1_FORCE) M1_FORCE else PRIMARY
    fun select(c: Context, mode: String) { p(c).edit().putString("mode", if (mode == M1_FORCE) M1_FORCE else PRIMARY).apply(); clearFallback(c) }

    fun disclosureAccepted(c: Context) = R3LabSettings.consent(c).accessibilityDisclosureAccepted

    fun accessibilityEnabled(c: Context): Boolean = runCatching {
        R3LabConflicts.parseEnabled(Settings.Secure.getString(c.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES), c.packageName).first
    }.getOrDefault(false)

    /** Regra pura de elegibilidade (testável). */
    fun eligible(api: Int, selected: String, disclosure: Boolean, serviceEnabled: Boolean) =
        api >= 30 && selected == PRIMARY && disclosure && serviceEnabled

    /** Ganchos do serviço: carregam o R3 só no APK do laboratório ou com R3 escolhido e aviso aceito. */
    fun hooksEnabled(c: Context): Boolean = BuildConfig.R3_LAB ||
        (Build.VERSION.SDK_INT >= 30 && selected(c) == PRIMARY && disclosureAccepted(c))

    /** Modos de laboratório M2/Comparar excluem o R3 (o M1 continua com sua recuperação normal). */
    fun primaryEligible(c: Context) = !BuildConfig.R3_LAB &&
        runCatching { com.srrotas.app.ReaderLab027036.mode(c) == com.srrotas.app.ReaderLab027036.MODE_M1 }.getOrDefault(false) &&
        eligible(Build.VERSION.SDK_INT, selected(c), disclosureAccepted(c), accessibilityEnabled(c))

    /**
     * Mesma regra de [effectivePrimary], mas tratando o serviço como ativo: ao desligar a Acessibilidade
     * o Android grava a configuração ANTES de desconectar o serviço, então no `onUnbind` ela já lê falso.
     */
    fun wasPrimaryAtDisconnect(c: Context, journeyId: String) = !BuildConfig.R3_LAB && journeyId.isNotBlank() &&
        runCatching { com.srrotas.app.ReaderLab027036.mode(c) == com.srrotas.app.ReaderLab027036.MODE_M1 }.getOrDefault(false) &&
        eligible(Build.VERSION.SDK_INT, selected(c), disclosureAccepted(c), serviceEnabled = true) && !fallbackActive(c, journeyId)

    /** R3 é o leitor efetivo desta jornada (elegível e sem fallback para o M1 nesta jornada). */
    fun effectivePrimary(c: Context, journeyId: String = currentJourney(c)) = primaryEligible(c) && !fallbackActive(c, journeyId)

    // ---------------------------------------------------------------- fallback por jornada
    data class Fallback(val journeyId: String, val reason: String, val atMs: Long)

    fun fallback(c: Context): Fallback? {
        val j = p(c).getString("fb_journey", null) ?: return null
        return Fallback(j, p(c).getString("fb_reason", "").orEmpty(), p(c).getLong("fb_at", 0L))
    }

    /** Fallback vale só para a jornada em que aconteceu; jornada nova volta a tentar o R3. */
    fun fallbackActive(c: Context, journeyId: String = currentJourney(c)) = journeyId.isNotBlank() && fallback(c)?.journeyId == journeyId

    fun setFallback(c: Context, journeyId: String, reason: String) {
        if (journeyId.isBlank()) return
        p(c).edit().putString("fb_journey", journeyId).putString("fb_reason", reason.take(60)).putLong("fb_at", System.currentTimeMillis()).apply()
    }

    fun clearFallback(c: Context) { p(c).edit().remove("fb_journey").remove("fb_reason").remove("fb_at").apply() }

    private fun currentJourney(c: Context) = runCatching { com.srrotas.app.SettingsRepository(c).currentJourneyId() }.getOrDefault("")

    fun statusText(c: Context): String = when {
        BuildConfig.R3_LAB -> "APK R3-Lab (laboratório)"
        selected(c) == M1_FORCE -> "M1 forçado (MediaProjection) — R3 desligado"
        Build.VERSION.SDK_INT < 30 -> "R3 exige Android 11+: usando M1"
        !disclosureAccepted(c) -> "R3 aguardando aviso de Acessibilidade: usando M1"
        !accessibilityEnabled(c) -> "R3 aguardando Acessibilidade ativa: usando M1"
        fallbackActive(c) -> "R3 em fallback nesta jornada (${fallback(c)?.reason}): usando M1"
        else -> "R3 principal (Acessibilidade) · M1 como reserva"
    }
}

/** Saúde da captura R3 para decidir fallback (pura). Rejeições de parser não contam. */
class R3Health(private val failLimit: Int = 8, private val silentMs: Long = 25_000L) {
    private var consecutiveFailures = 0
    private var lastSuccessAt = -1L
    private var targetsSince = -1L

    @Synchronized fun screenshotOk(now: Long) { consecutiveFailures = 0; lastSuccessAt = now }
    @Synchronized fun screenshotFailed() { consecutiveFailures++ }
    @Synchronized fun targetsVisible(now: Long, visible: Boolean) { targetsSince = if (visible) (if (targetsSince < 0) now else targetsSince) else -1L }
    @Synchronized fun reset() { consecutiveFailures = 0; lastSuccessAt = -1L; targetsSince = -1L }

    /** Motivo de fallback, ou null se a captura está saudável. */
    @Synchronized fun fallbackReason(now: Long, ocrPoisoned: Boolean): String? = when {
        ocrPoisoned -> "ocr_stuck"
        consecutiveFailures >= failLimit -> "screenshot_failures"
        targetsSince >= 0 && now - targetsSince >= silentMs && (lastSuccessAt < 0 || now - lastSuccessAt >= silentMs) -> "no_screenshot_with_target"
        else -> null
    }
}
