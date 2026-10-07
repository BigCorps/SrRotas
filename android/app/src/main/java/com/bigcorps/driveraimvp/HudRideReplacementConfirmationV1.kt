package com.srrotas.app

/** Efêmero: a confirmação vale só para a mesma jornada, corrida atual e oferta, por 7 s. */
internal class HudRideReplacementConfirmationV1 {
    companion object { const val WINDOW_MS = 7_000L }
    enum class Click { FIRST_RIDE, ARMED, CONFIRMED }
    private data class Pending(val journeyId: String, val currentId: String, val targetId: String, val expiresAt: Long)
    private var pending: Pending? = null
    fun clear() { pending = null }
    fun isArmed(journeyId: String?, currentId: String?, targetId: String, nowMs: Long): Boolean {
        val p = pending ?: return false
        if (p.journeyId != journeyId || p.currentId != currentId || nowMs >= p.expiresAt) {
            clear()
            return false
        }
        return p.targetId == targetId
    }
    fun click(journeyId: String, currentId: String?, targetId: String, nowMs: Long): Click {
        if (currentId == null || currentId == targetId) { clear(); return Click.FIRST_RIDE }
        if (isArmed(journeyId, currentId, targetId, nowMs)) { clear(); return Click.CONFIRMED }
        pending = Pending(journeyId, currentId, targetId, nowMs + WINDOW_MS)
        return Click.ARMED
    }
}
