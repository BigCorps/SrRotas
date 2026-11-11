package com.srrotas.app.r3lab

import android.content.Context
import android.content.ComponentName
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
    fun select(c: Context, mode: String) { p(c).edit().putString("mode", if (mode == M1_FORCE) M1_FORCE else PRIMARY).remove("manual_m1_next_start").apply(); clearFallback(c) }

    /** A preferência inicial implícita não é opt-in. Usuários antigos seguem no M1. */
    fun primaryRequested(c: Context): Boolean = selected(c) == PRIMARY && (p(c).contains("mode") || disclosureAccepted(c))

    /** Escolha M1 para UMA jornada sem mudar a preferência R3 salva. */
    fun requestM1Once(c: Context) { p(c).edit().putBoolean("manual_m1_next_start", true).commit() }
    fun cancelM1Once(c: Context) { p(c).edit().remove("manual_m1_next_start").commit() }
    fun consumeM1Once(c: Context): Boolean {
        val requested = p(c).getBoolean("manual_m1_next_start", false)
        cancelM1Once(c)
        return requested
    }

    fun disclosureAccepted(c: Context) = R3LabSettings.consent(c).accessibilityDisclosureAccepted

    /** Evita confundir o pacote R3-Lab com o serviço principal (issue #8). */
    fun matchesOfficialService(raw: String?, packageName: String, className: String): Boolean {
        val expected = ComponentName(packageName, className)
        return raw.orEmpty().split(':').mapNotNull { ComponentName.unflattenFromString(it.trim()) }.any { it == expected }
    }

    fun accessibilityEnabled(c: Context): Boolean = runCatching {
        val expected = ComponentName(c, com.srrotas.app.DriverAccessibilityService::class.java)
        val raw = Settings.Secure.getString(c.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        val secureEnabled = matchesOfficialService(raw, expected.packageName, expected.className)
        // Fonte oficial alternativa, compatível com representações distintas do Android/OEM.
        val manager = c.getSystemService(Context.ACCESSIBILITY_SERVICE) as? android.view.accessibility.AccessibilityManager
        val managerEnabled = manager?.getEnabledAccessibilityServiceList(
            android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_ALL_MASK
        )?.any { info ->
            info.resolveInfo?.serviceInfo?.let { it.packageName == expected.packageName && it.name == expected.className } == true
        } == true
        secureEnabled || managerEnabled
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
        !primaryRequested(c) -> "M1 padrão; configure o R3 para ativá-lo"
        !disclosureAccepted(c) -> "R3 aguardando aviso de Acessibilidade — sem iniciar M1 automaticamente"
        !accessibilityEnabled(c) -> "R3 aguardando serviço Sr. Rotas principal (não R3-Lab) — escolha R3 ou M1 no Agora"
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
