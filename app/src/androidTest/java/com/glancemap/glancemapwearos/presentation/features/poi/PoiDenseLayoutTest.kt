package com.glancemap.glancemapwearos.presentation.features.poi

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.glancemap.glancemapwearos.data.repository.PoiType
import com.glancemap.glancemapwearos.presentation.design.theme.GlanceMapTheme
import com.glancemap.glancemapwearos.test.SmallRoundWatchMaxText
import com.glancemap.glancemapwearos.test.withWearDeviceConfig
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PoiDenseLayoutTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun smallRoundMaxText_keepsCategoryRowExpandTargetReachable() {
        composeRule.setContent {
            withWearDeviceConfig(config = SmallRoundWatchMaxText) {
                GlanceMapTheme {
                    Box(modifier = Modifier.fillMaxSize()) {
                        PoiCategoryRow(
                            category =
                                PoiCategoryUiState(
                                    id = 7,
                                    name = "Long mountain shelters category",
                                    parentId = null,
                                    depth = 0,
                                    hasChildren = true,
                                    enabled = true,
                                ),
                            categoryCount =
                                PoiCategoryCountUiState(
                                    isLoaded = true,
                                    enabledPoiCount = 12,
                                    totalPoiCount = 340,
                                ),
                            isExpanded = false,
                            categoryIndentStep = 8.dp,
                            actionButtonSize = 24.dp,
                            actionIconSize = 13.dp,
                            compactMode = true,
                            onToggle = {},
                            onToggleExpanded = {},
                        )
                    }
                }
            }
        }
        composeRule.waitForIdle()

        composeRule
            .onNodeWithText("Long mountain shelters category")
            .assertIsDisplayed()
        composeRule
            .onNodeWithText("12/340 POI")
            .assertIsDisplayed()
        composeRule
            .onNodeWithContentDescription("Expand folder")
            .assertIsDisplayed()
            .assertHasClickAction()
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun smallRoundMaxText_keepsFileRowActionTargetsReachable() {
        composeRule.setContent {
            withWearDeviceConfig(config = SmallRoundWatchMaxText) {
                GlanceMapTheme {
                    Box(modifier = Modifier.fillMaxSize()) {
                        PoiFileRow(
                            file =
                                PoiFileUiState(
                                    name = "Very long POI file",
                                    path = "/tmp/pois.poi",
                                    isEnabled = true,
                                    isExpanded = false,
                                    categories = emptyList(),
                                    enabledPoiCount = 3,
                                    totalPoiCount = 120,
                                ),
                            showDelete = false,
                            rowSpacing = 5.dp,
                            actionButtonSize = 28.dp,
                            actionIconSize = 14.dp,
                            compactMode = true,
                            onToggle = {},
                            onToggleExpanded = {},
                            onDelete = {},
                        )
                    }
                }
            }
        }
        composeRule.waitForIdle()

        composeRule
            .onNodeWithContentDescription("Expand categories")
            .assertIsDisplayed()
            .assertHasClickAction()
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)
    }

    @Test
    fun smallRoundMaxText_keepsSavedPoiRenameAndDeleteTargetsReachable() {
        launchSavedPoiActionRow(showRename = true)
        composeRule
            .onNodeWithContentDescription("Rename saved place")
            .assertIsDisplayed()
            .assertHasClickAction()
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)

        launchSavedPoiActionRow(showDelete = true)
        composeRule
            .onNodeWithContentDescription("Delete saved place")
            .assertIsDisplayed()
            .assertHasClickAction()
            .assertWidthIsAtLeast(48.dp)
            .assertHeightIsAtLeast(48.dp)
    }

    private fun launchSavedPoiActionRow(
        showRename: Boolean = false,
        showDelete: Boolean = false,
    ) {
        composeRule.setContent {
            withWearDeviceConfig(config = SmallRoundWatchMaxText) {
                GlanceMapTheme {
                    Box(modifier = Modifier.fillMaxSize()) {
                        PoiCategoryPoiRow(
                            point =
                                PoiCategoryPreviewPointUiState(
                                    id = 42L,
                                    name = "Long saved mountain water point",
                                    type = PoiType.WATER,
                                    lat = 45.0,
                                    lon = 6.0,
                                ),
                            filePath = USER_POI_TEST_PATH,
                            depth = 1,
                            categoryIndentStep = 8.dp,
                            compactMode = true,
                            tapToCenterEnabled = true,
                            showDelete = showDelete,
                            showRename = showRename,
                            onDelete = {},
                            onRename = {},
                            onClick = {},
                        )
                    }
                }
            }
        }
        composeRule.waitForIdle()
    }
}

private const val USER_POI_TEST_PATH = "user-poi://test"
