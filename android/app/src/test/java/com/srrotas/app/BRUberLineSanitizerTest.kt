package com.srrotas.app

import org.junit.Assert.*
import org.junit.Test

class BRUberLineSanitizerTest {
    @Test fun removesCompactExternalMetricOverlay(){
        val raw="""5,31/km |56,10/hr 0,94/min |4,91*
Black
R$ 73,87
R$ 5,31/km aprox.
7 min (1.6 km)"""
        val cleaned=BRUberLineSanitizer.sanitize(raw)
        assertFalse(cleaned.contains("/hr",true))
        assertFalse(cleaned.contains("0,94/min",true))
        assertTrue(cleaned.contains("R$ 73,87"))
        assertTrue(cleaned.contains("R$ 5,31/km aprox."))
    }

    @Test fun removesExternalDurationDistanceSummary(){
        val cleaned=BRUberLineSanitizer.sanitize("1h19m -13.90km\nR$ 73,87\n7 min (1.6 km)")
        assertFalse(cleaned.contains("1h19m"))
        assertTrue(cleaned.contains("7 min (1.6 km)"))
    }

    @Test fun keepsNormalUberGeometryAndFixesBareDollar(){
        val cleaned=BRUberLineSanitizer.sanitize("$ 17,99\n4 min (0.7 km)\n10 minutos (2.0 km)")
        assertTrue(cleaned.contains("R$ 17,99"))
        assertTrue(cleaned.contains("4 min (0.7 km)"))
    }

    @Test fun fixesRealOneEllMinutes(){
        val cleaned=BRUberLineSanitizer.sanitize("4 min (1.6 km)\n1l minutos (3.9 km)")
        assertTrue(cleaned.contains("11 minutos (3.9 km)"))
    }

    @Test fun fixesDoubleEllMinutes(){
        val cleaned=BRUberLineSanitizer.sanitize("ll minutos (3.9 km)")
        assertTrue(cleaned.contains("11 minutos (3.9 km)"))
    }
}
