package com.srrotas.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import org.json.JSONObject
import java.io.File

/**
 * vc111 — releitura de imagem DURANTE a jornada, coordenada com o Reader oficial.
 *
 * Antes, `ScreenshotRescanGateV1` bloqueava qualquer releitura com jornada ou captura ativas. Agora o
 * processo isolado `:rescan` pede uma janela curta ao processo principal (broadcast explícito, não
 * exportado). O principal só concede sem corrida em andamento (motorista parado/entre corridas) e
 * sem digitalização do histórico; então segura novos quadros do Reader oficial ([OfficialOcrHold111],
 * no `FrameChangeDetector`), espera o OCR oficial em curso terminar e grava a concessão com prazo.
 * O `:rescan` faz um único OCR e devolve a janela; o prazo máximo libera o Reader de qualquer jeito.
 * Nenhuma segunda MediaProjection, nenhum OCR pesado simultâneo, nenhuma pausa permanente.
 */
object JourneyRescanSlot111 {
    const val ACTION_REQUEST = "com.srrotas.app.rescan111.REQUEST"
    const val ACTION_RELEASE = "com.srrotas.app.rescan111.RELEASE"
    const val EXTRA_NONCE = "nonce"
    const val HOLD_MAX_MS = 12_000L
    const val WAIT_OFFICIAL_MS = 2_500L
    private const val FILE = "journey_rescan_slot_111.json"

    enum class Denial(val message: String) {
        RIDE_IN_PROGRESS("Corrida em andamento: a imagem fica pendente e a releitura começa sozinha quando a corrida terminar (com esta tela aberta)."),
        HISTORY_SCAN("Digitalização do histórico em andamento: a releitura começa quando ela terminar."),
        OFFICIAL_BUSY("Leitura oficial ocupada neste instante; nova tentativa automática em alguns segundos."),
        DISABLED("Rescan OCR desativado."),
    }

    data class Slot(val nonce: String, val state: String, val reason: String?, val expiresAtWallMs: Long)

    /** Regra pura do processo principal. */
    fun decide(rescanEnabled: Boolean, rideInProgress: Boolean, historyScanActive: Boolean): Denial? = when {
        !rescanEnabled -> Denial.DISABLED
        rideInProgress -> Denial.RIDE_IN_PROGRESS
        historyScanActive -> Denial.HISTORY_SCAN
        else -> null
    }

    fun read(context: Context): Slot? = runCatching {
        val j = JSONObject(File(context.filesDir, FILE).readText())
        Slot(j.getString("nonce"), j.getString("state"), j.optString("reason").ifBlank { null }, j.optLong("expires_at"))
    }.getOrNull()

    internal fun write(context: Context, slot: Slot) = runCatching {
        val tmp = File(context.filesDir, "$FILE.tmp")
        tmp.writeText(JSONObject().put("nonce", slot.nonce).put("state", slot.state).put("reason", slot.reason ?: "")
            .put("expires_at", slot.expiresAtWallMs).toString())
        tmp.renameTo(File(context.filesDir, FILE))
    }

    /** Lido no `:rescan` por `ScreenshotRescanGateV1`: concessão vigente do processo principal. */
    fun grantValid(context: Context, nowWallMs: Long = System.currentTimeMillis()): Boolean =
        read(context)?.let { it.state == "granted" && nowWallMs < it.expiresAtWallMs } == true

    fun request(context: Context, nonce: String) = context.sendBroadcast(Intent(context, JourneyRescanReceiver111::class.java)
        .setAction(ACTION_REQUEST).putExtra(EXTRA_NONCE, nonce))

    fun release(context: Context, nonce: String) = context.sendBroadcast(Intent(context, JourneyRescanReceiver111::class.java)
        .setAction(ACTION_RELEASE).putExtra(EXTRA_NONCE, nonce))

    // ---- processo principal
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var current: String? = null
    private val autoRelease = Runnable { current?.let { finish(appContext ?: return@Runnable, it, "deadline") } }
    @Volatile private var appContext: Context? = null

