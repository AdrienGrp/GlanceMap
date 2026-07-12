package com.glancemap.glancemapwearos.presentation.features.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsBike
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.rotary.onPreRotaryScrollEvent
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.glancemap.glancemapwearos.data.repository.SettingsRepository
import com.glancemap.glancemapwearos.presentation.ui.WearHelpDialog
import com.glancemap.glancemapwearos.presentation.ui.WearScreenSize
import com.glancemap.glancemapwearos.presentation.ui.cappedFontScale
import com.glancemap.glancemapwearos.presentation.ui.rememberWearAdaptiveSpec
import com.glancemap.glancemapwearos.presentation.ui.rememberWearScreenSize
import java.text.DecimalFormat
import kotlin.math.round

@Suppress("FunctionNaming", "LongMethod")
@Composable
fun UserProfileSettingsScreen(
    viewModel: SettingsViewModel,
    onOpenGeneralSettings: () -> Unit,
    profileChangeEnabled: Boolean = true,
) {
    val listTokens = rememberSettingsListTokens()
    val isMetric by viewModel.isMetric.collectAsState()
    val activityProfile by viewModel.activityProfile.collectAsState()
    val userWeightKg by viewModel.userWeightKg.collectAsState()
    val backpackWeightKg by viewModel.backpackWeightKg.collectAsState()
    val bikeWeightKg by viewModel.bikeWeightKg.collectAsState()
    var showWeightPicker by remember { mutableStateOf(false) }
    var showBackpackPicker by remember { mutableStateOf(false) }
    var showBikeWeightPicker by remember { mutableStateOf(false) }
    var showInfoDialog by remember { mutableStateOf(false) }

    WearSettingsListScreen(listTokens = listTokens, horizontalAlignment = Alignment.CenterHorizontally) {
        item {
            SettingsInfoButton(
                contentDescription = "User profile info",
                onClick = { showInfoDialog = true },
            )
        }
        item {
            GeneralSettingsShortcutChip(
                onClick = onOpenGeneralSettings,
                applyTopPadding = false,
            )
        }
        item {
            SettingsOptionPickerRow(
                label = "Units",
                selectedValue = isMetric,
                options = listOf(true to "Metric", false to "Imperial"),
                secondaryLabel = if (isMetric) "Metric" else "Imperial",
                onSelect = viewModel::setMetric,
            )
        }
        item {
            ActivityProfileToggle(
                selectedProfile = activityProfile,
                onSelect = viewModel::setActivityProfile,
                enabled = profileChangeEnabled,
            )
        }
        item {
            SettingsPickerChip(
                label = "Body weight",
                secondaryLabel = formatUserWeight(userWeightKg, isMetric),
                onClick = { showWeightPicker = true },
            )
        }
        item {
            SettingsPickerChip(
                label = "Backpack",
                secondaryLabel = formatUserWeight(backpackWeightKg, isMetric),
                onClick = { showBackpackPicker = true },
            )
        }
        if (activityProfile == SettingsRepository.ACTIVITY_PROFILE_BIKE) {
            item {
                SettingsPickerChip(
                    label = "Bike weight",
                    secondaryLabel = formatUserWeight(bikeWeightKg, isMetric),
                    onClick = { showBikeWeightPicker = true },
                )
            }
        }
    }

    RotaryWeightPickerDialog(
        visible = showWeightPicker,
        title = "Body weight",
        valueKg = userWeightKg,
        minKg = SettingsRepository.MIN_USER_WEIGHT_KG,
        maxKg = SettingsRepository.MAX_USER_WEIGHT_KG,
        isMetric = isMetric,
        onDismiss = { showWeightPicker = false },
        onValueChange = viewModel::setUserWeightKg,
    )
    RotaryWeightPickerDialog(
        visible = showBackpackPicker,
        title = "Backpack",
        valueKg = backpackWeightKg,
        minKg = SettingsRepository.MIN_BACKPACK_WEIGHT_KG,
        maxKg = SettingsRepository.MAX_BACKPACK_WEIGHT_KG,
        isMetric = isMetric,
        onDismiss = { showBackpackPicker = false },
        onValueChange = viewModel::setBackpackWeightKg,
    )
    RotaryWeightPickerDialog(
        visible = showBikeWeightPicker,
        title = "Bike weight",
        valueKg = bikeWeightKg,
        minKg = SettingsRepository.MIN_BIKE_WEIGHT_KG,
        maxKg = SettingsRepository.MAX_BIKE_WEIGHT_KG,
        isMetric = isMetric,
        onDismiss = { showBikeWeightPicker = false },
        onValueChange = viewModel::setBikeWeightKg,
    )
    UserProfileInfoDialog(
        visible = showInfoDialog,
        onDismiss = { showInfoDialog = false },
    )
}

@Composable
private fun UserProfileInfoDialog(
    visible: Boolean,
    onDismiss: () -> Unit,
) {
    WearHelpDialog(
        visible = visible,
        title = "User profile",
        onDismiss = onDismiss,
        lines =
            listOf(
                "Weight and backpack improve hiking calories.\n" +
                    "Bike weight improves cycling ETA and calories.\n" +
                    "Activity changes default route tools, ETA and dashboard choices.\n" +
                    "Calories use Pandolf/Santee and LCDA with speed and slope.\n" +
                    "DEM elevation is used when available.",
            ),
    )
}

private fun formatUserWeight(
    weightKg: Float,
    isMetric: Boolean,
): String =
    if (isMetric) {
        "${ONE_DECIMAL_FORMAT.format(weightKg)} kg"
    } else {
        "${ONE_DECIMAL_FORMAT.format(weightKg * KG_TO_LB)} lb"
    }

