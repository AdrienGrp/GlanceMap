package com.glancemap.glancemapwearos.presentation.features.gpx

import com.glancemap.glancemapwearos.data.repository.SettingsRepository
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sqrt

private const val MIN_SEGMENT_METERS_FOR_GRADE = 1.0
private const val MAX_ABS_GRADE = 0.60
private const val ADVANCED_RATE_EFFECT_START_GRADE = 0.01
private const val ADVANCED_RATE_EFFECT_FULL_GRADE = 0.10
private const val ADVANCED_RATE_MIN_MULTIPLIER = 0.1
private const val ADVANCED_RATE_MAX_MULTIPLIER = 1.5
private const val STAMINA_ONE_HOUR_MULTIPLIER = 1.0
private const val STAMINA_THREE_HOUR_MULTIPLIER = 0.97
private const val STAMINA_SIX_HOUR_MULTIPLIER = 0.93
private const val STAMINA_TEN_HOUR_MULTIPLIER = 0.88
private const val STAMINA_LONG_ROUTE_MULTIPLIER = 0.85
private const val BIKE_GRADE_EFFECT_THRESHOLD = 0.01
private const val BIKE_MIN_RIDER_POWER_W = 90.0
private const val BIKE_DOWNHILL_RIDER_POWER_W = 40.0
private const val BIKE_DRIVETRAIN_EFFICIENCY = 0.96
private const val BIKE_AIR_DENSITY_KG_PER_M3 = 1.225
private const val BIKE_UPRIGHT_CDA_M2 = 0.55
private const val BIKE_UNKNOWN_SURFACE_CRR = 0.008
private const val BIKE_GRAVITY_MPS2 = 9.80665
private const val BIKE_MIN_SPEED_MPS = 3.0 / 3.6
private const val BIKE_DOWNHILL_SPEED_CAP_MPS = 30.0 / 3.6

internal data class GpxEtaModelConfig(
    val flatSpeedMps: Double,
    val advancedVerticalRateEnabled: Boolean = false,
    val staminaAdjustmentEnabled: Boolean = false,
    val activityProfile: String = SettingsRepository.DEFAULT_ACTIVITY_PROFILE,
    val userWeightKg: Double = SettingsRepository.DEFAULT_USER_WEIGHT_KG.toDouble(),
    val backpackWeightKg: Double = SettingsRepository.DEFAULT_BACKPACK_WEIGHT_KG.toDouble(),
    val bikeWeightKg: Double = SettingsRepository.DEFAULT_BIKE_WEIGHT_KG.toDouble(),
    val uphillVerticalMetersPerHour: Double = 0.0,
    val downhillVerticalMetersPerHour: Double = 0.0,
)

internal data class GpxEtaProjection(
    val cumulativeSeconds: DoubleArray,
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
    config: GpxEtaModelConfig,
): DoubleArray? {
    val configuredFlatSpeedMps = config.flatSpeedMps
    if (!configuredFlatSpeedMps.isFinite() || configuredFlatSpeedMps <= 0.0) return null

    val points = profile.points
    val n = points.size
    if (n == 0) return DoubleArray(0)
    if (n == 1) return DoubleArray(1)

    val cumulative = DoubleArray(n)
    val flatSpeedMps =
        applyStaminaFlatSpeedAdjustment(
            flatSpeedMps = configuredFlatSpeedMps,
            totalDistanceMeters = profile.cumDist.lastOrNull() ?: profile.segLen.sum(),
            enabled = config.staminaAdjustmentEnabled,
        )
    val minSpeed = (flatSpeedMps * 0.08).coerceAtLeast(0.05)

    for (i in 0 until n - 1) {
        val distanceMeters = profile.segLen.getOrElse(i) { 0.0 }.coerceAtLeast(0.0)
        if (distanceMeters <= 0.0) {
            cumulative[i + 1] = cumulative[i]
            continue
        }

        val e0 = points[i].elevation
        val e1 = points[i + 1].elevation
        val elevationDeltaMeters = if (e0 != null && e1 != null) e1 - e0 else null
        val segmentSpeed =
            if (
                config.activityProfile == SettingsRepository.ACTIVITY_PROFILE_BIKE &&
                elevationDeltaMeters != null &&
                distanceMeters >= MIN_SEGMENT_METERS_FOR_GRADE
            ) {
                applyBikePhysicsSpeedAdjustment(
                    flatSpeedMps = flatSpeedMps,
                    grade = (elevationDeltaMeters / distanceMeters).coerceIn(-MAX_ABS_GRADE, MAX_ABS_GRADE),
                    config = config,
                )
            } else {
                val factor =
                    if (
                        elevationDeltaMeters == null ||
                        distanceMeters < MIN_SEGMENT_METERS_FOR_GRADE
                    ) {
                        1.0
                    } else {
                        val grade = (elevationDeltaMeters / distanceMeters).coerceIn(-MAX_ABS_GRADE, MAX_ABS_GRADE)
                        // Normalized Tobler hiking factor; f(0) = 1.0
                        exp(-3.5 * (abs(grade + 0.05) - 0.05))
                    }
                applyAdvancedVerticalRateAdjustment(
                    candidateSpeedMps = (flatSpeedMps * factor).coerceAtLeast(minSpeed),
                    elevationDeltaMeters = elevationDeltaMeters,
                    distanceMeters = distanceMeters,
                    minSpeedMps = minSpeed,
                    config = config,
                )
            }
        cumulative[i + 1] = cumulative[i] + (distanceMeters / segmentSpeed)
    }

    return cumulative
}

