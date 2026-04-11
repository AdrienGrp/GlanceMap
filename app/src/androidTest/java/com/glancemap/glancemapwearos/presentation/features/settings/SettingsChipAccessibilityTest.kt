package com.glancemap.glancemapwearos.presentation.features.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.glancemap.glancemapwearos.presentation.design.theme.GlanceMapTheme
import com.glancemap.glancemapwearos.test.LargeRoundWatch
import com.glancemap.glancemapwearos.test.MediumSquareWatch
import com.glancemap.glancemapwearos.test.SmallRoundWatchLargeText
import com.glancemap.glancemapwearos.test.WearDeviceTestConfig
import com.glancemap.glancemapwearos.test.withWearDeviceConfig
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsChipAccessibilityTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun settingsChips_keepMinimumTouchTargetsOnSmallRoundLargeText() {
        assertChipTouchTargets(SmallRoundWatchLargeText)
    }

    @Test
    fun settingsChips_keepMinimumTouchTargetsOnMediumSquare() {
        assertChipTouchTargets(MediumSquareWatch)
    }

    @Test
    fun settingsChips_keepMinimumTouchTargetsOnLargeRound() {
        assertChipTouchTargets(LargeRoundWatch)
    }

    @Test
    fun toggleChip_remainsOperableWithLargeTextOnSmallRoundWatch() {
        launchChipShowcase(
            config = SmallRoundWatchLargeText,
            initialChip = SettingsChipCase.TOGGLE,
        )

        composeRule
            .onNodeWithTag(TAG_TOGGLE_CHIP)
            .assertIsDisplayed()
            .assertIsOff()
            .performClick()
            .assertIsOn()
    }

    private fun assertInteractiveNodeMeetsTouchTarget(tag: String) {
        composeRule
            .onNodeWithTag(tag)
            .assertIsDisplayed()
            .assertHasClickAction()
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)
    }

    private fun assertChipTouchTargets(config: WearDeviceTestConfig) {
        launchChipShowcase(config = config)

        assertInteractiveNodeMeetsTouchTarget(TAG_GENERAL_SHORTCUT)
        composeRule.runOnIdle { showcasedChip = SettingsChipCase.SECTION }
        composeRule.waitForIdle()

        assertInteractiveNodeMeetsTouchTarget(TAG_SECTION_CHIP)
        composeRule.runOnIdle { showcasedChip = SettingsChipCase.PICKER }
        composeRule.waitForIdle()

        assertInteractiveNodeMeetsTouchTarget(TAG_PICKER_CHIP)
        composeRule.runOnIdle { showcasedChip = SettingsChipCase.TOGGLE }
        composeRule.waitForIdle()

        assertInteractiveNodeMeetsTouchTarget(TAG_TOGGLE_CHIP)
    }

    private fun launchChipShowcase(
        config: WearDeviceTestConfig,
        initialChip: SettingsChipCase = SettingsChipCase.GENERAL_SHORTCUT,
    ) {
        showcasedChip = initialChip
        composeRule.setContent {
            withWearDeviceConfig(config = config) {
                GlanceMapTheme {
                    settingsChipShowcase()
                }
            }
        }
        composeRule.waitForIdle()
    }

    @Composable
    private fun settingsChipShowcase() {
        var enabled by remember { mutableStateOf(false) }

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            when (showcasedChip) {
                SettingsChipCase.GENERAL_SHORTCUT ->
                    GeneralSettingsShortcutChip(
                        onClick = {},
                        modifier = Modifier.testTag(TAG_GENERAL_SHORTCUT),
                    )

                SettingsChipCase.SECTION ->
                    SettingsSectionChip(
                        label = "Map settings",
                        onClick = {},
                        modifier = Modifier.testTag(TAG_SECTION_CHIP),
                    )

                SettingsChipCase.PICKER ->
                    SettingsPickerChip(
                        label = "Units",
                        secondaryLabel = "Metric",
                        onClick = {},
                        modifier = Modifier.testTag(TAG_PICKER_CHIP),
                    )

                SettingsChipCase.TOGGLE ->
                    SettingsToggleChip(
                        checked = enabled,
                        onCheckedChanged = { enabled = it },
                        label = "Offline maps",
                        secondaryLabel = "Keep files on watch",
                        modifier = Modifier.testTag(TAG_TOGGLE_CHIP),
                    )
            }
        }
    }

    private companion object {
        const val TAG_GENERAL_SHORTCUT = "settings_general_shortcut_chip"
        const val TAG_SECTION_CHIP = "settings_section_chip"
        const val TAG_PICKER_CHIP = "settings_picker_chip"
        const val TAG_TOGGLE_CHIP = "settings_toggle_chip"

        var showcasedChip by mutableStateOf(SettingsChipCase.GENERAL_SHORTCUT)
    }
}

private enum class SettingsChipCase {
    GENERAL_SHORTCUT,
    SECTION,
    PICKER,
    TOGGLE,
}
