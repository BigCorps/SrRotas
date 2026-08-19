package com.srrotas.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class HistoricalImportTimeTest {
    @Test
    fun parsesCommonScreenshotFilename() {
        val parsed =
            HistoricalImportTime.parseFilename(
                "Screenshot_20260819-174012.png",
            )
        assertNotNull(parsed)
        assertEquals(
            "2026-08-19T20:40:12Z",
            parsed,
        )
    }

    @Test
    fun parsesSeparatedFilename() {
        val parsed =
            HistoricalImportTime.parseFilename(
                "2026-08-19 17-40-12.jpg",
            )
        assertNotNull(parsed)
        assertEquals(
            "2026-08-19T20:40:12Z",
            parsed,
        )
    }
}
