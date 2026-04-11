package com.glancemap.glancemapwearos.core.service.location.adapters

import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.CancellationSignal
import android.os.SystemClock
import com.glancemap.glancemapwearos.core.service.location.policy.LocationSourceMode
import java.util.ArrayDeque
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

internal class WatchGpsLocationGateway(
    private val locationManager: LocationManager,
    private val packageManager: PackageManager,
    private val callbackExecutor: Executor
) : LocationGateway {
    private data class LocationSignature(
        val elapsedRealtimeNanos: Long,
        val timeMs: Long,
        val latitudeE7: Int,
        val longitudeE7: Int,
        val accuracyDeciMeters: Int
    )

    private data class SanitizedBatch(
        val locations: List<Location>,
        val duplicateCount: Int
    )

    private companion object {
        private const val MAX_RECENT_LOCATION_SIGNATURES = 64
    }

    private val activeListeners = LinkedHashSet<LocationListener>()
    private val requestMutex = Mutex()
    @Volatile private var registeringListener: LocationListener? = null
    private val recentLocationSignatures = LinkedHashSet<LocationSignature>()
    private val recentLocationSignatureOrder = ArrayDeque<LocationSignature>()

    @SuppressLint("MissingPermission")
    override suspend fun getCurrentLocation(request: CurrentLocationRequestParams): Location? {
        ensureGpsProviderAvailable()
        val cachedLocation = getLastLocation()
        val nowElapsedMs = SystemClock.elapsedRealtime()
        if (cachedLocation != null && locationAgeMs(cachedLocation, nowElapsedMs) <= request.maxUpdateAgeMs) {
            return cachedLocation
        }

        return withTimeoutOrNull(request.durationMs.coerceAtLeast(1L)) {
            suspendCancellableCoroutine { continuation ->
                val cancellationSignal = CancellationSignal()
                continuation.invokeOnCancellation { cancellationSignal.cancel() }
                locationManager.getCurrentLocation(
                    LocationManager.GPS_PROVIDER,
                    cancellationSignal,
                    callbackExecutor
                ) { location ->
                    if (continuation.isActive) {
                        continuation.resume(location)
                    }
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    override suspend fun getLastLocation(): Location? {
        ensureGpsProviderAvailable()
        return locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
    }

    @SuppressLint("MissingPermission")
    override suspend fun requestLocationUpdates(
        request: LocationUpdateRequestParams,
        sink: LocationUpdateSink
    ) {
        requestMutex.withLock {
            ensureGpsProviderAvailable()
            removeLocationUpdatesLocked()
            clearRecentLocationSignatures()
            val listener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    sink.onLocationAvailability(true)
                    emitLocations(
                        sink = sink,
                        rawLocations = listOf(location)
                    )
                }

                override fun onLocationChanged(locations: MutableList<Location>) {
                    if (locations.isNotEmpty()) {
                        sink.onLocationAvailability(true)
                        emitLocations(
                            sink = sink,
                            rawLocations = locations.toList()
                        )
                    }
                }

                override fun onProviderEnabled(provider: String) {
                    if (provider == LocationManager.GPS_PROVIDER) {
                        sink.onLocationAvailability(true)
                    }
                }

                override fun onProviderDisabled(provider: String) {
                    if (provider == LocationManager.GPS_PROVIDER) {
                        sink.onLocationAvailability(false)
                    }
                }
            }

            sink.onLocationAvailability(isGpsProviderEnabled())
            registeringListener = listener
            try {
                locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    request.intervalMs,
                    request.minDistanceMeters,
                    callbackExecutor,
                    listener
                )
                synchronized(activeListeners) {
                    activeListeners += listener
                }
            } catch (error: Exception) {
                // Request registration may have succeeded before a cancellation/exception surfaced.
                runCatching { locationManager.removeUpdates(listener) }
                throw error
            } finally {
                if (registeringListener === listener) {
                    registeringListener = null
                }
            }
        }
    }

    override suspend fun removeLocationUpdates() {
        requestMutex.withLock {
            removeLocationUpdatesLocked()
        }
    }

    private fun removeLocationUpdatesLocked() {
        val listeners = drainListeners(includeRegisteringListener = true)
        clearRecentLocationSignatures()
        var firstError: Exception? = null
        listeners.forEach { listener ->
            try {
                locationManager.removeUpdates(listener)
            } catch (error: Exception) {
                if (firstError == null) {
                    firstError = error
                }
            }
        }
        firstError?.let { throw it }
    }

    override fun removeLocationUpdatesBestEffort() {
        clearRecentLocationSignatures()
        val listeners = drainListeners(includeRegisteringListener = true)
        listeners.forEach { listener ->
            val removed = runCatching {
                locationManager.removeUpdates(listener)
            }.isSuccess
            if (removed) {
                synchronized(activeListeners) {
                    activeListeners.remove(listener)
                }
            }
        }
    }

    private fun emitLocations(sink: LocationUpdateSink, rawLocations: List<Location>) {
        val sanitized = sanitizeLocations(rawLocations)
        if (sanitized.locations.isEmpty()) return
        sink.onLocations(
            LocationUpdateEvent(
                origin = LocationSourceMode.WATCH_GPS,
                candidates = sanitized.locations,
                rawCandidateCount = rawLocations.size,
                duplicateCandidatesDropped = sanitized.duplicateCount
            )
        )
    }

    private fun drainListeners(includeRegisteringListener: Boolean): List<LocationListener> {
        synchronized(activeListeners) {
            val listeners = LinkedHashSet<LocationListener>()
            if (activeListeners.isNotEmpty()) {
                listeners += activeListeners
                activeListeners.clear()
            }
            if (includeRegisteringListener) {
                val pending = registeringListener
                if (pending != null) {
                    listeners += pending
                }
            }
            if (listeners.isEmpty()) return emptyList()
            return listeners.toList()
        }
    }

    private fun sanitizeLocations(locations: List<Location>): SanitizedBatch {
        if (locations.isEmpty()) return SanitizedBatch(emptyList(), 0)
        val uniqueLocations = ArrayList<Location>(locations.size)
        val batchSignatures = LinkedHashSet<LocationSignature>(locations.size)
        var duplicateCount = 0
        synchronized(recentLocationSignatures) {
            locations.forEach { location ->
                val signature = location.signature()
                val alreadySeen = signature in batchSignatures || signature in recentLocationSignatures
                if (alreadySeen) {
                    duplicateCount += 1
                } else {
                    batchSignatures += signature
                    rememberLocationSignature(signature)
                    uniqueLocations += location
                }
            }
        }
        return SanitizedBatch(
            locations = uniqueLocations,
            duplicateCount = duplicateCount
        )
    }

    private fun rememberLocationSignature(signature: LocationSignature) {
        if (!recentLocationSignatures.add(signature)) return
        recentLocationSignatureOrder.addLast(signature)
        while (recentLocationSignatureOrder.size > MAX_RECENT_LOCATION_SIGNATURES) {
            val removed = recentLocationSignatureOrder.removeFirst()
            recentLocationSignatures.remove(removed)
        }
    }

    private fun clearRecentLocationSignatures() {
        synchronized(recentLocationSignatures) {
            recentLocationSignatures.clear()
            recentLocationSignatureOrder.clear()
        }
    }

    private fun ensureGpsProviderAvailable() {
        val availabilityReason = resolveWatchGpsAvailabilityReason(
            hasGpsHardwareFeature = hasGpsHardwareFeature(),
            isGpsProviderPresent = isGpsProviderPresent(),
            isGpsProviderEnabled = isGpsProviderEnabled()
        )
        if (availabilityReason != WatchGpsAvailabilityReason.AVAILABLE) {
            throw WatchGpsUnavailableException(reason = availabilityReason)
        }
    }

    private fun hasGpsHardwareFeature(): Boolean {
        return runCatching { packageManager.hasSystemFeature(PackageManager.FEATURE_LOCATION_GPS) }
            .getOrDefault(false)
    }

    private fun isGpsProviderPresent(): Boolean {
        return runCatching { locationManager.allProviders.contains(LocationManager.GPS_PROVIDER) }
            .getOrDefault(false)
    }

    private fun isGpsProviderEnabled(): Boolean {
        return runCatching { locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) }
            .getOrDefault(false)
    }

    private fun locationAgeMs(location: Location, nowElapsedMs: Long): Long {
        val locationElapsedMs = location.elapsedRealtimeNanos / 1_000_000L
        if (locationElapsedMs <= 0L) return Long.MAX_VALUE
        return (nowElapsedMs - locationElapsedMs).coerceAtLeast(0L)
    }

    private fun Location.signature(): LocationSignature {
        return LocationSignature(
            elapsedRealtimeNanos = elapsedRealtimeNanos,
            timeMs = time,
            latitudeE7 = (latitude * 1e7).toInt(),
            longitudeE7 = (longitude * 1e7).toInt(),
            accuracyDeciMeters = (accuracy * 10f).toInt()
        )
    }
}
