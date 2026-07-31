package com.glancemap.glancemapwearos.presentation.features.navigate

import com.glancemap.glancemapwearos.data.repository.SettingsRepository

internal fun turnAlertDistanceMeters(
    speedMps: Float?,
    activityProfile: String = SettingsRepository.DEFAULT_ACTIVITY_PROFILE,
): Double {
    val speed =
        speedMps
            ?.takeIf { it.isFinite() && it > 0f }
            ?.toDouble()
            ?: return turnAlertDefaultDistanceMeters(activityProfile)
    return (speed * TURN_ALERT_LOOKAHEAD_SECONDS)
        .coerceIn(
            TURN_ALERT_MIN_DISTANCE_METERS,
            turnAlertMaxDistanceMeters(activityProfile),
        )
}

internal fun turnHapticDistanceMeters(
    speedMps: Float?,
    activityProfile: String = SettingsRepository.DEFAULT_ACTIVITY_PROFILE,
    gpsDeliveryIntervalMs: Long = SettingsRepository.DEFAULT_GPS_INTERVAL_MS,
): Double {
    val speed =
        speedMps
            ?.takeIf { it.isFinite() && it > 0f }
            ?.toDouble()
    val gpsDeliveryIntervalSeconds =
        gpsDeliveryIntervalMs
            .takeIf { it > 0L }
            ?.coerceAtMost(TURN_HAPTIC_MAX_GPS_INTERVAL_MS)
            ?.div(1_000.0)
            ?: SettingsRepository.DEFAULT_GPS_INTERVAL_MS / 1_000.0
    val lookAheadSeconds =
        TURN_HAPTIC_BASE_LOOKAHEAD_SECONDS +
            gpsDeliveryIntervalSeconds * TURN_HAPTIC_GPS_INTERVAL_WEIGHT
    val distanceMeters =
        speed?.let { it * lookAheadSeconds }
            ?: defaultTurnHapticDistanceForGpsInterval(
                activityProfile = activityProfile,
                gpsDeliveryIntervalSeconds = gpsDeliveryIntervalSeconds,
            )
    return distanceMeters
        .coerceIn(
            TURN_HAPTIC_MIN_DISTANCE_METERS,
            turnHapticMaxDistanceMeters(activityProfile),
        )
}

private fun defaultTurnHapticDistanceForGpsInterval(
    activityProfile: String,
    gpsDeliveryIntervalSeconds: Double,
): Double {
    val extraGpsDelaySeconds =
        (gpsDeliveryIntervalSeconds - SettingsRepository.DEFAULT_GPS_INTERVAL_MS / 1_000.0)
            .coerceAtLeast(0.0)
    return turnHapticDefaultDistanceMeters(activityProfile) +
        turnHapticAssumedSpeedMps(activityProfile) * extraGpsDelaySeconds * TURN_HAPTIC_GPS_INTERVAL_WEIGHT
}

internal fun turnHapticCrossingRecoveryMeters(
    speedMps: Float?,
    activityProfile: String = SettingsRepository.DEFAULT_ACTIVITY_PROFILE,
    gpsDeliveryIntervalMs: Long = SettingsRepository.DEFAULT_GPS_INTERVAL_MS,
): Double {
    val alertDistanceMeters =
        turnHapticDistanceMeters(
            speedMps = speedMps,
            activityProfile = activityProfile,
            gpsDeliveryIntervalMs = gpsDeliveryIntervalMs,
        )
    val speed = speedMps?.takeIf { it.isFinite() && it > 0f }?.toDouble()
    val gpsTravelMeters =
        speed?.let {
            val intervalSeconds =
                gpsDeliveryIntervalMs
                    .takeIf { interval -> interval > 0L }
                    ?.coerceAtMost(TURN_HAPTIC_MAX_GPS_INTERVAL_MS)
                    ?.div(1_000.0)
                    ?: SettingsRepository.DEFAULT_GPS_INTERVAL_MS / 1_000.0
            it * intervalSeconds
        } ?: alertDistanceMeters
    return (alertDistanceMeters + gpsTravelMeters)
        .coerceAtMost(turnHapticCrossingRecoveryMaxMeters(activityProfile))
}

