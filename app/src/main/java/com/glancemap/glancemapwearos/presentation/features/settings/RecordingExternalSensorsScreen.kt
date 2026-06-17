package com.glancemap.glancemapwearos.presentation.features.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.BluetoothSearching
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.glancemap.glancemapwearos.core.service.diagnostics.DebugTelemetry
import com.glancemap.glancemapwearos.presentation.features.recording.external.ExternalSensorDevice
import com.glancemap.glancemapwearos.presentation.features.recording.external.ExternalSensorKind
import com.glancemap.glancemapwearos.presentation.features.recording.external.ExternalSensorScanStatus
import com.glancemap.glancemapwearos.presentation.features.recording.external.ExternalSensorScanner
import com.glancemap.glancemapwearos.presentation.features.recording.external.ExternalRunPodRuntimeStatus
import com.glancemap.glancemapwearos.presentation.ui.rememberWearAdaptiveSpec
import com.google.android.horologist.annotations.ExperimentalHorologistApi
import com.google.android.horologist.compose.material.Chip

@Composable
fun RecordingExternalSensorsScreen(
    viewModel: SettingsViewModel,
    onOpenRecordingSettings: () -> Unit,
) {
    val listTokens = rememberSettingsListTokens()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scanner = remember(context) { ExternalSensorScanner(context.applicationContext) }
    val devices by scanner.devices.collectAsState()
    val status by scanner.status.collectAsState()
    val linkedHeartRateAddress by viewModel.recordingExternalHeartRateAddress.collectAsState()
    val linkedHeartRateName by viewModel.recordingExternalHeartRateName.collectAsState()
    val linkedRunPodAddress by viewModel.recordingExternalRunPodAddress.collectAsState()
    val linkedRunPodName by viewModel.recordingExternalRunPodName.collectAsState()
    val runPodRuntimeInfos by ExternalRunPodRuntimeStatus.infos.collectAsState()
    var permissionRefresh by remember { mutableIntStateOf(0) }
    var unsupportedSensorMessage by remember { mutableStateOf<String?>(null) }
    val hasPermissions =
        remember(context, permissionRefresh) {
            ExternalSensorScanner.hasRequiredPermissions(context)
        }
    val permissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            permissionRefresh += 1
            if (ExternalSensorScanner.hasRequiredPermissions(context)) {
                scanner.startScan()
            }
        }

    DisposableEffect(scanner) {
        onDispose { scanner.stopScan() }
    }

    DisposableEffect(lifecycleOwner) {
        val observer =
            LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    permissionRefresh += 1
                }
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    WearSettingsListScreen(listTokens = listTokens, horizontalAlignment = Alignment.CenterHorizontally) {
        item {
            SettingsSectionChip(
                label = "Recording settings",
                secondaryLabel = "Back to REC settings",
                onClick = onOpenRecordingSettings,
            )
        }
        item {
            ExternalSensorScanChip(
                hasPermissions = hasPermissions,
                status = status,
                onRequestPermissions = {
                    permissionLauncher.launch(ExternalSensorScanner.requiredPermissions().toTypedArray())
                },
                onStartScan = scanner::startScan,
                onStopScan = scanner::stopScan,
            )
        }
        linkedHeartRateAddress?.takeIf { it.isNotBlank() }?.let { address ->
            item {
                LinkedExternalSensorChip(
                    name = linkedHeartRateName.orLinkedSensorFallback("Heart strap"),
                    address = address,
                    onUnlink = {
                        unsupportedSensorMessage = null
                        viewModel.setRecordingExternalHeartRateDevice(null, null)
                    },
                )
            }
        }
        linkedRunPodAddress?.takeIf { it.isNotBlank() }?.let { address ->
            item {
                LinkedExternalSensorChip(
                    name = linkedRunPodName.orLinkedSensorFallback("Run pod"),
                    address = address,
                    batteryLevelPercent = runPodRuntimeInfos[address]?.batteryLevelPercent,
                    onUnlink = {
                        unsupportedSensorMessage = null
                        viewModel.setRecordingExternalRunPodDevice(null, null)
                    },
                )
            }
        }
        item {
            ExternalSensorInfo(
                primaryText = "Polar straps use Heart Rate. Pods usually use Running Speed/Cadence.",
                secondaryText = "Linked pods provide cadence during REC.",
            )
        }
        unsupportedSensorMessage?.let { message ->
            item {
                ExternalSensorInfo(
                    primaryText = message,
                    secondaryText = "We can see this sensor, but REC does not use pods yet.",
                )
            }
        }
        val linkedAddresses = setOfNotNull(linkedHeartRateAddress, linkedRunPodAddress)
        val visibleDevices = devices.filterNot { it.address in linkedAddresses }
        if (visibleDevices.isEmpty()) {
            item {
                ExternalSensorInfo(
                    primaryText =
                        if (status == ExternalSensorScanStatus.SCANNING) {
                            "Searching nearby sensors..."
                        } else {
                            "No devices found yet."
                        },
                    secondaryText = "Wake the strap or pod and keep it close to the watch.",
                )
            }
        } else {
            visibleDevices.forEach { device ->
                item {
                    val heartRateSelected = linkedHeartRateAddress == device.address
                    val runPodSelected = linkedRunPodAddress == device.address
                    ExternalSensorDeviceChip(
                        device = device,
                        heartRateSelected = heartRateSelected,
                        runPodSelected = runPodSelected,
                        onClick = {
                            if (heartRateSelected) {
                                unsupportedSensorMessage = null
                                viewModel.setRecordingExternalHeartRateDevice(null, null)
                            } else if (runPodSelected) {
                                unsupportedSensorMessage = null
                                viewModel.setRecordingExternalRunPodDevice(null, null)
                            } else if (device.canLinkHeartRate()) {
                                unsupportedSensorMessage = null
                                viewModel.setRecordingExternalHeartRateDevice(device.address, device.name)
                            } else if (device.canLinkRunPod()) {
                                unsupportedSensorMessage = null
                                viewModel.setRecordingExternalRunPodDevice(device.address, device.name)
                            } else {
                                unsupportedSensorMessage = "${device.name} is not supported yet"
                                DebugTelemetry.log(
                                    "ExternalSensors",
                                    "event=device_tap_unsupported name=${device.name.sanitizeTelemetryToken()} " +
                                        "kinds=${device.supportedLabel.sanitizeTelemetryToken()}",
                                )
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun LinkedExternalSensorChip(
    name: String,
    address: String,
    batteryLevelPercent: Int? = null,
    onUnlink: () -> Unit,
) {
    val adaptive = rememberWearAdaptiveSpec()
    val minHeight =
        when {
            adaptive.fontScale >= 1.45f -> 76.dp
            adaptive.fontScale >= 1.25f -> 68.dp
            else -> 52.dp
        }
    LinkedExternalSensorChipContent(
        name = name,
        address = address,
        batteryLevelPercent = batteryLevelPercent,
        minHeight = minHeight,
        onUnlink = onUnlink,
    )
}

@OptIn(ExperimentalHorologistApi::class)
@Composable
private fun LinkedExternalSensorChipContent(
    name: String,
    address: String,
    batteryLevelPercent: Int?,
    minHeight: androidx.compose.ui.unit.Dp,
    onUnlink: () -> Unit,
) {
    Chip(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = minHeight),
        label = {
            Text(
                text = name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        secondaryLabel = {
            Text(
                text = linkedSensorSecondaryText(address, batteryLevelPercent),
                modifier = Modifier.basicMarquee(),
                maxLines = 1,
                overflow = TextOverflow.Clip,
            )
        },
        icon = {
            Icon(
                imageVector = Icons.Default.CheckCircle,
                contentDescription = null,
                modifier = Modifier.size(ChipDefaults.IconSize),
            )
        },
        colors =
            ChipDefaults.secondaryChipColors(
                backgroundColor = Color(0xFF254336),
                contentColor = Color(0xFFF1FFF5),
                secondaryContentColor = Color(0xFFB7DCC4),
                iconColor = Color(0xFF8FF0A4),
            ),
        onClick = onUnlink,
    )
}

private fun linkedSensorSecondaryText(
    address: String,
    batteryLevelPercent: Int?,
): String =
    batteryLevelPercent
        ?.takeIf { it in 0..100 }
        ?.let { "$address · $it%" }
        ?: address

@Composable
private fun ExternalSensorScanChip(
    hasPermissions: Boolean,
    status: ExternalSensorScanStatus,
    onRequestPermissions: () -> Unit,
    onStartScan: () -> Unit,
    onStopScan: () -> Unit,
) {
    when {
        !hasPermissions ->
            SettingsPickerChip(
                label = "Allow Bluetooth",
                secondaryLabel = "Needed to find sensors",
                iconImageVector = Icons.Default.Bluetooth,
                onClick = onRequestPermissions,
            )
        status == ExternalSensorScanStatus.SCANNING ->
            SettingsPickerChip(
                label = "Stop scan",
                secondaryLabel = "Searching nearby BLE devices",
                iconImageVector = Icons.AutoMirrored.Filled.BluetoothSearching,
                onClick = onStopScan,
            )
        else ->
            SettingsPickerChip(
                label = "Scan sensors",
                secondaryLabel = scanStatusLabel(status),
                iconImageVector = Icons.AutoMirrored.Filled.BluetoothSearching,
                onClick = onStartScan,
            )
    }
}

@Composable
private fun ExternalSensorDeviceChip(
    device: ExternalSensorDevice,
    heartRateSelected: Boolean,
    runPodSelected: Boolean,
    onClick: () -> Unit,
) {
    val selected = heartRateSelected || runPodSelected
    val icon =
        when {
            selected -> Icons.Default.CheckCircle
            device.kinds.any { it.label == "Heart rate" } -> Icons.Default.Favorite
            device.kinds.isNotEmpty() -> Icons.Default.Sensors
            else -> Icons.Default.Bluetooth
        }
    SettingsPickerChip(
        label = device.name,
        secondaryLabel =
            buildString {
                if (heartRateSelected) append("Connected HR · ")
                if (runPodSelected) append("Connected pod · ")
                append(device.supportedLabel)
                device.rssi?.let { append(" · $it dBm") }
            },
        iconImageVector = icon,
        selected = selected,
        onClick = onClick,
    )
}

@Composable
private fun ExternalSensorInfo(
    primaryText: String,
    secondaryText: String,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = primaryText,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = secondaryText,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.72f),
        )
    }
}

private fun scanStatusLabel(status: ExternalSensorScanStatus): String =
    when (status) {
        ExternalSensorScanStatus.IDLE -> "Heart straps and pods"
        ExternalSensorScanStatus.SCANNING -> "Searching"
        ExternalSensorScanStatus.BLUETOOTH_UNAVAILABLE -> "Bluetooth unavailable"
        ExternalSensorScanStatus.BLUETOOTH_OFF -> "Turn Bluetooth on"
        ExternalSensorScanStatus.PERMISSION_MISSING -> "Permission needed"
        ExternalSensorScanStatus.SCAN_FAILED -> "Scan failed, try again"
    }

private fun ExternalSensorDevice.canLinkHeartRate(): Boolean =
    kinds.isEmpty() || ExternalSensorKind.HEART_RATE in kinds

private fun ExternalSensorDevice.canLinkRunPod(): Boolean =
    ExternalSensorKind.RUNNING_SPEED_CADENCE in kinds

private fun String.sanitizeTelemetryToken(): String =
    replace(' ', '_')
        .replace('|', '_')
        .replace('=', '_')

private fun String?.orLinkedSensorFallback(fallback: String): String =
    this
        ?.trim()
        ?.takeIf { it.isNotBlank() }
        ?: fallback
