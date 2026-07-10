package com.glancemap.glancemapwearos.core.service.diagnostics

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import java.util.ArrayDeque
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs

internal object EnergyDiagnostics {
    private const val TAG = "EnergyTelemetry"
    private const val MAX_LINES = 2_000
    private const val MAX_CURRENT_INTEGRATION_GAP_MS = 150_000L
    private const val UA_MS_PER_MAH = 3_600_000_000.0

    data class ModeStats(
        val sampleCount: Int,
        val currentSampleCount: Int,
        val avgCurrentNowUa: Long?,
        val medianAbsCurrentNowUa: Long?,
        val minCurrentNowUa: Int?,
        val maxCurrentNowUa: Int?,
        val minLevelPct: Int?,
        val maxLevelPct: Int?,
        val avgLevelPct: Double?,
        val minTempC: Double?,
        val maxTempC: Double?,
        val avgTempC: Double?,
    )

    data class Summary(
        val modes: Map<String, ModeStats>,
        val batteryUse: BatteryUseStats?,
    )

    data class BatteryUseStats(
        val durationMs: Long,
        val consumedMah: Double,
        val averageDrawMa: Double,
        val integratedCurrentMah: Double?,
        val medianDrawMa: Double?,
        val p90DrawMa: Double?,
        val measurement: String,
        val confidence: String,
        val chargeCounterStartUah: Int?,
        val chargeCounterEndUah: Int?,
    )

    private val lock = Any()

    private enum class CaptureMode {
        OFF,
        FULL,
        BATTERY_BENCHMARK,
    }

    private val captureMode = AtomicReference(CaptureMode.OFF)
    private val lines = ArrayDeque<String>()
    private var droppedLines: Int = 0

    fun clear() {
        synchronized(lock) {
            lines.clear()
            droppedLines = 0
        }
    }

    fun setEnabled(value: Boolean) {
        captureMode.set(if (value) CaptureMode.FULL else CaptureMode.OFF)
    }

    fun configure(
        captureActive: Boolean,
        fullDiagnostics: Boolean,
    ) {
        captureMode.set(
            when {
                !captureActive -> CaptureMode.OFF
                fullDiagnostics -> CaptureMode.FULL
                else -> CaptureMode.BATTERY_BENCHMARK
            },
        )
    }

    fun isEnabled(): Boolean = captureMode.get() != CaptureMode.OFF

    internal fun shouldRecordSample(reason: String): Boolean =
        when (captureMode.get()) {
            CaptureMode.OFF -> false
            CaptureMode.FULL -> true
            CaptureMode.BATTERY_BENCHMARK ->
                reason == "periodic" || reason == "capture_toggle_on" || reason == "capture_toggle_off"
        }

    fun snapshotLines(): List<String> = synchronized(lock) { lines.toList() }

    fun droppedLineCount(): Int = synchronized(lock) { droppedLines }

    fun maxBufferedLines(): Int = MAX_LINES

    fun summary(): Summary = summarizeLines(snapshotLines())

    internal fun summarizeLines(snapshot: List<String>): Summary {
        if (snapshot.isEmpty()) return Summary(modes = emptyMap(), batteryUse = null)
        val accumulators = linkedMapOf<String, ModeAccumulator>()
        snapshot.forEach { line ->
            if (" level=" !in line && " curNowUa=" !in line && " tempC=" !in line) return@forEach
            val mode = classifyMode(line)
            val accumulator = accumulators.getOrPut(mode) { ModeAccumulator() }
            accumulator.add(line)
        }
        return Summary(
            modes =
                accumulators
                    .mapValues { (_, accumulator) -> accumulator.toStats() }
                    .filterValues { stats -> stats.sampleCount > 0 },
            batteryUse = summarizeBatteryUse(snapshot),
        )
    }

