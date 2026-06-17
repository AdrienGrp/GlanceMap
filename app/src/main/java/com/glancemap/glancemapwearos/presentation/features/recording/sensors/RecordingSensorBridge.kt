package com.glancemap.glancemapwearos.presentation.features.recording.sensors

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.glancemap.glancemapwearos.core.service.diagnostics.DebugTelemetry
import com.glancemap.glancemapwearos.data.repository.SettingsRepository
import com.glancemap.glancemapwearos.presentation.features.recording.external.ExternalHeartRateSensorBridge
import com.glancemap.glancemapwearos.presentation.features.recording.external.ExternalRunPodSensorBridge
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.roundToInt

data class RecordingSensorMetrics(
    val heartRateBpm: Int? = null,
    val heartRateUpdatedAtMillis: Long = 0L,
    val stepCount: Int? = null,
    val stepCountUpdatedAtMillis: Long = 0L,
    val stepCountFromBluetooth: Boolean = false,
    val cadenceSpm: Int? = null,
    val cadenceUpdatedAtMillis: Long = 0L,
    val cadenceFromBluetooth: Boolean = false,
    val externalSpeedMps: Float? = null,
    val externalSpeedUpdatedAtMillis: Long = 0L,
    val externalDistanceRawUnits: Long? = null,
    val externalDistanceMeters: Double? = null,
    val externalDistanceUpdatedAtMillis: Long = 0L,
    val externalPowerWatts: Int? = null,
    val externalPowerUpdatedAtMillis: Long = 0L,
    val externalBatteryLevelPercent: Int? = null,
    val externalBatteryUpdatedAtMillis: Long = 0L,
    val barometricPressureHpa: Double? = null,
    val barometricPressureUpdatedAtMillis: Long = 0L,
    val heartRateFromBluetooth: Boolean = false,
)

