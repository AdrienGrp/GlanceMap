package com.glancemap.glancemapwearos.presentation.features.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.runtime.*
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
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.google.android.horologist.annotations.ExperimentalHorologistApi
import com.google.android.horologist.compose.material.ToggleChip
import com.google.android.horologist.compose.material.ToggleChipToggleControl
import com.glancemap.glancemapwearos.data.repository.SettingsRepository
import com.glancemap.glancemapwearos.presentation.ui.rememberWearAdaptiveSpec
import kotlin.math.abs

@OptIn(ExperimentalHorologistApi::class)
@Composable
fun GpsSettingsScreen(
    viewModel: SettingsViewModel,
    onOpenGeneralSettings: () -> Unit,
) {
    val listTokens = rememberSettingsListTokens()

    val isWatchGpsOnly by viewModel.watchGpsOnly.collectAsState()
    val recordingSampleIntervalSeconds by viewModel.recordingSampleIntervalSeconds.collectAsState()
    val recordingScreenOffSampleIntervalSeconds by viewModel.recordingScreenOffSampleIntervalSeconds.collectAsState()
    val recordingAutoPauseMode by viewModel.recordingAutoPauseMode.collectAsState()
    val turnByTurnGpsIntervalSeconds by viewModel.turnByTurnGpsIntervalSeconds.collectAsState()
    val turnByTurnScreenOffGpsIntervalSeconds by viewModel.turnByTurnScreenOffGpsIntervalSeconds.collectAsState()
    val gpsDebugTelemetry by viewModel.gpsDebugTelemetry.collectAsState()
    val gpsPassiveLocationExperiment by viewModel.gpsPassiveLocationExperiment.collectAsState()
    val recordingScreenOnDisabled =
        recordingSampleIntervalSeconds == SettingsRepository.RECORDING_SAMPLE_INTERVAL_DISABLED_SECONDS
    val recordingScreenOffDisabled =
        when (recordingScreenOffSampleIntervalSeconds) {
            SettingsRepository.GPS_INTERVAL_SAME_AS_SCREEN_ON_SECONDS -> recordingScreenOnDisabled
            SettingsRepository.RECORDING_SAMPLE_INTERVAL_DISABLED_SECONDS -> true
            else -> false
        }
    val turnByTurnScreenOnDisabled =
        turnByTurnGpsIntervalSeconds == SettingsRepository.RECORDING_SAMPLE_INTERVAL_DISABLED_SECONDS
    val turnByTurnScreenOffDisabled =
        when (turnByTurnScreenOffGpsIntervalSeconds) {
            SettingsRepository.GPS_INTERVAL_SAME_AS_SCREEN_ON_SECONDS -> turnByTurnScreenOnDisabled
            SettingsRepository.RECORDING_SAMPLE_INTERVAL_DISABLED_SECONDS -> true
            else -> false
        }

    WearSettingsListScreen(listTokens = listTokens, horizontalAlignment = Alignment.CenterHorizontally) {
        item {
            GeneralSettingsShortcutChip(onClick = onOpenGeneralSettings)
        }

        item {
            ToggleChip(
                checked = isWatchGpsOnly,
                onCheckedChanged = { viewModel.setWatchGpsOnly(it) },
                label = "GPS Source",
                secondaryLabel =
                    when {
                        isWatchGpsOnly -> "Watch Only (more ⚡)"
                        else -> "Auto (Watch + Phone)"
                    },
                toggleControl = ToggleChipToggleControl.Switch,
            )
        }

        item {
            GpsIntervalSummary(
                primaryText = "Basic GPS uses 3s when the screen is on.",
                secondaryText = "REC + TBT: shorter timing is used.",
            )
        }

        item {
            GpsSectionTitle(text = "REC")
        }
        item {
            GpsIntervalSummary(
                primaryText = "Hike: 3s. Bike: 1s.",
            )
        }
        item {
            GpsTimingPickerRow(
                label = "Screen on",
                selectedValue = recordingSampleIntervalSeconds,
                options = REC_SCREEN_ON_OPTIONS_SECONDS,
                secondaryLabel = gpsIntervalLabel(recordingSampleIntervalSeconds),
                dialogTitle = "REC screen on",
                onSelect = viewModel::setRecordingSampleIntervalSeconds,
            )
        }
        item {
            GpsTimingPickerRow(
                label = "Screen off",
                selectedValue = recordingScreenOffSampleIntervalSeconds,
                options = SCREEN_OFF_OPTIONS_SECONDS,
                secondaryLabel =
                    gpsScreenOffIntervalLabel(
                        seconds = recordingScreenOffSampleIntervalSeconds,
                        screenOnSeconds = recordingSampleIntervalSeconds,
                    ),
                dialogTitle = "REC screen off",
                screenOnSeconds = recordingSampleIntervalSeconds,
                offWarningText = "REC can have gaps while the screen is off.",
                onSelect = viewModel::setRecordingScreenOffSampleIntervalSeconds,
            )
        }
        if (recordingScreenOffDisabled) {
            item {
                GpsWarningText(
                    text = "Screen-off REC GPS is off. Saved recordings can have gaps, and GPS may take a moment to catch up when you look at the watch.",
                )
            }
        }
        item {
            SettingsOptionPickerRow(
                label = "Auto-pause",
                selectedValue = recordingAutoPauseMode,
                options = AUTO_PAUSE_OPTIONS.map { it to autoPauseModeLabel(it) },
                secondaryLabel = autoPauseModeLabel(recordingAutoPauseMode),
                onSelect = viewModel::setRecordingAutoPauseMode,
            )
        }

        item {
            GpsSectionTitle(text = "TBT")
        }
        item {
            GpsTimingPickerRow(
                label = "Screen on",
                selectedValue = turnByTurnGpsIntervalSeconds,
                options = TBT_SCREEN_ON_OPTIONS_SECONDS,
                secondaryLabel = gpsIntervalLabel(turnByTurnGpsIntervalSeconds),
                dialogTitle = "TBT screen on",
                offWarningText = "TBT will use normal map GPS only while the screen is on.",
                onSelect = viewModel::setTurnByTurnGpsIntervalSeconds,
            )
        }
        item {
            GpsTimingPickerRow(
                label = "Screen off",
                selectedValue = turnByTurnScreenOffGpsIntervalSeconds,
                options = SCREEN_OFF_OPTIONS_SECONDS,
                secondaryLabel =
                    gpsScreenOffIntervalLabel(
                        seconds = turnByTurnScreenOffGpsIntervalSeconds,
                        screenOnSeconds = turnByTurnGpsIntervalSeconds,
                    ),
                dialogTitle = "TBT screen off",
                screenOnSeconds = turnByTurnGpsIntervalSeconds,
                offWarningText = "TBT alerts pause while the screen is off.",
                onSelect = viewModel::setTurnByTurnScreenOffGpsIntervalSeconds,
            )
        }
        if (turnByTurnScreenOffDisabled) {
            item {
                GpsWarningText(
                    text = "Screen-off TBT GPS is off. Alerts will not work while the screen is off, and GPS may take a moment to catch up when you look at the watch.",
                )
            }
        }
        if (turnByTurnScreenOnDisabled) {
            item {
                GpsWarningText(
                    text = "Screen-on TBT GPS is off. TBT can still use normal map GPS when available.",
                )
            }
        }

        if (gpsDebugTelemetry) {
            item {
                ToggleChip(
                    checked = gpsPassiveLocationExperiment,
                    onCheckedChanged = { viewModel.setGpsPassiveLocationExperiment(it) },
                    label = "Use GPS from other apps",
                    secondaryLabel =
                        if (gpsPassiveLocationExperiment) {
                            "On during capture"
                        } else {
                            "Off during capture"
                        },
                    toggleControl = ToggleChipToggleControl.Switch,
                )
            }
        }
    }
}

