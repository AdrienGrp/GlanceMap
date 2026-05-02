package com.glancemap.glancemapcompanionapp

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CompanionAdaptiveSpecTest {
    @Test
    fun compactScreensUseCompactLayoutAndPageScroll() {
        val spec =
            companionAdaptiveSpec(
                windowWidth = 360.dp,
                windowHeight = 700.dp,
                fontScale = 1f,
            )

        assertEquals(CompanionWindowClass.COMPACT, spec.windowClass)
        assertTrue(spec.enablePageScroll)
        assertTrue(spec.useCompactPageLayout)
    }

    @Test
    fun standardPhoneScreensKeepPageScrollEnabled() {
        val spec =
            companionAdaptiveSpec(
                windowWidth = 411.dp,
                windowHeight = 891.dp,
                fontScale = 1f,
            )

        assertEquals(CompanionWindowClass.STANDARD, spec.windowClass)
        assertTrue(spec.enablePageScroll)
        assertFalse(spec.useCompactPageLayout)
    }

    @Test
    fun expandedPhoneScreensKeepPageScrollEnabled() {
        val spec =
            companionAdaptiveSpec(
                windowWidth = 480.dp,
                windowHeight = 1_000.dp,
                fontScale = 1f,
            )

        assertEquals(CompanionWindowClass.EXPANDED, spec.windowClass)
        assertTrue(spec.enablePageScroll)
        assertFalse(spec.useCompactPageLayout)
    }
}