@Composable
fun RecordingSensorBridge(
    active: Boolean,
    paused: Boolean,
    selectedMetricIds: List<String>,
    heartRateSource: String,
    cadenceSource: String,
    speedSource: String,
    distanceSource: String,
    stepsSource: String,
    externalHeartRateAddress: String?,
    externalRunPodAddress: String?,
    onMetrics: (RecordingSensorMetrics) -> Unit,
) {
    val context = LocalContext.current
    val externalHeartRateLinked = !externalHeartRateAddress.isNullOrBlank()
    val useExternalHeartRate =
        externalHeartRateLinked &&
            heartRateSource == SettingsRepository.RECORDING_HEART_RATE_SOURCE_STRAP
    val useWatchHeartRate =
        heartRateSource == SettingsRepository.RECORDING_HEART_RATE_SOURCE_WATCH
    val externalRunPodLinked = !externalRunPodAddress.isNullOrBlank()
    val useExternalCadence =
        externalRunPodLinked &&
            cadenceSource == SettingsRepository.RECORDING_SENSOR_SOURCE_POD
    val useInternalCadence =
        cadenceSource == SettingsRepository.RECORDING_SENSOR_SOURCE_WATCH_GPS
    val useInternalSteps =
        stepsSource == SettingsRepository.RECORDING_SENSOR_SOURCE_WATCH_GPS
    val useExternalSpeed =
        externalRunPodLinked && speedSource == SettingsRepository.RECORDING_SENSOR_SOURCE_POD
    val useExternalDistance =
        externalRunPodLinked && distanceSource == SettingsRepository.RECORDING_SENSOR_SOURCE_POD
    val useExternalPower =
        externalRunPodLinked && SettingsRepository.RECORDING_METRIC_POWER in selectedMetricIds
    val useExternalRunPod =
        useExternalCadence || useExternalSpeed || useExternalDistance || useExternalPower
    val collectBarometricPressure = active
    val sensorMetricIds =
        remember(selectedMetricIds, useWatchHeartRate, useInternalCadence, useInternalSteps, collectBarometricPressure) {
            val filteredMetricIds =
                selectedMetricIds
                .filter { it in recordingSensorMetricIds }
                .filterNot { !useWatchHeartRate && it == SettingsRepository.RECORDING_METRIC_HEART_RATE }
                .filterNot { !useInternalCadence && it == SettingsRepository.RECORDING_METRIC_CADENCE }
                .filterNot { !useInternalSteps && it == SettingsRepository.RECORDING_METRIC_STEPS }
            if (collectBarometricPressure) {
                (filteredMetricIds + SettingsRepository.RECORDING_METRIC_BAROMETRIC_PRESSURE).distinct()
            } else {
                filteredMetricIds
            }
        }
    var permissionResultVersion by remember { mutableIntStateOf(0) }
    val permissionsToRequest = remember(context, sensorMetricIds, permissionResultVersion) {
        recordingSensorPermissionsToRequest(context, sensorMetricIds)
    }
    val permissionsLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            permissionResultVersion += 1
            DebugTelemetry.log(
                "TraceRecordingSensors",
                "event=permissions_result " +
                    "body=${result[Manifest.permission.BODY_SENSORS] ?: true} " +
                    "activity=${result[activityRecognitionPermission()] ?: true}",
            )
        }
    var metrics by remember { mutableStateOf(RecordingSensorMetrics()) }
    var stepCounterBase by remember { mutableStateOf<Float?>(null) }
    var lastStepCounterValue by remember { mutableStateOf<Float?>(null) }
    var lastStepCounterTimeMs by remember { mutableLongStateOf(0L) }
    val stepDetectorEventTimes = remember { mutableStateListOf<Long>() }

    ExternalHeartRateSensorBridge(
        active = active && useExternalHeartRate,
        paused = paused,
        address = externalHeartRateAddress,
        onHeartRate = { bpm, timeMillis ->
            metrics =
                metrics.copy(
                    heartRateBpm = bpm,
                    heartRateUpdatedAtMillis = timeMillis,
                    heartRateFromBluetooth = true,
                )
            onMetrics(metrics)
        },
    )
    ExternalRunPodSensorBridge(
        active = active && useExternalRunPod,
        paused = paused,
        address = externalRunPodAddress,
        onMeasurement = { measurement ->
            metrics =
                metrics.copy(
                    cadenceSpm =
                        if (useExternalCadence) {
                            measurement.cadenceSpm ?: metrics.cadenceSpm
                        } else {
                            metrics.cadenceSpm
                        },
                    cadenceUpdatedAtMillis =
                        if (useExternalCadence && measurement.cadenceSpm != null) {
                            measurement.timeMillis
                        } else {
                            metrics.cadenceUpdatedAtMillis
                        },
                    cadenceFromBluetooth =
                        if (useExternalCadence && measurement.cadenceSpm != null) {
                            true
                        } else {
                            metrics.cadenceFromBluetooth
                        },
                    externalSpeedMps = measurement.speedMps ?: metrics.externalSpeedMps,
                    externalSpeedUpdatedAtMillis =
                        if (measurement.speedMps != null) {
                            measurement.timeMillis
                        } else {
                            metrics.externalSpeedUpdatedAtMillis
                        },
                    externalDistanceMeters = measurement.totalDistanceMeters ?: metrics.externalDistanceMeters,
                    externalDistanceRawUnits =
                        measurement.rawTotalDistanceUnits ?: metrics.externalDistanceRawUnits,
                    externalDistanceUpdatedAtMillis =
                        if (measurement.totalDistanceMeters != null) {
                            measurement.timeMillis
                        } else {
                            metrics.externalDistanceUpdatedAtMillis
                        },
                    externalPowerWatts =
                        if (useExternalPower) {
                            measurement.powerWatts ?: metrics.externalPowerWatts
                        } else {
                            metrics.externalPowerWatts
                        },
                    externalPowerUpdatedAtMillis =
                        if (useExternalPower && measurement.powerWatts != null) {
                            measurement.timeMillis
                        } else {
                            metrics.externalPowerUpdatedAtMillis
                        },
                    externalBatteryLevelPercent = measurement.batteryLevelPercent ?: metrics.externalBatteryLevelPercent,
                    externalBatteryUpdatedAtMillis =
                        if (measurement.batteryLevelPercent != null) {
                            measurement.timeMillis
                        } else {
                            metrics.externalBatteryUpdatedAtMillis
                        },
                )
            onMetrics(metrics)
        },
    )

    LaunchedEffect(active) {
        if (!active) {
            metrics = RecordingSensorMetrics()
            stepCounterBase = null
            lastStepCounterValue = null
            lastStepCounterTimeMs = 0L
            stepDetectorEventTimes.clear()
            onMetrics(metrics)
        }
    }

    LaunchedEffect(active, sensorMetricIds, permissionsToRequest) {
        if (active && sensorMetricIds.isNotEmpty() && permissionsToRequest.isNotEmpty()) {
            permissionsLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }

    LaunchedEffect(
        context,
        active,
        paused,
        sensorMetricIds,
        permissionsToRequest,
        heartRateSource,
        cadenceSource,
        speedSource,
        distanceSource,
        stepsSource,
        useExternalHeartRate,
        useExternalCadence,
        useExternalRunPod,
    ) {
        if (!active) return@LaunchedEffect
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        while (isActive) {
            logRecordingSensorStatus(
                context = context,
                sensorManager = sensorManager,
                selectedMetricIds = sensorMetricIds,
                heartRateSource = heartRateSource,
                externalHeartRateLinked = externalHeartRateLinked,
                useExternalHeartRate = useExternalHeartRate,
                useWatchHeartRate = useWatchHeartRate,
                externalRunPodLinked = externalRunPodLinked,
                cadenceSource = cadenceSource,
                speedSource = speedSource,
                distanceSource = distanceSource,
                stepsSource = stepsSource,
                useExternalCadence = useExternalCadence,
                useExternalRunPod = useExternalRunPod,
                paused = paused,
                event = "status",
            )
            delay(RECORDING_SENSOR_STATUS_INTERVAL_MS)
        }
    }

    DisposableEffect(
        context,
        active,
        paused,
        sensorMetricIds,
        permissionsToRequest,
        heartRateSource,
        cadenceSource,
        speedSource,
        distanceSource,
        stepsSource,
        useExternalHeartRate,
        useExternalCadence,
        useExternalRunPod,
    ) {
        if (!active || paused || sensorMetricIds.isEmpty()) {
            return@DisposableEffect onDispose {}
        }

        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val listener =
            object : SensorEventListener {
                override fun onSensorChanged(event: SensorEvent) {
                    when (event.sensor.type) {
                        Sensor.TYPE_HEART_RATE -> {
                            val bpm = event.values.firstOrNull()?.roundToInt()?.takeIf { it > 0 }
                            val now = System.currentTimeMillis()
                            metrics =
                                metrics.copy(
                                    heartRateBpm = bpm,
                                    heartRateUpdatedAtMillis = if (bpm != null) now else metrics.heartRateUpdatedAtMillis,
                                    heartRateFromBluetooth = false,
                                )
                        }
                        Sensor.TYPE_STEP_COUNTER -> {
                            val value = event.values.firstOrNull() ?: return
                            val base = stepCounterBase ?: value.also { stepCounterBase = it }
                            val steps = (value - base).roundToInt().coerceAtLeast(0)
                            val now = System.currentTimeMillis()
                            val previousValue = lastStepCounterValue
                            val previousTimeMs = lastStepCounterTimeMs
                            val cadence =
                                if (previousValue != null && previousTimeMs > 0L && now > previousTimeMs) {
                                    val deltaSteps = (value - previousValue).coerceAtLeast(0f)
                                    val deltaMinutes = (now - previousTimeMs) / 60_000.0
                                    if (deltaMinutes > 0.0) {
                                        (deltaSteps / deltaMinutes).roundToInt().takeIf { it > 0 }
                                    } else {
                                        null
                                    }
                                } else {
                                    metrics.cadenceSpm
                                }
                            lastStepCounterValue = value
                            lastStepCounterTimeMs = now
                            metrics =
                                metrics.copy(
                                    stepCount = steps,
                                    stepCountUpdatedAtMillis = now,
                                    stepCountFromBluetooth = false,
                                    cadenceSpm = cadence ?: metrics.cadenceSpm,
                                    cadenceUpdatedAtMillis =
                                        if (cadence != null) {
                                            now
                                        } else {
                                            metrics.cadenceUpdatedAtMillis
                                        },
                                    cadenceFromBluetooth =
                                        if (cadence != null) {
                                            false
                                        } else {
                                            metrics.cadenceFromBluetooth
                                        },
                                )
                        }
                        Sensor.TYPE_STEP_DETECTOR -> {
                            val now = System.currentTimeMillis()
                            stepDetectorEventTimes.add(now)
                            while (stepDetectorEventTimes.firstOrNull()?.let { now - it > CADENCE_WINDOW_MS } == true) {
                                stepDetectorEventTimes.removeAt(0)
                            }
                            val cadence =
                                ((stepDetectorEventTimes.size * 60_000.0) / CADENCE_WINDOW_MS)
                                    .roundToInt()
                                    .takeIf { it > 0 }
                            metrics =
                                metrics.copy(
                                    cadenceSpm = cadence ?: metrics.cadenceSpm,
                                    cadenceUpdatedAtMillis =
                                        if (cadence != null) {
                                            now
                                        } else {
                                            metrics.cadenceUpdatedAtMillis
                                        },
                                    cadenceFromBluetooth =
                                        if (cadence != null) {
                                            false
                                        } else {
                                            metrics.cadenceFromBluetooth
                                        },
                                )
                        }
                        Sensor.TYPE_PRESSURE -> {
                            val pressure = event.values.firstOrNull()?.toDouble()?.takeIf { it > 0.0 }
                            val now = System.currentTimeMillis()
                            metrics =
                                metrics.copy(
                                    barometricPressureHpa = pressure,
                                    barometricPressureUpdatedAtMillis =
                                        if (pressure != null) {
                                            now
                                        } else {
                                            metrics.barometricPressureUpdatedAtMillis
                                        },
                                )
                        }
                    }
                    onMetrics(metrics)
                }

                override fun onAccuracyChanged(
                    sensor: Sensor?,
                    accuracy: Int,
                ) = Unit
            }

        val registered =
            registerRecordingSensors(
                sensorManager = sensorManager,
            listener = listener,
            selectedMetricIds = sensorMetricIds,
            context = context,
            )
        logRecordingSensorStatus(
            context = context,
            sensorManager = sensorManager,
            selectedMetricIds = sensorMetricIds,
            heartRateSource = heartRateSource,
            externalHeartRateLinked = externalHeartRateLinked,
            useExternalHeartRate = useExternalHeartRate,
            useWatchHeartRate = useWatchHeartRate,
            externalRunPodLinked = externalRunPodLinked,
            cadenceSource = cadenceSource,
            speedSource = speedSource,
            distanceSource = distanceSource,
            stepsSource = stepsSource,
            useExternalCadence = useExternalCadence,
            useExternalRunPod = useExternalRunPod,
            registered = registered,
            paused = paused,
            event = "register",
        )

        onDispose {
            sensorManager.unregisterListener(listener)
            DebugTelemetry.log("TraceRecordingSensors", "event=unregister")
        }
    }
}