@Composable
private fun GpsTimingPickerRow(
    label: String,
    selectedValue: Int,
    options: List<Int>,
    secondaryLabel: String,
    dialogTitle: String,
    onSelect: (Int) -> Unit,
    screenOnSeconds: Int? = null,
    offWarningText: String? = null,
) {
    var pickerVisible by remember { mutableStateOf(false) }

    SettingsPickerChip(
        label = label,
        secondaryLabel = secondaryLabel,
        onClick = { pickerVisible = true },
    )
    GpsTimingStepperDialog(
        visible = pickerVisible,
        title = dialogTitle,
        selectedValue = selectedValue,
        options = options,
        screenOnSeconds = screenOnSeconds,
        offWarningText = offWarningText,
        onDismiss = { pickerVisible = false },
        onSelect = onSelect,
    )
}

@Composable
private fun GpsTimingStepperDialog(
    visible: Boolean,
    title: String,
    selectedValue: Int,
    options: List<Int>,
    screenOnSeconds: Int?,
    offWarningText: String?,
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit,
) {
    if (!visible) return

    val adaptive = rememberWearAdaptiveSpec()
    val focusRequester = remember { FocusRequester() }
    var selectedIndex by remember(options, selectedValue) {
        mutableIntStateOf(options.indexOf(selectedValue).coerceAtLeast(0))
    }
    var rotaryAccumulator by remember { mutableFloatStateOf(0f) }
    val selectedSeconds = options.getOrElse(selectedIndex) { selectedValue }
    val selectedOption = gpsTimingOption(seconds = selectedSeconds, screenOnSeconds = screenOnSeconds)
    val canDecrease = selectedIndex > 0
    val canIncrease = selectedIndex < options.lastIndex
    val compactHighFontLayout = adaptive.fontScale > 1.05f

    fun selectIndex(index: Int) {
        val safeIndex = index.coerceIn(0, options.lastIndex)
        if (safeIndex == selectedIndex) return
        selectedIndex = safeIndex
        onSelect(options[safeIndex])
    }

    fun selectBySecondsDelta(deltaSeconds: Int) {
        val currentSeconds = options.getOrElse(selectedIndex) { selectedValue }
        val positiveOptions = options.filter { it > 0 }
        if (positiveOptions.isEmpty()) return
        if (currentSeconds <= 0) {
            if (deltaSeconds > 0) {
                selectIndex(options.indexOf(positiveOptions.first()))
            } else {
                selectIndex(selectedIndex - 1)
            }
            return
        }

        val firstPositiveIndex = options.indexOf(positiveOptions.first())
        val targetSeconds = currentSeconds + deltaSeconds
        val targetOption =
            if (deltaSeconds > 0) {
                positiveOptions.firstOrNull { it >= targetSeconds } ?: positiveOptions.last()
            } else {
                if (targetSeconds < positiveOptions.first()) {
                    selectIndex(firstPositiveIndex - 1)
                    return
                }
                positiveOptions.lastOrNull { it <= targetSeconds } ?: positiveOptions.first()
            }
        selectIndex(options.indexOf(targetOption))
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
                    .background(Color.Black.copy(alpha = 0.95f)),
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(
                            start = adaptive.dialogHorizontalPadding,
                            top = adaptive.dialogVerticalPadding + 12.dp,
                            end = adaptive.dialogHorizontalPadding,
                            bottom = adaptive.dialogVerticalPadding + 22.dp,
                        )
                        .onPreRotaryScrollEvent { event ->
                            rotaryAccumulator += event.verticalScrollPixels
                            if (abs(rotaryAccumulator) >= GPS_STEPPER_ROTARY_STEP_PX) {
                                if (rotaryAccumulator > 0f) {
                                    selectIndex(selectedIndex + 1)
                                } else {
                                    selectIndex(selectedIndex - 1)
                                }
                                rotaryAccumulator = 0f
                            }
                            true
                        }.focusRequester(focusRequester)
                        .focusable(),
                verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                GpsPickerDismissHandle(onDismiss = onDismiss)
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 6.dp),
                )
                GpsTimingStepperValue(
                    label = selectedOption.label,
                    detail = selectedOption.detail,
                    compactHighFontLayout = compactHighFontLayout,
                    canDecrease = canDecrease,
                    canIncrease = canIncrease,
                    onDecrease = { selectIndex(selectedIndex - 1) },
                    onIncrease = { selectIndex(selectedIndex + 1) },
                    onLongDecrease = { selectBySecondsDelta(-GPS_STEPPER_LONG_PRESS_SECONDS) },
                    onLongIncrease = { selectBySecondsDelta(GPS_STEPPER_LONG_PRESS_SECONDS) },
                )
                if (
                    offWarningText != null &&
                    selectedSeconds == SettingsRepository.RECORDING_SAMPLE_INTERVAL_DISABLED_SECONDS
                ) {
                    Text(
                        text = offWarningText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun GpsPickerDismissHandle(onDismiss: () -> Unit) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .height(12.dp)
                .pointerInput(Unit) {
                    var totalDrag = 0f
                    detectVerticalDragGestures(
                        onDragEnd = { totalDrag = 0f },
                        onDragCancel = { totalDrag = 0f },
                    ) { _, dragAmount ->
                        totalDrag += dragAmount
                        if (totalDrag > GPS_PICKER_DRAG_DISMISS_PX) {
                            onDismiss()
                            totalDrag = 0f
                        }
                    }
                },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier =
                Modifier
                    .width(26.dp)
                    .height(3.dp)
                    .background(Color.White.copy(alpha = 0.42f), RoundedCornerShape(50)),
        )
    }
}

