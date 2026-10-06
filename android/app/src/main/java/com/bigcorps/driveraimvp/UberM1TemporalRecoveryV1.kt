package com.srrotas.app

import kotlin.math.abs

/** Memória M1 de dois OCRs existentes; nunca captura imagem ou publica oferta. */
internal class UberM1TemporalRecoveryV1 {
    data class Line(val text: String, val left: Int, val top: Int, val right: Int, val bottom: Int) {
        val x: Double get() = (left + right) / 2.0
        val y: Double get() = (top + bottom) / 2.0
    }
    data class Frame(
        val scope: String, val at: Long, val width: Int, val height: Int,
        val candidate: Boolean, val anchor: Boolean, val navigationNoise: Boolean,
        val fares: List<Double>, val fareX: Double, val fareY: Double, val lines: List<Line>,
    )
    data class Observation(
        val lines: List<Line>? = null, val buffered: Int = 0,
        val conflict: Int = 0, val stale: Int = 0, val attempted: Int = 0,
    )
    private var previous: Frame? = null
    private val windowMs = 2_000L
    fun clear() { previous = null }
    fun expire(observedAt: Long) {
        if ((previous?.at ?: Long.MAX_VALUE) <= observedAt) clear()
    }

    fun observe(frame: Frame): Observation {
        val old = previous
        // Entradas só são armadas pelo service depois do roteador sem oferta Uber.
        if (!frame.candidate || !frame.anchor || frame.navigationNoise ||
            frame.scope.isBlank() || frame.width <= 0 || frame.height <= 0 || frame.lines.size > 60 || frame.lines.sumOf { it.text.length } > 8000) {
            clear()
            return Observation()
        }
        if (frame.fares.size != 1 || frame.fares[0] !in 2.0..1000.0) {
            clear()
            return Observation(conflict = 1, attempted = 1)
        }
        val currentGeometry = geometryLines(frame.lines)
        // Recovery temporal só preenche um par faltante; não substitui rejeição
        // de um frame já completo nem aceita uma geometria isolada.
        if (currentGeometry.size != 1 || frame.lines.sumOf { FlexibleDriverOfferParser.geometryCount(it.text) } != 1) {
            clear()
            return Observation(attempted = 1)
        }
        if (old == null || old.scope != frame.scope) {
            previous = frame
            return Observation(buffered = 1, attempted = 1)
        }
        val age = frame.at - old.at
        if (age !in 1..windowMs) {
            previous = frame
            return Observation(buffered = 1, stale = 1, attempted = 1)
        }
        if (old.width != frame.width || old.height != frame.height ||
            abs(old.fares.single() - frame.fares.single()) > 0.01 ||
            abs(old.fareX - frame.fareX) > frame.width * .015 ||
            abs(old.fareY - frame.fareY) > frame.height * .015) {
            clear()
            return Observation(conflict = 1, attempted = 1)
        }
        val oldGeometry = geometryLines(old.lines).single()
        val newGeometry = currentGeometry.single()
        if (abs(oldGeometry.x - newGeometry.x) > frame.width * .08 ||
            abs(oldGeometry.x - old.fareX) > frame.width * .15 ||
            abs(newGeometry.x - frame.fareX) > frame.width * .15) {
            clear()
            return Observation(conflict = 1, attempted = 1)
        }
        if (abs(oldGeometry.y - newGeometry.y) < frame.height * .025) {
            // Mesmo slot é observação repetida; qualquer mudança nele é conflito.
            if (normalized(oldGeometry.text) != normalized(newGeometry.text)) {
                clear()
                return Observation(conflict = 1, attempted = 1)
            }
            previous = frame
            return Observation(buffered = 1, attempted = 1)
        }
        // Mesma posição com texto diferente (endereço, categoria, ação etc.)
        // indica possível troca de card mesmo com tarifa igual: não unir.
        for (a in old.lines) for (b in frame.lines) {
            if (abs(a.y - b.y) < frame.height * .012 && abs(a.x - b.x) < frame.width * .04 &&
                normalized(a.text) != normalized(b.text)) {
                clear()
                return Observation(conflict = 1, attempted = 1)
            }
        }
        val merged = (old.lines + frame.lines).distinctBy { normalized(it.text) }
            .sortedWith(compareBy<Line> { it.top }.thenBy { it.left })
        clear() // Par consumido; nenhum acúmulo de longa duração.
        if (merged.sumOf { FlexibleDriverOfferParser.geometryCount(it.text) } != 2) return Observation(conflict = 1, attempted = 1)
        val text = merged.joinToString("\n") { it.text }
        if (UberScreenGate.classify(text) != UberScreenGate.Kind.OFFER_CANDIDATE)
            return Observation(conflict = 1, attempted = 1)
        return Observation(lines = merged, attempted = 1)
    }

    private fun geometryLines(lines: List<Line>) = lines.filter {
        FlexibleDriverOfferParser.geometryCount(it.text) == 1
    }
    private fun normalized(text: String) = DriverOcrNormalizer.sanitize(text).lowercase()
}
