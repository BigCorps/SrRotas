package com.srrotas.app.r3lab

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.PowerManager
import android.os.SystemClock
import android.view.Display
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.srrotas.app.BuildConfig
import com.srrotas.app.DriverOcrNormalizer
import com.srrotas.app.DriverPlatformOfferRouter
import com.srrotas.app.OfferDropDiagnosis109
import com.srrotas.app.ReaderLab027036
import com.srrotas.app.SettingsRepository
import com.srrotas.app.SpatialOcrLine
import java.util.UUID
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

/**
 * R3-Lab — captura independente por AccessibilityService (API 30+), sem MediaProjection.
 *
 * Ciclo (thread própria `r3-lab`, tick de 1 s — tick ≠ screenshot ≠ OCR):
 *   gate fail-closed → seleção de janelas Uber/99 → 1 screenshot em voo (token + prazo 5 s)
 *   → HardwareBuffer fechado em finally → recorte por janela → assinatura 64×64 (quadro igual não
 *   vai ao OCR; refresh forçado) → 1 OCR por vez, só o mais recente espera (prazo 8 s) →
 *   interpretação C (roteador oficial) ou D (perfil R3 + parser oficial) → integridade oficial →
 *   estabilização → banco ISOLADO com vínculo exato frame → observação → oferta do laboratório.
 * Só existe no APK `r3lab` (BuildConfig.R3_LAB). Nunca toca M1, HUD, LocalStore oficial ou backend.
 */
object R3Supervisor {
    private const val TICK_MS = 1_000L
    private val serviceGeneration = AtomicLong(0)
    @Volatile private var service: AccessibilityService? = null
    private var thread: HandlerThread? = null
    @Volatile private var handler: Handler? = null
    private val io = Executors.newSingleThreadExecutor { Thread(it, "r3-lab-io").apply { priority = Thread.MIN_PRIORITY } }
    private val clock = { SystemClock.elapsedRealtime() }
    private val lease = R3CaptureLease(clock, 5_000L)
    private val frameGate = R3FrameGate()
    private val stabilizer = R3Stabilizer()
    val profiles = R3ProfileManager().also(R3DefaultProfiles::installDefaults)
    private val events = ArrayDeque<R3WindowSelector.EventEvidence>()
    @Volatile private var screenOn = true
    /** Coordenador único (issue #6): screenshot, OCR ao vivo e replay; falha fechado se o ML Kit travar. */
    internal val exclusivity = R3Exclusivity(clock, stuckMs = 30_000L)
    private val TICK_TOKEN = Any()
    @Volatile private var recognizer: TextRecognizer? = null
    private var roundRobin = 0
    private var nextCaptureAt = 0L
    private val windowBackoff = HashMap<String, Long>()
    private var lastPersistAt = 0L
    private var lastRetentionAt = 0L

    class Frame(val envelope: R3CaptureEnvelope, val bitmap: Bitmap)

