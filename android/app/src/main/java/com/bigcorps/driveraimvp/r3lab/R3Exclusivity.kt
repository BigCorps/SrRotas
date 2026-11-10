package com.srrotas.app.r3lab

/**
 * vc R3 (issue #6) — exclusividade REAL de captura/OCR do laboratório, num único lugar e atômica.
 *
 * Recursos coordenados: screenshot em voo (lease com prazo), tarefa ML Kit em voo (ticket), e o
 * replay manual ("Reprocessar OCR"). Regras:
 *  - no máximo UMA tarefa ML Kit existe por vez — ao vivo OU replay;
 *  - só a conclusão REAL da tarefa (mesmo ticket) a encerra; callback de ticket antigo é ignorado;
 *  - o watchdog NUNCA zera nem "esquece" uma tarefa: após [stuckMs] ele ENVENENA o coordenador
 *    (falha fechado). Nenhum OCR novo começa até a tarefa travada concluir de fato; se nunca
 *    concluir, exige reinício controlado do app (processo novo);
 *  - o replay reserva o coordenador ATOMICAMENTE (bloqueia na hora novos screenshots e OCRs ao vivo)
 *    e só recebe permissão de OCR quando não há screenshot nem tarefa em voo;
 *  - screenshot iniciado antes da reserva que chega depois pode entregar quadros, mas o OCR deles
 *    fica pendente até o replay liberar.
 */
class R3Exclusivity(private val clockMs: () -> Long, private val stuckMs: Long = 30_000L) {
    enum class Kind { LIVE, REPLAY }
    data class OcrTicket(val id: Long, val kind: Kind, val startedAtMs: Long)
    data class ReplayToken(val id: Long)

    private var serial = 0L
    private var task: OcrTicket? = null
    private var replay: ReplayToken? = null
    var poisoned = false; private set
    private var stuckTicketId: Long? = null

    /** Novo screenshot ao vivo só sem replay reservado, sem tarefa em voo e sem veneno. */
    @Synchronized fun mayStartScreenshot(): Boolean = replay == null && task == null && !poisoned

    /** Tenta iniciar uma tarefa ML Kit. LIVE é negado com replay reservado; REPLAY exige o token vigente. */
    @Synchronized fun tryBeginOcr(kind: Kind, replayToken: ReplayToken? = null): OcrTicket? {
        if (poisoned || task != null) return null
        when (kind) {
            Kind.LIVE -> if (replay != null) return null
            Kind.REPLAY -> if (replayToken == null || replay != replayToken) return null
        }
        return OcrTicket(++serial, kind, clockMs()).also { task = it }
    }

    /** Conclusão real da tarefa. Retorna true só para o ticket vigente (callback antigo não finaliza o novo). */
    @Synchronized fun endOcr(ticket: OcrTicket): Boolean {
        if (task != ticket) return false
        task = null
        if (stuckTicketId == ticket.id) { poisoned = false; stuckTicketId = null } // a travada enfim terminou
        return true
    }

    /** Watchdog: tarefa além de [stuckMs] envenena (não libera). Retorna true quando acabou de envenenar. */
    @Synchronized fun watchdog(): Boolean {
        val t = task ?: return false
        if (poisoned || clockMs() - t.startedAtMs < stuckMs) return false
        poisoned = true; stuckTicketId = t.id
        return true
    }

    @Synchronized fun reserveReplay(): ReplayToken? {
        if (replay != null) return null
        return ReplayToken(++serial).also { replay = it }
    }

    /** O replay pode começar seu OCR quando nada está em voo (screenshot é consultado à parte). */
    @Synchronized fun replayReady(token: ReplayToken, screenshotInFlight: Boolean): Boolean =
        replay == token && task == null && !screenshotInFlight && !poisoned

    @Synchronized fun releaseReplay(token: ReplayToken): Boolean { if (replay != token) return false; replay = null; return true }

    @Synchronized fun taskInFlight(): Boolean = task != null
    @Synchronized fun replayReserved(): Boolean = replay != null
    @Synchronized fun currentTask(): OcrTicket? = task
}
