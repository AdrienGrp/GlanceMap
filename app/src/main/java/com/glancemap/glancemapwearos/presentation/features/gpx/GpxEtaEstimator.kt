package com.glancemap.glancemapwearos.presentation.features.gpx

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln

private const val MIN_SEGMENT_METERS_FOR_GRADE = 1.0
private const val MAX_ABS_GRADE = 0.60
private const val ADVANCED_RATE_EFFECT_START_GRADE = 0.01
private const val ADVANCED_RATE_EFFECT_FULL_GRADE = 0.10
private const val ADVANCED_RATE_MIN_MULTIPLIER = 0.1
private const val ADVANCED_RATE_MAX_MULTIPLIER = 1.5

internal data class GpxEtaModelConfig(
    val flatSpeedMps: Double,
    val advancedVerticalRateEnabled: Boolean = false,
    val uphillVerticalMetersPerHour: Double = 0.0,
    val downhillVerticalMetersPerHour: Double = 0.0
)

internal data class GpxEtaProjection(
    val cumulativeSeconds: DoubleArray
) {
    val totalSeconds: Double?
        get() = cumulativeSeconds.lastOrNull()

    fun secondsAtPointIndex(index: Int): Double? {
        if (cumulativeSeconds.isEmpty()) return null
        return cumulativeSeconds.getOrElse(index.coerceIn(0, cumulativeSeconds.lastIndex)) {
            cumulativeSeconds.lastOrNull() ?: 0.0
        }
    }

    fun secondsAtTrackPosition(position: TrackPosition): Double? {
        if (cumulativeSeconds.isEmpty()) return null
        if (cumulativeSeconds.size == 1) return cumulativeSeconds[0]

        val segmentIndex = position.segmentIndex.coerceIn(0, cumulativeSeconds.lastIndex - 1)
        val t = position.t.coerceIn(0.0, 1.0)
        val start = cumulativeSeconds[segmentIndex]
        val end = cumulativeSeconds.getOrElse(segmentIndex + 1) { start }
        return start + t * (end - start)
    }
}

private fun buildCumulativeEtaSeconds(
    profile: TrackProfile,
    config: GpxEtaModelConfig
): DoubleArray? {
    val flatSpeedMps = config.flatSpeedMps
    if (!flatSpeedMps.isFinite() || flatSpeedMps <= 0.0) return null

    val points = profile.points
    val n = points.size
    if (n == 0) return DoubleArray(0)
    if (n == 1) return DoubleArray(1)

    val cumulative = DoubleArray(n)
    val minSpeed = (flatSpeedMps * 0.08).coerceAtLeast(0.05)

    for (i in 0 until n - 1) {
        val distanceMeters = profile.segLen.getOrElse(i) { 0.0 }.coerceAtLeast(0.0)
        if (distanceMeters <= 0.0) {
            cumulative[i + 1] = cumulative[i]
            continue
        }

        val e0 = points[i].elevation
        val e1 = points[i + 1].elevation
        val factor = if (
            e0 == null || e1 == null || distanceMeters < MIN_SEGMENT_METERS_FOR_GRADE
        ) {
            1.0
        } else {
            val grade = ((e1 - e0) / distanceMeters).coerceIn(-MAX_ABS_GRADE, MAX_ABS_GRADE)
            // Normalized Tobler hiking factor; f(0) = 1.0
            exp(-3.5 * (abs(grade + 0.05) - 0.05))
        }

        val elevationDeltaMeters = if (e0 != null && e1 != null) e1 - e0 else null
        val segmentSpeed = applyAdvancedVerticalRateAdjustment(
            candidateSpeedMps = (flatSpeedMps * factor).coerceAtLeast(minSpeed),
            elevationDeltaMeters = elevationDeltaMeters,
            distanceMeters = distanceMeters,
            minSpeedMps = minSpeed,
            config = config
        )
        cumulative[i + 1] = cumulative[i] + (distanceMeters / segmentSpeed)
    }

    return cumulative
}

internal fun buildEtaProjection(
    profile: TrackProfile,
    flatSpeedMps: Double
): GpxEtaProjection? {
    return buildEtaProjection(
        profile = profile,
        config = GpxEtaModelConfig(flatSpeedMps = flatSpeedMps)
    )
}

internal fun buildEtaProjection(
    profile: TrackProfile,
    config: GpxEtaModelConfig
): GpxEtaProjection? {
    val cumulative = buildCumulativeEtaSeconds(profile, config) ?: return null
    return GpxEtaProjection(cumulativeSeconds = cumulative)
}

private fun applyAdvancedVerticalRateAdjustment(
    candidateSpeedMps: Double,
    elevationDeltaMeters: Double?,
    distanceMeters: Double,
    minSpeedMps: Double,
    config: GpxEtaModelConfig
): Double {
    if (!config.advancedVerticalRateEnabled) return candidateSpeedMps
    val verticalDelta = elevationDeltaMeters ?: return candidateSpeedMps
    if (distanceMeters < MIN_SEGMENT_METERS_FOR_GRADE) return candidateSpeedMps

    val absGrade = (abs(verticalDelta) / distanceMeters)
        .coerceIn(0.0, MAX_ABS_GRADE)
    if (absGrade <= ADVANCED_RATE_EFFECT_START_GRADE) return candidateSpeedMps

    val verticalRateMps = when {
        verticalDelta > 0.0 -> config.uphillVerticalMetersPerHour / 3600.0
        verticalDelta < 0.0 -> config.downhillVerticalMetersPerHour / 3600.0
        else -> return candidateSpeedMps
    }
    if (!verticalRateMps.isFinite() || verticalRateMps <= 0.0) return candidateSpeedMps

    val baselineVerticalRateMps = (candidateSpeedMps * absGrade).coerceAtLeast(1e-6)
    val rawMultiplier = verticalRateMps / baselineVerticalRateMps
    if (!rawMultiplier.isFinite() || rawMultiplier <= 0.0) return candidateSpeedMps

    val gradeWeight = ((absGrade - ADVANCED_RATE_EFFECT_START_GRADE) /
        (ADVANCED_RATE_EFFECT_FULL_GRADE - ADVANCED_RATE_EFFECT_START_GRADE))
        .coerceIn(0.0, 1.0)
    val adjustedMultiplier = exp(ln(rawMultiplier) * gradeWeight)
        .coerceIn(ADVANCED_RATE_MIN_MULTIPLIER, ADVANCED_RATE_MAX_MULTIPLIER)

    return (candidateSpeedMps * adjustedMultiplier).coerceAtLeast(minSpeedMps)
}