    internal fun onRequest(context: Context, nonce: String) {
        val app = context.applicationContext; appContext = app
        val repo = SettingsRepository(app)
        val journey = repo.currentJourneyId()
        val ride = journey.isNotBlank() && runCatching { LocalStore.get(app).currentDoingRide(journey) != null }.getOrDefault(true)
        val denial = decide(ScreenshotRescanGateV1.enabled(app), ride, UberHistoryProjectionTap108.isActive())
        if (denial != null) {
            OfficialOcrHold111.count("denied_${denial.name.lowercase()}")
            write(app, Slot(nonce, "denied", denial.name, 0L)); return
        }
        current = nonce
        OfficialOcrHold111.hold(HOLD_MAX_MS)
        main.removeCallbacks(autoRelease); main.postDelayed(autoRelease, HOLD_MAX_MS)
        val started = SystemClock.elapsedRealtime()
        fun grantWhenIdle() {
            if (current != nonce) return
            if (!OfficialOcrHold111.officialInFlight() || SystemClock.elapsedRealtime() - started > WAIT_OFFICIAL_MS) {
                if (OfficialOcrHold111.officialInFlight()) {
                    OfficialOcrHold111.count("denied_official_busy"); finish(app, nonce, "official_busy")
                    write(app, Slot(nonce, "denied", Denial.OFFICIAL_BUSY.name, 0L)); return
                }
                OfficialOcrHold111.count("granted")
                write(app, Slot(nonce, "granted", null, System.currentTimeMillis() + HOLD_MAX_MS - (SystemClock.elapsedRealtime() - started)))
            } else main.postDelayed({ grantWhenIdle() }, 100L)
        }
        grantWhenIdle()
    }

    internal fun finish(context: Context, nonce: String, reason: String) {
        if (current != nonce) return
        current = null
        main.removeCallbacks(autoRelease)
        OfficialOcrHold111.release()
        OfficialOcrHold111.count("released_$reason")
        write(context.applicationContext, Slot(nonce, "released", reason, 0L))
    }
}

/** Receptor explícito, não exportado, no processo principal. */
class JourneyRescanReceiver111 : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val nonce = intent.getStringExtra(JourneyRescanSlot111.EXTRA_NONCE)?.takeIf { it.length in 8..64 } ?: return
        when (intent.action) {
            JourneyRescanSlot111.ACTION_REQUEST -> JourneyRescanSlot111.onRequest(context, nonce)
            JourneyRescanSlot111.ACTION_RELEASE -> JourneyRescanSlot111.finish(context, nonce, "done")
        }
    }
}

/**
 * vc111 — pausa curta e limitada do Reader oficial (processo principal) enquanto uma releitura
 * concedida roda. O `FrameChangeDetector` trata quadros como inalterados durante o prazo; ao fim o
 * próximo quadro diferente é processado normalmente. Também sabe se há OCR oficial em curso
 * (FRAME_CAPTURED sem OCR_OK/OCR_FAIL), pelos marcos já registrados em `RadarHudTrace024`.
 */
object OfficialOcrHold111 {
    @Volatile private var holdUntil = 0L
    @Volatile private var frameAt = 0L
    @Volatile private var doneAt = 0L
    @Volatile var framesHeld = 0L; private set
    @Volatile var maxHoldMs = 0L; private set
    @Volatile private var heldSince = 0L
    private val counters = java.util.concurrent.ConcurrentHashMap<String, Long>()

    fun hold(ms: Long, now: Long = SystemClock.elapsedRealtime()) { holdUntil = now + ms.coerceIn(0L, JourneyRescanSlot111.HOLD_MAX_MS); heldSince = now }

    fun release(now: Long = SystemClock.elapsedRealtime()) {
        if (heldSince > 0) maxHoldMs = maxOf(maxHoldMs, now - heldSince)
        holdUntil = 0L; heldSince = 0L
    }

    /** Consultado no início de `FrameChangeDetector.evaluate`. */
    fun active(now: Long = SystemClock.elapsedRealtime()): Boolean {
        val on = now < holdUntil
        if (on) framesHeld++
        return on
    }

    fun onTrace(stage: String) {
        when (stage) { "FRAME_CAPTURED" -> frameAt = SystemClock.elapsedRealtime(); "OCR_OK", "OCR_FAIL" -> doneAt = SystemClock.elapsedRealtime() }
    }

    fun officialInFlight(now: Long = SystemClock.elapsedRealtime()) = frameAt > doneAt && now - frameAt < 3_000L

    fun count(key: String) { counters.merge(key, 1L, Long::plus) }

    fun toJson(): JSONObject = JSONObject().put("schema", "sr-journey-rescan-111").put("frames_held", framesHeld)
        .put("max_hold_ms", maxHoldMs).put("hold_limit_ms", JourneyRescanSlot111.HOLD_MAX_MS)
        .put("counters", JSONObject(counters.toMap()))
        .put("note", "Pausa curta do Reader oficial só durante releitura concedida (sem corrida em andamento).")

    internal fun resetForTest() { holdUntil = 0; frameAt = 0; doneAt = 0; framesHeld = 0; maxHoldMs = 0; heldSince = 0; counters.clear() }
}
