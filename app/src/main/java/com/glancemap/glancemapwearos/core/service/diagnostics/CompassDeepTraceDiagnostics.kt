package com.glancemap.glancemapwearos.core.service.diagnostics

import android.content.Context
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.ArrayDeque

internal data class CompassDeepTraceState(
    val active: Boolean = false,
    val startedAtEpochMs: Long? = null,
    val lastStopReason: String? = null,
)

internal data class CompassDeepTraceSnapshot(
    val active: Boolean,
    val sessionCount: Int,
    val windowCount: Int,
    val droppedLines: Int,
    val lastStopReason: String?,
    val lines: List<String>,
)

internal object CompassDeepTraceDiagnostics {
    private const val TAG = "CompassDeepTrace"
    private const val MAX_BUFFERED_LINES = 720
    private const val WINDOW_DURATION_MS = 5_000L

    private val lock = Any()
    private val _state = MutableStateFlow(CompassDeepTraceState())
    private val lines = ArrayDeque<String>()
    private var droppedLines = 0
    private var sessionCount = 0
    private var windowCount = 0
    private var activeSessionStartWindowCount = 0
    private var currentWindow: CompassDeepTraceWindowAccumulator? = null
    private var sensorRegistration: CompassDeepTraceSensorRegistration? = null

    val state: StateFlow<CompassDeepTraceState> = _state.asStateFlow()

    fun start(
        context: Context,
        batteryBenchmarkSelected: Boolean,
    ): Boolean {
        val nowElapsedMs = SystemClock.elapsedRealtime()
        val nowEpochMs = System.currentTimeMillis()
        val startLine: String
        val inventoryLine: String
        val registration: CompassDeepTraceSensorRegistration
        synchronized(lock) {
            if (_state.value.active) return false
            sessionCount += 1
            activeSessionStartWindowCount = windowCount
            currentWindow = CompassDeepTraceWindowAccumulator(startedAtElapsedMs = nowElapsedMs)
            registration =
                startCompassDeepTraceSensorRegistration(context) { sensor, values, atElapsedMs ->
                    if (values.size >= 3) {
                        recordAt(atElapsedMs) { window ->
                            window.recordRawSensor(sensor, values[0], values[1], values[2])
                        }
                    }
                }
            sensorRegistration = registration
            _state.value =
                CompassDeepTraceState(
                    active = true,
                    startedAtEpochMs = nowEpochMs,
                )
            startLine =
                "session_start id=$sessionCount atMs=$nowEpochMs autoStop=false " +
                "windowMs=$WINDOW_DURATION_MS bufferLines=$MAX_BUFFERED_LINES sensorPeriodUs=40000"
            appendLineLocked(startLine)
            val registeredSensors = registration.registeredSensors
            inventoryLine = "session_sensors id=$sessionCount registered=${registeredSensors.ifEmpty { "none" }}"
            appendLineLocked(inventoryLine)
        }

        Log.d(TAG, startLine)
        Log.d(TAG, inventoryLine)

        if (batteryBenchmarkSelected || EnergyDiagnostics.isBatteryBenchmarkActive()) {
            EnergyDiagnostics.markBatteryBenchmarkInvalid("compass_deep_trace")
        }
        return true
    }

    fun stop(reason: String) {
        val registration: CompassDeepTraceSensorRegistration?
        val completedLines = mutableListOf<String>()
        synchronized(lock) {
            if (!_state.value.active) return
            flushWindowLocked(SystemClock.elapsedRealtime())?.let(completedLines::add)
            val stopLine =
                "session_stop id=$sessionCount atMs=${System.currentTimeMillis()} reason=$reason " +
                    "windows=${windowCount - activeSessionStartWindowCount}"
            appendLineLocked(stopLine)
            completedLines += stopLine
            registration = sensorRegistration
            sensorRegistration = null
            currentWindow = null
            _state.value = CompassDeepTraceState(lastStopReason = reason)
        }
        registration?.stop()
        completedLines.forEach { Log.d(TAG, it) }
    }

    fun clear() {
        stop(reason = "cleared")
        synchronized(lock) {
            lines.clear()
            droppedLines = 0
            sessionCount = 0
            windowCount = 0
            activeSessionStartWindowCount = 0
            _state.value = CompassDeepTraceState()
        }
    }

    fun onDiagnosticsCaptureState(
        captureActive: Boolean,
        fullDiagnostics: Boolean,
    ) {
        if (!captureActive) {
            stop(reason = "capture_stopped")
        } else if (!fullDiagnostics && _state.value.active) {
            EnergyDiagnostics.markBatteryBenchmarkInvalid("compass_deep_trace")
        }
    }

    fun recordProviderSample(sample: CompassDeepTraceProviderSample) {
        recordAt(sample.atElapsedMs) { it.recordProvider(sample) }
    }

    fun recordRenderSample(sample: CompassDeepTraceRenderSample) {
        recordAt(sample.atElapsedMs) { it.recordRender(sample) }
    }

    fun snapshot(): CompassDeepTraceSnapshot =
        synchronized(lock) {
            CompassDeepTraceSnapshot(
                active = _state.value.active,
                sessionCount = sessionCount,
                windowCount = windowCount,
                droppedLines = droppedLines,
                lastStopReason = _state.value.lastStopReason,
                lines = lines.toList(),
            )
        }

    private fun recordAt(
        atElapsedMs: Long,
        record: (CompassDeepTraceWindowAccumulator) -> Unit,
    ) {
        var completedLine: String? = null
        synchronized(lock) {
            if (!_state.value.active) return
            val window = currentWindow ?: CompassDeepTraceWindowAccumulator(atElapsedMs)
            if (atElapsedMs - window.startedAtElapsedMs >= WINDOW_DURATION_MS) {
                completedLine = flushWindowLocked(atElapsedMs)
                currentWindow = CompassDeepTraceWindowAccumulator(atElapsedMs)
            }
            record(currentWindow ?: return)
        }
        completedLine?.let { Log.d(TAG, it) }
    }

    private fun flushWindowLocked(endedAtElapsedMs: Long): String? {
        val window = currentWindow
        return if (window == null || !window.hasSamples) {
            null
        } else {
            windowCount += 1
            val line =
                "atMs=${System.currentTimeMillis()} " +
                    window.toTelemetryLine(index = windowCount, endedAtElapsedMs = endedAtElapsedMs)
            appendLineLocked(line)
            line
        }
    }

    private fun appendLineLocked(line: String) {
        lines.addLast(line)
        while (lines.size > MAX_BUFFERED_LINES) {
            lines.removeFirst()
            droppedLines += 1
        }
    }
}
