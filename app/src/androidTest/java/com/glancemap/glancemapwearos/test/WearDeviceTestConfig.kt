package com.glancemap.glancemapwearos.test

import android.content.res.Configuration
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.test.DeviceConfigurationOverride
import androidx.compose.ui.test.DeviceConfigurationOverride as ApplyDeviceConfigurationOverride
import androidx.compose.ui.test.FontScale
import androidx.compose.ui.test.ForcedSize
import androidx.compose.ui.test.RoundScreen
import androidx.compose.ui.test.then
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

internal data class WearDeviceTestConfig(
    val size: DpSize,
    val isRound: Boolean,
    val fontScale: Float = 1f
)

internal val SmallRoundWatch = WearDeviceTestConfig(
    size = DpSize(192.dp, 192.dp),
    isRound = true
)

internal val SmallRoundWatchLargeText = WearDeviceTestConfig(
    size = DpSize(192.dp, 192.dp),
    isRound = true,
    fontScale = 1.2f
)

internal val MediumRoundWatch = WearDeviceTestConfig(
    size = DpSize(208.dp, 208.dp),
    isRound = true
)

internal val MediumSquareWatch = WearDeviceTestConfig(
    size = DpSize(208.dp, 208.dp),
    isRound = false
)

internal val LargeRoundWatch = WearDeviceTestConfig(
    size = DpSize(240.dp, 240.dp),
    isRound = true
)

@Composable
internal fun WithWearDeviceConfig(
    config: WearDeviceTestConfig,
    content: @Composable () -> Unit
) {
    val baseConfiguration = LocalConfiguration.current
    val overriddenConfiguration = remember(baseConfiguration, config) {
        Configuration(baseConfiguration).apply {
            screenWidthDp = config.size.width.value.roundToInt()
            screenHeightDp = config.size.height.value.roundToInt()
            smallestScreenWidthDp = minOf(screenWidthDp, screenHeightDp)
            fontScale = config.fontScale
            screenLayout = screenLayout and Configuration.SCREENLAYOUT_ROUND_MASK.inv()
            screenLayout = screenLayout or if (config.isRound) {
                Configuration.SCREENLAYOUT_ROUND_YES
            } else {
                Configuration.SCREENLAYOUT_ROUND_NO
            }
        }
    }

    CompositionLocalProvider(LocalConfiguration provides overriddenConfiguration) {
        ApplyDeviceConfigurationOverride(
            override = DeviceConfigurationOverride.ForcedSize(config.size)
                .then(DeviceConfigurationOverride.RoundScreen(config.isRound))
                .then(DeviceConfigurationOverride.FontScale(config.fontScale))
        ) {
            Box(modifier = Modifier.requiredSize(config.size)) {
                content()
            }
        }
    }
}