private fun logRecordingSensorStatus(
    context: Context,
    sensorManager: SensorManager,
    selectedMetricIds: List<String>,
    heartRateSource: String,
    externalHeartRateLinked: Boolean,
    useExternalHeartRate: Boolean,
    useWatchHeartRate: Boolean,
    externalRunPodLinked: Boolean,
    cadenceSource: String,
    speedSource: String,
    distanceSource: String,
    stepsSource: String,
    useExternalCadence: Boolean,
    useExternalRunPod: Boolean,
    registered: List<String>? = null,
    paused: Boolean,
    event: String,
) {
    val available = availableRecordingSensors(sensorManager)
    val bodySensorsGranted = hasPermission(context, Manifest.permission.BODY_SENSORS)
    val activityRecognitionGranted = hasActivityRecognitionPermission(context)
    val requested = selectedMetricIds.joinToString("|").ifBlank { "none" }
    val registeredText = registered?.joinToString("|")?.ifBlank { "none" } ?: "unknown"
    DebugTelemetry.log(
        "TraceRecordingSensors",
        "event=$event requested=$requested " +
            "registered=$registeredText " +
            "available=${available.joinToString("|").ifBlank { "none" }} " +
            "heartRateSource=$heartRateSource " +
            "externalHeartRateLinked=$externalHeartRateLinked " +
            "externalHeartRateActive=$useExternalHeartRate " +
            "watchHeartRateActive=$useWatchHeartRate " +
            "externalRunPodLinked=$externalRunPodLinked " +
            "cadenceSource=$cadenceSource " +
            "speedSource=$speedSource " +
            "distanceSource=$distanceSource " +
            "stepsSource=$stepsSource " +
            "externalCadenceActive=$useExternalCadence " +
            "externalRunPodActive=$useExternalRunPod " +
            "paused=$paused " +
            "bodySensorsGranted=$bodySensorsGranted " +
            "activityRecognitionGranted=$activityRecognitionGranted",
    )
}

