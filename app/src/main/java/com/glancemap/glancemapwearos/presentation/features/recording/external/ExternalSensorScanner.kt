package com.glancemap.glancemapwearos.presentation.features.recording.external

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import com.glancemap.glancemapwearos.core.service.diagnostics.DebugTelemetry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ExternalSensorScanner(
    private val context: Context,
) {
    private val devicesByAddress = linkedMapOf<String, ExternalSensorDevice>()
    private val loggedDeviceAddresses = mutableSetOf<String>()
    private val _devices = MutableStateFlow<List<ExternalSensorDevice>>(emptyList())
    private val _status = MutableStateFlow(ExternalSensorScanStatus.IDLE)

    val devices: StateFlow<List<ExternalSensorDevice>> = _devices.asStateFlow()
    val status: StateFlow<ExternalSensorScanStatus> = _status.asStateFlow()

    private val callback =
        object : ScanCallback() {
            override fun onScanResult(
                callbackType: Int,
                result: ScanResult,
            ) {
                mergeResult(result)
            }

            override fun onBatchScanResults(results: MutableList<ScanResult>) {
                results.forEach(::mergeResult)
            }

            override fun onScanFailed(errorCode: Int) {
                _status.value = ExternalSensorScanStatus.SCAN_FAILED
                DebugTelemetry.log("ExternalSensors", "event=scan_failed code=$errorCode")
            }
        }

    @SuppressLint("MissingPermission")
    fun startScan() {
        if (!hasRequiredPermissions(context)) {
            _status.value = ExternalSensorScanStatus.PERMISSION_MISSING
            return
        }
        val adapter =
            (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)
                ?.adapter
        if (adapter == null) {
            _status.value = ExternalSensorScanStatus.BLUETOOTH_UNAVAILABLE
            return
        }
        if (!adapter.isEnabled) {
            _status.value = ExternalSensorScanStatus.BLUETOOTH_OFF
            return
        }

        val scanner = adapter.bluetoothLeScanner
        if (scanner == null) {
            _status.value = ExternalSensorScanStatus.BLUETOOTH_UNAVAILABLE
            return
        }

        runCatching {
            scanner.startScan(
                null,
                ScanSettings.Builder()
                    .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                    .build(),
                callback,
            )
        }.onSuccess {
            _status.value = ExternalSensorScanStatus.SCANNING
            DebugTelemetry.log("ExternalSensors", "event=scan_started")
        }.onFailure { error ->
            _status.value = ExternalSensorScanStatus.SCAN_FAILED
            DebugTelemetry.log("ExternalSensors", "event=scan_start_failed error=${error.javaClass.simpleName}")
        }
    }

    @SuppressLint("MissingPermission")
    fun stopScan() {
        val adapter =
            (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)
                ?.adapter
        val scanner = adapter?.bluetoothLeScanner
        runCatching { scanner?.stopScan(callback) }
        if (_status.value == ExternalSensorScanStatus.SCANNING) {
            _status.value = ExternalSensorScanStatus.IDLE
        }
        DebugTelemetry.log("ExternalSensors", "event=scan_stopped devices=${devicesByAddress.size}")
    }

    private fun mergeResult(result: ScanResult) {
        val address = safeAddress(result) ?: return
        val serviceUuids = result.scanRecord?.serviceUuids.orEmpty().map { it.uuid }.toSet()
        val kinds = ExternalSensorKind.entries.filter { it.serviceUuid in serviceUuids }.toSet()
        val name = safeName(result).ifBlank { "BLE ${address.takeLast(5)}" }
        if (loggedDeviceAddresses.add(address)) {
            DebugTelemetry.log(
                "ExternalSensors",
                "event=device_seen name=${name.sanitizeTelemetryToken()} " +
                    "addressSuffix=${address.takeLast(5)} " +
                    "kinds=${kinds.joinToString("|") { it.label.sanitizeTelemetryToken() }.ifBlank { "unknown" }} " +
                    "services=${serviceUuids.joinToString("|") { it.toString() }.ifBlank { "none" }}",
            )
        }
        devicesByAddress[address] =
            ExternalSensorDevice(
                name = name,
                address = address,
                rssi = result.rssi,
                kinds = kinds,
                lastSeenAtMillis = System.currentTimeMillis(),
            )
        _devices.value =
            devicesByAddress.values
                .sortedWith(
                    compareByDescending<ExternalSensorDevice> { it.kinds.isNotEmpty() }
                        .thenByDescending { it.rssi ?: Int.MIN_VALUE },
                )
    }

    private fun safeName(result: ScanResult): String =
        result.scanRecord?.deviceName
            ?: runCatching { result.device.name }.getOrNull()
            ?: ""

    private fun safeAddress(result: ScanResult): String? =
        runCatching { result.device.address }.getOrNull()

    companion object {
        fun requiredPermissions(): List<String> =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                listOf(
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT,
                )
            } else {
                listOf(Manifest.permission.ACCESS_FINE_LOCATION)
            }

        fun hasRequiredPermissions(context: Context): Boolean =
            requiredPermissions().all { permission ->
                ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
            }
    }
}

private fun String.sanitizeTelemetryToken(): String =
    replace(' ', '_')
        .replace('|', '_')
        .replace('=', '_')
