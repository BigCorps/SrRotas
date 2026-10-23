package com.srrotas.app

/** CurrentRide ocupa um dos N cards normais; jamais aumenta o limite nem duplica a oferta. */
internal object HudDisplayOffersV1 {
    fun <T> select(recent: List<T>, active: T?, limit: Int, id: (T) -> String): List<T> =
        (listOfNotNull(active) + recent).distinctBy(id).take(limit.coerceAtLeast(0))
}