@Composable
private fun GpsTimingStepperValue(
    label: String,
    detail: String?,
    compactHighFontLayout: Boolean,
    canDecrease: Boolean,
    canIncrease: Boolean,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    onLongDecrease: () -> Unit,
    onLongIncrease: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(if (compactHighFontLayout) 5.dp else 8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        val stepperButtonSize = if (compactHighFontLayout) 48.dp else 64.dp
        val stepperSpacing = if (compactHighFontLayout) 4.dp else 12.dp
        Row(
            horizontalArrangement =
                Arrangement.spacedBy(
                    stepperSpacing,
                    Alignment.CenterHorizontally,
                ),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            GpsStepperButton(
                enabled = canDecrease,
                size = stepperButtonSize,
                onClick = onDecrease,
                onLongClick = onLongDecrease,
                icon = Icons.Filled.Remove,
                contentDescription = "Decrease GPS timing",
            )
            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .background(
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.24f),
                            shape = RoundedCornerShape(28.dp),
                        ).padding(
                            horizontal = if (compactHighFontLayout) 6.dp else 10.dp,
                            vertical =
                                when {
                                    compactHighFontLayout -> if (detail == null) 10.dp else 8.dp
                                    detail == null -> 14.dp
                                    else -> 10.dp
                                },
                        ),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = label,
                        style =
                            if (compactHighFontLayout) {
                                MaterialTheme.typography.titleLarge
                            } else if (label.length <= 3) {
                                MaterialTheme.typography.displaySmall
                            } else {
                                MaterialTheme.typography.titleLarge
                            },
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                    )
                    if (detail != null) {
                        Text(
                            text = detail,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.76f),
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                        )
                    }
                }
            }
            GpsStepperButton(
                enabled = canIncrease,
                size = stepperButtonSize,
                onClick = onIncrease,
                onLongClick = onLongIncrease,
                icon = Icons.Filled.Add,
                contentDescription = "Increase GPS timing",
            )
        }
        Text(
            text = if (compactHighFontLayout) "Tap ±1s · hold ±5s" else "Tap ±1s · long press ±5s",
            style =
                if (compactHighFontLayout) {
                    MaterialTheme.typography.labelSmall
                } else {
                    MaterialTheme.typography.bodySmall
                },
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.62f),
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun GpsStepperButton(
    enabled: Boolean,
    size: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
) {
    val containerColor =
        if (enabled) {
            Color.White.copy(alpha = 0.22f)
        } else {
            Color.White.copy(alpha = 0.12f)
        }
    val contentColor =
        if (enabled) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.96f)
        } else {
            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.42f)
        }
    Box(
        modifier =
            Modifier
                .width(size)
                .height(size)
                .background(containerColor, CircleShape)
                .pointerInput(enabled, onClick, onLongClick) {
                    if (!enabled) return@pointerInput
                    detectTapGestures(
                        onTap = { onClick() },
                        onLongPress = { onLongClick() },
                    )
                },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = contentColor,
        )
    }
}

