package com.glancemap.glancemapwearos.core.service.diagnostics

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.PowerManager
import java.util.ArrayDeque

internal object EnergyDiagnostics {
    private const val TAG = "EnergyTelemetry"
    private const val MAX_LINES = 800

    private val lock = Any()
    private val lines = ArrayDeque<String>()
    private var droppedLines: Int = 0

    fun clear() {
        synchronized(lock) {
            lines.clear()
            droppedLines = 0
        }
    }

    fun snapshotLines(): List<String> = synchronized(lock) { lines.toList() }

    fun droppedLineCount(): Int = synchronized(lock) { droppedLines }

    fun maxBufferedLines(): Int = MAX_LINES

    fun recordSample(
        context: Context,
        reason: String,
        detail: String = "",
    ) {
        if (!DebugTelemetry.isEnabled()) return

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
                append("reason=").append(reason)
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
        if (!DebugTelemetry.isEnabled()) return
        val line =
            if (detail.isBlank()) {
                "reason=$reason"
            } else {
                "reason=$reason $detail"
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
        return "%.0f".format(pct)
    }

    private fun batteryTemperatureC(intent: Intent?): String {
        val tempTenths =
            intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
                ?: Int.MIN_VALUE
        if (tempTenths == Int.MIN_VALUE) return "na"
        return "%.1f".format(tempTenths / 10f)
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
}
