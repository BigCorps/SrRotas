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
    @Volatile private var paused = false
    private var recognizer: TextRecognizer? = null
    private var roundRobin = 0
    private var nextCaptureAt = 0L
    private val windowBackoff = HashMap<String, Long>()
    private var lastPersistAt = 0L
    private var lastRetentionAt = 0L

    class Frame(val envelope: R3CaptureEnvelope, val bitmap: Bitmap)

    private val ocrQueue = R3OcrQueue<Frame>(clock, 8_000L) { frame, reason ->
        R3LabTelemetry.count("ocr_$reason"); recycle(frame.bitmap)
    }

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            screenOn = intent.action != Intent.ACTION_SCREEN_OFF
            R3LabTelemetry.count(if (screenOn) "screen_on" else "screen_off")
        }
    }

    fun labBuild() = BuildConfig.R3_LAB
    fun isAttached() = service != null
    fun generation() = serviceGeneration.get()

    // ---------------------------------------------------------------- ciclo do serviço
    fun attach(svc: AccessibilityService) {
        if (!labBuild() || Build.VERSION.SDK_INT < 30) return
        detach(svc)
        R3LabTelemetry.load(svc)
        R3LabTelemetry.startSession(svc)
        service = svc
        serviceGeneration.incrementAndGet()
        screenOn = (svc.getSystemService(Context.POWER_SERVICE) as PowerManager).isInteractive
        runCatching { svc.registerReceiver(screenReceiver, IntentFilter().apply { addAction(Intent.ACTION_SCREEN_ON); addAction(Intent.ACTION_SCREEN_OFF) }) }
        recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        thread = HandlerThread("r3-lab").also { it.start(); handler = Handler(it.looper) }
        handler?.postDelayed(::tick, TICK_MS)
        R3LabTelemetry.state("ATTACHED")
    }

    /** Revogação/desconexão: invalida a geração, cancela token, descarta fila e fecha recursos. */
    fun detach(svc: AccessibilityService) {
        if (service == null) return
        serviceGeneration.incrementAndGet()
        handler?.removeCallbacksAndMessages(null)
        runCatching { svc.unregisterReceiver(screenReceiver) }
        lease.cancelService(serviceGeneration.get() - 1)
        ocrQueue.clear()
        val pending = stabilizer.flush(clock(), force = true)
        val mode = R3LabSettings.mode(svc)
        val app = svc.applicationContext
        io.execute { pending.forEach { commit(app, it, mode) }; R3LabTelemetry.persist(app) }
        runCatching { recognizer?.close() }; recognizer = null
        thread?.quitSafely(); thread = null; handler = null
        service = null
        R3LabTelemetry.state("DETACHED")
    }

    fun onEvent(event: AccessibilityEvent) {
        if (service == null) return
        val pkg = event.packageName?.toString() ?: return
        if (pkg !in R3AllowedPackages.ALL) return
        synchronized(events) {
            events.addLast(R3WindowSelector.EventEvidence(pkg, event.windowId, clock()))
            while (events.size > 24) events.removeFirst()
        }
    }

    /** Pausa a captura (ex.: reprocessamento manual na tela do laboratório). */
    fun setPaused(value: Boolean) { paused = value }

    // ---------------------------------------------------------------- tick
    private fun tick() {
        val h = handler ?: return
        h.postDelayed(::tick, TICK_MS)
        val svc = service ?: return
        val now = clock()
        lease.expire()?.let { R3LabTelemetry.count("watchdog_capture") }
        ocrQueue.expire()?.let { R3LabTelemetry.count("watchdog_ocr"); ocrQueue.startPending()?.let(::startOcr) }
        flushStable(svc, now, force = false)
        housekeeping(svc, now)
        if (paused) { R3LabTelemetry.state("PAUSED"); return }

        val consent = R3LabSettings.consent(svc)
        val selection = runCatching { selectWindows(svc, now) }.getOrElse { R3Selection(emptyList(), false, "windows_error") }
        val env = R3LabEnvironment(
            apiLevel = Build.VERSION.SDK_INT, labBuild = labBuild(), accessibilityServiceEnabled = true, screenOn = screenOn,
            officialM1CaptureActive = runCatching { SettingsRepository(svc).isProjectionActive() }.getOrDefault(true),
            operationalAppVisible = selection.operationalAppVisible,
            anotherOcrRunning = runCatching { ReaderLab027036.mode(svc) != ReaderLab027036.MODE_M1 }.getOrDefault(true),
            targetPackage = selection.targets.firstOrNull()?.packageName,
        )
        val block = R3LabGate.canCapture(consent, env)
        R3LabTelemetry.targets(selection.targets.joinToString { "${R3AllowedPackages.platform(it.packageName)}${if (it.rootless) "*" else ""}" })
        if (block != null) {
            R3LabTelemetry.state("BLOCKED", block.name)
            if (block == R3LabBlock.TARGET_UNVERIFIED) R3LabTelemetry.count("no_target:${selection.reason}") else R3LabTelemetry.count("blocked:${block.name}")
            return
        }
        if (ocrQueue.busy()) { R3LabTelemetry.count("skip_ocr_busy"); return }
        if (now < nextCaptureAt) return
        val targets = selection.targets.filter { (windowBackoff[it.key] ?: 0L) <= now }
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
            R3Window(w.id, w.type == AccessibilityWindowInfo.TYPE_APPLICATION, pkg, box.left, box.top, box.right, box.bottom)
        }
        val recent = synchronized(events) { events.toList() }
        return R3WindowSelector.select(windows, screen.width(), screen.height(), recent, now)
    }

    // ---------------------------------------------------------------- screenshot
    private fun capture(svc: AccessibilityService, targets: List<R3Target>, now: Long) {
        val windowShot = Build.VERSION.SDK_INT >= 34
        val chosen = if (windowShot) listOf(targets[roundRobin++ % targets.size]) else targets
        val profile = profiles.activeFor(chosen.first().packageName) ?: return
        val token = lease.start(serviceGeneration.get(), profile.generation) ?: run { R3LabTelemetry.count("skip_capture_busy"); return }
        val executor = Executor { r -> handler?.post(r) ?: r.run() }
        val startedAt = now
        R3LabTelemetry.count("screenshot_attempt")
        val callback = object : AccessibilityService.TakeScreenshotCallback {
            override fun onSuccess(result: AccessibilityService.ScreenshotResult) {
                val hw = result.hardwareBuffer
                if (!lease.matches(token) || token.serviceGeneration != serviceGeneration.get()) {
                    runCatching { hw.close() }; R3LabTelemetry.count("screenshot_stale_callback"); return
                }
                val full: Bitmap? = try {
                    Bitmap.wrapHardwareBuffer(hw, result.colorSpace)?.copy(Bitmap.Config.ARGB_8888, false)
                } catch (t: Throwable) { R3LabTelemetry.count("convert_error:${t.javaClass.simpleName}"); null }
                finally { runCatching { hw.close() }; lease.finish(token) }
                if (full == null) { R3LabTelemetry.count("convert_null"); return }
                R3LabTelemetry.count("screenshot_success"); R3LabTelemetry.latency("screenshot", clock() - startedAt)
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
        ocrQueue.submit(Frame(envelope, bitmap))?.let(::startOcr)
        return true
    }

    // ---------------------------------------------------------------- OCR
    private fun startOcr(job: R3OcrQueue.Job<Frame>) {
        val rec = recognizer
        if (rec == null) { ocrQueue.complete(job.id); recycle(job.item.bitmap); return }
        R3LabTelemetry.count("ocr_start")
        val executor = Executor { r -> handler?.post(r) ?: r.run() }
        val task: Task<Text> = try { rec.process(InputImage.fromBitmap(job.item.bitmap, 0)) } catch (t: Throwable) {
            R3LabTelemetry.count("ocr_sync_error:${t.javaClass.simpleName}")
            recycle(job.item.bitmap); ocrQueue.complete(job.id)?.let(::startOcr); return
        }
        task.addOnCompleteListener(executor) { done -> onOcrDone(job, done) }
    }

    private fun onOcrDone(job: R3OcrQueue.Job<Frame>, task: Task<Text>) {
        val frame = job.item
        val stale = !ocrQueue.isCurrent(job.id) || frame.envelope.serviceGeneration != serviceGeneration.get()
        val next = ocrQueue.complete(job.id)
        var handedToIo = false
        try {
            when {
                stale -> R3LabTelemetry.count("ocr_stale_result")
                !task.isSuccessful -> R3LabTelemetry.count("ocr_fail")
                else -> {
                    R3LabTelemetry.count("ocr_ok"); R3LabTelemetry.latency("ocr", clock() - job.startedAtMs)
                    handedToIo = interpret(frame, task.result)
                }
            }
        } catch (t: Throwable) {
            R3LabTelemetry.count("interpret_error:${t.javaClass.simpleName}")
        } finally {
            if (!handedToIo) recycle(frame.bitmap)
            next?.let(::startOcr)
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
        val candidates = if (mode == R3LabSettings.MODE_D) {
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
        val retain = R3LabGate.mayRetainImage(R3LabSettings.consent(app))
        val bitmap = frame.bitmap
        io.execute {
            try {
                val db = R3LabDb.get(app)
                db.insertFrame(e, bitmap.width, bitmap.height, if (retain) R3EvidenceStatus.STAGED else R3EvidenceStatus.FAILED, if (retain) null else "consent_off")
                db.insertObservation(observationId, e.frameId, mode, spatial.size, candidates.size, clock() - e.capturedAtElapsedMs)
                if (retain) R3LabTelemetry.count(if (R3EvidenceStore(app, db).persist(e.frameId, bitmap)) "image_ready" else "image_failed")
            } catch (t: Throwable) {
                R3LabTelemetry.count("db_error:${t.javaClass.simpleName}")
            } finally { recycle(bitmap) }
        }
        val windowKey = "${e.sourcePackage}#${e.windowId}"
        candidates.forEach { stabilizer.observe(windowKey, R3Stabilizer.Seen(e.frameId, observationId, it, clock())) }
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

    private fun housekeeping(svc: AccessibilityService, now: Long) {
        val app = svc.applicationContext
        if (now - lastPersistAt > 30_000L) { lastPersistAt = now; io.execute { R3LabTelemetry.persist(app) } }
        if (now - lastRetentionAt > 600_000L) { lastRetentionAt = now; io.execute { runCatching { R3EvidenceStore(app).enforceRetention() } } }
    }

    private fun recycle(b: Bitmap) { runCatching { if (!b.isRecycled) b.recycle() } }
}
