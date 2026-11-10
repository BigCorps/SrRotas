package com.srrotas.app.r3lab

/** Janela vista pelo serviço de Acessibilidade (sem tipos Android, para teste puro). */
data class R3Window(
    val windowId: Int,
    val isApplication: Boolean,
    val packageName: String?,   // null quando getRoot() não está disponível
    val left: Int, val top: Int, val right: Int, val bottom: Int,
    val layer: Int = 0,
    val isSystem: Boolean = false,
) {
    val width get() = right - left
    val height get() = bottom - top
}

data class R3Target(val packageName: String, val windowId: Int, val left: Int, val top: Int, val right: Int, val bottom: Int, val rootless: Boolean,
                    /** Outra janela (não barra do sistema) acima e sobre a área do alvo. */
                    val occluded: Boolean = false) {
    val key get() = "$packageName#$windowId"
    fun bounds() = intArrayOf(left, top, right, bottom)
}

data class R3Selection(val targets: List<R3Target>, val operationalAppVisible: Boolean, val reason: String)

/**
 * Seleção conservadora de janelas: somente Uber/99, somente janelas de aplicativo com tamanho útil.
 * Sem `getRoot()`, a janela só é aceita com evidência RECENTE (evento do mesmo windowId e pacote).
 * Uber e 99 visíveis ao mesmo tempo (tela dividida) geram dois alvos — nunca descarta um deles.
 */
object R3WindowSelector {
    const val ROOTLESS_EVIDENCE_MS = 3_000L

    data class EventEvidence(val packageName: String, val windowId: Int, val atMs: Long)

    private fun isEdgeBar(w: R3Window, screenW: Int, screenH: Int) =
        w.isSystem && (w.height <= screenH * 0.08 || w.width <= screenW * 0.08)

    private fun overlapArea(o: R3Window, t: R3Target): Long {
        val w = (minOf(o.right, t.right) - maxOf(o.left, t.left)).coerceAtLeast(0)
        val h = (minOf(o.bottom, t.bottom) - maxOf(o.top, t.top)).coerceAtLeast(0)
        return w.toLong() * h
    }

    fun select(windows: List<R3Window>, screenW: Int, screenH: Int, recent: List<EventEvidence>, nowMs: Long): R3Selection {
        val operational = windows.any { it.packageName in R3AllowedPackages.OPERATIONAL }
        val targets = windows.filter { it.isApplication && it.width >= screenW * 0.25 && it.height >= screenH * 0.20 }.mapNotNull { w ->
            when {
                w.packageName in R3AllowedPackages.ALL -> R3Target(w.packageName!!, w.windowId, w.left, w.top, w.right, w.bottom, false)
                w.packageName == null -> recent.lastOrNull { it.windowId == w.windowId && nowMs - it.atMs in 0..ROOTLESS_EVIDENCE_MS }
                    ?.takeIf { it.packageName in R3AllowedPackages.ALL }
                    ?.let { R3Target(it.packageName, w.windowId, w.left, w.top, w.right, w.bottom, true) }
                else -> null
            }
        }.distinctBy { it.key }.map { t ->
            val target = windows.first { it.windowId == t.windowId }
            t.copy(occluded = windows.any { o -> o.windowId != t.windowId && o.layer > target.layer && !isEdgeBar(o, screenW, screenH) &&
                overlapArea(o, t) > (t.right - t.left).toLong() * (t.bottom - t.top) * 0.02 })
        }
        val reason = when {
            targets.isNotEmpty() -> "targets_${targets.size}"
            windows.any { it.packageName == null && it.isApplication } -> "rootless_without_recent_evidence"
            else -> "no_uber_99_window"
        }
        return R3Selection(targets, operational, reason)
    }
}

/**
 * Assinatura visual 64×64 em tons de cinza + diferença absoluta média. Quadro igual não vai ao OCR,
 * mas há refresh forçado ([forceMs]) para não perder microdiferenças (ex.: contador de tempo).
 */
class R3FrameGate(private val threshold: Double = 2.0, private val forceMs: Long = 2_500L) {
    data class Decision(val process: Boolean, val reason: String, val meanDelta: Double)
    private val last = HashMap<String, Pair<IntArray, Long>>()

    @Synchronized fun evaluate(key: String, signature: IntArray, nowMs: Long): Decision {
        val previous = last[key]
        if (previous == null || previous.first.size != signature.size) { last[key] = signature to nowMs; return Decision(true, "first", 0.0) }
        var sum = 0L
        for (i in signature.indices) sum += kotlin.math.abs(signature[i] - previous.first[i])
        val mean = sum.toDouble() / signature.size
        return when {
            mean >= threshold -> { last[key] = signature to nowMs; Decision(true, "changed", mean) }
            nowMs - previous.second >= forceMs -> { last[key] = signature to nowMs; Decision(true, "forced_refresh", mean) }
            else -> Decision(false, "unchanged", mean)
        }
    }

    @Synchronized fun reset() = last.clear()

    companion object {
        const val SIZE = 64
        /** Luminância 0..255 reduzida para SIZE×SIZE por amostragem por área; entrada ARGB. */
        fun signature(pixels: IntArray, width: Int, height: Int): IntArray {
            val out = IntArray(SIZE * SIZE)
            for (y in 0 until SIZE) for (x in 0 until SIZE) {
                val sx = (x * width) / SIZE; val sy = (y * height) / SIZE
                val p = pixels[sy * width + sx]
                out[y * SIZE + x] = (((p shr 16) and 0xff) * 299 + ((p shr 8) and 0xff) * 587 + (p and 0xff) * 114) / 1000
            }
            return out
        }
    }
}

/**
 * Um OCR pesado por vez; enquanto ocupado, só o quadro MAIS RECENTE espera (os anteriores são
 * descartados com aviso para liberar recursos). Watchdog marca o OCR em voo como obsoleto.
 */
class R3OcrQueue<T : Any>(private val clockMs: () -> Long, private val deadlineMs: Long = 8_000L, private val discard: (T, String) -> Unit) {
    data class Job<T>(val id: Long, val item: T, val startedAtMs: Long)
    private var serial = 0L
    private var running: Job<T>? = null
    private var pending: T? = null

    /** Retorna o job a iniciar agora (ou null se ficou pendente/substituiu outro). */
    @Synchronized fun submit(item: T, canStart: Boolean = true): Job<T>? {
        if (running == null && canStart) return Job(++serial, item, clockMs()).also { running = it }
        pending?.let { discard(it, "superseded") }
        pending = item
        return null
    }

    /** Conclusão do job [id]; devolve o próximo a iniciar. Conclusão de job obsoleto é ignorada. */
    @Synchronized fun complete(id: Long): Job<T>? {
        if (running?.id != id) return null
        running = null
        val next = pending ?: return null
        pending = null
        return Job(++serial, next, clockMs()).also { running = it }
    }

    @Synchronized fun isCurrent(id: Long) = running?.id == id
    @Synchronized fun busy() = running != null

    /** Watchdog: job vencido deixa de ser o atual (o resultado tardio será ignorado). */
    @Synchronized fun expire(): Job<T>? {
        val job = running ?: return null
        if (clockMs() - job.startedAtMs < deadlineMs) return null
        running = null
        return job
    }

    /** Após o watchdog, inicia o quadro pendente (se houver). */
    @Synchronized fun startPending(): Job<T>? {
        if (running != null) return null
        val next = pending ?: return null
        pending = null
        return Job(++serial, next, clockMs()).also { running = it }
    }

    @Synchronized fun clear() { pending?.let { discard(it, "stopped") }; pending = null; running = null }
}
