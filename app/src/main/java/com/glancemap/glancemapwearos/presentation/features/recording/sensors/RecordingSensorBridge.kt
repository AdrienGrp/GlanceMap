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
import kotlin.math.roundToInt

data class RecordingSensorMetrics(
    val heartRateBpm: Int? = null,
    val heartRateUpdatedAtMillis: Long = 0L,
    val stepCount: Int? = null,
    val stepCountUpdatedAtMillis: Long = 0L,
    val cadenceSpm: Int? = null,
    val cadenceUpdatedAtMillis: Long = 0L,
    val barometricPressureHpa: Double? = null,
    val barometricPressureUpdatedAtMillis: Long = 0L,
)

@Composable
fun RecordingSensorBridge(
    active: Boolean,
    paused: Boolean,
    selectedMetricIds: List<String>,
    onMetrics: (RecordingSensorMetrics) -> Unit,
) {
    val context = LocalContext.current
    val sensorMetricIds = remember(selectedMetricIds) { selectedMetricIds.filter { it in recordingSensorMetricIds } }
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

    DisposableEffect(context, active, paused, sensorMetricIds, permissionsToRequest) {
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
                                    cadenceSpm = cadence ?: metrics.cadenceSpm,
                                    cadenceUpdatedAtMillis =
                                        if (cadence != null) {
                                            now
                                        } else {
                                            metrics.cadenceUpdatedAtMillis
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
        val available = availableRecordingSensors(sensorManager)
        val bodySensorsGranted = hasPermission(context, Manifest.permission.BODY_SENSORS)
        val activityRecognitionGranted = hasActivityRecognitionPermission(context)
        DebugTelemetry.log(
            "TraceRecordingSensors",
            "event=register requested=${sensorMetricIds.joinToString("|")} " +
                "registered=${registered.joinToString("|").ifBlank { "none" }} " +
                "available=${available.joinToString("|").ifBlank { "none" }} " +
                "bodySensorsGranted=$bodySensorsGranted " +
                "activityRecognitionGranted=$activityRecognitionGranted",
        )

        onDispose {
            sensorManager.unregisterListener(listener)
            DebugTelemetry.log("TraceRecordingSensors", "event=unregister")
        }
    }
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
