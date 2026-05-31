package com.glancemap.glancemapwearos.presentation.features.maps

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.glancemap.glancemapwearos.presentation.design.theme.GlanceMapTheme
import com.glancemap.glancemapwearos.presentation.features.gpx.GpxInspectionAStats
import com.glancemap.glancemapwearos.presentation.features.gpx.InspectionAUiState
import com.glancemap.glancemapwearos.test.SmallRoundWatchMaxText
import com.glancemap.glancemapwearos.test.withWearDeviceConfig
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GpxInspectionPopupResponsiveTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun smallRoundMaxText_keepsInspectionActionReachable() {
        composeRule.setContent {
            withWearDeviceConfig(config = SmallRoundWatchMaxText) {
                GlanceMapTheme {
                    GpxInspectionPopupA(
                        state =
                            InspectionAUiState(
                                trackTitle = "Long ridge route",
                                a =
                                    GpxInspectionAStats(
                                        distanceFromStart = 4660.0,
                                        elevationGainFromStart = 364.0,
                                        elevationLossFromStart = 75.0,
                                        durationFromStartSec = 7200.0,
                                        distanceToEnd = 12840.0,
                                        elevationGainToEnd = 955.0,
                                        elevationLossToEnd = 1120.0,
                                        durationToEndSec = 16200.0,
                                    ),
                            ),
                        onDismiss = {},
                        onSelectB = {},
                        isMetric = true,
                    )
                }
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Dist").assertIsDisplayed()
        composeRule.onNodeWithText("ETA").assertIsDisplayed()
        composeRule
            .onNodeWithContentDescription("Add second point B")
            .assertIsDisplayed()
        composeRule
            .onNodeWithText("Add 2nd point (B)")
            .assertIsDisplayed()
            .assertHasClickAction()
            .assertHeightIsAtLeast(48.dp)
    }
}
