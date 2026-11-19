package com.yk.photocritic.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class PanelSizeTest {
    @Test
    fun stepsOneSizeAtATimeAndStopsAtTheEnds() {
        assertEquals(PanelSize.COLLAPSED, PanelSize.MINIMIZED.larger())
        assertEquals(PanelSize.EXPANDED, PanelSize.COLLAPSED.larger())
        assertEquals(PanelSize.EXPANDED, PanelSize.EXPANDED.larger())
        assertEquals(PanelSize.COLLAPSED, PanelSize.EXPANDED.smaller())
        assertEquals(PanelSize.MINIMIZED, PanelSize.COLLAPSED.smaller())
        assertEquals(PanelSize.MINIMIZED, PanelSize.MINIMIZED.smaller())
    }
}
