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

    @Synchronized
    fun availabilitySummary(
        address: String?,
        nowElapsedMs: Long = SystemClock.elapsedRealtime(),
    ): String {
        val normalizedAddress = normalizeAddress(address) ?: return "unlinked"
        if (normalizedAddress in _connectedAddresses.value) return "connected"
        if (normalizedAddress in _connectingAddresses.value) return "connecting"
        val lastConnectedAt = lastConnectedAtElapsedMs[normalizedAddress] ?: return "not_verified"
        val ageMs = (nowElapsedMs - lastConnectedAt).coerceAtLeast(0L)
        return if (ageMs <= RECENTLY_VERIFIED_WINDOW_MS) {
            "recently_verified(ageMs=$ageMs)"
        } else {
            "verification_expired(ageMs=$ageMs)"
        }
    }

    fun normalizedAddress(address: String?): String? = normalizeAddress(address)

    private fun normalizeAddress(address: String?): String? =
        address
            ?.trim()
            ?.uppercase()
            ?.takeIf(String::isNotBlank)

    // The settings screen owns only a temporary GATT connection. Keep that successful
    // verification through the normal settings -> navigate -> REC handoff without
    // maintaining an always-on BLE connection.
    private const val RECENTLY_VERIFIED_WINDOW_MS = 5 * 60_000L
}