internal fun turnAlertMaxDistanceMeters(activityProfile: String): Double =
    if (activityProfile == SettingsRepository.ACTIVITY_PROFILE_BIKE) {
        TURN_ALERT_BIKE_MAX_DISTANCE_METERS
    } else {
        TURN_ALERT_MAX_DISTANCE_METERS
    }

private fun turnAlertDefaultDistanceMeters(activityProfile: String): Double =
    if (activityProfile == SettingsRepository.ACTIVITY_PROFILE_BIKE) {
        TURN_ALERT_BIKE_DEFAULT_DISTANCE_METERS
    } else {
        TURN_ALERT_DEFAULT_DISTANCE_METERS
    }

private fun turnHapticMaxDistanceMeters(activityProfile: String): Double =
    if (activityProfile == SettingsRepository.ACTIVITY_PROFILE_BIKE) {
        TURN_HAPTIC_BIKE_MAX_DISTANCE_METERS
    } else {
        TURN_HAPTIC_MAX_DISTANCE_METERS
    }

private fun turnHapticCrossingRecoveryMaxMeters(activityProfile: String): Double =
    if (activityProfile == SettingsRepository.ACTIVITY_PROFILE_BIKE) {
        TURN_HAPTIC_BIKE_CROSSING_RECOVERY_MAX_METERS
    } else {
        TURN_HAPTIC_CROSSING_RECOVERY_MAX_METERS
    }

private fun turnHapticDefaultDistanceMeters(activityProfile: String): Double =
    if (activityProfile == SettingsRepository.ACTIVITY_PROFILE_BIKE) {
        TURN_HAPTIC_BIKE_DEFAULT_DISTANCE_METERS
    } else {
        TURN_HAPTIC_DEFAULT_DISTANCE_METERS
    }

private fun turnHapticAssumedSpeedMps(activityProfile: String): Double =
    if (activityProfile == SettingsRepository.ACTIVITY_PROFILE_BIKE) {
        TURN_HAPTIC_BIKE_ASSUMED_SPEED_MPS
    } else {
        TURN_HAPTIC_ASSUMED_SPEED_MPS
    }

private const val TURN_ALERT_DEFAULT_DISTANCE_METERS = 35.0
private const val TURN_ALERT_BIKE_DEFAULT_DISTANCE_METERS = 70.0
private const val TURN_ALERT_MIN_DISTANCE_METERS = 35.0
private const val TURN_ALERT_MAX_DISTANCE_METERS = 90.0
private const val TURN_ALERT_BIKE_MAX_DISTANCE_METERS = 180.0
private const val TURN_ALERT_LOOKAHEAD_SECONDS = 8.0
private const val TURN_HAPTIC_DEFAULT_DISTANCE_METERS = 12.0
private const val TURN_HAPTIC_BIKE_DEFAULT_DISTANCE_METERS = 25.0
private const val TURN_HAPTIC_MIN_DISTANCE_METERS = 10.0
private const val TURN_HAPTIC_MAX_DISTANCE_METERS = 25.0
private const val TURN_HAPTIC_BIKE_MAX_DISTANCE_METERS = 60.0
private const val TURN_HAPTIC_BASE_LOOKAHEAD_SECONDS = 4.0
private const val TURN_HAPTIC_GPS_INTERVAL_WEIGHT = 0.5
private const val TURN_HAPTIC_ASSUMED_SPEED_MPS = 1.5
private const val TURN_HAPTIC_BIKE_ASSUMED_SPEED_MPS = 5.0
private const val TURN_HAPTIC_MAX_GPS_INTERVAL_MS = 10_000L
private const val TURN_HAPTIC_CROSSING_RECOVERY_MAX_METERS = 40.0
private const val TURN_HAPTIC_BIKE_CROSSING_RECOVERY_MAX_METERS = 120.0
