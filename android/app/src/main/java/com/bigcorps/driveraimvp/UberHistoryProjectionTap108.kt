package com.srrotas.app

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.time.Instant
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * vc108 — digitalização do histórico Uber reaproveitando a MediaProjection OFICIAL já ativa.
 *
 * Não cria projeção, display virtual, leitor de quadros nem OCR: apenas recebe, enquanto o motorista
 * pediu explicitamente, o texto que o leitor oficial já reconheceu em cada quadro alterado
 * (`RadarHudTrace024.recordOcr`). Limites de quadros, intervalo, fila e duração protegem o OCR
 * principal; o processamento ocorre fora da thread principal. Ao finalizar, usa o mesmo arquivo
 * e a mesma revisão do fluxo existente (`UberDigitizationActivity026.reviewHistoryScan`).
 */
object UberHistoryProjectionTap108 {
    const val MAX_FRAMES = 160
    const val MIN_INTERVAL_MS = 700L
    const val MAX_DURATION_MS = 180_000L
    private const val MAX_CHARS = 20_000
    private const val MAX_QUEUE = 3

    enum class Start { STARTED, ALREADY_ACTIVE, NO_OFFICIAL_PROJECTION, RIDE_IN_PROGRESS, LEGACY_SCAN_ACTIVE }

    data class Progress(val frames: Int, val ridesSeen: Int, val elapsedSeconds: Long, val dropped: Int)

    @Volatile private var active = false
    @Volatile private var appContext: Context? = null
    @Volatile private var startedAt = 0L
    @Volatile private var lastAcceptedAt = 0L
    private val lock = Any()
    private val frames = mutableListOf<String>()
    private val hashes = HashSet<String>()
    private val rideKeys = HashSet<String>()
    private val queued = AtomicInteger(0)
    private var dropped = 0
    private val worker = Executors.newSingleThreadExecutor { Thread(it, "SrRotasHistoryTap").apply { priority = Thread.MIN_PRIORITY } }
    private val main = Handler(Looper.getMainLooper())
    private val timeout = Runnable { appContext?.let { finish(it, "timeout") } }

    fun isActive(): Boolean = active

    fun officialProjectionActive(context: Context): Boolean = runCatching {
        SettingsRepository(context).isProjectionActive() &&
            context.getSystemService(ActivityManager::class.java).getRunningServices(Int.MAX_VALUE)
                .any { it.service.className.endsWith("MediaProjectionOcrService") }
    }.getOrDefault(false)

    fun start(context: Context, officialActive: Boolean = officialProjectionActive(context)): Start {
        val app = context.applicationContext
        if (active) return Start.ALREADY_ACTIVE
        if (UberDigitizationCaptureService026.historyActive(app)) return Start.LEGACY_SCAN_ACTIVE
        if (!officialActive) return Start.NO_OFFICIAL_PROJECTION
        // Não digitalizar o histórico durante uma corrida em andamento (distração ao dirigir).
        val journey = SettingsRepository(app).currentJourneyId()
        if (journey.isNotBlank() && LocalStore.get(app).currentDoingRide(journey) != null) return Start.RIDE_IN_PROGRESS
        synchronized(lock) { frames.clear(); hashes.clear(); rideKeys.clear(); dropped = 0 }
        appContext = app; startedAt = System.currentTimeMillis(); lastAcceptedAt = 0L
        active = true
        main.postDelayed(timeout, MAX_DURATION_MS)
        JourneyBubbleController.refresh(app)
        return Start.STARTED
    }

    /** Chamado pelo leitor oficial (thread principal) para cada OCR concluído; O(1) quando inativo. */
    fun offer(rawText: String) {
        if (!active) return
        val now = System.currentTimeMillis()
        if (now - lastAcceptedAt < MIN_INTERVAL_MS || rawText.length < 20) return
        if (queued.get() >= MAX_QUEUE) { synchronized(lock) { dropped++ }; return }
        lastAcceptedAt = now
        queued.incrementAndGet()
        val text = rawText.take(MAX_CHARS)
        runCatching { worker.execute { try { accept(text) } finally { queued.decrementAndGet() } } }
            .onFailure { queued.decrementAndGet() }
    }

    internal fun accept(text: String) {
        if (!active) return
        val hash = sha(text.replace(Regex("\\s+"), " ").trim())
        val reachedLimit = synchronized(lock) {
            if (!hashes.add(hash) || frames.size >= MAX_FRAMES) return@synchronized frames.size >= MAX_FRAMES
            frames += text
            runCatching { UberDigitizationParser026.parseRidesFrame(UberDigitizationText0265.normalize(text), Instant.now(), relaxed = true) }
                .getOrDefault(emptyList()).forEach { rideKeys += UberRideIdentity108.coarseKey(it) }
            frames.size >= MAX_FRAMES
        }
        if (reachedLimit) appContext?.let { app -> main.post { finish(app, "frame_limit") } }
    }

    fun progress(): Progress = synchronized(lock) {
        Progress(frames.size, rideKeys.size, if (active) (System.currentTimeMillis() - startedAt) / 1000 else 0, dropped)
    }

