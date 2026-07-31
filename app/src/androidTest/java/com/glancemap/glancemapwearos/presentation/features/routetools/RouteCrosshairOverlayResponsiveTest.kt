package com.glancemap.glancemapwearos.presentation.features.routetools

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.glancemap.glancemapwearos.presentation.design.theme.GlanceMapTheme
import com.glancemap.glancemapwearos.presentation.ui.WearScreenSize
import com.glancemap.glancemapwearos.test.SmallRoundWatchMaxText
import com.glancemap.glancemapwearos.test.withWearDeviceConfig
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.mapsforge.core.model.LatLong

@RunWith(AndroidJUnit4::class)
class RouteCrosshairOverlayResponsiveTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun smallRoundMaxText_keepsMultiPointRouteActionsReachable() {
        val session =
            RouteToolSession(
                options = RouteToolOptions(createMode = RouteCreateMode.MULTI_POINT_CHAIN),
                chainPoints =
                    listOf(
                        LatLong(45.0, 6.0),
                        LatLong(45.01, 6.01),
                    ),
            )

        composeRule.setContent {
            withWearDeviceConfig(config = SmallRoundWatchMaxText) {
                GlanceMapTheme {
                    Box(modifier = Modifier.fillMaxSize()) {
                        RouteCrosshairOverlay(
                            session = session,
                            screenSize = WearScreenSize.SMALL,
                            isMetric = true,
                            onPickHere = {},
                            onCancel = {},
                            onUndoLastPoint = {},
                        )
                    }
                }
            }
        }
        composeRule.waitForIdle()

        composeRule.onNodeWithText("Multi-point • 2").assertIsDisplayed()
        composeRule.onNodeWithText("Add point").assertIsDisplayed()
        composeRule
            .onNodeWithContentDescription("Close route tool")
            .assertIsDisplayed()
            .assertHasClickAction()
        composeRule
            .onNodeWithContentDescription("Remove last point")
            .assertIsDisplayed()
            .assertHasClickAction()
        composeRule
            .onNodeWithContentDescription("Select position")
            .assertIsDisplayed()
            .assertHasClickAction()
    }
}
