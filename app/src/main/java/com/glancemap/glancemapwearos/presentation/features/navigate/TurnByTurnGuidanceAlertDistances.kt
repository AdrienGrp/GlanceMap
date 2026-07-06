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
): Double {
    val speed =
        speedMps
            ?.takeIf { it.isFinite() && it > 0f }
            ?.toDouble()
            ?: return turnHapticDefaultDistanceMeters(activityProfile)
    return (speed * TURN_HAPTIC_LOOKAHEAD_SECONDS)
        .coerceIn(
            TURN_HAPTIC_MIN_DISTANCE_METERS,
            turnHapticMaxDistanceMeters(activityProfile),
        )
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

private fun turnHapticDefaultDistanceMeters(activityProfile: String): Double =
    if (activityProfile == SettingsRepository.ACTIVITY_PROFILE_BIKE) {
        TURN_HAPTIC_BIKE_DEFAULT_DISTANCE_METERS
    } else {
        TURN_HAPTIC_DEFAULT_DISTANCE_METERS
    }

private const val TURN_ALERT_DEFAULT_DISTANCE_METERS = 35.0
private const val TURN_ALERT_BIKE_DEFAULT_DISTANCE_METERS = 70.0
private const val TURN_ALERT_MIN_DISTANCE_METERS = 35.0
private const val TURN_ALERT_MAX_DISTANCE_METERS = 90.0
private const val TURN_ALERT_BIKE_MAX_DISTANCE_METERS = 180.0
private const val TURN_ALERT_LOOKAHEAD_SECONDS = 8.0
private const val TURN_HAPTIC_DEFAULT_DISTANCE_METERS = 10.0
private const val TURN_HAPTIC_BIKE_DEFAULT_DISTANCE_METERS = 18.0
private const val TURN_HAPTIC_MIN_DISTANCE_METERS = 8.0
private const val TURN_HAPTIC_MAX_DISTANCE_METERS = 18.0
private const val TURN_HAPTIC_BIKE_MAX_DISTANCE_METERS = 35.0
private const val TURN_HAPTIC_LOOKAHEAD_SECONDS = 3.0
