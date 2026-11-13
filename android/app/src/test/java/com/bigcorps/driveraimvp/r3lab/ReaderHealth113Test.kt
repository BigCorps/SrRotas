package com.srrotas.app.r3lab

import android.Manifest
import android.app.Application
import android.content.Context
import android.view.View
import android.view.WindowManager
import com.srrotas.app.JourneyBubbleController
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import org.robolectric.shadows.ShadowSettings
import java.io.File

/**
 * Issue #10 — jornada nunca fica silenciosamente sem leitor (SM-X626B, Android 16): máquina de estados,
 * heartbeat persistido, aviso acionável, recuperação com M1 na MESMA jornada e janela flutuante reanexada.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class ReaderHealth113Test {
    private val app get() = RuntimeEnvironment.getApplication()

    private fun inputs(
        open: Boolean = true, observing: Boolean = true, m1: Boolean = false, selected: Boolean = true,
        effective: Boolean = true, fallback: Boolean = false, configured: Boolean = true, connected: Boolean = true,
        tickAge: Long? = 1_000L, poisoned: Boolean = false,
    ) = ReaderHealthInputs(open, observing, m1, selected, effective, fallback, configured, connected, tickAge, poisoned)

    private fun eval(i: ReaderHealthInputs) = ReaderHealthRules113.evaluate(i)

    @Before fun clean() {
        listOf("r3_mode", "reader_health_113").forEach { app.getSharedPreferences(it, 0).edit().clear().commit() }
        ReaderHeartbeat113.resetMemoryForTest(); ReaderHealthMonitor113.resetForTest()
    }
    @After fun tearDown() { JourneyBubbleController.hide(app); ShadowLooper.idleMainLooper() }

    // ---------------------------------------------------------------- máquina de estados
    @Test fun stateMachineCoversEveryReaderState() {
        assertEquals(ReaderState.NOT_IN_JOURNEY, eval(inputs(open = false)).state)
        assertEquals(ReaderState.PAUSED, eval(inputs(observing = false)).state)
        assertEquals(ReaderState.ACTIVE_R3, eval(inputs()).state)
        assertEquals(ReaderState.ACTIVE_M1, eval(inputs(m1 = true, effective = false, fallback = true)).state)
        assertEquals(ReaderState.FALLBACK_AWAITING_USER, eval(inputs(effective = false, fallback = true)).state)
        assertEquals(ReaderState.R3_STALLED, eval(inputs(connected = false)).state)
        assertEquals(ReaderState.NO_READER, eval(inputs(effective = false)).state)
        assertEquals("m1_not_capturing", eval(inputs(selected = false, effective = false)).reason)
        // Só os estados sem leitor pedem ação do motorista.
        assertFalse(eval(inputs()).needsUser); assertFalse(eval(inputs(m1 = true)).needsUser); assertFalse(eval(inputs(observing = false)).needsUser)
        assertTrue(eval(inputs(connected = false)).needsUser); assertTrue(eval(inputs(effective = false, fallback = true)).needsUser)
        assertTrue(eval(inputs(effective = false)).needsUser)
    }

    @Test fun anOpenJourneyIsNeverSilentlyWithoutReader() {
        // Todas as combinações: jornada aberta e observando, sem leitor ativo ⇒ sempre um estado que pede ação.
        val bools = listOf(false, true)
        for (sel in bools) for (eff in bools) for (fb in bools) for (cfg in bools) for (con in bools) for (poi in bools) for (age in listOf<Long?>(null, 1_000L, 60_000L)) {
            val v = eval(inputs(selected = sel, effective = eff, fallback = fb, configured = cfg, connected = con, poisoned = poi, tickAge = age))
            assertTrue("$v", v.state == ReaderState.ACTIVE_R3 || v.needsUser)
            if (v.state == ReaderState.ACTIVE_R3) assertTrue(eff && !fb && cfg && con && !poi && age != null && age <= ReaderHealthRules113.TICK_STALE_MS)
        }
    }

    // ---------------------------------------------------------------- uso prolongado e parada
    @Test fun thirtyMinutesHealthyThenSilentStopIsAnnouncedOnceAndRepeatedUntilRecovered() {
        var t = 1_000_000L
        val step = 2_500L
        repeat((30 * 60_000L / step).toInt()) {
            assertEquals(ReaderHealthMonitor113.Action.NONE, ReaderHealthMonitor113.decide(eval(inputs(tickAge = 1_000L)), t)); t += step
        }
        // R3 para de pulsar (serviço morto sem onUnbind): idade do tick cresce.
        val stopAt = t
        val actions = mutableListOf<Pair<Long, ReaderHealthMonitor113.Action>>()
        while (t < stopAt + 11 * 60_000L) {
            val v = eval(inputs(tickAge = t - stopAt + 1_000L))
            actions += t to ReaderHealthMonitor113.decide(v, t); t += step
        }
        val notifies = actions.filter { it.second == ReaderHealthMonitor113.Action.NOTIFY }.map { it.first - stopAt }
        assertTrue("first notice after stale + grace: $notifies", notifies.first() in (ReaderHealthRules113.TICK_STALE_MS + ReaderHealthMonitor113.GRACE_MS - step)..(ReaderHealthRules113.TICK_STALE_MS + ReaderHealthMonitor113.GRACE_MS + 2 * step))
        assertTrue("renotified while unresolved", notifies.size >= 2)
        assertTrue(notifies.zipWithNext().all { (a, b) -> b - a >= ReaderHealthMonitor113.RENOTIFY_MS })
        assertEquals(ReaderHealthMonitor113.Action.CANCEL, ReaderHealthMonitor113.decide(eval(inputs(m1 = true)), t))
        assertEquals(ReaderHealthMonitor113.Action.NONE, ReaderHealthMonitor113.decide(eval(inputs(m1 = true)), t + step))
    }

    @Test fun shortRebindBlipDoesNotNotify() {
        val t = 5_000_000L
        assertEquals(ReaderHealthMonitor113.Action.NONE, ReaderHealthMonitor113.decide(eval(inputs(connected = false)), t))
        assertEquals(ReaderHealthMonitor113.Action.NONE, ReaderHealthMonitor113.decide(eval(inputs(connected = false)), t + 10_000L))
        assertEquals(ReaderHealthMonitor113.Action.NONE, ReaderHealthMonitor113.decide(eval(inputs()), t + 12_500L))
        assertEquals(ReaderHealthMonitor113.Action.NONE, ReaderHealthMonitor113.decide(eval(inputs(connected = false)), t + 15_000L))
    }

    @Test fun heartbeatSurvivesProcessRecreationAndKilledServiceReadsAsStalled() {
        ReaderHeartbeat113.serviceConnected(app)
        ReaderHeartbeat113.tick(app, "CAPTURING", null)
        val written = System.currentTimeMillis()
        // Processo recriado: memória zerada, serviço ainda não reconectou (morte sem onUnbind).
        ReaderHeartbeat113.resetMemoryForTest()
        assertFalse(ReaderHeartbeat113.connected)
        val age = ReaderHeartbeat113.tickAgeMs(app, written + 40_000L)!!
        assertTrue("tick read back from disk: $age", age in 39_000L..41_000L)
        val v = eval(inputs(connected = ReaderHeartbeat113.connected, tickAge = age))
        assertEquals(ReaderState.R3_STALLED, v.state); assertEquals("accessibility_not_connected", v.reason)
        assertEquals("r3_heartbeat_stale", eval(inputs(tickAge = age)).reason)
        assertEquals(1, app.getSharedPreferences("reader_health_113", 0).getInt("service_connected_count", 0))
    }

    @Test fun permanentlyStuckOcrIsStalledAndAsksForForceStopButPoisonDiesWithTheProcess() {
        ReaderHeartbeat113.setPoisoned(app, true)
        val v = eval(inputs(poisoned = ReaderHeartbeat113.persistedPoisoned(app)))
        assertEquals(ReaderState.R3_STALLED, v.state); assertEquals("ocr_stuck_restart_required", v.reason)
        assertTrue(ReaderHealthMonitor113.message(v).contains("travou"))
        ReaderHeartbeat113.resetMemoryForTest() // Forçar parada: a Task morta não sobrevive ao processo
        assertFalse(ReaderHeartbeat113.persistedPoisoned(app))
        assertNotNull(app.getSharedPreferences("reader_health_113", 0).getLong("ocr_stuck_at", 0L).takeIf { it > 0 })
    }

    @Test fun accessibilityTurnedOffIsAnnouncedNotSilent() {
        val v = eval(inputs(effective = false, configured = false))
        assertEquals(ReaderState.NO_READER, v.state); assertEquals("r3_selected_but_unavailable", v.reason)
        val v2 = eval(inputs(configured = false))
        assertEquals("accessibility_disabled", v2.reason)
    }

    // ---------------------------------------------------------------- troca R3 ↔ M1 na jornada
    @Test fun switchingModesMidJourneyKeepsTheJourneyFallbackAndAsksForM1() {
        R3Mode.setFallback(app, "journey-A", "accessibility_disconnected")
        R3Mode.select(app, R3Mode.M1_FORCE)
        R3Mode.select(app, R3Mode.PRIMARY)
        assertTrue("fallback of the open journey survives mode switches", R3Mode.fallbackActive(app, "journey-A"))
        val waiting = eval(inputs(effective = false, fallback = R3Mode.fallbackActive(app, "journey-A")))
        assertEquals(ReaderState.FALLBACK_AWAITING_USER, waiting.state)
        // Motorista autoriza o M1 na mesma jornada.
        assertEquals(ReaderState.ACTIVE_M1, eval(inputs(m1 = true, effective = false, fallback = true)).state)
        assertFalse("a new journey tries R3 again", R3Mode.fallbackActive(app, "journey-B"))
    }

    @Test fun legacyM1OnlyUsersKeepTheirOwnNoticeAndR3UsersGetOneNoticeOnly() {
        val m1Only = inputs(selected = false, effective = false)
        assertTrue(ReaderHealthMonitor113.ownedByLegacyM1(m1Only, eval(m1Only)))
        val r3 = inputs(effective = false)
        assertFalse(ReaderHealthMonitor113.ownedByLegacyM1(r3, eval(r3)))
        assertTrue(src("DiagnosticControls0270.kt").contains("com.srrotas.app.r3lab.R3Mode.primaryRequested(context)"))
    }

    @Test fun m1HealthNeverFailsOpenOnServiceProbeFailure() {
        assertFalse(ReaderHealthMonitor113.m1ConfirmedActive(true, false))
        assertFalse(ReaderHealthMonitor113.m1ConfirmedActive(false, true))
        assertFalse(ReaderHealthMonitor113.m1ConfirmedActive(false, false))
        assertTrue(ReaderHealthMonitor113.m1ConfirmedActive(true, true))
        val monitor = src("r3lab/ReaderHealthMonitor113.kt")
        assertTrue(monitor.contains("getOrDefault(false) // sem consulta confiável"))
        assertTrue(monitor.contains("android.content.ComponentName(c, com.srrotas.app.MediaProjectionOcrService::class.java)"))
    }

    @Test fun persistedProjectionFlagOfADeadM1IsNotAnActiveReader() {
        // Processo morto: projection_active=true ficou gravado, mas o serviço M1 não existe mais.
        com.srrotas.app.SettingsRepository(app).setProjectionActive(true)
        assertFalse(ReaderHealthMonitor113.m1Alive(app))
        assertFalse(ReaderHealthMonitor113.inputs(app).m1ProjectionActive)
        assertEquals(ReaderState.FALLBACK_AWAITING_USER, eval(inputs(m1 = false, effective = false, fallback = true)).state)
        com.srrotas.app.SettingsRepository(app).setProjectionActive(false)
    }

    @Test fun reviewGuardsLegacyM1ButtonM2LabSingleNoticeAndNoStaleOngoingNotice() {
        val now = src("NowPanel027037.kt")
        assertTrue("pure-M1 users keep Retomar captura", now.contains("!com.srrotas.app.r3lab.ReaderHealthMonitor113.ownedByLegacyM1(healthInputs!!, verdict)"))
        val mon = src("r3lab/ReaderHealthMonitor113.kt")
        val check = mon.substringAfter("fun check(").substringBefore("fun ownedByLegacyM1")
        assertTrue("new process cancels a leftover ongoing notice", check.indexOf("if (!firstCheckDone) { firstCheckDone = true; cancel(app) }") in 0 until check.indexOf("val inputs"))
        assertTrue("M2 lab is not 'no reader'", check.indexOf("MODE_M2") in 0 until check.indexOf("val inputs"))
        assertTrue("one event ⇒ one notice", mon.substringAfter("private fun notifyNoReader").contains("R3FallbackNotice.cancel(c)"))
        assertTrue(mon.substringAfter("private fun cancel(c: Context)").contains("R3FallbackNotice.cancel(c)"))
        assertTrue("no duplicate MainActivity", mon.contains("FLAG_ACTIVITY_CLEAR_TOP"))
        val ensure = mon.substringAfter("private fun ensureBubble").substringBefore("fun message(")
        assertFalse("reattach counted only when a detached window is discarded", ensure.contains("bubble(c, \"reattached\")"))
        assertTrue("add failures back off", ensure.contains("bubble_add_failed"))
    }

    @Test fun accessibilityOffWhileR3IsTheIntendedReaderIsReportedPrecisely() {
        // R3 pretendido (wasPrimaryAtDisconnect trata o serviço como ativo) e serviço desligado.
        val v = eval(inputs(effective = true, configured = false, connected = false))
        assertEquals(ReaderState.R3_STALLED, v.state); assertEquals("accessibility_disabled", v.reason)
        assertTrue(src("r3lab/ReaderHealthMonitor113.kt").contains("r3Effective = R3Mode.wasPrimaryAtDisconnect(c, journeyId)"))
    }

    // ---------------------------------------------------------------- avisos
    @Test fun deniedNotificationIsRecordedNeverSilentlyAssumedPosted() {
        shadowOf(app).denyPermissions(Manifest.permission.POST_NOTIFICATIONS)
        R3FallbackNotice.show(app, "ocr_stuck")
        val p = app.getSharedPreferences("reader_health_113", 0)
        assertFalse(p.getBoolean("last_notice_posted", true)); assertEquals("permission_denied", p.getString("last_notice_blocked", null))
        shadowOf(app).grantPermissions(Manifest.permission.POST_NOTIFICATIONS)
        R3FallbackNotice.show(app, "ocr_stuck")
        assertTrue(p.getBoolean("last_notice_posted", false)); assertEquals("", p.getString("last_notice_blocked", null))
        val n = shadowOf(app.getSystemService(android.app.NotificationManager::class.java)).allNotifications.single()
        val target = shadowOf(n.contentIntent).savedIntent
        assertEquals(JourneyReaderRecoveryActivity113::class.java.name, target.component!!.className)
        assertNull("never the ambiguous Start action", target.getStringExtra(com.srrotas.app.MainActivity.EXTRA_BUBBLE_ACTION))
    }

    // ---------------------------------------------------------------- recuperação na mesma jornada
    @Test fun m1RecoveryKeepsTheJourneyStopsR3FirstAndAlwaysAsksFreshConsent() {
        val main = src("ConsolidatedMainActivity027037.kt")
        val recover = main.substringAfter("fun recoverCurrentJourneyWithM1()").substringBefore("private fun requestCapturePermission")
        assertFalse("never starts a new journey", recover.contains("startJourney"))
        assertFalse("never ends the journey", recover.contains("endJourney") || recover.contains("stopCurrentJourney"))
        assertTrue("no M1 over a LIVE projection", recover.contains("if (com.srrotas.app.r3lab.ReaderHealthMonitor113.m1Alive(this))"))
        assertTrue("stale persisted flag cleared before fresh consent", recover.contains("repo.setProjectionActive(false)"))
        val fb = recover.indexOf("setFallback(this, journeyId, \"manual_recover_m1\")")
        assertTrue("R3 off only after the overlay check (journey never left without reader) …", fb > recover.indexOf("Settings.canDrawOverlays(this)"))
        assertTrue("… and BEFORE the projection is requested (no two OCRs)", fb in 0 until recover.indexOf("requestCapturePermission(recovery = true)"))
        // Nova autorização: a recuperação força projeção nova (nunca reutiliza o token anterior).
        assertTrue(main.contains("putExtra(MediaProjectionOcrService.EXTRA_FORCE_FRESH_PROJECTION, wasRecovery)"))
        assertTrue(main.contains("MainActivity.BUBBLE_ACTION_RECOVER_M1 ->"))
        val act = src("r3lab/ReaderHealthMonitor113.kt").substringAfter("class JourneyReaderRecoveryActivity113")
        assertTrue("healthy reader → no recovery buttons", act.contains("if (v.state == ReaderState.ACTIVE_M1 || v.state == ReaderState.ACTIVE_R3) return"))
        assertFalse(act.contains("BUBBLE_ACTION_START") || act.contains("endJourney"))
        assertTrue(src("../../../../AndroidManifest.xml").contains("JourneyReaderRecoveryActivity113\" android:exported=\"false\""))
    }

    @Test fun stuckOcrTriggersFallbackAndHealthIsMonitoredOutsideR3() {
        val sup = src("r3lab/R3Supervisor.kt")
        val poisonedBranch = sup.substringAfter("if (exclusivity.poisoned) {").substringBefore("return")
        assertTrue("poisoned tick still checks health", poisonedBranch.contains("checkHealth("))
        assertTrue(sup.contains("ReaderHeartbeat113.tick("))
        assertTrue(src("ReaderRecoverySupervisor027036.kt").let { it.indexOf("ReaderHealthMonitor113.check(context)") in 0 until it.indexOf("if (!ReaderLab027036.m1Enabled(context))") })
        val svc = src("DriverAccessibilityService.kt")
        assertTrue(svc.contains("ReaderHeartbeat113.serviceConnected(this)") && svc.contains("ReaderHeartbeat113.serviceDisconnected(this, \"unbind\")"))
        assertTrue("service back → floating window back", svc.substringAfter("onServiceConnected").substringBefore("onUnbind").contains("ReaderHealthMonitor113.check(this)"))
    }

    // ---------------------------------------------------------------- janela flutuante
    @Test fun floatingWindowRemovedBySystemIsReattachedOnceWithoutDuplicate() {
        ShadowSettings.setCanDrawOverlays(true)
        JourneyBubbleController.show(app); ShadowLooper.idleMainLooper()
        assertTrue(JourneyBubbleController.isShowingAttached())
        val root = field<View>("root")!!
        // O sistema remove a janela por fora (root continua referenciado).
        app.getSystemService(WindowManager::class.java).removeViewImmediate(root)
        assertFalse(root.isAttachedToWindow)
        ShadowLooper.idleMainLooper(4, java.util.concurrent.TimeUnit.SECONDS)
        val again = field<View>("root")!!
        assertNotSame(root, again); assertTrue(again.isAttachedToWindow)
        assertEquals(1, app.getSharedPreferences("reader_health_113", 0).getInt("bubble_reattached_count", 0))
        // show() repetido não cria uma segunda janela.
        JourneyBubbleController.show(app); JourneyBubbleController.show(app); ShadowLooper.idleMainLooper()
        assertSame(again, field<View>("root"))
        ShadowLooper.idleMainLooper(2, java.util.concurrent.TimeUnit.SECONDS)
        assertEquals(1, app.getSharedPreferences("reader_health_113", 0).getInt("bubble_reattached_count", 0))
    }

    // ---------------------------------------------------------------- diagnóstico
    @Test fun diagnosticCarriesContemporaneousReaderStateWithoutPrivateData() {
        R3Mode.setFallback(app, "journey-secret-123456", "ocr_stuck")
        ReaderHeartbeat113.serviceConnected(app); ReaderHeartbeat113.tick(app, "CAPTURING", null)
        val j = ReaderHeartbeat113.toJson(app, eval(inputs(effective = false, fallback = true)))
        assertEquals("sr-reader-health-113", j.getString("schema"))
        assertEquals("FALLBACK_AWAITING_USER", j.getString("state"))
        listOf("selected_mode", "service_configured", "service_connected_now", "tick_age_s", "fallback_reason", "last_notice_posted",
            "bubble_reattached_count", "hud_offer_visible", "journey_bubble_attached", "m1_projection_active", "r3_counters").forEach { assertTrue(it, j.has(it)) }
        val text = j.toString()
        assertFalse("no journey id", text.contains("journey-secret"))
        listOf("R$", "Rua", "Av.", "\"lat\"", "\"lng\"", "base64").forEach { assertFalse(it, text.contains(it)) }
        assertTrue(src("ReaderLabCombinedDiagnostic0270361.kt").contains("base.put(\"reader_health_113\""))
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> field(name: String): T? = JourneyBubbleController::class.java.getDeclaredField(name).apply { isAccessible = true }.get(JourneyBubbleController) as T?

    private fun src(rel: String): String {
        val root = generateSequence(File("").absoluteFile) { it.parentFile }.first { File(it, "app/build.gradle.kts").exists() }
        return File(root, "app/src/main/java/com/bigcorps/driveraimvp/$rel").readText()
    }
}