@Composable
private fun GpsSectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Center,
        maxLines = 1,
    )
}

@Composable
private fun GpsWarningText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier.fillMaxWidth(),
        textAlign = TextAlign.Center,
    )
}

@Suppress("FunctionName")
@Composable
private fun GpsIntervalSummary(
    primaryText: String,
    secondaryText: String = "",
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = primaryText,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.fillMaxWidth(),
            maxLines = 2,
            textAlign = TextAlign.Center,
        )
        if (secondaryText.isNotBlank()) {
            Text(
                text = secondaryText,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
            )
        }
    }
}

private const val GPS_PICKER_DRAG_DISMISS_PX = 55f
private const val GPS_STEPPER_ROTARY_STEP_PX = 48f
private const val GPS_STEPPER_LONG_PRESS_SECONDS = 5

private val GPS_TIMING_SECONDS_OPTIONS = (1..60).toList() + listOf(90, 120)

private val REC_SCREEN_ON_OPTIONS_SECONDS =
    listOf(SettingsRepository.RECORDING_SAMPLE_INTERVAL_DISABLED_SECONDS) + GPS_TIMING_SECONDS_OPTIONS

private val TBT_SCREEN_ON_OPTIONS_SECONDS =
    listOf(SettingsRepository.RECORDING_SAMPLE_INTERVAL_DISABLED_SECONDS) + GPS_TIMING_SECONDS_OPTIONS