internal fun applyStaminaFlatSpeedAdjustment(
    flatSpeedMps: Double,
    totalDistanceMeters: Double,
    enabled: Boolean,
): Double {
    if (!enabled) return flatSpeedMps
    if (!flatSpeedMps.isFinite() || flatSpeedMps <= 0.0) return flatSpeedMps
    if (!totalDistanceMeters.isFinite() || totalDistanceMeters <= 0.0) return flatSpeedMps

    val estimatedFlatHours = totalDistanceMeters / flatSpeedMps / 3600.0
    val multiplier =
        when {
            estimatedFlatHours <= 1.0 -> STAMINA_ONE_HOUR_MULTIPLIER
            estimatedFlatHours <= 3.0 ->
                interpolateStaminaMultiplier(
                    hours = estimatedFlatHours,
                    startHours = 1.0,
                    endHours = 3.0,
                    startMultiplier = STAMINA_ONE_HOUR_MULTIPLIER,
                    endMultiplier = STAMINA_THREE_HOUR_MULTIPLIER,
                )
            estimatedFlatHours <= 6.0 ->
                interpolateStaminaMultiplier(
                    hours = estimatedFlatHours,
                    startHours = 3.0,
                    endHours = 6.0,
                    startMultiplier = STAMINA_THREE_HOUR_MULTIPLIER,
                    endMultiplier = STAMINA_SIX_HOUR_MULTIPLIER,
                )
            estimatedFlatHours <= 10.0 ->
                interpolateStaminaMultiplier(
                    hours = estimatedFlatHours,
                    startHours = 6.0,
                    endHours = 10.0,
                    startMultiplier = STAMINA_SIX_HOUR_MULTIPLIER,
                    endMultiplier = STAMINA_TEN_HOUR_MULTIPLIER,
                )
            else -> STAMINA_LONG_ROUTE_MULTIPLIER
        }
    return flatSpeedMps * multiplier
}

private fun interpolateStaminaMultiplier(
    hours: Double,
    startHours: Double,
    endHours: Double,
    startMultiplier: Double,
    endMultiplier: Double,
): Double {
    val fraction = ((hours - startHours) / (endHours - startHours)).coerceIn(0.0, 1.0)
    return startMultiplier + (endMultiplier - startMultiplier) * fraction
}

