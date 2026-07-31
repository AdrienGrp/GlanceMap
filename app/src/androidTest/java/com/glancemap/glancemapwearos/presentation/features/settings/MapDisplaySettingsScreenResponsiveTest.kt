package com.glancemap.glancemapwearos.presentation.features.settings

import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.glancemap.glancemapwearos.data.repository.SettingsRepositoryImpl
import com.glancemap.glancemapwearos.presentation.design.theme.GlanceMapTheme
import com.glancemap.glancemapwearos.test.SmallRoundWatchMaxText
import com.glancemap.glancemapwearos.test.WearDeviceTestConfig
import com.glancemap.glancemapwearos.test.withWearDeviceConfig
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MapDisplaySettingsScreenResponsiveTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun smallRoundMaxText_keepsShowTimeChipReachableAtScreenStart() {
        launchMapDisplaySettings(config = SmallRoundWatchMaxText)

        composeRule
            .onNodeWithText("General")
            .assertIsDisplayed()
        composeRule
            .onNodeWithTag(TAG_MAP_DISPLAY_SHOW_TIME_CHIP)
            .assertIsDisplayed()
            .assertHasClickAction()
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)
            .assertIsOn()
        composeRule
            .onNodeWithText("Time format")
            .assertIsDisplayed()
    }

    private fun launchMapDisplaySettings(config: WearDeviceTestConfig) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val repository = SettingsRepositoryImpl.getInstance(context)
        runBlocking {
            repository.resetToDefaults()
            repository.setShowTimeInNavigate(true)
        }
        val viewModel = SettingsViewModel(repository)

        composeRule.setContent {
            withWearDeviceConfig(config = config) {
                GlanceMapTheme {
                    MapDisplaySettingsScreen(
                        viewModel = viewModel,
                        onOpenGeneralSettings = {},
                    )
                }
            }
        }
        composeRule.waitForIdle()
    }
}
