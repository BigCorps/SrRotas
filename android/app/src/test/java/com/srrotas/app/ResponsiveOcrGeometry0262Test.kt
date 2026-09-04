package com.srrotas.app

import org.junit.Assert.assertTrue
import org.junit.Test

class ResponsiveOcrGeometry0262Test {
    @Test
    fun tabletRadiusGrowsWithFrameWithoutTakingWholeScreen() {
        val phone = ResponsiveOcrGeometry0262.horizontalRadius(1080, strict = true)
        val tablet = ResponsiveOcrGeometry0262.horizontalRadius(2560, strict = true)
        assertTrue(tablet > phone)
        assertTrue(tablet < 2560 / 2)
    }

    @Test
    fun loosePaneIsWiderThanNavigationSafePane() {
        val strict = ResponsiveOcrGeometry0262.paneRadius(1600, strict = true)
        val loose = ResponsiveOcrGeometry0262.paneRadius(1600, strict = false)
        assertTrue(loose > strict)
    }

    @Test
    fun verticalRadiusScalesForTallTabletFrame() {
        assertTrue(
            ResponsiveOcrGeometry0262.verticalRadius(2560) >
                ResponsiveOcrGeometry0262.verticalRadius(1920),
        )
    }
}
