package com.srrotas.app

import java.util.Locale

/**
 * vc111 — avisos da rolagem do Histórico: somente corridas NOVAS e reconhecidas com segurança
 * (tarifa + serviço + horário, ou duração e distância), nunca a cada quadro repetido. No máximo um
 * aviso a cada [MIN_GAP_MS]; as que chegam no intervalo viram um aviso agregado ("3 corridas capturadas").
 */
class UberHistoryNotice111 {
    companion object {
        const val MIN_GAP_MS = 2_500L
        const val MAX_NOTICES = 40

        fun reliable(r: UberCompletedRide026) = r.rideStatus == UberCompletedRide026.STATUS_COMPLETED && r.fare > 0.0 &&
            r.serviceType != "unknown" && (r.occurredAt != null || (r.durationSeconds != null && r.distanceKm != null))

        fun message(r: UberCompletedRide026): String {
            val service = when (r.serviceType.lowercase(Locale.ROOT)) {
                "unknown", "" -> null; "uberx" -> "UberX"; else -> r.serviceType.replaceFirstChar { it.uppercase() }
            }
            val fare = if (r.fare > 0.0) "R$ " + String.format(Locale("pt", "BR"), "%.2f", r.fare) else null
            return when {
                service != null && fare != null -> "$service $fare capturada"
                service != null -> "$service capturada"
                fare != null -> "Corrida $fare capturada"
                else -> "Corrida capturada"
            }
        }
    }

    private val seen = mutableListOf<UberCompletedRide026>()
    private var lastShownAt = -MIN_GAP_MS
    private var pending = 0
    private var shown = 0

    fun reset() { seen.clear(); lastShownAt = -MIN_GAP_MS; pending = 0; shown = 0 }

    /** Retorna o texto do aviso a exibir agora, ou null (repetida, incerta ou limitada pela frequência). */
    fun onRide(ride: UberCompletedRide026, nowMs: Long): String? {
        if (!reliable(ride)) return null
        if (seen.any { UberRideIdentity108.sameRide(it, ride) || it.sourceKey == ride.sourceKey }) return null
        seen += ride
        if (shown >= MAX_NOTICES) return null
        if (nowMs - lastShownAt < MIN_GAP_MS) { pending++; return null }
        lastShownAt = nowMs; shown++
        return message(ride)
    }

    /** Agrega as corridas novas que chegaram durante o intervalo mínimo. */
    fun flush(nowMs: Long): String? {
        if (pending == 0 || nowMs - lastShownAt < MIN_GAP_MS || shown >= MAX_NOTICES) return null
        val n = pending; pending = 0; lastShownAt = nowMs; shown++
        return if (n == 1) "Mais 1 corrida capturada" else "$n corridas capturadas"
    }
}