    private val scheduler = R3LiveOcrScheduler<Frame>(exclusivity) { frame, reason ->
        R3LabTelemetry.count("ocr_$reason"); recycle(frame.bitmap)
    }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            screenOn = intent.action != Intent.ACTION_SCREEN_OFF
            R3LabTelemetry.count(if (screenOn) "screen_on" else "screen_off")
        }
    }

    fun labBuild() = BuildConfig.R3_LAB

    /**
     * Consentimento efetivo: no APK do laboratório, os toggles do lab; no APK principal (vc112), o R3 é
     * o leitor quando elegível (`R3Mode`) — a imagem continua com consentimento SEPARADO.
     */
    fun effectiveConsent(c: Context): R3LabConsent = if (labBuild()) R3LabSettings.consent(c)
        else R3LabSettings.consent(c).copy(labEnabled = R3Mode.primaryEligible(c))

    private val health = R3Health()
    @Volatile private var officialDispatcher: com.srrotas.app.OfferDispatcher? = null
    private val mainHandler = Handler(android.os.Looper.getMainLooper())
    /** localId da RideOffer submetida → (frameId, observationId) que a produziram (vínculo EXATO). */
    private val submitted = object : LinkedHashMap<String, Pair<String, String>>(256, 0.75f, false) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Pair<String, String>>?) = size > 400
    }

    /** OCR do R3 em voo (para a janela de releitura vc111 esperar, como faz com o M1). */
    fun ocrInFlight(): Boolean = exclusivity.taskInFlight()
    fun isAttached() = service != null
    fun generation() = serviceGeneration.get()

    // ---------------------------------------------------------------- ciclo do serviço
    fun attach(svc: AccessibilityService) {
        if (Build.VERSION.SDK_INT < 30 || !R3Mode.hooksEnabled(svc)) return
        R3Mode.supervisorLoaded = true
        detach(svc)
        R3LabTelemetry.load(svc)
        R3LabTelemetry.startSession(svc)
        service = svc
        serviceGeneration.incrementAndGet()
        screenOn = (svc.getSystemService(Context.POWER_SERVICE) as PowerManager).isInteractive
        runCatching { svc.registerReceiver(screenReceiver, IntentFilter().apply { addAction(Intent.ACTION_SCREEN_ON); addAction(Intent.ACTION_SCREEN_OFF) }) }
        recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        health.reset()
        com.srrotas.app.OfficialOcrHold111.externalInFlight = { exclusivity.taskInFlight() }
        thread = HandlerThread("r3-lab").also { it.start(); handler = Handler(it.looper) }
        scheduleTick()
        R3LabTelemetry.state("ATTACHED")
    }

    /** Revogação/desconexão: invalida a geração, cancela token, descarta fila e fecha recursos. */
    fun detach(svc: AccessibilityService) {
        if (service == null) return
        serviceGeneration.incrementAndGet()
        // Remove SÓ o tick: callbacks de screenshot/OCR já enfileirados ainda rodam (quitSafely) e
        // liberam HardwareBuffer/bitmaps como obsoletos.
        handler?.removeCallbacksAndMessages(TICK_TOKEN)
        runCatching { svc.unregisterReceiver(screenReceiver) }
        lease.cancelService(serviceGeneration.get() - 1)
        scheduler.clear("stopped") // quadros ainda não enviados ao OCR: descartados (bitmap liberado)
        val pending = stabilizer.flush(clock(), force = true)
        val mode = R3LabSettings.mode(svc)
        val app = svc.applicationContext
        // Revogação/opt-out: ofertas ainda abertas só são gravadas se o laboratório continua ativo.
        io.execute {
            if (effectiveConsent(app).labEnabled) pending.forEach { commit(app, it, mode) }
            else if (pending.isNotEmpty()) R3LabTelemetry.count("discarded_on_optout", pending.size.toLong())
            R3LabTelemetry.persist(app)
        }
        // A tarefa ML Kit em voo (se houver) continua dona do seu bitmap e do ticket até concluir.
        runCatching { recognizer?.close() }; recognizer = null
        thread?.quitSafely(); thread = null; handler = null
        service = null
        R3LabTelemetry.state("DETACHED")
        // Acessibilidade desligada/revogada durante uma jornada com R3: fallback M1 (pedido pela UI).
        if (!labBuild()) runCatching {
            val j = SettingsRepository(app).currentJourneyId()
            if (j.isNotBlank() && R3Mode.selected(app) == R3Mode.PRIMARY && !R3Mode.fallbackActive(app, j)) requestFallback(app, j, "accessibility_disconnected")
        }
    }

    /** Reanexa quando o motorista ativa o R3 com o serviço já conectado. */
    fun ensureAttached(svc: AccessibilityService) { if (service == null) attach(svc) }

    fun onEvent(event: AccessibilityEvent) {
        if (service == null) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg !in R3AllowedPackages.ALL) return
        synchronized(events) {
            events.addLast(R3WindowSelector.EventEvidence(pkg, event.windowId, clock()))
            while (events.size > 24) events.removeFirst()
        }
    }

    /**
     * "Reprocessar OCR" (thread de trabalho, nunca a principal). Reserva o coordenador ATOMICAMENTE —
     * nenhum screenshot/OCR ao vivo novo começa a partir daqui —, espera o screenshot e a tarefa ML Kit
     * em voo terminarem, roda UM OCR de replay e libera sempre (finally). Se o OCR do replay não
     * concluir no prazo, a tarefa continua dona do ticket até concluir de fato (o watchdog falha fechado).
     */
    fun replayRecognize(bitmap: Bitmap, waitMs: Long = 12_000L, ocrMs: Long = 30_000L): Result<Text> {
        val token = exclusivity.reserveReplay() ?: return Result.failure(IllegalStateException("replay_busy"))
        try {
            val deadline = clock() + waitMs
            while (true) {
                lease.expire()?.let { R3LabTelemetry.count("watchdog_capture") }
                if (exclusivity.replayReady(token, lease.isBusy())) break
                if (exclusivity.poisoned) return Result.failure(IllegalStateException("ocr_stuck_restart_required"))
                if (clock() >= deadline) return Result.failure(IllegalStateException("live_not_idle"))
                Thread.sleep(50)
            }
            // Reconhecedor e cópia do bitmap ANTES do ticket: nenhuma falha aqui deixa ticket órfão.
            val rec = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
            // A tarefa é dona da sua cópia: o chamador pode reciclar o original mesmo se o OCR passar do prazo.
            val owned = bitmap.copy(Bitmap.Config.ARGB_8888, false) ?: run { runCatching { rec.close() }; return Result.failure(IllegalStateException("replay_copy_failed")) }
            val ticket = exclusivity.tryBeginOcr(R3Exclusivity.Kind.REPLAY, token)
                ?: run { recycle(owned); runCatching { rec.close() }; return Result.failure(IllegalStateException("replay_denied")) }
            val latch = java.util.concurrent.CountDownLatch(1)
            val outcome = java.util.concurrent.atomic.AtomicReference<Result<Text>>()
            val task = try { rec.process(InputImage.fromBitmap(owned, 0)) } catch (t: Throwable) {
                exclusivity.endOcr(ticket); recycle(owned); runCatching { rec.close() }; return Result.failure(t)
            }
            task.addOnCompleteListener(Executor { it.run() }) { done ->
                outcome.set(if (done.isSuccessful) Result.success(done.result) else Result.failure(done.exception ?: IllegalStateException("replay_ocr_failed")))
                exclusivity.endOcr(ticket) // só a conclusão REAL libera
                recycle(owned)
                runCatching { rec.close() }
                latch.countDown()
                // Quadro ao vivo que ficou pendente durante um replay vencido segue agora.
                labExecutor.execute { pumpOcr() }
            }
            val finished = try { latch.await(ocrMs, java.util.concurrent.TimeUnit.MILLISECONDS) } catch (_: InterruptedException) { false }
            if (!finished) { R3LabTelemetry.count("replay_ocr_timeout"); return Result.failure(IllegalStateException("replay_ocr_timeout")) }
            return outcome.get() ?: Result.failure(IllegalStateException("replay_ocr_failed"))
        } finally {
            exclusivity.releaseReplay(token)
            labExecutor.execute { pumpOcr() } // quadros ao vivo que ficaram pendentes podem seguir
        }
    }

    private fun scheduleTick() { handler?.postAtTime(::tick, TICK_TOKEN, SystemClock.uptimeMillis() + TICK_MS) }

    /** Executor na thread r3-lab; se ela já encerrou, roda inline (nunca descarta o callback). */
    private val labExecutor = Executor { r -> val h = handler; if (h == null || !h.post(r)) r.run() }

    // ---------------------------------------------------------------- tick
    private fun tick() {
        if (handler == null) return
        scheduleTick()
        val svc = service ?: return
        val now = clock()
        lease.expire()?.let { R3LabTelemetry.count("watchdog_capture") }
        // Watchdog do OCR (issue #6): nunca zera nem recria por cima de uma tarefa viva. Tarefa além
        // de 30 s envenena o coordenador: nenhum OCR novo até ela concluir; se nunca concluir,
        // a tela pede reinício controlado do app.
        if (exclusivity.watchdog()) R3LabTelemetry.count("ocr_stuck_fail_closed")
        flushStable(svc, now, force = false)
        housekeeping(svc, now)
        if (exclusivity.poisoned) { R3LabTelemetry.state("OCR_TRAVADO", "OCR_STUCK_RESTART_REQUIRED"); return }
        if (exclusivity.replayReserved()) { R3LabTelemetry.state("PAUSED"); return }

        val consent = effectiveConsent(svc)
        val primary = !labBuild()
        val selection = runCatching { selectWindows(svc, now) }.getOrElse { R3Selection(emptyList(), false, "windows_error") }
        val journeyId = runCatching { SettingsRepository(svc).currentJourneyId() }.getOrDefault("")
        val env = R3LabEnvironment(
            apiLevel = Build.VERSION.SDK_INT, labBuild = labBuild(), accessibilityServiceEnabled = true, screenOn = screenOn,
            officialM1CaptureActive = runCatching { SettingsRepository(svc).isProjectionActive() }.getOrDefault(true),
            operationalAppVisible = selection.operationalAppVisible,
            // Releitura vc111 concedida (pausa curta do Reader) também pausa o R3.
            anotherOcrRunning = runCatching { ReaderLab027036.mode(svc) != ReaderLab027036.MODE_M1 }.getOrDefault(true) ||
                com.srrotas.app.OfficialOcrHold111.active(),
            // No APK principal o "app operacional" é este mesmo processo: o M1 é visto diretamente.
            operationalM1StopConfirmed = primary || R3LabSettings.protocolConfirmed(svc),
            targetPackage = selection.targets.firstOrNull()?.packageName,
        )
        val block = R3LabGate.canCapture(consent, env)
            ?: if (primary && !R3Mode.effectivePrimary(svc, journeyId)) R3LabBlock.LAB_DISABLED else null
            ?: if (primary && !runCatching { com.srrotas.app.JourneyCoordinator.canObserveOffers(svc) }.getOrDefault(false)) R3LabBlock.JOURNEY_INACTIVE else null
        if (primary) checkHealth(svc, journeyId, now, selection.targets.isNotEmpty() && block == null)
        R3LabTelemetry.targets(selection.targets.joinToString { "${R3AllowedPackages.platform(it.packageName)}${if (it.rootless) "*" else ""}" })
        if (block != null) {
            R3LabTelemetry.state("BLOCKED", block.name)
            if (block == R3LabBlock.TARGET_UNVERIFIED) R3LabTelemetry.count("no_target:${selection.reason}") else R3LabTelemetry.count("blocked:${block.name}")
            return
        }
        if (scheduler.hasPending()) pumpOcr() // nunca deixa um quadro pendente esquecido
        if (!exclusivity.mayStartScreenshot() || scheduler.hasPending()) { R3LabTelemetry.count("skip_ocr_busy"); return }
        if (now < nextCaptureAt) return
        // API 30–33: o recorte é da TELA; janela de outro app por cima (notificação, bolha) entraria
        // no OCR e na evidência — alvo coberto é pulado. API 34+ captura só os pixels da janela.
        val targets = selection.targets.filter { (windowBackoff[it.key] ?: 0L) <= now && (Build.VERSION.SDK_INT >= 34 || !it.occluded) }
        if (selection.targets.any { it.occluded } && Build.VERSION.SDK_INT < 34) R3LabTelemetry.count("skip_occluded_target")
        if (targets.isEmpty()) { R3LabTelemetry.count("skip_window_backoff"); return }
        R3LabTelemetry.state("RUNNING")
        capture(svc, targets, now)
    }

    private fun selectWindows(svc: AccessibilityService, now: Long): R3Selection {
        val screen = if (Build.VERSION.SDK_INT >= 30) (svc.getSystemService(WindowManager::class.java)).currentWindowMetrics.bounds else Rect(0, 0, 1, 1)
        val windows = svc.windows.map { w ->
            val box = Rect().also(w::getBoundsInScreen)
            val root = runCatching { w.root }.getOrNull()
            val pkg = root?.packageName?.toString()
            @Suppress("DEPRECATION") runCatching { root?.recycle() }
            R3Window(w.id, w.type == AccessibilityWindowInfo.TYPE_APPLICATION, pkg, box.left, box.top, box.right, box.bottom, w.layer,
                w.type == AccessibilityWindowInfo.TYPE_SYSTEM)
        }
        val recent = synchronized(events) { events.toList() }
        // A própria janela/HUD do app nunca conta como "app operacional" (no APK principal somos nós).
        return R3WindowSelector.select(windows.map { if (it.packageName == svc.packageName) it.copy(packageName = "self:" + it.packageName) else it },
            screen.width(), screen.height(), recent, now)
    }

    // ---------------------------------------------------------------- screenshot
    private fun capture(svc: AccessibilityService, targets: List<R3Target>, now: Long) {
        val windowShot = Build.VERSION.SDK_INT >= 34
        val chosen = if (windowShot) listOf(targets[roundRobin++ % targets.size]) else targets
        val profile = profiles.activeFor(chosen.first().packageName) ?: return
        // Início do screenshot ATÔMICO com a exclusividade: um replay reservado neste instante impede o início.
        val token = synchronized(exclusivity) {
            if (!exclusivity.mayStartScreenshot()) null else lease.start(serviceGeneration.get(), profile.generation)
        } ?: run { R3LabTelemetry.count("skip_capture_busy"); return }
        val executor = labExecutor
        val startedAt = now
        R3LabTelemetry.count("screenshot_attempt")
        val callback = object : AccessibilityService.TakeScreenshotCallback {
            override fun onSuccess(result: AccessibilityService.ScreenshotResult) {
                val hw = result.hardwareBuffer
                if (!lease.matches(token) || token.serviceGeneration != serviceGeneration.get()) {
                    runCatching { hw.close() }; R3LabTelemetry.count("screenshot_stale_callback"); return
                }
                var wrapped: Bitmap? = null
                val full: Bitmap? = try {
                    wrapped = Bitmap.wrapHardwareBuffer(hw, result.colorSpace)
                    wrapped?.copy(Bitmap.Config.ARGB_8888, false)
                } catch (t: Throwable) { R3LabTelemetry.count("convert_error:${t.javaClass.simpleName}"); null }
                finally { wrapped?.let(::recycle); runCatching { hw.close() }; lease.finish(token) }
                if (full == null) { R3LabTelemetry.count("convert_null"); return }
                R3LabTelemetry.count("screenshot_success"); R3LabTelemetry.latency("screenshot", clock() - startedAt)
                health.screenshotOk(clock())
                var fullHanded = false
                try {
                    for (t in chosen) {
                        val bitmap = (if (windowShot) full else crop(full, t)) ?: continue
                        val handed = offerFrame(svc, t, bitmap, token, if (windowShot) R3SourceKind.WINDOW else R3SourceKind.DISPLAY_CROP)
                        if (handed && bitmap === full) fullHanded = true
                        if (!handed && bitmap !== full) recycle(bitmap)
                    }
                } finally { if (!fullHanded) recycle(full) }
            }

            override fun onFailure(errorCode: Int) {
                lease.finish(token)
                R3LabTelemetry.count("screenshot_fail:$errorCode")
                health.screenshotFailed()
                if (token.serviceGeneration != serviceGeneration.get()) return // serviço já desligado: sem backoff
                val n = clock()
                when (errorCode) {
                    AccessibilityService.ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT -> nextCaptureAt = n + 1_000L
                    AccessibilityService.ERROR_TAKE_SCREENSHOT_SECURE_WINDOW -> chosen.forEach { windowBackoff[it.key] = n + 15_000L }
                    AccessibilityService.ERROR_TAKE_SCREENSHOT_INVALID_WINDOW -> chosen.forEach { windowBackoff[it.key] = n + 3_000L }
                    else -> nextCaptureAt = n + 2_000L
                }
            }
        }
        try {
            if (windowShot) svc.takeScreenshotOfWindow(chosen.first().windowId, executor, callback)
            else svc.takeScreenshot(Display.DEFAULT_DISPLAY, executor, callback)
        } catch (t: Throwable) {
            lease.finish(token); R3LabTelemetry.count("screenshot_throw:${t.javaClass.simpleName}"); nextCaptureAt = clock() + 2_000L
        }
    }

    private fun crop(full: Bitmap, t: R3Target): Bitmap? {
        val l = t.left.coerceIn(0, full.width - 1); val top = t.top.coerceIn(0, full.height - 1)
        val r = t.right.coerceIn(l + 1, full.width); val b = t.bottom.coerceIn(top + 1, full.height)
        if (l == 0 && top == 0 && r == full.width && b == full.height) return full
        return runCatching { Bitmap.createBitmap(full, l, top, r - l, b - top) }.getOrNull()
    }

    /** Retorna true quando o bitmap passou a pertencer à fila de OCR. */
    private fun offerFrame(svc: AccessibilityService, t: R3Target, bitmap: Bitmap, token: R3CaptureLease.Token, kind: R3SourceKind): Boolean {
        val now = clock()
        val signature = runCatching {
            val small = Bitmap.createScaledBitmap(bitmap, R3FrameGate.SIZE, R3FrameGate.SIZE, true)
            val px = IntArray(R3FrameGate.SIZE * R3FrameGate.SIZE).also { small.getPixels(it, 0, R3FrameGate.SIZE, 0, 0, R3FrameGate.SIZE, R3FrameGate.SIZE) }
            if (small !== bitmap) small.recycle()
            R3FrameGate.signature(px, R3FrameGate.SIZE, R3FrameGate.SIZE)
        }.getOrNull() ?: return false
        val decision = frameGate.evaluate(t.key, signature, now)
        R3LabTelemetry.count("frame_${decision.reason}")
        if (!decision.process) return false
        val profile = profiles.activeFor(t.packageName) ?: return false // perfil congelado por quadro
        val envelope = R3CaptureEnvelope(UUID.randomUUID().toString(), token.id, token.serviceGeneration, profile, t.packageName,
            t.windowId, now, System.currentTimeMillis(), kind, t.bounds())
        // Fica pendente (mais recente vence); só inicia se o coordenador conceder (sem replay/tarefa/veneno).
        scheduler.enqueue(Frame(envelope, bitmap))
        pumpOcr()
        return true
    }

    // ---------------------------------------------------------------- OCR
    /** Inicia o próximo quadro pendente se o coordenador conceder um ticket LIVE. */
    private fun pumpOcr() {
        val (frame, ticket) = scheduler.next() ?: return
        val rec = recognizer
        if (rec == null) { exclusivity.endOcr(ticket); recycle(frame.bitmap); R3LabTelemetry.count("ocr_no_recognizer"); return }
        R3LabTelemetry.count("ocr_start")
        val task: Task<Text> = try { rec.process(InputImage.fromBitmap(frame.bitmap, 0)) } catch (t: Throwable) {
            exclusivity.endOcr(ticket)
            R3LabTelemetry.count("ocr_sync_error:${t.javaClass.simpleName}")
            recycle(frame.bitmap); labExecutor.execute { pumpOcr() }; return
        }
        task.addOnCompleteListener(labExecutor) { done ->
            // Só a conclusão REAL encerra o ticket; o bitmap pertence à tarefa até aqui.
            if (!exclusivity.endOcr(ticket)) R3LabTelemetry.count("ocr_ticket_mismatch")
            onOcrDone(frame, ticket, done)
            pumpOcr()
        }
    }

    private fun onOcrDone(frame: Frame, ticket: R3Exclusivity.OcrTicket, task: Task<Text>) {
        val stale = frame.envelope.serviceGeneration != serviceGeneration.get()
        var handedToIo = false
        try {
            when {
                stale -> R3LabTelemetry.count("ocr_stale_result")
                !task.isSuccessful -> R3LabTelemetry.count("ocr_fail")
                else -> {
                    R3LabTelemetry.count("ocr_ok"); R3LabTelemetry.latency("ocr", clock() - ticket.startedAtMs)
                    handedToIo = interpret(frame, task.result)
                }
            }
        } catch (t: Throwable) {
            R3LabTelemetry.count("interpret_error:${t.javaClass.simpleName}")
        } finally {
            if (!handedToIo) recycle(frame.bitmap)
        }
    }

    private fun lines(text: Text): List<SpatialOcrLine> = text.textBlocks.flatMap { it.lines }.mapNotNull { line ->
        val box = line.boundingBox ?: return@mapNotNull null
        DriverOcrNormalizer.sanitize(line.text).takeIf { it.isNotBlank() }?.let { SpatialOcrLine(it, box) }
    }

    /** Retorna true quando o bitmap foi entregue à thread de I/O (que o recicla). */
    private fun interpret(frame: Frame, text: Text): Boolean {
        val svc = service ?: return false
        val app = svc.applicationContext
        val e = frame.envelope
        val platform = e.profile.platform
        val settings = SettingsRepository(app).load()
        val mode = R3LabSettings.mode(app)
        val spatial = lines(text)
        val observationId = UUID.randomUUID().toString()
        val primary = !labBuild()
        val candidates = if (primary) {
            // vc112: oficial primeiro; perfil R3 só para card sem oferta oficial, validado pelo parser oficial.
            val routed = DriverPlatformOfferRouter.parse(text, settings, frame.bitmap.width, frame.bitmap.height)
            R3PrimaryInterpreter.interpret(e.profile, spatial, routed.offers, settings, frame.bitmap.width, e.frameId, observationId).also { r ->
                R3LabTelemetry.count("primary_source:${r.source}")
                if (r.candidates.isEmpty() && routed.candidate && platform == "uber")
                    runCatching { OfferDropDiagnosis109.diagnose(spatial, settings) }.getOrNull()?.let { d -> R3LabTelemetry.count("rejection:${d.stage}:${d.reason}") }
            }.candidates
        } else if (mode == R3LabSettings.MODE_D) {
            R3ProfileExtractor.extract(e.profile, spatial, e.frameId, observationId, settings, frame.bitmap.width)
        } else {
            val routed = DriverPlatformOfferRouter.parse(text, settings, frame.bitmap.width, frame.bitmap.height)
            R3OfficialInterpretation.fromOffers(routed.offers, platform, e.frameId, observationId).also {
                if (it.isEmpty() && routed.candidate) {
                    val reason = if (platform == "uber") runCatching { OfferDropDiagnosis109.diagnose(spatial, settings).let { d -> "${d.stage}:${d.reason}" } }.getOrDefault("uber_no_offer")
                    else "99:${routed.reason.take(40)}"
                    R3LabTelemetry.count("rejection:$reason")
                }
            }
        }
        R3LabTelemetry.latency("frame_to_observation", clock() - e.capturedAtElapsedMs)
        if (candidates.isEmpty()) { R3LabTelemetry.count("frame_without_candidate"); return false }
        candidates.forEach { c ->
            R3LabTelemetry.count(if (c.accepted) "candidate_accepted" else "candidate_rejected")
            c.rejection?.let { R3LabTelemetry.count("rejection:$it") }
        }
        val bitmap = frame.bitmap
        io.execute {
            try {
                // Consentimento e serviço conferidos NO MOMENTO da gravação: após revogação/opt-out
                // (serviço desligado, laboratório ou imagens desativados) nenhum byte é gravado.
                val consentNow = effectiveConsent(app)
                if (!R3LabGate.mayPersistObservation(e.serviceGeneration, serviceGeneration.get(), consentNow)) {
                    R3LabTelemetry.count("discarded_on_optout"); return@execute
                }
                val retain = R3LabGate.mayRetainImage(consentNow)
                val db = R3LabDb.get(app)
                db.insertFrameWithObservation(e, bitmap.width, bitmap.height, if (retain) R3EvidenceStatus.STAGED else R3EvidenceStatus.FAILED,
                    if (retain) null else "consent_off", observationId, mode, spatial.size, candidates.size, clock() - e.capturedAtElapsedMs)
                // Reconfere o consentimento imediatamente antes de gravar bytes (janela mínima de opt-out).
                if (retain && R3LabGate.mayRetainImage(effectiveConsent(app)) && e.serviceGeneration == serviceGeneration.get())
                    R3LabTelemetry.count(if (R3EvidenceStore(app, db).persist(e.frameId, bitmap)) "image_ready" else "image_failed")
                else if (retain) { db.updateImage(e.frameId, R3EvidenceStatus.FAILED, null, null, "consent_revoked"); R3LabTelemetry.count("image_skipped_optout") }
            } catch (t: Throwable) {
                R3LabTelemetry.count("db_error:${t.javaClass.simpleName}")
            } finally { recycle(bitmap) }
        }
        if (primary) submitOfficial(app, e, observationId, candidates)
        val windowKey = "${e.sourcePackage}#${e.windowId}"
        candidates.forEach { if (!stabilizer.observe(windowKey, R3Stabilizer.Seen(e.frameId, observationId, it, clock()))) R3LabTelemetry.count("stabilizer_reopen_suppressed") }
        return true
    }

    // ---------------------------------------------------------------- estabilização e persistência
    private fun flushStable(svc: AccessibilityService, now: Long, force: Boolean) {
        val done = stabilizer.flush(now, force)
        if (done.isEmpty()) return
        val app = svc.applicationContext
        val mode = R3LabSettings.mode(app)
        io.execute { done.forEach { commit(app, it, mode) } }
    }

    private fun commit(app: Context, stable: R3Stabilizer.Stable, mode: String) {
        val id = runCatching { R3LabDb.get(app).commitOffer(stable, mode) }.getOrNull()
        R3LabTelemetry.count(when { id == null -> "commit_failed"; stable.accepted -> "offers_accepted"; else -> "offers_rejected" })
    }

    /**
     * vc112: ofertas VALIDADAS do R3 entram pelo caminho oficial (`OfferDispatcher.submitStabilized`):
     * integridade 028, admissão 029/030, estabilização, HUD, notificação, histórico e jornada. Candidatos
     * rejeitados nunca chegam lá. O mapa localId → quadro permite vincular a imagem ao ID GRAVADO.
     */
    private fun submitOfficial(app: Context, e: R3CaptureEnvelope, observationId: String, candidates: List<R3Candidate>) {
        val offers = candidates.filter { it.accepted }.mapNotNull { it.offer }.map { o ->
            o.copy(sourcePackage = e.sourcePackage, captureMethod = "r3-accessibility-ocr/${e.profile.platform}")
        }
        if (offers.isEmpty()) return
        synchronized(submitted) { offers.forEach { submitted[it.localId] = e.frameId to observationId } }
        R3LabTelemetry.count("dispatch_submitted", offers.size.toLong())
        mainHandler.post {
            val d = officialDispatcher ?: com.srrotas.app.OfferDispatcher(app) { persisted -> onOfficialPersisted(app, persisted) }.also { officialDispatcher = it }
            runCatching { d.submitStabilized(offers) }.onFailure { R3LabTelemetry.count("dispatch_error:${it.javaClass.simpleName}") }
        }
    }

    /** Chamado pelo dispatcher SÓ depois de `LocalStore.saveOffer` confirmar a gravação. */
    private fun onOfficialPersisted(app: Context, offer: com.srrotas.app.RideOffer) {
        val link = synchronized(submitted) { submitted[offer.localId] } ?: return // oferta de outro leitor: nada a vincular
        R3LabTelemetry.count("official_persisted")
        io.execute {
            val ok = runCatching { R3LabDb.get(app).linkOfficial(offer.localId, link.first, link.second) }.getOrDefault(false)
            R3LabTelemetry.count(if (ok) "official_evidence_linked" else "official_evidence_unavailable")
        }
    }

    private fun checkHealth(svc: AccessibilityService, journeyId: String, now: Long, capturing: Boolean) {
        health.targetsVisible(now, capturing)
        if (journeyId.isBlank() || !R3Mode.effectivePrimary(svc, journeyId)) return
        val reason = health.fallbackReason(now, exclusivity.poisoned) ?: return
        requestFallback(svc.applicationContext, journeyId, reason)
        health.reset()
    }

    /** Fallback M1: marca a jornada e pede ao motorista a autorização de captura do M1 (nunca automática). */
    private fun requestFallback(app: Context, journeyId: String, reason: String) {
        R3Mode.setFallback(app, journeyId, reason)
        R3LabTelemetry.count("fallback_m1:$reason")
        mainHandler.post { runCatching { R3FallbackNotice.show(app, reason) } }
    }

    private fun housekeeping(svc: AccessibilityService, now: Long) {
        val app = svc.applicationContext
        if (now - lastPersistAt > 30_000L) { lastPersistAt = now; io.execute { R3LabTelemetry.persist(app) } }
        if (now - lastRetentionAt > 600_000L) { lastRetentionAt = now; io.execute { runCatching { R3EvidenceStore(app).enforceRetention() } } }
    }

    private fun recycle(b: Bitmap) { runCatching { if (!b.isRecycled) b.recycle() } }
}
