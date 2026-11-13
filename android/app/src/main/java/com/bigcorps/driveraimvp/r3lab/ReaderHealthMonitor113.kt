package com.srrotas.app.r3lab

import android.Manifest
import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.LinearLayout
import android.widget.ScrollView
import com.srrotas.app.JourneyBubbleController
import com.srrotas.app.JourneyCoordinator
import com.srrotas.app.MainActivity
import com.srrotas.app.SettingsRepository
import com.srrotas.app.SrUi023
import com.srrotas.app.UiKit

/**
 * Issue #10 — monitor no processo PRINCIPAL (fora do R3): avalia o leitor da jornada a cada ciclo do
 * supervisor existente, avisa quando a jornada está sem leitor e reanexa a janela flutuante quando ela
 * sumiu (respeitando permissão e preferência). Nunca inicia/encerra jornada nem inicia M1 sozinho.
 */
object ReaderHealthMonitor113 {
    const val GRACE_MS = 20_000L
    const val RENOTIFY_MS = 5 * 60_000L
    @Volatile private var badSince = 0L
    @Volatile private var lastNoticeAt = 0L
    @Volatile private var noticeShown = false
    @Volatile var last: ReaderVerdict? = null; private set

    fun inputs(c: Context): ReaderHealthInputs {
        val repo = SettingsRepository(c)
        val journeyId = repo.currentJourneyId()
        // Estado em memória do coordenador (sem SQLite na thread principal a cada 2,5 s).
        val snap = runCatching { JourneyCoordinator.snapshot(c) }.getOrNull()
        val open = journeyId.isNotBlank() && (snap == null || snap.journeyState == com.srrotas.app.JourneyOperationalState.ACTIVE || snap.journeyState == com.srrotas.app.JourneyOperationalState.PAUSED)
        return ReaderHealthInputs(
            journeyOpen = open,
            journeyObserving = open && runCatching { JourneyCoordinator.canObserveOffers(c) }.getOrDefault(true),
            m1ProjectionActive = m1Alive(c),
            r3Selected = R3Mode.primaryRequested(c),
            // R3 é o leitor PRETENDIDO desta jornada (serviço tratado como ativo): Acessibilidade desligada
            // vira R3_STALLED/accessibility_disabled em vez de cair no genérico.
            r3Effective = R3Mode.wasPrimaryAtDisconnect(c, journeyId),
            fallbackActive = R3Mode.fallbackActive(c, journeyId),
            serviceConfigured = R3Mode.accessibilityEnabled(c),
            serviceConnected = ReaderHeartbeat113.connected,
            tickAgeMs = ReaderHeartbeat113.tickAgeMs(c),
            ocrPoisoned = ReaderHeartbeat113.persistedPoisoned(c),
        )
    }

    /**
     * M1 vivo de fato: o sinalizador `projection_active` é persistido e sobrevive à morte do processo
     * (serviço START_NOT_STICKY). Exige também o serviço MediaProjectionOcrService rodando.
     */
    /** Falha fechada se ActivityManager não confirmar o serviço EXATO do próprio app. */
    internal fun m1ConfirmedActive(persistedFlag: Boolean, ownServiceObserved: Boolean): Boolean =
        persistedFlag && ownServiceObserved

    fun m1Alive(c: Context): Boolean {
        val persisted = runCatching { SettingsRepository(c).isProjectionActive() }.getOrDefault(false)
        if (!persisted) return false
        val running = runCatching {
            val manager = c.getSystemService(android.app.ActivityManager::class.java)
                ?: return@runCatching false
            val expected = android.content.ComponentName(c, com.srrotas.app.MediaProjectionOcrService::class.java)
            @Suppress("DEPRECATION")
            manager.getRunningServices(Int.MAX_VALUE).any { it.service == expected }
        }.getOrDefault(false) // sem consulta confiável = leitor não confirmado, nunca 'ACTIVE_M1'
        return m1ConfirmedActive(persisted, running)
    }

    @Volatile private var firstCheckDone = false

    /** Chamado pelo ciclo de 2,5 s do supervisor de leitura (processo principal). */
    fun check(c: Context, now: Long = System.currentTimeMillis()) {
        val app = c.applicationContext
        // Processo novo: aviso "ongoing" de um processo anterior não fica pendurado (re-avisa após a graça).
        if (!firstCheckDone) { firstCheckDone = true; cancel(app) }
        // Laboratório M2 isolado lê pela Acessibilidade sem R3/M1: não é "sem leitor".
        if (runCatching { com.srrotas.app.ReaderLab027036.mode(app) == com.srrotas.app.ReaderLab027036.MODE_M2 }.getOrDefault(false)) {
            if (noticeShown) { cancel(app); noticeShown = false }; badSince = 0L; return
        }
        val inputs = inputs(app)
        val v = ReaderHealthRules113.evaluate(inputs)
        last = v
        ensureBubble(app, inputs)
        // Usuário só-M1 (R3 não escolhido): o aviso legado de captura interrompida (0270) continua o dono.
        if (ownedByLegacyM1(inputs, v)) { if (noticeShown) { cancel(app); noticeShown = false }; badSince = 0L; return }
        when (decide(v, now)) {
            Action.NOTIFY -> notifyNoReader(app, v)
            Action.CANCEL -> cancel(app)
            Action.NONE -> Unit
        }
    }

