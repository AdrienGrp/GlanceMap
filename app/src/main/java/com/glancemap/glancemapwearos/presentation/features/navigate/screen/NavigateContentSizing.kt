package com.glancemap.glancemapwearos.presentation.features.navigate

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.glancemap.glancemapwearos.data.repository.SettingsRepository
import com.glancemap.glancemapwearos.presentation.ui.WearAdaptiveSpec
import com.glancemap.glancemapwearos.presentation.ui.WearScreenSize

internal data class NavigateContentSizing(
    val gestureExclusionStripDp: Float,
    val zoomButtonSize: Dp,
    val zoomIconSize: Dp,
    val zoomLabelTopPadding: Dp,
    val zoomScaleBarWidth: Dp,
    val showZoomPlusButton: Boolean,
    val showZoomMinusButton: Boolean,
    val sideButtonSize: Dp,
    val sideButtonIconSize: Dp,
    val sideButtonEdgePadding: Dp,
    val liveElevationIconSize: Dp,
    val navButtonBottomPadding: Dp,
    val navButtonSize: Dp,
    val navButtonIconSize: Dp,
    val northIndicatorButtonSize: Dp,
    val northIndicatorIconSize: Dp,
    val permissionContentPadding: Dp,
    val permissionButtonMinHeight: Dp,
    val permissionScrollTopPadding: Dp,
    val permissionScrollBottomPadding: Dp,
)