private val SCREEN_OFF_OPTIONS_SECONDS =
    listOf(
        SettingsRepository.GPS_INTERVAL_SAME_AS_SCREEN_ON_SECONDS,
        SettingsRepository.RECORDING_SAMPLE_INTERVAL_DISABLED_SECONDS,
    ) + GPS_TIMING_SECONDS_OPTIONS

private val AUTO_PAUSE_OPTIONS =
    listOf(
        SettingsRepository.RECORDING_AUTO_PAUSE_OFF,
        SettingsRepository.RECORDING_AUTO_PAUSE_ALWAYS,
    )

private fun autoPauseModeLabel(mode: String): String =
    when (mode) {
        SettingsRepository.RECORDING_AUTO_PAUSE_ALWAYS -> "On"
        else -> "Off"
    }

private fun gpsScreenOffIntervalLabel(
    seconds: Int,
    screenOnSeconds: Int? = null,
): String =
    when (seconds) {
        SettingsRepository.GPS_INTERVAL_SAME_AS_SCREEN_ON_SECONDS ->
            screenOnSeconds?.let { "Same (${gpsIntervalLabel(it)})" } ?: "Same as screen on"

        else -> gpsIntervalLabel(seconds)
    }

private fun gpsIntervalLabel(seconds: Int): String =
    when {
        seconds == SettingsRepository.RECORDING_SAMPLE_INTERVAL_DISABLED_SECONDS -> "Off"
        seconds <= 1 -> "1 second"
        else -> "$seconds seconds"
    }

private data class GpsTimingOption(
    val label: String,
    val detail: String? = null,
)

private fun gpsTimingOption(
    seconds: Int,
    screenOnSeconds: Int?,
): GpsTimingOption =
    when (seconds) {
        SettingsRepository.GPS_INTERVAL_SAME_AS_SCREEN_ON_SECONDS ->
            GpsTimingOption(
                label = "Same",
                detail = screenOnSeconds?.let { "Screen on · ${gpsShortIntervalLabel(it)}" } ?: "Screen on",
            )

        SettingsRepository.RECORDING_SAMPLE_INTERVAL_DISABLED_SECONDS ->
            GpsTimingOption(label = "Off")

        else ->
            GpsTimingOption(
                label = gpsShortIntervalLabel(seconds),
                detail = null,
            )
    }

private fun gpsShortIntervalLabel(seconds: Int): String =
    if (seconds <= 1) {
        "1s"
    } else {
        "${seconds}s"
    }