    fun ownedByLegacyM1(i: ReaderHealthInputs, v: ReaderVerdict) = !i.r3Selected && v.reason == "m1_not_capturing"

    enum class Action { NONE, NOTIFY, CANCEL }

    /** Regra de aviso: estado ruim por ≥ GRACE_MS → avisa; repete a cada RENOTIFY_MS; cancela ao normalizar. */
    @Synchronized fun decide(v: ReaderVerdict, now: Long): Action {
        if (!v.needsUser) {
            badSince = 0L
            if (!noticeShown) return Action.NONE
            noticeShown = false; return Action.CANCEL
        }
        if (badSince == 0L) badSince = now
        if (now - badSince < GRACE_MS) return Action.NONE
        if (noticeShown && now - lastNoticeAt < RENOTIFY_MS) return Action.NONE
        noticeShown = true; lastNoticeAt = now
        return Action.NOTIFY
    }

    internal fun resetForTest() { badSince = 0L; lastNoticeAt = 0L; noticeShown = false; last = null; firstCheckDone = false }

    /** Janela flutuante da jornada: reanexa se sumiu (sistema removeu/processo recriado). */
    private fun ensureBubble(c: Context, i: ReaderHealthInputs) {
        if (!i.journeyOpen) return
        if (!Settings.canDrawOverlays(c) || !com.srrotas.app.JourneyUiPreferences(c).enabled()) return
        if (JourneyBubbleController.isShowingAttached()) return
        // Falha ao adicionar: tenta de novo no máximo a cada 60 s (sem log/disco a cada 2,5 s).
        if (System.currentTimeMillis() - ReaderHeartbeat113.lastEventAt(c, "bubble_add_failed") < 60_000L) return
        JourneyBubbleController.show(c) // contagem de reanexação só em discardDetached
    }

    fun message(v: ReaderVerdict): String = when (v.reason) {
        "ocr_stuck_restart_required" -> "O OCR do leitor R3 travou. Toque para recuperar esta jornada com o M1 ou reiniciar o app."
        "accessibility_disabled" -> "A Acessibilidade do Sr. Rotas está desligada. Toque para reativar o R3 ou usar o M1 nesta jornada."
        "accessibility_not_connected", "r3_heartbeat_stale" -> "O leitor R3 parou de responder. Toque para recuperar a leitura desta jornada."
        "fallback_needs_m1_authorization" -> "O leitor R3 foi desativado nesta jornada. Toque para autorizar a captura do M1."
        else -> "Jornada aberta sem leitor de ofertas. Toque para recuperar a leitura."
    }

    private const val CHANNEL = "sr_reader_health_113"
    private const val ID = 15191

    private fun notifyNoReader(c: Context, v: ReaderVerdict) {
        val nm = c.getSystemService(NotificationManager::class.java)
        val blocked = when {
            nm == null -> "no_manager"
            Build.VERSION.SDK_INT >= 33 && c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED -> "permission_denied"
            !nm.areNotificationsEnabled() -> "notifications_disabled"
            else -> null
        }
        var posted = false
        R3FallbackNotice.cancel(c) // o aviso do monitor substitui o do fallback
        if (blocked == null) runCatching {
            if (Build.VERSION.SDK_INT >= 26) nm!!.createNotificationChannel(NotificationChannel(CHANNEL, "Leitor de ofertas da jornada", NotificationManager.IMPORTANCE_HIGH))
            val pending = PendingIntent.getActivity(c, ID, JourneyReaderRecoveryActivity113.intent(c), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val b = if (Build.VERSION.SDK_INT >= 26) android.app.Notification.Builder(c, CHANNEL) else @Suppress("DEPRECATION") android.app.Notification.Builder(c)
            nm!!.notify(ID, b.setSmallIcon(android.R.drawable.stat_notify_error).setContentTitle("Sr. Rotas — jornada sem leitor")
                .setContentText(message(v)).setStyle(android.app.Notification.BigTextStyle().bigText(message(v)))
                .setContentIntent(pending).setOngoing(true).setAutoCancel(false).build())
            posted = true
        }
        ReaderHeartbeat113.notice(c, v.reason, posted, blocked ?: if (!posted) "notify_failed" else null)
        // A janela flutuante (quando visível) e a tela Agora também mostram o estado; ver NowPanel.
    }

    /** Cancela o aviso do monitor e o aviso de fallback do R3 (um evento ⇒ um aviso). */
    private fun cancel(c: Context) {
        runCatching { c.getSystemService(NotificationManager::class.java)?.cancel(ID) }
        R3FallbackNotice.cancel(c)
    }
}

/**
 * Recuperação da JORNADA ATUAL (mesmo journeyId, Histórico preservado). Duas rotas explícitas:
 *  (a) M1: marca fallback desta jornada (o R3 deixa de capturar — sem OCR concorrente) e pede a
 *      autorização de captura do Android (nova sessão; nunca reutiliza token);
 *  (b) R3: reabre a Acessibilidade se desligada; com OCR travado, só "Forçar parada" resolve.
 * Nunca encerra a jornada.
 */
class JourneyReaderRecoveryActivity113 : Activity() {
    companion object {
        fun intent(c: Context) = Intent(c, JourneyReaderRecoveryActivity113::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    }

    private lateinit var body: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        UiKit.applySystemBars(this)
        body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(UiKit.dp(this@JourneyReaderRecoveryActivity113, 16), UiKit.dp(this@JourneyReaderRecoveryActivity113, 18), UiKit.dp(this@JourneyReaderRecoveryActivity113, 16), UiKit.dp(this@JourneyReaderRecoveryActivity113, 26)) }
        val scroll = ScrollView(this).apply { setBackgroundColor(SrUi023.palette(this@JourneyReaderRecoveryActivity113).background); addView(body) }
        setContentView(scroll); UiKit.applySafeArea(scroll)
    }

