package com.srrotas.app.r3lab

import java.util.UUID

/**
 * Estabilização temporal do laboratório. Agrupa observações do MESMO card (mesma janela, plataforma
 * e campos de identidade) e emite um resultado quando o card some por [idleMs] ou após [maxAgeMs].
 * Campo ausente na observação principal só é preenchido por outra observação do mesmo candidato,
 * marcado RECOVERED e apontando para o frame/observação reais. Nada é inferido.
 */
class R3Stabilizer(private val idleMs: Long = 1_500L, private val maxAgeMs: Long = 6_000L) {
    data class Seen(val frameId: String, val observationId: String, val candidate: R3Candidate, val atMs: Long)
    data class Stable(
        val candidateId: String,
        val platform: String,
        val primary: Seen,
        val supplemental: List<Seen>,
        val fields: Map<String, R3FieldEvidence>,
        val accepted: Boolean,
        val rejection: String?,
    ) {
        val observations get() = listOf(primary) + supplemental
    }

    private class Open(val candidateId: String, val firstAtMs: Long) { val seen = mutableListOf<Seen>(); var lastAtMs = firstAtMs }
    private val open = LinkedHashMap<String, Open>()

    @Synchronized fun observe(windowKey: String, seen: Seen) {
        val key = seen.candidate.identity(windowKey)
        val entry = open.getOrPut(key) { Open(UUID.randomUUID().toString(), seen.atMs) }
        entry.seen += seen
        entry.lastAtMs = maxOf(entry.lastAtMs, seen.atMs)
    }

    @Synchronized fun openCount() = open.size

    /** Fecha candidatos ociosos/antigos; [force] fecha todos (parada do serviço). */
    @Synchronized fun flush(nowMs: Long, force: Boolean = false): List<Stable> {
        val done = open.entries.filter { (_, e) -> force || nowMs - e.lastAtMs >= idleMs || nowMs - e.firstAtMs >= maxAgeMs }
        done.forEach { open.remove(it.key) }
        return done.map { (_, e) -> stable(e) }
    }

    private fun stable(e: Open): Stable {
        val ranked = e.seen.sortedWith(compareByDescending<Seen> { it.candidate.accepted }
            .thenByDescending { it.candidate.confidence }.thenByDescending { it.candidate.fields.size }.thenBy { it.atMs })
        val primary = ranked.first()
        val supplemental = ranked.drop(1).distinctBy { it.observationId }.take(4)
        val fields = LinkedHashMap(primary.candidate.fields)
        for (other in supplemental) for ((name, ev) in other.candidate.fields) {
            if (name !in fields && ev.origin == R3FieldOrigin.OBSERVED) fields[name] = ev.copy(origin = R3FieldOrigin.RECOVERED)
        }
        return Stable(e.candidateId, primary.candidate.platform, primary, supplemental, fields, primary.candidate.accepted, primary.candidate.rejection)
    }
}