internal fun applyBikePhysicsSpeedAdjustment(
    flatSpeedMps: Double,
    grade: Double,
    config: GpxEtaModelConfig,
): Double {
    if (!flatSpeedMps.isFinite() || flatSpeedMps <= 0.0) return flatSpeedMps
    if (!grade.isFinite() || abs(grade) < BIKE_GRADE_EFFECT_THRESHOLD) return flatSpeedMps

    val totalMassKg =
        (
            config.userWeightKg.coerceAtLeast(1.0) +
                config.backpackWeightKg.coerceAtLeast(0.0) +
                config.bikeWeightKg.coerceAtLeast(0.0)
        ).coerceAtLeast(1.0)
    val calibratedFlatWheelPower =
        cyclingWheelPowerForSpeed(
            speedMps = flatSpeedMps,
            grade = 0.0,
            totalMassKg = totalMassKg,
        ).coerceAtLeast(0.0)
    val sustainableWheelPower =
        maxOf(
            calibratedFlatWheelPower,
            BIKE_MIN_RIDER_POWER_W * BIKE_DRIVETRAIN_EFFICIENCY,
        )
    val targetWheelPower =
        if (grade < -BIKE_GRADE_EFFECT_THRESHOLD) {
            minOf(sustainableWheelPower, BIKE_DOWNHILL_RIDER_POWER_W * BIKE_DRIVETRAIN_EFFICIENCY)
        } else {
            sustainableWheelPower
        }

    val minSpeedMps = minOf(BIKE_MIN_SPEED_MPS, flatSpeedMps).coerceAtLeast(0.3)
    val maxSpeedMps =
        if (grade > BIKE_GRADE_EFFECT_THRESHOLD) {
            flatSpeedMps
        } else {
            maxOf(flatSpeedMps, BIKE_DOWNHILL_SPEED_CAP_MPS)
        }
    val solvedSpeedMps =
        solveCyclingSpeedForWheelPower(
            targetWheelPower = targetWheelPower,
            grade = grade,
            totalMassKg = totalMassKg,
            minSpeedMps = minSpeedMps,
            maxSpeedMps = maxSpeedMps,
        )

    return if (grade > BIKE_GRADE_EFFECT_THRESHOLD) {
        solvedSpeedMps.coerceIn(minSpeedMps, flatSpeedMps)
    } else {
        solvedSpeedMps.coerceIn(flatSpeedMps, maxSpeedMps)
    }
}

private fun solveCyclingSpeedForWheelPower(
    targetWheelPower: Double,
    grade: Double,
    totalMassKg: Double,
    minSpeedMps: Double,
    maxSpeedMps: Double,
): Double {
    if (maxSpeedMps <= minSpeedMps) return minSpeedMps
    if (cyclingWheelPowerForSpeed(minSpeedMps, grade, totalMassKg) >= targetWheelPower) {
        return minSpeedMps
    }
    if (cyclingWheelPowerForSpeed(maxSpeedMps, grade, totalMassKg) <= targetWheelPower) {
        return maxSpeedMps
    }

    var low = minSpeedMps
    var high = maxSpeedMps
    repeat(48) {
        val mid = (low + high) / 2.0
        if (cyclingWheelPowerForSpeed(mid, grade, totalMassKg) < targetWheelPower) {
            low = mid
        } else {
            high = mid
        }
    }
    return (low + high) / 2.0
}

private fun cyclingWheelPowerForSpeed(
    speedMps: Double,
    grade: Double,
    totalMassKg: Double,
): Double {
    val cosTheta = 1.0 / sqrt(1.0 + grade * grade)
    val gravityForce = totalMassKg * BIKE_GRAVITY_MPS2 * grade
    val rollingForce = totalMassKg * BIKE_GRAVITY_MPS2 * BIKE_UNKNOWN_SURFACE_CRR * cosTheta
    val aerodynamicForce = 0.5 * BIKE_AIR_DENSITY_KG_PER_M3 * BIKE_UPRIGHT_CDA_M2 * speedMps * speedMps
    return (gravityForce + rollingForce + aerodynamicForce) * speedMps
}

internal fun buildEtaProjection(
    profile: TrackProfile,
    flatSpeedMps: Double,
): GpxEtaProjection? =
    buildEtaProjection(
        profile = profile,
        config = GpxEtaModelConfig(flatSpeedMps = flatSpeedMps),
    )

internal fun buildEtaProjection(
    profile: TrackProfile,
    config: GpxEtaModelConfig,
): GpxEtaProjection? {
    val cumulative = buildCumulativeEtaSeconds(profile, config) ?: return null
    return GpxEtaProjection(cumulativeSeconds = cumulative)
}

internal fun estimateRemainingRouteSeconds(
    trackPoints: List<TrackPoint>,
    distanceFromStartMeters: Double?,
    config: GpxEtaModelConfig,
): Long? =
    buildRouteEtaProjection(trackPoints, config)
        ?.remainingSecondsAtDistance(distanceFromStartMeters)

