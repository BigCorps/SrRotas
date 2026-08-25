package com.srrotas.app

object PickupPresentation0211 {
    data class Grade(val label: String, val rank: Int)

    /** rank: 2=Boa, 1=Média/indisponível, 0=Alta. */
    fun grade(
        pickupKm: Double?,
        pickupMinutes: Int?,
        maxKm: Double,
        maxMinutes: Int,
    ): Grade {
        if (pickupKm == null && pickupMinutes == null) return Grade("—", 1)

        val high =
            (maxKm > 0.0 && pickupKm != null && pickupKm > maxKm) ||
                (maxMinutes > 0 && pickupMinutes != null && pickupMinutes > maxMinutes)
        if (high) return Grade("Alta", 0)

        val medium =
            (maxKm > 0.0 && pickupKm != null && pickupKm >= maxKm * 0.75) ||
                (maxMinutes > 0 && pickupMinutes != null && pickupMinutes >= maxMinutes * 0.75)

        return if (medium) Grade("Média", 1) else Grade("Boa", 2)
    }
}