@Composable
internal fun rememberNavigateContentSizing(
    adaptive: WearAdaptiveSpec,
    screenSize: WearScreenSize,
    mapZoomButtonsMode: String,
): NavigateContentSizing =
    remember(adaptive, screenSize, mapZoomButtonsMode) {
        val permissionContentPadding =
            if (adaptive.isRound) {
                when (screenSize) {
                    WearScreenSize.LARGE -> 16.dp
                    WearScreenSize.MEDIUM -> 14.dp
                    WearScreenSize.SMALL -> 12.dp
                }
            } else {
                when (screenSize) {
                    WearScreenSize.LARGE -> 18.dp
                    WearScreenSize.MEDIUM -> 16.dp
                    WearScreenSize.SMALL -> 14.dp
                }
            }
        NavigateContentSizing(
            gestureExclusionStripDp =
                if (adaptive.isRound) {
                    when (screenSize) {
                        WearScreenSize.LARGE -> 96f
                        WearScreenSize.MEDIUM -> 84f
                        WearScreenSize.SMALL -> 72f
                    }
                } else {
                    when (screenSize) {
                        WearScreenSize.LARGE -> 84f
                        WearScreenSize.MEDIUM -> 72f
                        WearScreenSize.SMALL -> 60f
                    }
                },
            zoomButtonSize =
                if (adaptive.isRound) {
                    when (screenSize) {
                        WearScreenSize.LARGE -> 26.dp
                        WearScreenSize.MEDIUM -> 24.dp
                        WearScreenSize.SMALL -> 22.dp
                    }
                } else {
                    when (screenSize) {
                        WearScreenSize.LARGE -> 28.dp
                        WearScreenSize.MEDIUM -> 26.dp
                        WearScreenSize.SMALL -> 24.dp
                    }
                },
            zoomIconSize =
                if (adaptive.isRound) {
                    when (screenSize) {
                        WearScreenSize.LARGE -> 18.dp
                        WearScreenSize.MEDIUM -> 16.dp
                        WearScreenSize.SMALL -> 14.dp
                    }
                } else {
                    when (screenSize) {
                        WearScreenSize.LARGE -> 19.dp
                        WearScreenSize.MEDIUM -> 17.dp
                        WearScreenSize.SMALL -> 15.dp
                    }
                },
            zoomLabelTopPadding =
                if (adaptive.isRound) {
                    when (screenSize) {
                        WearScreenSize.LARGE -> 24.dp
                        WearScreenSize.MEDIUM -> 22.dp
                        WearScreenSize.SMALL -> 18.dp
                    }
                } else {
                    when (screenSize) {
                        WearScreenSize.LARGE -> 20.dp
                        WearScreenSize.MEDIUM -> 18.dp
                        WearScreenSize.SMALL -> 16.dp
                    }
                },
            zoomScaleBarWidth =
                if (adaptive.isRound) {
                    when (screenSize) {
                        WearScreenSize.LARGE -> 52.dp
                        WearScreenSize.MEDIUM -> 48.dp
                        WearScreenSize.SMALL -> 42.dp
                    }
                } else {
                    when (screenSize) {
                        WearScreenSize.LARGE -> 58.dp
                        WearScreenSize.MEDIUM -> 54.dp
                        WearScreenSize.SMALL -> 48.dp
                    }
                },
            showZoomPlusButton =
                mapZoomButtonsMode != SettingsRepository.ZOOM_BUTTONS_HIDE_BOTH &&
                    mapZoomButtonsMode != SettingsRepository.ZOOM_BUTTONS_HIDE_PLUS,
            showZoomMinusButton = mapZoomButtonsMode != SettingsRepository.ZOOM_BUTTONS_HIDE_BOTH,
            sideButtonSize =
                if (adaptive.isRound) {
                    when (screenSize) {
                        WearScreenSize.LARGE -> 30.dp
                        WearScreenSize.MEDIUM -> 28.dp
                        WearScreenSize.SMALL -> 26.dp
                    }
                } else {
                    when (screenSize) {
                        WearScreenSize.LARGE -> 32.dp
                        WearScreenSize.MEDIUM -> 30.dp
                        WearScreenSize.SMALL -> 28.dp
                    }
                },
            sideButtonIconSize =
                if (adaptive.isRound) {
                    when (screenSize) {
                        WearScreenSize.LARGE -> 20.dp
                        WearScreenSize.MEDIUM -> 18.dp
                        WearScreenSize.SMALL -> 16.dp
                    }
                } else {
                    when (screenSize) {
                        WearScreenSize.LARGE -> 21.dp
                        WearScreenSize.MEDIUM -> 19.dp
                        WearScreenSize.SMALL -> 17.dp
                    }
                },
            sideButtonEdgePadding =
                if (adaptive.isRound) {
                    when (screenSize) {
                        WearScreenSize.LARGE -> 4.dp
                        WearScreenSize.MEDIUM -> 3.dp
                        WearScreenSize.SMALL -> 2.dp
                    }
                } else {
                    when (screenSize) {
                        WearScreenSize.LARGE -> 6.dp
                        WearScreenSize.MEDIUM -> 5.dp
                        WearScreenSize.SMALL -> 4.dp
                    }
                },
            liveElevationIconSize =
                if (adaptive.isRound) {
                    when (screenSize) {
                        WearScreenSize.LARGE -> 22.dp
                        WearScreenSize.MEDIUM -> 20.dp
                        WearScreenSize.SMALL -> 18.dp
                    }
                } else {
                    when (screenSize) {
                        WearScreenSize.LARGE -> 24.dp
                        WearScreenSize.MEDIUM -> 22.dp
                        WearScreenSize.SMALL -> 20.dp
                    }
                },
            navButtonBottomPadding =
                if (adaptive.isRound) {
                    when (screenSize) {
                        WearScreenSize.LARGE -> 4.dp
                        WearScreenSize.MEDIUM -> 3.dp
                        WearScreenSize.SMALL -> 2.dp
                    }
                } else {
                    when (screenSize) {
                        WearScreenSize.LARGE -> 8.dp
                        WearScreenSize.MEDIUM -> 7.dp
                        WearScreenSize.SMALL -> 6.dp
                    }
                },
            navButtonSize =
                if (adaptive.isRound) {
                    when (screenSize) {
                        WearScreenSize.LARGE -> 28.dp
                        WearScreenSize.MEDIUM -> 26.dp
                        WearScreenSize.SMALL -> 24.dp
                    }
                } else {
                    when (screenSize) {
                        WearScreenSize.LARGE -> 30.dp
                        WearScreenSize.MEDIUM -> 28.dp
                        WearScreenSize.SMALL -> 26.dp
                    }
                },
            navButtonIconSize =
                if (adaptive.isRound) {
                    when (screenSize) {
                        WearScreenSize.LARGE -> 20.dp
                        WearScreenSize.MEDIUM -> 18.dp
                        WearScreenSize.SMALL -> 16.dp
                    }
                } else {
                    when (screenSize) {
                        WearScreenSize.LARGE -> 21.dp
                        WearScreenSize.MEDIUM -> 19.dp
                        WearScreenSize.SMALL -> 17.dp
                    }
                },
            northIndicatorButtonSize =
                if (adaptive.isRound) {
                    when (screenSize) {
                        WearScreenSize.LARGE -> 20.dp
                        WearScreenSize.MEDIUM -> 18.dp
                        WearScreenSize.SMALL -> 16.dp
                    }
                } else {
                    when (screenSize) {
                        WearScreenSize.LARGE -> 22.dp
                        WearScreenSize.MEDIUM -> 20.dp
                        WearScreenSize.SMALL -> 18.dp
                    }
                },
            northIndicatorIconSize =
                if (adaptive.isRound) {
                    when (screenSize) {
                        WearScreenSize.LARGE -> 14.dp
                        WearScreenSize.MEDIUM -> 12.dp
                        WearScreenSize.SMALL -> 11.dp
                    }
                } else {
                    when (screenSize) {
                        WearScreenSize.LARGE -> 15.dp
                        WearScreenSize.MEDIUM -> 13.dp
                        WearScreenSize.SMALL -> 12.dp
                    }
                },
            permissionContentPadding = permissionContentPadding,
            permissionButtonMinHeight =
                when {
                    adaptive.fontScale >= 1.45f -> 56.dp
                    adaptive.fontScale >= 1.25f -> 50.dp
                    else -> 0.dp
                },
            permissionScrollTopPadding =
                permissionContentPadding +
                    adaptive.headerTopSafeInset +
                    if (adaptive.isRound) {
                        when {
                            adaptive.fontScale >= 1.45f -> 34.dp
                            adaptive.fontScale >= 1.25f -> 26.dp
                            else -> 18.dp
                        }
                    } else {
                        0.dp
                    },
            permissionScrollBottomPadding =
                permissionContentPadding +
                    when {
                        adaptive.fontScale >= 1.45f -> 36.dp
                        adaptive.fontScale >= 1.25f -> 28.dp
                        else -> 20.dp
                    },
        )
    }