private fun availableRecordingSensors(sensorManager: SensorManager): List<String> =
    buildList {
        if (sensorManager.getDefaultSensor(Sensor.TYPE_HEART_RATE) != null) {
            add("heart_rate")
        }
        if (sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER) != null) {
            add("step_counter")
        }
        if (sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR) != null) {
            add("step_detector")
        }
        if (sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE) != null) {
            add("pressure")
        }
    }

fun recordingSensorMetricsSelected(metricIds: List<String>): Boolean =
    metricIds.any { it in recordingSensorMetricIds }

private fun registerRecordingSensors(
    sensorManager: SensorManager,
    listener: SensorEventListener,
    selectedMetricIds: List<String>,
    context: Context,
): List<String> {
    val registered = mutableListOf<String>()
    fun register(
        type: Int,
        token: String,
    ) {
        val sensor = sensorManager.getDefaultSensor(type) ?: return
        if (sensorManager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)) {
            registered += token
        }
    }

    if (
        SettingsRepository.RECORDING_METRIC_HEART_RATE in selectedMetricIds &&
        hasPermission(context, Manifest.permission.BODY_SENSORS)
    ) {
        register(Sensor.TYPE_HEART_RATE, "heart_rate")
    }
    if (
        (
            SettingsRepository.RECORDING_METRIC_STEPS in selectedMetricIds ||
                SettingsRepository.RECORDING_METRIC_CADENCE in selectedMetricIds
        ) &&
        hasActivityRecognitionPermission(context)
    ) {
        register(Sensor.TYPE_STEP_COUNTER, "step_counter")
        register(Sensor.TYPE_STEP_DETECTOR, "step_detector")
    }
    if (SettingsRepository.RECORDING_METRIC_BAROMETRIC_PRESSURE in selectedMetricIds) {
        register(Sensor.TYPE_PRESSURE, "pressure")
    }
    return registered
}

