package com.glancemap.glancemapwearos.presentation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.wear.compose.material3.Text
import com.glancemap.glancemapwearos.presentation.design.theme.GlanceMapTheme
import com.glancemap.glancemapwearos.presentation.navigation.WatchRoutes
import com.glancemap.glancemapwearos.test.LargeRoundWatch
import com.glancemap.glancemapwearos.test.MediumSquareWatch
import com.glancemap.glancemapwearos.test.SmallRoundWatchLargeText
import com.glancemap.glancemapwearos.test.WearDeviceTestConfig
import com.glancemap.glancemapwearos.test.WithWearDeviceConfig
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DismissableScreenGestureTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun rightEdgeDragLeft_returnsToNavigateOnSmallRoundLargeText() {
        assertRightEdgeDragNavigates(SmallRoundWatchLargeText)
    }

    @Test
    fun rightEdgeDragLeft_returnsToNavigateOnMediumSquare() {
        assertRightEdgeDragNavigates(MediumSquareWatch)
    }

    @Test
    fun rightEdgeDragLeft_returnsToNavigateOnLargeRound() {
        assertRightEdgeDragNavigates(LargeRoundWatch)
    }

    @Test
    fun shortDragBelowThreshold_doesNotNavigateAway() {
        launchDismissableHost(MediumSquareWatch)

        composeRule.onNodeWithTag(TAG_DISMISSABLE_EDGE)
            .performTouchInput {
                down(centerRight)
                moveBy(Offset(-20f, 0f))
                up()
            }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(TAG_DISMISSABLE_DESTINATION)
            .assertIsDisplayed()
    }

    private fun launchDismissableHost(config: WearDeviceTestConfig) {
        composeRule.setContent {
            WithWearDeviceConfig(config = config) {
                GlanceMapTheme {
                    DismissableScreenTestHost()
                }
            }
        }
        composeRule.waitForIdle()
    }

    private fun assertRightEdgeDragNavigates(config: WearDeviceTestConfig) {
        launchDismissableHost(config)

        composeRule.onNodeWithTag(TAG_DISMISSABLE_EDGE)
            .performTouchInput {
                down(centerRight)
                moveBy(Offset(-200f, 0f))
                up()
            }
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(TAG_NAVIGATE_DESTINATION)
            .assertIsDisplayed()
    }

    @Composable
    private fun DismissableScreenTestHost() {
        val navController = rememberNavController()

        NavHost(
            navController = navController,
            startDestination = WatchRoutes.SETTINGS
        ) {
            composable(WatchRoutes.NAVIGATE) {
                Text(
                    text = "Navigate destination",
                    modifier = Modifier.testTag(TAG_NAVIGATE_DESTINATION)
                )
            }
            composable(WatchRoutes.SETTINGS) {
                DismissableScreen(
                    onDismiss = { navController.popBackStack() },
                    onSwipeLeftNavigate = { navController.navigateBackToNavigate() },
                    edgeGestureTestTag = TAG_DISMISSABLE_EDGE
                ) {
                    Text(
                        text = "Dismissable destination",
                        modifier = Modifier.testTag(TAG_DISMISSABLE_DESTINATION)
                    )
                }
            }
        }
    }

    private fun NavHostController.navigateBackToNavigate() {
        val popped = popBackStack(WatchRoutes.NAVIGATE, inclusive = false)
        if (!popped) {
            navigate(WatchRoutes.NAVIGATE) {
                popUpTo(WatchRoutes.NAVIGATE) {
                    inclusive = false
                    saveState = true
                }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    private companion object {
        const val TAG_DISMISSABLE_EDGE = "dismissable_screen_edge_lane"
        const val TAG_DISMISSABLE_DESTINATION = "dismissable_screen_destination"
        const val TAG_NAVIGATE_DESTINATION = "navigate_screen_destination"
    }
}
