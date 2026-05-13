package com.glancemap.glancemapwearos.presentation.features.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.LocationDisabled
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.IconButton
import androidx.wear.compose.material3.IconButtonDefaults
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import androidx.wear.tooling.preview.devices.WearDevices
import com.glancemap.glancemapwearos.presentation.features.settings.SettingsViewModel
import com.glancemap.glancemapwearos.presentation.navigation.WatchRoutes
import com.glancemap.glancemapwearos.presentation.ui.WearScreenSize
import com.glancemap.glancemapwearos.presentation.ui.rememberWearAdaptiveSpec
import com.glancemap.glancemapwearos.presentation.ui.rememberWearScreenSize
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf

@Composable
fun MainScreen(
    navController: NavHostController,
    settingsViewModel: SettingsViewModel? = null,
) {
    val offlineModeFlow = settingsViewModel?.offlineMode ?: flowOf(false)
    val offlineMode by offlineModeFlow.collectAsState(initial = false)
    var gpsStatusMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(gpsStatusMessage) {
        if (gpsStatusMessage == null) return@LaunchedEffect
        delay(1_500L)
        gpsStatusMessage = null
    }

    val screenSize = rememberWearScreenSize()
    val adaptive = rememberWearAdaptiveSpec()
    val horizontalPadding =
        when (screenSize) {
            WearScreenSize.LARGE -> 24.dp
            WearScreenSize.MEDIUM -> 20.dp
            WearScreenSize.SMALL -> 16.dp
        }
    val baseButtonWidth =
        when (screenSize) {
            WearScreenSize.LARGE -> 116.dp
            WearScreenSize.MEDIUM -> 108.dp
            WearScreenSize.SMALL -> 100.dp
        }
    val verticalSpacing =
        when (screenSize) {
            WearScreenSize.LARGE -> 8.dp
            WearScreenSize.MEDIUM -> 7.dp
            WearScreenSize.SMALL -> 6.dp
        }
    val settingsButtonBottomPadding =
        when (screenSize) {
            WearScreenSize.LARGE -> 5.dp
            WearScreenSize.MEDIUM -> 4.dp
            WearScreenSize.SMALL -> 3.dp
        }
    val settingsButtonSize =
        when (screenSize) {
            WearScreenSize.LARGE -> 24.dp
            WearScreenSize.MEDIUM -> 22.dp
            WearScreenSize.SMALL -> 20.dp
        }
    val navigateIconButtonSize =
        when (screenSize) {
            WearScreenSize.LARGE -> 44.dp
            WearScreenSize.MEDIUM -> 44.dp
            WearScreenSize.SMALL -> 44.dp
        }
    val navigateIconButtonHeight =
        when (screenSize) {
            WearScreenSize.LARGE -> 80.dp
            WearScreenSize.MEDIUM -> 76.dp
            WearScreenSize.SMALL -> 72.dp
        }
    val navigateIconSize =
        when (screenSize) {
            WearScreenSize.LARGE -> 26.dp
            WearScreenSize.MEDIUM -> 24.dp
            WearScreenSize.SMALL -> 22.dp
        }
    val navigateIconEdgePadding =
        when (screenSize) {
            WearScreenSize.LARGE -> 10.dp
            WearScreenSize.MEDIUM -> 8.dp
            WearScreenSize.SMALL -> 8.dp
        }
    val modeToggleButtonSize =
        when (screenSize) {
            WearScreenSize.LARGE -> 26.dp
            WearScreenSize.MEDIUM -> 24.dp
            WearScreenSize.SMALL -> 22.dp
        }
    val modeToggleIconSize =
        when (screenSize) {
            WearScreenSize.LARGE -> 16.dp
            WearScreenSize.MEDIUM -> 15.dp
            WearScreenSize.SMALL -> 14.dp
        }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val compactScreen = adaptive.isRound && minOf(maxWidth, maxHeight) < 200.dp
        val centerButtonHeight =
            when (screenSize) {
                WearScreenSize.LARGE -> 44.dp
                WearScreenSize.MEDIUM -> 42.dp
                WearScreenSize.SMALL -> if (compactScreen) 36.dp else 40.dp
            }
        val centerButtonIconSize =
            when (screenSize) {
                WearScreenSize.LARGE -> 18.dp
                WearScreenSize.MEDIUM -> 17.dp
                WearScreenSize.SMALL -> if (compactScreen) 15.dp else 16.dp
            }
        val centerVerticalSpacing = if (compactScreen) 4.dp else verticalSpacing
        val contentHorizontalPadding = if (compactScreen) 0.dp else horizontalPadding
        val centerSideGap = if (compactScreen) 6.dp else 8.dp
        val leftReservedWidth = modeToggleButtonSize + navigateIconEdgePadding + centerSideGap
        val rightReservedWidth = navigateIconButtonSize + navigateIconEdgePadding + centerSideGap
        val centerAvailableWidth = (maxWidth - leftReservedWidth - rightReservedWidth).coerceAtLeast(84.dp)
        val centerButtonWidth =
            if (adaptive.isRound) {
                if (compactScreen) {
                    centerAvailableWidth.coerceIn(86.dp, baseButtonWidth)
                } else {
                    baseButtonWidth
                }
            } else {
                // Square screens have no circular edge clipping; use a slightly wider centered lane.
                (maxWidth - (horizontalPadding * 2)).coerceIn(baseButtonWidth, 148.dp)
            }
        val centerColumnOffset =
            if (adaptive.isRound && compactScreen) {
                (modeToggleButtonSize - navigateIconButtonSize) / 3
            } else {
                0.dp
            }
        val centerRowYOffset = 0.dp

        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier =
                    Modifier
                        .align(Alignment.Center)
                        .offset(x = centerColumnOffset)
                        .padding(horizontal = contentHorizontalPadding),
                verticalArrangement = Arrangement.spacedBy(centerVerticalSpacing, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                HomeActionButton(
                    label = "POI",
                    icon = Icons.Filled.Place,
                    width = centerButtonWidth,
                    height = centerButtonHeight,
                    iconSize = centerButtonIconSize,
                    compact = compactScreen,
                    onClick = { navController.navigate(WatchRoutes.POI) },
                )
                HomeActionButton(
                    label = "GPX",
                    icon = Icons.Filled.Timeline,
                    width = centerButtonWidth,
                    height = centerButtonHeight,
                    iconSize = centerButtonIconSize,
                    compact = compactScreen,
                    onClick = { navController.navigate(WatchRoutes.GPX) },
                )
                HomeActionButton(
                    label = "Maps",
                    icon = Icons.Filled.Map,
                    width = centerButtonWidth,
                    height = centerButtonHeight,
                    iconSize = centerButtonIconSize,
                    compact = compactScreen,
                    onClick = { navController.navigate(WatchRoutes.MAPS) },
                )
                HomeActionButton(
                    label = "Download",
                    icon = Icons.Filled.Download,
                    width = centerButtonWidth,
                    height = centerButtonHeight,
                    iconSize = centerButtonIconSize,
                    compact = compactScreen,
                    onClick = { navController.navigate(WatchRoutes.DOWNLOAD) },
                )
            }

            IconButton(
                onClick = {
                    val nextOfflineMode = !offlineMode
                    settingsViewModel?.setOfflineMode(nextOfflineMode)
                    gpsStatusMessage = if (nextOfflineMode) "GPS deactivated" else "GPS activated"
                },
                modifier =
                    Modifier
                        .align(Alignment.CenterStart)
                        .offset(y = centerRowYOffset)
                        .padding(start = navigateIconEdgePadding)
                        .size(modeToggleButtonSize),
                colors =
                    IconButtonDefaults.iconButtonColors(
                        containerColor = if (offlineMode) Color(0xFF222222) else MaterialTheme.colorScheme.primary,
                        contentColor = if (offlineMode) Color(0xFFE53935) else MaterialTheme.colorScheme.onPrimary,
                    ),
            ) {
                Icon(
                    imageVector = if (offlineMode) Icons.Filled.LocationDisabled else Icons.Filled.MyLocation,
                    contentDescription = if (offlineMode) "Offline mode" else "Online mode",
                    modifier = Modifier.size(modeToggleIconSize),
                )
            }

            IconButton(
                onClick = { navController.navigate(WatchRoutes.NAVIGATE) },
                modifier =
                    Modifier
                        .align(Alignment.CenterEnd)
                        .offset(y = centerRowYOffset)
                        .padding(end = navigateIconEdgePadding)
                        .width(navigateIconButtonSize)
                        .height(navigateIconButtonHeight),
                colors =
                    IconButtonDefaults.iconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
            ) {
                Icon(
                    imageVector = Icons.Default.Explore,
                    contentDescription = "Navigate",
                    modifier = Modifier.size(navigateIconSize),
                )
            }

            IconButton(
                onClick = { navController.navigate(WatchRoutes.SETTINGS) },
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = settingsButtonBottomPadding)
                        .size(settingsButtonSize),
                colors =
                    IconButtonDefaults.iconButtonColors(
                        containerColor = Color.Black.copy(alpha = 0.8f),
                        contentColor = Color.White,
                    ),
            ) {
                Icon(Icons.Default.Settings, contentDescription = "Settings")
            }

            gpsStatusMessage?.let { message ->
                GpsStatusOverlay(
                    message = message,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
        }
    }
}

@Composable
private fun GpsStatusOverlay(
    message: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .background(
                    color = Color.Black.copy(alpha = 0.88f),
                    shape = RoundedCornerShape(14.dp),
                ).padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(
            text = message,
            color = Color.White,
            maxLines = 1,
        )
    }
}

@Composable
private fun HomeActionButton(
    label: String,
    icon: ImageVector,
    width: Dp,
    height: Dp,
    iconSize: Dp,
    compact: Boolean,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        modifier =
            Modifier
                .width(width)
                .height(height),
        contentPadding =
            if (compact) {
                PaddingValues(horizontal = 10.dp, vertical = 0.dp)
            } else {
                ButtonDefaults.ContentPadding
            },
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            modifier = Modifier.size(iconSize),
        )
        Spacer(Modifier.size(if (compact) 4.dp else ButtonDefaults.IconSpacing))
        Text(
            text = label,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Preview(device = WearDevices.SMALL_ROUND, showSystemUi = true)
@Composable
fun MainScreenPreview() {
    val navController = rememberSwipeDismissableNavController()
    MainScreen(navController)
}
