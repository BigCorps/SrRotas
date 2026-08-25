package com.srrotas.app

object TimeWindow0212 {
    fun label(hourBucket: Int): String =
        if (hourBucket in 0..23) "%02dh–%02dh".format(hourBucket, (hourBucket + 3).coerceAtMost(24))
        else "vários horários"
}