    /** Encerra e abre a revisão existente. Retorna false quando nada foi capturado. */
    fun finish(context: Context, reason: String = "manual"): Boolean {
        if (!active) return false
        active = false
        main.removeCallbacks(timeout)
        val app = context.applicationContext
        val captured = synchronized(lock) { frames.toList().also { frames.clear(); hashes.clear(); rideKeys.clear() } }
        JourneyBubbleController.refresh(app)
        if (captured.isEmpty()) return false
        val file = File(app.cacheDir, "uber-history-scan-${UUID.randomUUID()}.json")
        file.writeText(JSONObject().put("reason", reason).put("source", "official_projection_v108")
            .put("frames", JSONArray(captured)).toString())
        app.startActivity(Intent(app, UberDigitizationActivity026::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra(UberDigitizationActivity026.EXTRA_MODE, UberDigitizationParser026.MODE_HISTORY)
            .putExtra(UberDigitizationActivity026.EXTRA_SCAN_FILE, file.name))
        return true
    }

    /** Cancela sem revisão e descarta o texto capturado. */
    fun cancel(context: Context) {
        active = false
        main.removeCallbacks(timeout)
        synchronized(lock) { frames.clear(); hashes.clear(); rideKeys.clear() }
        JourneyBubbleController.refresh(context.applicationContext)
    }

    fun startMessage(result: Start): String = when (result) {
        Start.STARTED -> "Leitura oficial em uso. Abra o Histórico da Uber e role devagar; finalize pela câmera do Sr. Rotas."
        Start.ALREADY_ACTIVE -> "A digitalização do histórico já está em andamento."
        Start.NO_OFFICIAL_PROJECTION -> "Leitura oficial inativa."
        Start.RIDE_IN_PROGRESS -> "Corrida em andamento: digitalize o histórico depois de concluí-la."
        Start.LEGACY_SCAN_ACTIVE -> "Outra digitalização do histórico está em andamento."
    }

    private fun sha(value: String) = MessageDigest.getInstance("SHA-256").digest(value.toByteArray())
        .joinToString("") { "%02x".format(it) }
}

/**
 * vc108 — identidade de uma viagem do histórico entre quadros e leituras diferentes.
 * O `sourceKey` inclui duração/distância: uma leitura OCR divergente gerava duas entradas.
 * Mesma situação + mesma tarifa + mesmo horário (±90 s) é a mesma viagem; sem horário, só
 * com serviço igual e duração/distância compatíveis.
 */
object UberRideIdentity108 {
    fun sameRide(a: UberCompletedRide026, b: UberCompletedRide026): Boolean {
        if (a.rideStatus != b.rideStatus || kotlin.math.abs(a.fare - b.fare) > 0.011) return false
        val ta = a.occurredAt?.let { runCatching { Instant.parse(it) }.getOrNull() }
        val tb = b.occurredAt?.let { runCatching { Instant.parse(it) }.getOrNull() }
        if (ta != null && tb != null) return kotlin.math.abs(ta.epochSecond - tb.epochSecond) <= 90
        if (a.serviceType != b.serviceType) return false
        val duration = if (a.durationSeconds != null && b.durationSeconds != null) kotlin.math.abs(a.durationSeconds - b.durationSeconds) <= 20 else null
        val distance = if (a.distanceKm != null && b.distanceKm != null) kotlin.math.abs(a.distanceKm - b.distanceKm) <= 0.1 else null
        if (duration == false || distance == false) return false
        return duration == true || distance == true
    }

    /** Chave grosseira só para contar progresso (não decide duplicidade). */
    fun coarseKey(r: UberCompletedRide026) = "${r.rideStatus}|${"%.2f".format(java.util.Locale.US, r.fare)}|${r.occurredAt?.take(16).orEmpty()}"
}

/** vc108 — cobertura da leitura do histórico versus o resumo Uber da jornada (sem prometer 100%). */
object UberHistoryCoverage108 {
    data class Coverage(val completed: Int, val cancelled: Int, val alreadySaved: Int, val uberCompleted: Int?, val missing: Int?)

    fun compute(rides: List<UberCompletedRide026>, saved: List<UberCompletedRide026>, uberCompleted: Int?): Coverage {
        val completed = rides.filter { it.rideStatus == UberCompletedRide026.STATUS_COMPLETED }
        val known = completed.count { ride -> saved.any { UberRideIdentity108.sameRide(it, ride) } }
        val union = (saved.filter { it.rideStatus == UberCompletedRide026.STATUS_COMPLETED } +
            completed.filter { ride -> saved.none { UberRideIdentity108.sameRide(it, ride) } }).size
        return Coverage(completed.size, rides.size - completed.size, known, uberCompleted, uberCompleted?.let { maxOf(0, it - union) })
    }

    fun describe(context: Context, rides: List<UberCompletedRide026>): String = runCatching {
        val times = rides.mapNotNull { it.occurredAt?.let { t -> runCatching { Instant.parse(t) }.getOrNull() } }
        val from = times.minOrNull(); val to = times.maxOrNull()
        val store = UberDigitizationStore026.get(context)
        val saved = if (from != null && to != null) store.ridesBetween(from.minusSeconds(120).toString(), to.plusSeconds(120).toString()) else emptyList()
        // Resumo da jornada local que contém estas viagens (apenas contagem de referência).
        val journey = if (from != null) LocalStore.get(context).journeysInRange(from.minusSeconds(16 * 3600).toString(), from.plusSeconds(60).toString())
            .firstOrNull { j -> j.endedAt == null || runCatching { Instant.parse(j.endedAt) }.getOrNull()?.isAfter(from) == true } else null
        val session = journey?.let { store.sessionForJourney(it.id) }
        val c = compute(rides, saved, session?.completedTrips)
        buildString {
            append("\n\nCobertura: ${c.completed} concluída(s) e ${c.cancelled} cancelada(s) nesta leitura; ${c.alreadySaved} já estavam salvas.")
            if (c.uberCompleted != null) append(" Resumo Uber da jornada: ${c.uberCompleted} concluídas · ${c.missing} ainda sem registro individual.")
            else append(" Sem resumo Uber digitalizado para comparar.")
        }
    }.getOrDefault("")
}
