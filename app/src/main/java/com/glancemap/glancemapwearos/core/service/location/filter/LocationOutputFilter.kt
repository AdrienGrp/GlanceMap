package com.glancemap.glancemapwearos.core.service.location.filter

import android.location.Location
import com.glancemap.glancemapwearos.core.service.diagnostics.DebugTelemetry
import com.glancemap.glancemapwearos.core.service.location.policy.LocationFixPolicy
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

@Suppress("TooManyFunctions") // Small projection and output helpers keep the filtering pipeline explicit.
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
        val measurement = createMeasurement(location = location, nowElapsedMs = nowElapsedMs)
        val estimate = filter.update(measurement)
        val output = resolveOutput(location = location, estimate = estimate)
        val filteredLocation = createFilteredLocation(location = location, estimate = estimate, output = output)
        recordTelemetry(location = location, measurement = measurement, estimate = estimate, output = output)
        return filteredLocation
    }

    private fun createMeasurement(
        location: Location,
        nowElapsedMs: Long,
    ): PositionMeasurement =
        PositionMeasurement(
            xMeters = longitudeToMeters(location.longitude),
            yMeters = latitudeToMeters(location.latitude),
            accuracyMeters = location.accuracy,
            elapsedMs = resolveMeasurementElapsedMs(location = location, nowElapsedMs = nowElapsedMs),
            speedMps = location.speed.takeIf { location.hasSpeed() },
        )

    private fun resolveOutput(
        location: Location,
        estimate: FilteredPositionEstimate,
    ): ResolvedLocationOutput {
        val speed =
            resolveOutputSpeed(
                LocationOutputSpeedInput(
                    hasRawSpeed = location.hasSpeed(),
                    rawSpeedMps = location.speed.takeIf { location.hasSpeed() },
                    accuracyM = location.accuracy,
                    estimatedSpeedMps = estimate.speedMps,
                    positionStdDevMeters = estimate.positionStdDevMeters,
                ),
            )
        val bearing =
            resolveOutputBearing(
                LocationOutputBearingInput(
                    hasRawBearing = location.hasBearing(),
                    rawBearingDeg = location.bearing.takeIf { location.hasBearing() },
                    rawBearingAccuracyDeg =
                        location.bearingAccuracyDegrees.takeIf { location.hasBearingAccuracy() },
                    outputSpeed = speed,
                    estimatedSpeedMps = estimate.speedMps,
                    estimatedBearingDeg = estimate.bearingDeg,
                    positionStdDevMeters = estimate.positionStdDevMeters,
                ),
            )
        return ResolvedLocationOutput(
            speed = speed,
            bearing = bearing,
            accuracyRetention = resolveOutputAccuracyRetention(speed = speed, bearing = bearing),
        )
    }

    private fun createFilteredLocation(
        location: Location,
        estimate: FilteredPositionEstimate,
        output: ResolvedLocationOutput,
    ): Location =
        Location(location).apply {
            latitude = metersToLatitude(estimate.yMeters)
            longitude = normalizeLongitude(metersToLongitude(estimate.xMeters))
            applyResolvedOutput(output)
        }

    private fun Location.applyResolvedOutput(output: ResolvedLocationOutput) {
        output.speed?.let { speed = it.value } ?: removeSpeed()
        if (!output.accuracyRetention.retainSpeedAccuracy) removeSpeedAccuracy()
        output.bearing?.let { bearing = it.value } ?: removeBearing()
        if (!output.accuracyRetention.retainBearingAccuracy) removeBearingAccuracy()
    }

    private fun recordTelemetry(
        location: Location,
        measurement: PositionMeasurement,
        estimate: FilteredPositionEstimate,
        output: ResolvedLocationOutput,
    ) {
        if (!DebugTelemetry.isEnabled()) return
        LocationOutputFilterTelemetry.record(
            positionOffsetM =
                hypot(
                    estimate.xMeters - measurement.xMeters,
                    estimate.yMeters - measurement.yMeters,
                ).toFloat(),
            speedOffsetMps =
                output.speed
                    ?.takeIf { location.hasSpeed() }
                    ?.let { it.value - location.speed },
            bearingOffsetDeg =
                output.bearing
                    ?.takeIf { location.hasBearing() }
                    ?.let { angularDistanceDegrees(it.value, location.bearing) },
        )
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

private fun resolveMeasurementElapsedMs(
    location: Location,
    nowElapsedMs: Long,
): Long {
    val ageMs = LocationFixPolicy.locationAgeMs(location, nowElapsedMs)
    return if (ageMs == Long.MAX_VALUE) {
        nowElapsedMs
    } else {
        (nowElapsedMs - ageMs).coerceAtLeast(0L)
    }
}

internal enum class LocationOutputValueSource {
    PROVIDER,
    FILTER_ESTIMATE,
}

internal data class ResolvedLocationOutputValue(
    val value: Float,
    val source: LocationOutputValueSource,
)

internal data class LocationOutputAccuracyRetention(
    val retainSpeedAccuracy: Boolean,
    val retainBearingAccuracy: Boolean,
)

private data class ResolvedLocationOutput(
    val speed: ResolvedLocationOutputValue?,
    val bearing: ResolvedLocationOutputValue?,
    val accuracyRetention: LocationOutputAccuracyRetention,
)

internal data class LocationOutputSpeedInput(
    val hasRawSpeed: Boolean,
    val rawSpeedMps: Float?,
    val accuracyM: Float,
    val estimatedSpeedMps: Float,
    val positionStdDevMeters: Float,
)

internal data class LocationOutputBearingInput(
    val hasRawBearing: Boolean,
    val rawBearingDeg: Float?,
    val rawBearingAccuracyDeg: Float? = null,
    val outputSpeed: ResolvedLocationOutputValue?,
    val estimatedSpeedMps: Float,
    val estimatedBearingDeg: Float,
    val positionStdDevMeters: Float,
)

internal fun resolveOutputAccuracyRetention(
    speed: ResolvedLocationOutputValue?,
    bearing: ResolvedLocationOutputValue?,
): LocationOutputAccuracyRetention =
    LocationOutputAccuracyRetention(
        retainSpeedAccuracy = speed?.source == LocationOutputValueSource.PROVIDER,
        retainBearingAccuracy = bearing?.source == LocationOutputValueSource.PROVIDER,
    )

internal fun resolveOutputSpeed(
    hasRawSpeed: Boolean,
    rawSpeedMps: Float?,
    accuracyM: Float,
    estimatedSpeedMps: Float,
    positionStdDevMeters: Float,
): ResolvedLocationOutputValue? =
    resolveOutputSpeed(
        LocationOutputSpeedInput(
            hasRawSpeed = hasRawSpeed,
            rawSpeedMps = rawSpeedMps,
            accuracyM = accuracyM,
            estimatedSpeedMps = estimatedSpeedMps,
            positionStdDevMeters = positionStdDevMeters,
        ),
    )

internal fun resolveOutputSpeed(input: LocationOutputSpeedInput): ResolvedLocationOutputValue? {
    val trustedRawSpeed =
        input.rawSpeedMps
            ?.takeIf { input.hasRawSpeed && it.isFinite() }
            ?.coerceAtLeast(0f)
    val trustedEstimatedSpeed =
        input.estimatedSpeedMps
            .takeIf { it.isFinite() && it >= MIN_ESTIMATED_SPEED_MPS }
    return trustedRawSpeed?.let { rawSpeed ->
        resolveAvailableRawSpeed(input = input, rawSpeed = rawSpeed, estimatedSpeed = trustedEstimatedSpeed)
    } ?: resolveEstimatedSpeed(input = input, estimatedSpeed = trustedEstimatedSpeed)
}

private fun resolveAvailableRawSpeed(
    input: LocationOutputSpeedInput,
    rawSpeed: Float,
    estimatedSpeed: Float?,
): ResolvedLocationOutputValue {
    val shouldPreferEstimate =
        estimatedSpeed != null &&
            rawSpeed <= LOW_RAW_SPEED_OVERRIDE_MAX_MPS &&
            input.accuracyM.isFinite() &&
            input.accuracyM <= LOW_RAW_SPEED_OVERRIDE_MAX_ACCURACY_M &&
            input.positionStdDevMeters.isFinite() &&
            input.positionStdDevMeters <= LOW_RAW_SPEED_OVERRIDE_MAX_STDDEV_M &&
            estimatedSpeed >= rawSpeed + LOW_RAW_SPEED_OVERRIDE_MIN_GAIN_MPS
    return if (shouldPreferEstimate) {
        ResolvedLocationOutputValue(estimatedSpeed, LocationOutputValueSource.FILTER_ESTIMATE)
    } else {
        ResolvedLocationOutputValue(rawSpeed, LocationOutputValueSource.PROVIDER)
    }
}

private fun resolveEstimatedSpeed(
    input: LocationOutputSpeedInput,
    estimatedSpeed: Float?,
): ResolvedLocationOutputValue? =
    estimatedSpeed
        ?.takeIf { input.accuracyM.isFinite() && input.accuracyM <= MAX_ESTIMATED_SPEED_ACCURACY_M }
        ?.takeIf {
            input.positionStdDevMeters.isFinite() &&
                input.positionStdDevMeters <= MAX_ESTIMATED_SPEED_STDDEV_M
        }?.let { ResolvedLocationOutputValue(it.coerceAtLeast(0f), LocationOutputValueSource.FILTER_ESTIMATE) }

internal fun resolveOutputBearing(input: LocationOutputBearingInput): ResolvedLocationOutputValue? {
    val providerBearing = resolveProviderBearing(input)
    val estimatedBearingIsTrustworthy =
        input.estimatedSpeedMps.isFinite() &&
            input.estimatedSpeedMps >= MIN_FILTERED_BEARING_SPEED_MPS &&
            input.estimatedBearingDeg.isFinite() &&
            input.positionStdDevMeters.isFinite() &&
            input.positionStdDevMeters <= MAX_ESTIMATED_BEARING_STDDEV_M
    val normalizedEstimatedBearing = normalizeBearingDegrees(input.estimatedBearingDeg)
    val providerAccuracyIsReliable =
        input.rawBearingAccuracyDeg
            ?.takeIf(Float::isFinite)
            ?.let { it <= MAX_RELIABLE_PROVIDER_BEARING_ACCURACY_DEG }
            ?: false
    val providerAgreesWithEstimate =
        providerBearing != null &&
            estimatedBearingIsTrustworthy &&
            angularDistanceDegrees(providerBearing.value, normalizedEstimatedBearing) <=
            MAX_PROVIDER_FILTER_AGREEMENT_DEG
    return if (providerBearing != null && (providerAccuracyIsReliable || providerAgreesWithEstimate)) {
        providerBearing
    } else if (estimatedBearingIsTrustworthy) {
        ResolvedLocationOutputValue(
            value = normalizedEstimatedBearing,
            source = LocationOutputValueSource.FILTER_ESTIMATE,
        )
    } else {
        providerBearing
    }
}

private fun resolveProviderBearing(input: LocationOutputBearingInput): ResolvedLocationOutputValue? =
    input.rawBearingDeg
        ?.takeIf { input.hasRawBearing && it.isFinite() }
        ?.takeIf { (input.outputSpeed?.value ?: 0f) >= MIN_PROVIDER_BEARING_SPEED_MPS }
        ?.let {
            ResolvedLocationOutputValue(
                value = normalizeBearingDegrees(it),
                source = LocationOutputValueSource.PROVIDER,
            )
        }

private fun normalizeBearingDegrees(bearingDeg: Float): Float {
    var normalized = bearingDeg % 360f
    if (normalized < 0f) normalized += 360f
    return normalized
}

private fun angularDistanceDegrees(
    firstDeg: Float,
    secondDeg: Float,
): Float {
    val directDifference = abs(firstDeg - secondDeg) % 360f
    return min(directDifference, 360f - directDifference)
}

private const val EARTH_RADIUS_METERS = 6_371_000.0
private const val MIN_COSINE_LATITUDE = 0.01
private const val MIN_FILTERED_BEARING_SPEED_MPS = 0.6f
private const val MIN_PROVIDER_BEARING_SPEED_MPS = 0.45f
private const val MAX_ESTIMATED_BEARING_STDDEV_M = 12f
private const val MAX_RELIABLE_PROVIDER_BEARING_ACCURACY_DEG = 35f
private const val MAX_PROVIDER_FILTER_AGREEMENT_DEG = 25f
private const val MAX_ESTIMATED_SPEED_ACCURACY_M = 12f
private const val MAX_ESTIMATED_SPEED_STDDEV_M = 10f
private const val MIN_ESTIMATED_SPEED_MPS = 0.9f
private const val LOW_RAW_SPEED_OVERRIDE_MAX_MPS = 0.75f
private const val LOW_RAW_SPEED_OVERRIDE_MAX_ACCURACY_M = 18f
private const val LOW_RAW_SPEED_OVERRIDE_MAX_STDDEV_M = 8f
private const val LOW_RAW_SPEED_OVERRIDE_MIN_GAIN_MPS = 0.45f