internal data class GpxRouteEtaProjection(
    val cumulativeDistancesMeters: DoubleArray,
    val etaProjection: GpxEtaProjection,
) {
    fun remainingSecondsAtDistance(distanceFromStartMeters: Double?): Long? {
        if (distanceFromStartMeters == null || cumulativeDistancesMeters.size < 2) return null
        val totalSeconds = etaProjection.totalSeconds ?: return null
        val distance = distanceFromStartMeters.coerceIn(0.0, cumulativeDistancesMeters.last())
        val segmentIndex =
            cumulativeDistancesMeters
                .binarySearch(distance)
                .let { index ->
                    if (index >= 0) {
                        index.coerceAtMost(cumulativeDistancesMeters.lastIndex - 1)
                    } else {
                        (-index - 2).coerceIn(0, cumulativeDistancesMeters.lastIndex - 1)
                    }
                }
        val segmentStartDistance = cumulativeDistancesMeters[segmentIndex]
        val segmentEndDistance = cumulativeDistancesMeters[segmentIndex + 1]
        val segmentFraction =
            if (segmentEndDistance > segmentStartDistance) {
                ((distance - segmentStartDistance) / (segmentEndDistance - segmentStartDistance)).coerceIn(0.0, 1.0)
            } else {
                0.0
            }
        val elapsedSeconds =
            etaProjection.secondsAtTrackPosition(
                TrackPosition(
                    trackId = "",
                    segmentIndex = segmentIndex,
                    t = segmentFraction,
                ),
            ) ?: return null
        return (totalSeconds - elapsedSeconds).toLong().coerceAtLeast(0L)
    }
}

internal fun buildRouteEtaProjection(
    trackPoints: List<TrackPoint>,
    config: GpxEtaModelConfig,
): GpxRouteEtaProjection? {
    if (trackPoints.size < 2) return null
    val profile =
        buildProfile(
            sig = FileSig(lastModified = 0L, length = trackPoints.size.toLong()),
            pts = trackPoints,
        )
    val projection = buildEtaProjection(profile, config) ?: return null
    return GpxRouteEtaProjection(
        cumulativeDistancesMeters = profile.cumDist,
        etaProjection = projection,
    )
}

private fun applyAdvancedVerticalRateAdjustment(
    candidateSpeedMps: Double,
    elevationDeltaMeters: Double?,
    distanceMeters: Double,
    minSpeedMps: Double,
    config: GpxEtaModelConfig,
): Double {
    if (!config.advancedVerticalRateEnabled) return candidateSpeedMps
    val verticalDelta = elevationDeltaMeters ?: return candidateSpeedMps
    if (distanceMeters < MIN_SEGMENT_METERS_FOR_GRADE) return candidateSpeedMps

    val absGrade =
        (abs(verticalDelta) / distanceMeters)
            .coerceIn(0.0, MAX_ABS_GRADE)
    if (absGrade <= ADVANCED_RATE_EFFECT_START_GRADE) return candidateSpeedMps

    val verticalRateMps =
        when {
            verticalDelta > 0.0 -> config.uphillVerticalMetersPerHour / 3600.0
            verticalDelta < 0.0 -> config.downhillVerticalMetersPerHour / 3600.0
            else -> return candidateSpeedMps
        }
    if (!verticalRateMps.isFinite() || verticalRateMps <= 0.0) return candidateSpeedMps

    val baselineVerticalRateMps = (candidateSpeedMps * absGrade).coerceAtLeast(1e-6)
    val rawMultiplier = verticalRateMps / baselineVerticalRateMps
    if (!rawMultiplier.isFinite() || rawMultiplier <= 0.0) return candidateSpeedMps

    val gradeWeight =
        (
            (absGrade - ADVANCED_RATE_EFFECT_START_GRADE) /
                (ADVANCED_RATE_EFFECT_FULL_GRADE - ADVANCED_RATE_EFFECT_START_GRADE)
        ).coerceIn(0.0, 1.0)
    val adjustedMultiplier =
        exp(ln(rawMultiplier) * gradeWeight)
            .coerceIn(ADVANCED_RATE_MIN_MULTIPLIER, ADVANCED_RATE_MAX_MULTIPLIER)

    return (candidateSpeedMps * adjustedMultiplier).coerceAtLeast(minSpeedMps)
}