    override fun onResume() {
        super.onResume()
        // Em primeiro plano: devolve o serviço da jornada (notificação/controles) se o processo foi recriado.
        if (com.srrotas.app.JourneyLocationRuntime.hasPermission(this)) runCatching { com.srrotas.app.JourneyLocationRuntime.ensure(this) }
        render()
    }

    private fun render() {
        body.removeAllViews()
        val inputs = ReaderHealthMonitor113.inputs(this)
        val v = ReaderHealthRules113.evaluate(inputs)
        body.addView(UiKit.margin(UiKit.secondaryButton(this, "← Voltar") { finish() }, top = 2))
        body.addView(SrUi023.title(this, "Leitor desta jornada", 22f))
        body.addView(SrUi023.body(this, stateText(v), 12.5f))
        if (!inputs.journeyOpen) { body.addView(UiKit.margin(SrUi023.body(this, "Nenhuma jornada aberta.", 11f), top = 8)); return }
        body.addView(UiKit.margin(SrUi023.body(this, "A jornada continua aberta e o Histórico é preservado em qualquer opção abaixo.", 10.5f), top = 6))
        if (v.state == ReaderState.ACTIVE_M1 || v.state == ReaderState.ACTIVE_R3) return
        body.addView(UiKit.margin(UiKit.primaryButton(this, "Recuperar esta jornada com o M1 (autorizar captura)") { recoverWithM1() }, top = 12))
        when {
            inputs.ocrPoisoned -> {
                body.addView(UiKit.margin(SrUi023.body(this, "O OCR do R3 travou e só é liberado reiniciando o app (nunca um segundo OCR por cima). A jornada e o Histórico continuam; a Acessibilidade volta sozinha.", 10.5f), top = 10))
                body.addView(UiKit.margin(UiKit.primaryButton(this, "Reiniciar o leitor R3 (reabre o app)") { ReaderRestart113.restart(this) }, top = 6))
                body.addView(UiKit.margin(UiKit.secondaryButton(this, "Abrir informações do app (Forçar parada)") {
                    startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.fromParts("package", packageName, null)))
                }, top = 6))
            }
            !inputs.serviceConfigured -> body.addView(UiKit.margin(UiKit.secondaryButton(this, "Reativar o R3: abrir Acessibilidade") {
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }, top = 8))
            inputs.fallbackActive && inputs.r3Selected -> body.addView(UiKit.margin(UiKit.secondaryButton(this, "Tentar o R3 de novo nesta jornada") {
                R3Mode.clearFallback(this); render()
            }, top = 8))
        }
    }

    private fun recoverWithM1() {
        if (SettingsRepository(this).currentJourneyId().isBlank()) return
        // MainActivity desliga o R3 desta jornada (fallback) ANTES de pedir a MediaProjection: nunca dois OCRs.
        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(MainActivity.EXTRA_BUBBLE_ACTION, MainActivity.BUBBLE_ACTION_RECOVER_M1))
        finish()
    }

    private fun stateText(v: ReaderVerdict) = when (v.state) {
        ReaderState.ACTIVE_R3 -> "Leitor R3 ativo e respondendo."
        ReaderState.ACTIVE_M1 -> "Leitor M1 (MediaProjection) ativo."
        ReaderState.PAUSED -> "Jornada pausada: leitura suspensa."
        ReaderState.NOT_IN_JOURNEY -> "Fora de jornada."
        else -> ReaderHealthMonitor113.message(v)
    } + " (${v.reason})"
}