private fun recordingSensorPermissionsToRequest(
    context: Context,
    selectedMetricIds: List<String>,
): List<String> =
    buildList {
        if (
            SettingsRepository.RECORDING_METRIC_HEART_RATE in selectedMetricIds &&
            !hasPermission(context, Manifest.permission.BODY_SENSORS)
        ) {
            add(Manifest.permission.BODY_SENSORS)
        }
        val needsStepPermission =
            SettingsRepository.RECORDING_METRIC_STEPS in selectedMetricIds ||
                SettingsRepository.RECORDING_METRIC_CADENCE in selectedMetricIds
        if (needsStepPermission && !hasActivityRecognitionPermission(context)) {
            add(activityRecognitionPermission())
        }
    }

private fun hasActivityRecognitionPermission(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
        hasPermission(context, Manifest.permission.ACTIVITY_RECOGNITION)

private fun activityRecognitionPermission(): String =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        Manifest.permission.ACTIVITY_RECOGNITION
    } else {
        ""
    }

private fun hasPermission(
    context: Context,
    permission: String,
): Boolean =
    permission.isBlank() ||
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

private val recordingSensorMetricIds =
    setOf(
        SettingsRepository.RECORDING_METRIC_HEART_RATE,
        SettingsRepository.RECORDING_METRIC_STEPS,
        SettingsRepository.RECORDING_METRIC_CADENCE,
        SettingsRepository.RECORDING_METRIC_BAROMETRIC_PRESSURE,
    )

private const val CADENCE_WINDOW_MS = 30_000L
private const val RECORDING_SENSOR_STATUS_INTERVAL_MS = 60_000L
