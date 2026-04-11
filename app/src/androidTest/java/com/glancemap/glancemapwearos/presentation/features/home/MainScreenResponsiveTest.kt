package com.glancemap.glancemapwearos.presentation.features.home

import androidx.compose.runtime.Composable
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
class MainScreenResponsiveTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun smallRoundLargeText_keepsCoreActionsVisible() {
        launchMainScreen(config = SmallRoundWatchLargeText)

        composeRule.onNodeWithText("POI").assertIsDisplayed()
        composeRule.onNodeWithText("GPX").assertIsDisplayed()
        composeRule.onNodeWithText("Maps").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Navigate").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Settings").assertIsDisplayed()
    }

    @Test
    fun mediumSquare_navigatesToPoi() {
        launchMainScreen(config = MediumSquareWatch)
        composeRule.onNodeWithText("POI").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("POI destination").assertIsDisplayed()
    }

    @Test
    fun mediumSquare_navigatesToSettings() {
        launchMainScreen(config = MediumSquareWatch)
        composeRule.onNodeWithContentDescription("Settings").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Settings destination").assertIsDisplayed()
    }

    @Test
    fun largeRound_navigatesToMaps() {
        launchMainScreen(config = LargeRoundWatch)
        composeRule.onNodeWithText("Maps").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Maps destination").assertIsDisplayed()
    }

    @Test
    fun largeRound_navigatesToNavigate() {
        launchMainScreen(config = LargeRoundWatch)
        composeRule.onNodeWithContentDescription("Navigate").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Navigate destination").assertIsDisplayed()
    }

    private fun launchMainScreen(config: WearDeviceTestConfig) {
        composeRule.setContent {
            WithWearDeviceConfig(config = config) {
                GlanceMapTheme {
                    MainScreenTestHost()
                }
            }
        }
        composeRule.waitForIdle()
    }
}

@Composable
private fun MainScreenTestHost() {
    val navController = rememberNavController()
    NavHost(
        navController = navController,
        startDestination = WatchRoutes.MAIN_MENU
    ) {
        composable(WatchRoutes.MAIN_MENU) {
            MainScreen(navController = navController)
        }
        composableDestination(route = WatchRoutes.POI, label = "POI destination")
        composableDestination(route = WatchRoutes.GPX, label = "GPX destination")
        composableDestination(route = WatchRoutes.MAPS, label = "Maps destination")
        composableDestination(route = WatchRoutes.NAVIGATE, label = "Navigate destination")
        composableDestination(route = WatchRoutes.SETTINGS, label = "Settings destination")
    }
}

private fun androidx.navigation.NavGraphBuilder.composableDestination(
    route: String,
    label: String
) {
    composable(route) {
        Text(text = label)
    }
}
