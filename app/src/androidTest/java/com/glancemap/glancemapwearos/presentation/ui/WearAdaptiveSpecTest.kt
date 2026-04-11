package com.glancemap.glancemapwearos.presentation.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.glancemap.glancemapwearos.test.LargeRoundWatch
import com.glancemap.glancemapwearos.test.MediumSquareWatch
import com.glancemap.glancemapwearos.test.SmallRoundWatch
import com.glancemap.glancemapwearos.test.WearDeviceTestConfig
import com.glancemap.glancemapwearos.test.WithWearDeviceConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class WearAdaptiveSpecTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun rememberWearScreenSize_reportsSmallRoundWatch() {
        assertEquals(WearScreenSize.SMALL, captureScreenSize(SmallRoundWatch))
    }

    @Test
    fun rememberWearScreenSize_reportsMediumSquareWatch() {
        assertEquals(WearScreenSize.MEDIUM, captureScreenSize(MediumSquareWatch))
    }

    @Test
    fun rememberWearScreenSize_reportsLargeRoundWatch() {
        assertEquals(WearScreenSize.LARGE, captureScreenSize(LargeRoundWatch))
    }

    @Test
    fun rememberWearScreenSize_scalesDownWhenTextGetsLarge() {
        assertEquals(
            WearScreenSize.SMALL,
            captureScreenSize(LargeRoundWatch.copy(fontScale = 1.2f))
        )
    }

    @Test
    fun rememberWearAdaptiveSpec_reportsCompactRoundTokens() {
        val compactRound = captureAdaptiveSpec(SmallRoundWatch)
        assertEquals(WearWindowClass.COMPACT, compactRound.windowClass)
        assertEquals(WearScreenSize.SMALL, compactRound.screenSize)
        assertTrue(compactRound.isRound)
        assertEquals(6.dp, compactRound.headerTopSafeInset)
        assertEquals(14.dp, compactRound.dialogCornerRadius)
        assertEquals(10.dp, compactRound.dialogHorizontalPadding)
        assertEquals(7.dp, compactRound.dialogVerticalPadding)
        assertEquals(104.dp, compactRound.dialogBodyMaxHeight)
        assertEquals(110.dp, compactRound.helpDialogMaxHeight)
        assertEquals(18.dp, compactRound.edgeSwipeWidth)
        assertEquals(0.26f, compactRound.edgeSwipeHeightFraction, 0f)
        assertEquals(48.dp, compactRound.edgeSwipeThreshold)
    }

    @Test
    fun rememberWearAdaptiveSpec_reportsStandardSquareTokens() {
        val standardSquare = captureAdaptiveSpec(MediumSquareWatch)
        assertEquals(WearWindowClass.STANDARD, standardSquare.windowClass)
        assertEquals(WearScreenSize.MEDIUM, standardSquare.screenSize)
        assertTrue(standardSquare.isSquare)
        assertEquals(0.dp, standardSquare.headerTopSafeInset)
        assertEquals(14.dp, standardSquare.dialogCornerRadius)
        assertEquals(14.dp, standardSquare.dialogHorizontalPadding)
        assertEquals(9.dp, standardSquare.dialogVerticalPadding)
        assertEquals(134.dp, standardSquare.dialogBodyMaxHeight)
        assertEquals(142.dp, standardSquare.helpDialogMaxHeight)
        assertEquals(20.dp, standardSquare.edgeSwipeWidth)
        assertEquals(0.22f, standardSquare.edgeSwipeHeightFraction, 0f)
        assertEquals(56.dp, standardSquare.edgeSwipeThreshold)
    }

    @Test
    fun rememberWearAdaptiveSpec_reportsExpandedRoundTokens() {
        val expandedRound = captureAdaptiveSpec(LargeRoundWatch)
        assertEquals(WearWindowClass.EXPANDED, expandedRound.windowClass)
        assertEquals(WearScreenSize.LARGE, expandedRound.screenSize)
        assertTrue(expandedRound.isRound)
        assertEquals(0.dp, expandedRound.headerTopSafeInset)
        assertEquals(18.dp, expandedRound.dialogCornerRadius)
        assertEquals(14.dp, expandedRound.dialogHorizontalPadding)
        assertEquals(9.dp, expandedRound.dialogVerticalPadding)
        assertEquals(142.dp, expandedRound.dialogBodyMaxHeight)
        assertEquals(160.dp, expandedRound.helpDialogMaxHeight)
        assertEquals(22.dp, expandedRound.edgeSwipeWidth)
        assertEquals(0.20f, expandedRound.edgeSwipeHeightFraction, 0f)
        assertEquals(60.dp, expandedRound.edgeSwipeThreshold)
    }

    @Test
    fun rememberWearAdaptiveSpec_compactsWhenFontScaleGetsLarge() {
        val accessibilitySpec = captureAdaptiveSpec(LargeRoundWatch.copy(fontScale = 1.2f))
        assertEquals(WearWindowClass.COMPACT, accessibilitySpec.windowClass)
        assertEquals(WearScreenSize.SMALL, accessibilitySpec.screenSize)
        assertEquals(1.2f, accessibilitySpec.fontScale, 0f)
    }

    @Test
    fun rememberWearAdaptiveSpec_clampsFontScaleUpperBound() {
        val clampedSpec = captureAdaptiveSpec(LargeRoundWatch.copy(fontScale = 2f))
        assertEquals(WearWindowClass.COMPACT, clampedSpec.windowClass)
        assertEquals(1.6f, clampedSpec.fontScale, 0f)
    }

    private fun captureScreenSize(config: WearDeviceTestConfig): WearScreenSize {
        var captured: WearScreenSize? = null
        setAdaptiveContent(config) {
            val screenSize = rememberWearScreenSize()
            SideEffect { captured = screenSize }
        }
        return requireNotNull(captured)
    }

    private fun captureAdaptiveSpec(config: WearDeviceTestConfig): WearAdaptiveSpec {
        var captured: WearAdaptiveSpec? = null
        setAdaptiveContent(config) {
            val adaptive = rememberWearAdaptiveSpec()
            SideEffect { captured = adaptive }
        }
        return requireNotNull(captured)
    }

    private fun setAdaptiveContent(
        config: WearDeviceTestConfig,
        content: @Composable () -> Unit
    ) {
        composeRule.setContent {
            WithWearDeviceConfig(config = config, content = content)
        }
        composeRule.waitForIdle()
    }
}
