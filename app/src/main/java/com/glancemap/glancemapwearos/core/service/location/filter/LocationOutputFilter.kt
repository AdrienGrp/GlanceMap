package com.glancemap.glancemapwearos.core.service.location.filter

import android.location.Location
import com.glancemap.glancemapwearos.core.service.location.policy.LocationFixPolicy
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max

internal class LocationOutputFilter(
    private val filter: AdaptivePositionFilter = AdaptivePositionFilter(),
) {
    private var originLatitudeDeg: Double = Double.NaN
    private var originLongitudeDeg: Double = Double.NaN
    private var cosOriginLatitude: Double = Double.NaN

    fun reset() {
        filter.reset()
        originLatitudeDeg = Double.NaN
        originLongitudeDeg = Double.NaN
        cosOriginLatitude = Double.NaN
    }

    fun filter(
        location: Location,
        nowElapsedMs: Long,
    ): Location {
        ensureOrigin(location)
        val measurementElapsedMs =
            resolveMeasurementElapsedMs(
                location = location,
                nowElapsedMs = nowElapsedMs,
            )
        val estimate =
            filter.update(
                measurement =
                    PositionMeasurement(
                        xMeters = longitudeToMeters(location.longitude),
                        yMeters = latitudeToMeters(location.latitude),
                        accuracyMeters = location.accuracy,
                        elapsedMs = measurementElapsedMs,
                        speedMps = location.speed.takeIf { location.hasSpeed() },
                    ),
            )
        val outputSpeedMps =
            resolveOutputSpeedMps(
                hasRawSpeed = location.hasSpeed(),
                rawSpeedMps = location.speed.takeIf { location.hasSpeed() },
                accuracyM = location.accuracy,
                estimatedSpeedMps = estimate.speedMps,
                positionStdDevMeters = estimate.positionStdDevMeters,
            )
        return Location(location).apply {
            latitude = metersToLatitude(estimate.yMeters)
            longitude = normalizeLongitude(metersToLongitude(estimate.xMeters))
            if (outputSpeedMps != null) {
                speed = outputSpeedMps
            } else {
                removeSpeed()
            }
            if (outputSpeedMps != null && outputSpeedMps >= MIN_FILTERED_BEARING_SPEED_MPS) {
                bearing = estimate.bearingDeg
            } else {
                removeBearing()
            }
        }
    }

    private fun ensureOrigin(location: Location) {
        if (originLatitudeDeg.isFinite() && originLongitudeDeg.isFinite()) return
        originLatitudeDeg = location.latitude
        originLongitudeDeg = location.longitude
        cosOriginLatitude =
            max(
                abs(cos(Math.toRadians(originLatitudeDeg))),
                MIN_COSINE_LATITUDE,
            )
    }

    private fun resolveMeasurementElapsedMs(
        location: Location,
        nowElapsedMs: Long,
    ): Long {
        val ageMs = LocationFixPolicy.locationAgeMs(location, nowElapsedMs)
        if (ageMs == Long.MAX_VALUE) return nowElapsedMs
        return (nowElapsedMs - ageMs).coerceAtLeast(0L)
    }

    private fun latitudeToMeters(latitudeDeg: Double): Double = Math.toRadians(latitudeDeg - originLatitudeDeg) * EARTH_RADIUS_METERS

    private fun longitudeToMeters(longitudeDeg: Double): Double =
        Math.toRadians(longitudeDeg - originLongitudeDeg) *
            EARTH_RADIUS_METERS *
            cosOriginLatitude

    private fun metersToLatitude(yMeters: Double): Double = originLatitudeDeg + Math.toDegrees(yMeters / EARTH_RADIUS_METERS)

    private fun metersToLongitude(xMeters: Double): Double =
        originLongitudeDeg +
            Math.toDegrees(
                xMeters / (EARTH_RADIUS_METERS * cosOriginLatitude),
            )

    private fun normalizeLongitude(longitudeDeg: Double): Double {
        var normalized = longitudeDeg
        while (normalized > 180.0) normalized -= 360.0
        while (normalized < -180.0) normalized += 360.0
        return normalized
    }
}

internal fun resolveOutputSpeedMps(
    hasRawSpeed: Boolean,
    rawSpeedMps: Float?,
    accuracyM: Float,
    estimatedSpeedMps: Float,
    positionStdDevMeters: Float,
): Float? {
    val trustedRawSpeed =
        rawSpeedMps
            ?.takeIf { hasRawSpeed && it.isFinite() }
            ?.coerceAtLeast(0f)
    val trustedEstimatedSpeed =
        estimatedSpeedMps
            .takeIf { it.isFinite() && it >= MIN_ESTIMATED_SPEED_MPS }
    if (trustedRawSpeed != null) {
        val shouldPreferEstimatedSpeed =
            trustedEstimatedSpeed != null &&
                trustedRawSpeed <= LOW_RAW_SPEED_OVERRIDE_MAX_MPS &&
                accuracyM.isFinite() &&
                accuracyM <= LOW_RAW_SPEED_OVERRIDE_MAX_ACCURACY_M &&
                positionStdDevMeters.isFinite() &&
                positionStdDevMeters <= LOW_RAW_SPEED_OVERRIDE_MAX_STDDEV_M &&
                trustedEstimatedSpeed >= trustedRawSpeed + LOW_RAW_SPEED_OVERRIDE_MIN_GAIN_MPS
        return if (shouldPreferEstimatedSpeed) {
            trustedEstimatedSpeed
        } else {
            trustedRawSpeed
        }
    }

    if (!accuracyM.isFinite() || accuracyM > MAX_ESTIMATED_SPEED_ACCURACY_M) return null
    if (!positionStdDevMeters.isFinite() || positionStdDevMeters > MAX_ESTIMATED_SPEED_STDDEV_M) {
        return null
    }
    if (trustedEstimatedSpeed == null) return null

    return trustedEstimatedSpeed.coerceAtLeast(0f)
}

private const val EARTH_RADIUS_METERS = 6_371_000.0
private const val MIN_COSINE_LATITUDE = 0.01
private const val MIN_FILTERED_BEARING_SPEED_MPS = 0.6f
private const val MAX_ESTIMATED_SPEED_ACCURACY_M = 12f
private const val MAX_ESTIMATED_SPEED_STDDEV_M = 10f
private const val MIN_ESTIMATED_SPEED_MPS = 0.9f
private const val LOW_RAW_SPEED_OVERRIDE_MAX_MPS = 0.75f
private const val LOW_RAW_SPEED_OVERRIDE_MAX_ACCURACY_M = 18f
private const val LOW_RAW_SPEED_OVERRIDE_MAX_STDDEV_M = 8f
private const val LOW_RAW_SPEED_OVERRIDE_MIN_GAIN_MPS = 0.45f