@Suppress("FunctionNaming")
@Composable
private fun ActivityProfileToggle(
    selectedProfile: String,
    onSelect: (String) -> Unit,
    enabled: Boolean,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "Activity",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (!enabled) {
            Text(
                "Stop the recording to change profile",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ActivityProfileButton(
                label = "Hike",
                selected = selectedProfile != SettingsRepository.ACTIVITY_PROFILE_BIKE,
                onClick = { onSelect(SettingsRepository.ACTIVITY_PROFILE_HIKE) },
                enabled = enabled,
                icon = Icons.Default.Terrain,
                modifier = Modifier.weight(1f),
            )
            ActivityProfileButton(
                label = "Bike",
                selected = selectedProfile == SettingsRepository.ACTIVITY_PROFILE_BIKE,
                onClick = { onSelect(SettingsRepository.ACTIVITY_PROFILE_BIKE) },
                enabled = enabled,
                icon = Icons.AutoMirrored.Filled.DirectionsBike,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Suppress("FunctionNaming", "LongParameterList")
@Composable
private fun ActivityProfileButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    enabled: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier =
            modifier
                .heightIn(min = 58.dp),
        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
        colors =
            ButtonDefaults.buttonColors(
                containerColor =
                    if (selected) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        Color.White.copy(alpha = 0.10f)
                    },
                contentColor =
                    if (selected) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        Color.White
                    },
            ),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(19.dp),
            )
            Text(
                label,
                maxLines = 1,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun RotaryWeightPickerDialog(
    visible: Boolean,
    title: String,
    valueKg: Float,
    minKg: Float,
    maxKg: Float,
    isMetric: Boolean,
    onDismiss: () -> Unit,
    onValueChange: (Float) -> Unit,
) {
    if (!visible) return

    val adaptive = rememberWearAdaptiveSpec()
    val screenSize = rememberWearScreenSize()
    val focusRequester = remember { FocusRequester() }
    var localValueKg by remember(valueKg) {
        mutableFloatStateOf(valueKg.roundToTenth().coerceIn(minKg, maxKg))
    }
    var rotaryAccumulator by remember { mutableFloatStateOf(0f) }
    val contentWidthFraction =
        when (screenSize) {
            WearScreenSize.LARGE -> 0.70f
            WearScreenSize.MEDIUM -> 0.72f
            WearScreenSize.SMALL -> 0.76f
        }

    fun applyDelta(deltaSteps: Int) {
        if (deltaSteps == 0) return
        val nextValue =
            (localValueKg + deltaSteps * WEIGHT_PICKER_STEP_KG)
                .roundToTenth()
                .coerceIn(minKg, maxKg)
        if (nextValue == localValueKg) return
        localValueKg = nextValue
        onValueChange(nextValue)
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.96f))
                    .onPreRotaryScrollEvent { event ->
                        val delta = event.verticalScrollPixels
                        if (!delta.isFinite() || delta == 0f) return@onPreRotaryScrollEvent false
                        rotaryAccumulator += delta
                        when {
                            rotaryAccumulator >= WEIGHT_PICKER_ROTARY_THRESHOLD_PX -> {
                                applyDelta(1)
                                rotaryAccumulator = 0f
                                true
                            }
                            rotaryAccumulator <= -WEIGHT_PICKER_ROTARY_THRESHOLD_PX -> {
                                applyDelta(-1)
                                rotaryAccumulator = 0f
                                true
                            }
                            else -> true
                        }
                    }.focusRequester(focusRequester)
                    .focusable(),
            contentAlignment = Alignment.Center,
        ) {
            cappedFontScale(maxFontScale = 1f) {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth(contentWidthFraction)
                            .padding(
                                horizontal = adaptive.dialogHorizontalPadding,
                                vertical = adaptive.dialogVerticalPadding,
                            ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.82f),
                        textAlign = TextAlign.Center,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = formatUserWeight(localValueKg, isMetric),
                        style = MaterialTheme.typography.displaySmall,
                        color = MaterialTheme.colorScheme.onBackground,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(34.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        WeightPickerButton(
                            text = "-",
                            onClick = { applyDelta(-1) },
                            onLongClick = { applyDelta(-WEIGHT_PICKER_LONG_PRESS_STEP_COUNT) },
                        )
                        WeightPickerButton(
                            text = "+",
                            onClick = { applyDelta(1) },
                            onLongClick = { applyDelta(WEIGHT_PICKER_LONG_PRESS_STEP_COUNT) },
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = onDismiss,
                        modifier =
                            Modifier
                                .height(48.dp)
                                .width(108.dp),
                        colors = ButtonDefaults.buttonColors(),
                    ) {
                        Text("Done")
                    }
                }
            }
        }
    }
}

@Composable
private fun WeightPickerButton(
    text: String,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .size(WEIGHT_PICKER_ACTION_BUTTON_SIZE)
                .background(MaterialTheme.colorScheme.secondaryContainer, CircleShape)
                .pointerInput(onClick, onLongClick) {
                    detectTapGestures(
                        onTap = { onClick() },
                        onLongPress = { onLongClick() },
                    )
                },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            textAlign = TextAlign.Center,
        )
    }
}

private fun Float.roundToTenth(): Float = (round(this * 10f) / 10f)

private const val KG_TO_LB = 2.2046226218f
private const val WEIGHT_PICKER_STEP_KG = 0.1f
private const val WEIGHT_PICKER_LONG_PRESS_STEP_COUNT = 50
private const val WEIGHT_PICKER_ROTARY_THRESHOLD_PX = 32f
private val WEIGHT_PICKER_ACTION_BUTTON_SIZE = 48.dp
private val ONE_DECIMAL_FORMAT = DecimalFormat("0.0")