    fun recordSample(
        context: Context,
        reason: String,
        detail: String = "",
    ) {
        if (!shouldRecordSample(reason)) return

        val batteryManager = context.getSystemService(BatteryManager::class.java)
        val powerManager = context.getSystemService(PowerManager::class.java)
        val batteryIntent =
            runCatching {
                context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            }.getOrNull()

        val levelPct = batteryPercent(batteryIntent)
        val batteryStatus = batteryStatus(batteryIntent)
        val plugged = batteryPlugged(batteryIntent)
        val temperatureC = batteryTemperatureC(batteryIntent)
        val voltageMv = batteryVoltageMv(batteryIntent)

        val currentNowUa = batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
        val currentAvgUa = batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_AVERAGE)
        val capacityPct = batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        val chargeCounterUah = batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER)

        val powerSave = powerManager?.isPowerSaveMode ?: false
        val interactive = powerManager?.isInteractive ?: false
        val thermal =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && powerManager != null) {
                powerManager.currentThermalStatus.toString()
            } else {
                "na"
            }

        val line =
            buildString {
                append("atMs=").append(System.currentTimeMillis())
                append(" reason=").append(reason)
                if (detail.isNotBlank()) {
                    append(" ").append(detail)
                }
                append(" level=").append(levelPct)
                append(" status=").append(batteryStatus)
                append(" plugged=").append(plugged)
                append(" tempC=").append(temperatureC)
                append(" voltMv=").append(voltageMv)
                append(" curNowUa=").append(propertyOrNa(currentNowUa))
                append(" curAvgUa=").append(propertyOrNa(currentAvgUa))
                append(" capPropPct=").append(propertyOrNa(capacityPct))
                append(" chargeCounterUah=").append(propertyOrNa(chargeCounterUah))
                append(" saver=").append(powerSave)
                append(" interactive=").append(interactive)
                append(" thermal=").append(thermal)
            }

        push(line)
        DebugTelemetry.log(TAG, line)
    }

    fun recordEvent(
        reason: String,
        detail: String = "",
    ) {
        // Context-only events are useful for full troubleshooting, but deliberately omitted
        // from battery benchmark mode to keep its measurement overhead minimal.
        if (captureMode.get() != CaptureMode.FULL || !DebugTelemetry.isEnabled()) return
        val line =
            if (detail.isBlank()) {
                "atMs=${System.currentTimeMillis()} reason=$reason"
            } else {
                "atMs=${System.currentTimeMillis()} reason=$reason $detail"
            }
        push(line)
        DebugTelemetry.log(TAG, line)
    }

    private fun push(line: String) {
        synchronized(lock) {
            lines.addLast(line)
            while (lines.size > MAX_LINES) {
                lines.removeFirst()
                droppedLines += 1
            }
        }
    }

    private fun propertyOrNa(value: Int?): String {
        if (value == null || value == Int.MIN_VALUE) return "na"
        return value.toString()
    }

    private fun batteryPercent(intent: Intent?): String {
        val level = intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        if (level < 0 || scale <= 0) return "na"
        val pct = (level * 100f / scale.toFloat())
        return TelemetryFormatters.decimal(pct, 0)
    }

    private fun batteryTemperatureC(intent: Intent?): String {
        val tempTenths =
            intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
                ?: Int.MIN_VALUE
        if (tempTenths == Int.MIN_VALUE) return "na"
        return TelemetryFormatters.decimal(tempTenths / 10f, 1)
    }

    private fun batteryVoltageMv(intent: Intent?): String {
        val voltage = intent?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, Int.MIN_VALUE) ?: Int.MIN_VALUE
        if (voltage == Int.MIN_VALUE) return "na"
        return voltage.toString()
    }

    private fun batteryStatus(intent: Intent?): String =
        when (intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1) {
            BatteryManager.BATTERY_STATUS_CHARGING -> "charging"
            BatteryManager.BATTERY_STATUS_DISCHARGING -> "discharging"
            BatteryManager.BATTERY_STATUS_FULL -> "full"
            BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "not_charging"
            else -> "unknown"
        }

    private fun batteryPlugged(intent: Intent?): String =
        when (intent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) {
            BatteryManager.BATTERY_PLUGGED_AC -> "ac"
            BatteryManager.BATTERY_PLUGGED_USB -> "usb"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "wireless"
            else -> "battery"
        }

    private fun classifyMode(line: String): String =
        when {
            "screenState=SCREEN_OFF" in line || "interactive=false" in line -> "screen_off"
            "mode=BURST" in line || "burst=true" in line -> "burst"
            "mode=INTERACTIVE" in line || "screenState=INTERACTIVE" in line || "tracking=true" in line -> {
                "interactive"
            }
            "tracking=false" in line -> "idle"
            "transfer" in line.lowercase() || "channel" in line.lowercase() || "http" in line.lowercase() -> "transfer"
            else -> "other"
        }

    private data class BatteryObservation(
        val atMs: Long,
        val dischargeCurrentUa: Long?,
        val chargeCounterUah: Int?,
    )

    private data class IntegratedCurrentUse(
        val consumedMah: Double,
        val durationMs: Long,
    )

    private data class ChargeCounterUse(
        val consumedMah: Double,
        val durationMs: Long,
    )

    private fun summarizeBatteryUse(lines: List<String>): BatteryUseStats? {
        val observations =
            lines
                .mapNotNull(::batteryObservationOrNull)
                .sortedBy { it.atMs }
        if (observations.size < 2) return null
        return buildBatteryUse(observations)
    }

    private fun buildBatteryUse(observations: List<BatteryObservation>): BatteryUseStats? {
        val integratedUse = integrateCurrentUse(observations)
        val chargeObservations = observations.filter { it.chargeCounterUah != null }
        val startCharge = chargeObservations.firstOrNull()
        val endCharge = chargeObservations.lastOrNull()
        val chargeCounterUse = resolveChargeCounterUse(startCharge, endCharge)
        val consumedMah = chargeCounterUse?.consumedMah ?: integratedUse?.consumedMah
        val durationMs = chargeCounterUse?.durationMs ?: integratedUse?.durationMs
        return if (consumedMah != null && durationMs != null) {
            val currentSamples = observations.mapNotNull { it.dischargeCurrentUa }.sorted()
            BatteryUseStats(
                durationMs = durationMs,
                consumedMah = consumedMah,
                averageDrawMa = consumedMah * 3_600_000.0 / durationMs,
                integratedCurrentMah = integratedUse?.consumedMah,
                medianDrawMa = percentile(currentSamples, 0.5)?.div(1_000.0),
                p90DrawMa = percentile(currentSamples, 0.9)?.div(1_000.0),
                measurement = if (chargeCounterUse != null) "charge_counter" else "integrated_current",
                confidence = if (chargeCounterUse != null) "high" else "medium",
                chargeCounterStartUah = startCharge?.chargeCounterUah,
                chargeCounterEndUah = endCharge?.chargeCounterUah,
            )
        } else {
            null
        }
    }

    private fun batteryObservationOrNull(line: String): BatteryObservation? {
        val atMs = tokenValue(line, "atMs=")?.toLongOrNull()
        val discharging = tokenValue(line, "status=") == "discharging"
        val unplugged = tokenValue(line, "plugged=") == "battery"
        return if (atMs != null && discharging && unplugged) {
            BatteryObservation(
                atMs = atMs,
                dischargeCurrentUa =
                    tokenValue(line, "curNowUa=")
                        ?.toLongOrNull()
                        ?.takeIf { it != Int.MIN_VALUE.toLong() }
                        ?.let(::abs),
                chargeCounterUah =
                    tokenValue(line, "chargeCounterUah=")
                        ?.toIntOrNull()
                        ?.takeIf { it != Int.MIN_VALUE },
            )
        } else {
            null
        }
    }

    private fun resolveChargeCounterUse(
        start: BatteryObservation?,
        end: BatteryObservation?,
    ): ChargeCounterUse? {
        val startUah = start?.chargeCounterUah
        val endUah = end?.chargeCounterUah
        val durationMs = if (start != null && end != null) end.atMs - start.atMs else 0L
        val countersAvailable = startUah != null && endUah != null
        val counterDecreased = countersAvailable && checkNotNull(startUah) > checkNotNull(endUah)
        if (!counterDecreased || durationMs <= 0L) return null
        return ChargeCounterUse(
            consumedMah = (checkNotNull(startUah) - checkNotNull(endUah)) / 1_000.0,
            durationMs = durationMs,
        )
    }

    private fun integrateCurrentUse(observations: List<BatteryObservation>): IntegratedCurrentUse? {
        var integratedUaMs = 0.0
        var integratedDurationMs = 0L
        observations.zipWithNext().forEach { (previous, current) ->
            val previousUa = previous.dischargeCurrentUa ?: return@forEach
            val currentUa = current.dischargeCurrentUa ?: return@forEach
            val durationMs = current.atMs - previous.atMs
            if (durationMs <= 0L || durationMs > MAX_CURRENT_INTEGRATION_GAP_MS) return@forEach
            integratedUaMs += ((previousUa + currentUa) / 2.0) * durationMs
            integratedDurationMs += durationMs
        }
        return if (integratedDurationMs > 0L) {
            IntegratedCurrentUse(
                consumedMah = integratedUaMs / UA_MS_PER_MAH,
                durationMs = integratedDurationMs,
            )
        } else {
            null
        }
    }

    private fun percentile(
        sortedValues: List<Long>,
        quantile: Double,
    ): Long? {
        if (sortedValues.isEmpty()) return null
        val index = ((sortedValues.lastIndex) * quantile).toInt().coerceIn(sortedValues.indices)
        return sortedValues[index]
    }

    @Suppress("ReturnCount")
    private fun tokenValue(
        line: String,
        key: String,
    ): String? {
        val index = line.indexOf(key)
        if (index < 0) return null
        val start = index + key.length
        if (start >= line.length) return null
        val end = line.indexOf(' ', start).let { if (it < 0) line.length else it }
        return line.substring(start, end).trim()
    }

    private class ModeAccumulator {
        private var sampleCount = 0
        private var currentSampleCount = 0
        private var currentTotalUa = 0L
        private val absoluteCurrentSamplesUa = mutableListOf<Long>()
        private var minCurrentUa: Int? = null
        private var maxCurrentUa: Int? = null
        private var levelSampleCount = 0
        private var levelTotal = 0
        private var minLevel: Int? = null
        private var maxLevel: Int? = null
        private var tempSampleCount = 0
        private var tempTotal = 0.0
        private var minTemp: Double? = null
        private var maxTemp: Double? = null

        @Suppress("CyclomaticComplexMethod")
        fun add(line: String) {
            sampleCount += 1
            tokenValue(line, "curNowUa=")
                ?.toIntOrNull()
                ?.takeIf { it != Int.MIN_VALUE }
                ?.let { current ->
                    currentSampleCount += 1
                    currentTotalUa += current.toLong()
                    absoluteCurrentSamplesUa += abs(current.toLong())
                    minCurrentUa = minCurrentUa?.let { minOf(it, current) } ?: current
                    maxCurrentUa = maxCurrentUa?.let { maxOf(it, current) } ?: current
                }
            tokenValue(line, "level=")
                ?.toIntOrNull()
                ?.let { level ->
                    levelSampleCount += 1
                    levelTotal += level
                    minLevel = minLevel?.let { minOf(it, level) } ?: level
                    maxLevel = maxLevel?.let { maxOf(it, level) } ?: level
                }
            tokenValue(line, "tempC=")
                ?.toDoubleOrNull()
                ?.let { temp ->
                    tempSampleCount += 1
                    tempTotal += temp
                    minTemp = minTemp?.let { minOf(it, temp) } ?: temp
                    maxTemp = maxTemp?.let { maxOf(it, temp) } ?: temp
                }
        }

        fun toStats(): ModeStats =
            ModeStats(
                sampleCount = sampleCount,
                currentSampleCount = currentSampleCount,
                avgCurrentNowUa =
                    if (currentSampleCount > 0) {
                        currentTotalUa / currentSampleCount
                    } else {
                        null
                    },
                medianAbsCurrentNowUa = median(absoluteCurrentSamplesUa),
                minCurrentNowUa = minCurrentUa,
                maxCurrentNowUa = maxCurrentUa,
                minLevelPct = minLevel,
                maxLevelPct = maxLevel,
                avgLevelPct =
                    if (levelSampleCount > 0) {
                        levelTotal.toDouble() / levelSampleCount.toDouble()
                    } else {
                        null
                    },
                minTempC = minTemp,
                maxTempC = maxTemp,
                avgTempC =
                    if (tempSampleCount > 0) {
                        tempTotal / tempSampleCount.toDouble()
                    } else {
                        null
                    },
            )

        private fun median(values: List<Long>): Long? {
            if (values.isEmpty()) return null
            val sorted = values.sorted()
            val middle = sorted.size / 2
            return if (sorted.size % 2 == 0) {
                (sorted[middle - 1] + sorted[middle]) / 2L
            } else {
                sorted[middle]
            }
        }
    }
}
