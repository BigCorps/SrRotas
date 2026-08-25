package com.srrotas.app

import org.junit.Assert.assertEquals
import org.junit.Test

class TimeWindow0212Test {
    @Test fun representsActualThreeHourBuckets() {
        assertEquals("12h–15h", TimeWindow0212.label(12))
        assertEquals("21h–24h", TimeWindow0212.label(21))
        assertEquals("vários horários", TimeWindow0212.label(-1))
    }
}
