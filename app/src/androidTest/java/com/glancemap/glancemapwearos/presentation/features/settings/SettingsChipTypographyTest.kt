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
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.glancemap.glancemapwearos.presentation.design.theme.GlanceMapTheme
import com.glancemap.glancemapwearos.test.SmallRoundWatchLargeText
import com.glancemap.glancemapwearos.test.withWearDeviceConfig
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsChipTypographyTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun generalShortcutChip_keepsSecondaryTextVisibleOnSmallRoundLargeText() {
        launchChipShowcase(showcase = TypographyShowcase.GENERAL_SHORTCUT)

        composeRule
            .onNodeWithTag(TAG_TYPOGRAPHY_CHIP)
            .assertIsDisplayed()
            .assertHasClickAction()
        composeRule.onNodeWithText("General Settings", useUnmergedTree = true).assertIsDisplayed()
        composeRule
            .onNodeWithText(
                "Back to settings menu",
                useUnmergedTree = true,
            ).assertIsDisplayed()
    }

    @Test
    fun pickerChip_keepsPrimaryAndSecondaryTextVisibleOnSmallRoundLargeText() {
        launchChipShowcase(showcase = TypographyShowcase.PICKER)

        composeRule
            .onNodeWithTag(TAG_TYPOGRAPHY_CHIP)
            .assertIsDisplayed()
            .assertHasClickAction()
        composeRule
            .onNodeWithText(
                "Clear partial transfer",
                useUnmergedTree = true,
            ).assertIsDisplayed()
        composeRule.onNodeWithText("1 file · 123.4 MB", useUnmergedTree = true).assertIsDisplayed()
    }

    @Test
    fun toggleChip_keepsPrimaryAndSecondaryTextVisibleOnSmallRoundLargeText() {
        launchChipShowcase(showcase = TypographyShowcase.TOGGLE)

        composeRule
            .onNodeWithTag(TAG_TYPOGRAPHY_CHIP)
            .assertIsDisplayed()
            .assertHasClickAction()
        composeRule.onNodeWithText("Offline maps", useUnmergedTree = true).assertIsDisplayed()
        composeRule
            .onNodeWithText(
                "Keep files on watch",
                useUnmergedTree = true,
            ).assertIsDisplayed()
    }

    private fun launchChipShowcase(showcase: TypographyShowcase) {
        typographyShowcase = showcase
        composeRule.setContent {
            withWearDeviceConfig(config = SmallRoundWatchLargeText) {
                GlanceMapTheme {
                    typographyChipShowcase()
                }
            }
        }
        composeRule.waitForIdle()
    }

    @Composable
    private fun typographyChipShowcase() {
        var enabled by remember { mutableStateOf(true) }

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            when (typographyShowcase) {
                TypographyShowcase.GENERAL_SHORTCUT ->
                    GeneralSettingsShortcutChip(
                        onClick = {},
                        modifier = Modifier.testTag(TAG_TYPOGRAPHY_CHIP),
                    )

                TypographyShowcase.PICKER ->
                    SettingsPickerChip(
                        label = "Clear partial transfer",
                        secondaryLabel = "1 file · 123.4 MB",
                        iconImageVector = null,
                        onClick = {},
                        modifier = Modifier.testTag(TAG_TYPOGRAPHY_CHIP),
                    )

                TypographyShowcase.TOGGLE ->
                    SettingsToggleChip(
                        checked = enabled,
                        onCheckedChanged = { enabled = it },
                        label = "Offline maps",
                        secondaryLabel = "Keep files on watch",
                        modifier = Modifier.testTag(TAG_TYPOGRAPHY_CHIP),
                    )
            }
        }
    }

    private companion object {
        const val TAG_TYPOGRAPHY_CHIP = "settings_typography_chip"

        var typographyShowcase by mutableStateOf(TypographyShowcase.GENERAL_SHORTCUT)
    }
}

private enum class TypographyShowcase {
    GENERAL_SHORTCUT,
    PICKER,
    TOGGLE,
}
