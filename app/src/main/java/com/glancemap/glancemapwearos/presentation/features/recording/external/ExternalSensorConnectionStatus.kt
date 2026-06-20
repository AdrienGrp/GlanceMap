package com.glancemap.glancemapwearos.presentation.features.recording.external

import android.os.SystemClock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object ExternalSensorConnectionStatus {
    private val _connectedAddresses = MutableStateFlow<Set<String>>(emptySet())
    val connectedAddresses: StateFlow<Set<String>> = _connectedAddresses.asStateFlow()
    private val _connectingAddresses = MutableStateFlow<Set<String>>(emptySet())
    val connectingAddresses: StateFlow<Set<String>> = _connectingAddresses.asStateFlow()
    private val _batteryLevels = MutableStateFlow<Map<String, Int>>(emptyMap())
    val batteryLevels: StateFlow<Map<String, Int>> = _batteryLevels.asStateFlow()
    private val lastConnectedAtElapsedMs = mutableMapOf<String, Long>()

    @Synchronized
    fun update(
        address: String,
        connected: Boolean,
    ) {
        val normalizedAddress = normalizeAddress(address) ?: return
        _connectedAddresses.value =
            if (connected) {
                lastConnectedAtElapsedMs[normalizedAddress] = SystemClock.elapsedRealtime()
                _connectedAddresses.value + normalizedAddress
            } else {
                _connectedAddresses.value - normalizedAddress
            }
        _connectingAddresses.value = _connectingAddresses.value - normalizedAddress
    }

    @Synchronized
    fun markConnecting(address: String) {
        val normalizedAddress = normalizeAddress(address) ?: return
        _connectingAddresses.value = _connectingAddresses.value + normalizedAddress
    }

    @Synchronized
    fun updateBattery(
        address: String,
        batteryLevelPercent: Int?,
    ) {
        val level = batteryLevelPercent?.takeIf { it in 0..100 } ?: return
        val normalizedAddress = normalizeAddress(address) ?: return
        _batteryLevels.value = _batteryLevels.value + (normalizedAddress to level)
    }

    @Synchronized
    fun isConnectedOrRecentlyVerified(
        address: String?,
        nowElapsedMs: Long = SystemClock.elapsedRealtime(),
        recentWindowMs: Long = RECENTLY_VERIFIED_WINDOW_MS,
    ): Boolean {
        val normalizedAddress = normalizeAddress(address) ?: return false
        if (normalizedAddress in _connectedAddresses.value) return true
        val lastConnectedAt = lastConnectedAtElapsedMs[normalizedAddress] ?: return false
        return nowElapsedMs - lastConnectedAt in 0L..recentWindowMs
    }

    fun normalizedAddress(address: String?): String? = normalizeAddress(address)

    private fun normalizeAddress(address: String?): String? =
        address
            ?.trim()
            ?.uppercase()
            ?.takeIf(String::isNotBlank)

    private const val RECENTLY_VERIFIED_WINDOW_MS = 60_000L
}
