package com.glancemap.glancemapwearos.presentation.features.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.glancemap.glancemapwearos.presentation.design.theme.GlanceMapTheme
import com.glancemap.glancemapwearos.test.LargeRoundWatch
import com.glancemap.glancemapwearos.test.MediumSquareWatch
import com.glancemap.glancemapwearos.test.SmallRoundWatch
import com.glancemap.glancemapwearos.test.WearDeviceTestConfig
import com.glancemap.glancemapwearos.test.withWearDeviceConfig
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsAdaptiveTokensTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun rememberSettingsListTokens_usesCompactDefaults() {
        assertEquals(
            SettingsListTokens(
                horizontalPadding = 12.dp,
                topPadding = 16.dp,
                bottomPadding = 48.dp,
                itemSpacing = 6.dp,
            ),
            captureTokens(SmallRoundWatch),
        )
    }

    @Test
    fun rememberSettingsListTokens_usesStandardDefaults() {
        assertEquals(
            SettingsListTokens(
                horizontalPadding = 14.dp,
                topPadding = 20.dp,
                bottomPadding = 56.dp,
                itemSpacing = 7.dp,
            ),
            captureTokens(MediumSquareWatch),
        )
    }

    @Test
    fun rememberSettingsListTokens_usesExpandedDefaults() {
        assertEquals(
            SettingsListTokens(
                horizontalPadding = 16.dp,
                topPadding = 24.dp,
                bottomPadding = 64.dp,
                itemSpacing = 8.dp,
            ),
            captureTokens(LargeRoundWatch),
        )
    }

    @Test
    fun rememberSettingsListTokens_preservesCompactCustomSpacing() {
        assertEquals(
            SettingsListTokens(
                horizontalPadding = 10.dp,
                topPadding = 11.dp,
                bottomPadding = 12.dp,
                itemSpacing = 5.dp,
            ),
            captureTokens(SmallRoundWatch) {
                rememberSettingsListTokens(
                    compactHorizontal = 10.dp,
                    standardHorizontal = 20.dp,
                    expandedHorizontal = 30.dp,
                    compactTop = 11.dp,
                    standardTop = 21.dp,
                    expandedTop = 31.dp,
                    compactBottom = 12.dp,
                    standardBottom = 22.dp,
                    expandedBottom = 32.dp,
                    compactItemSpacing = 5.dp,
                    standardItemSpacing = 6.dp,
                    expandedItemSpacing = 7.dp,
                )
            },
        )
    }

    @Test
    fun rememberSettingsListTokens_preservesStandardCustomSpacing() {
        assertEquals(
            SettingsListTokens(
                horizontalPadding = 20.dp,
                topPadding = 21.dp,
                bottomPadding = 22.dp,
                itemSpacing = 6.dp,
            ),
            captureTokens(MediumSquareWatch) {
                rememberSettingsListTokens(
                    compactHorizontal = 10.dp,
                    standardHorizontal = 20.dp,
                    expandedHorizontal = 30.dp,
                    compactTop = 11.dp,
                    standardTop = 21.dp,
                    expandedTop = 31.dp,
                    compactBottom = 12.dp,
                    standardBottom = 22.dp,
                    expandedBottom = 32.dp,
                    compactItemSpacing = 5.dp,
                    standardItemSpacing = 6.dp,
                    expandedItemSpacing = 7.dp,
                )
            },
        )
    }

    @Test
    fun rememberSettingsListTokens_preservesExpandedCustomSpacing() {
        assertEquals(
            SettingsListTokens(
                horizontalPadding = 30.dp,
                topPadding = 31.dp,
                bottomPadding = 32.dp,
                itemSpacing = 7.dp,
            ),
            captureTokens(LargeRoundWatch) {
                rememberSettingsListTokens(
                    compactHorizontal = 10.dp,
                    standardHorizontal = 20.dp,
                    expandedHorizontal = 30.dp,
                    compactTop = 11.dp,
                    standardTop = 21.dp,
                    expandedTop = 31.dp,
                    compactBottom = 12.dp,
                    standardBottom = 22.dp,
                    expandedBottom = 32.dp,
                    compactItemSpacing = 5.dp,
                    standardItemSpacing = 6.dp,
                    expandedItemSpacing = 7.dp,
                )
            },
        )
    }

    private fun captureTokens(
        config: WearDeviceTestConfig,
        tokenFactory: @Composable () -> SettingsListTokens = { rememberSettingsListTokens() },
    ): SettingsListTokens {
        var captured: SettingsListTokens? = null
        composeRule.setContent {
            withWearDeviceConfig(config = config) {
                GlanceMapTheme {
                    val tokens = tokenFactory()
                    SideEffect { captured = tokens }
                }
            }
        }
        composeRule.waitForIdle()
        return requireNotNull(captured)
    }
}
