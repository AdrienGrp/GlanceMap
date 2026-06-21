package com.glancemap.glancemapwearos.presentation.features.navigate

internal fun turnAlertDistanceMeters(speedMps: Float?): Double {
    val speed =
        speedMps
            ?.takeIf { it.isFinite() && it > 0f }
            ?.toDouble()
            ?: return TURN_ALERT_DEFAULT_DISTANCE_METERS
    return (speed * TURN_ALERT_LOOKAHEAD_SECONDS)
        .coerceIn(TURN_ALERT_MIN_DISTANCE_METERS, TURN_ALERT_MAX_DISTANCE_METERS)
}

internal fun turnHapticDistanceMeters(speedMps: Float?): Double {
    val speed =
        speedMps
            ?.takeIf { it.isFinite() && it > 0f }
            ?.toDouble()
            ?: return TURN_HAPTIC_DEFAULT_DISTANCE_METERS
    return (speed * TURN_HAPTIC_LOOKAHEAD_SECONDS)
        .coerceIn(TURN_HAPTIC_MIN_DISTANCE_METERS, TURN_HAPTIC_MAX_DISTANCE_METERS)
}

private const val TURN_ALERT_DEFAULT_DISTANCE_METERS = 35.0
private const val TURN_ALERT_MIN_DISTANCE_METERS = 35.0
private const val TURN_ALERT_MAX_DISTANCE_METERS = 90.0
private const val TURN_ALERT_LOOKAHEAD_SECONDS = 8.0
private const val TURN_HAPTIC_DEFAULT_DISTANCE_METERS = 10.0
private const val TURN_HAPTIC_MIN_DISTANCE_METERS = 8.0
private const val TURN_HAPTIC_MAX_DISTANCE_METERS = 18.0
private const val TURN_HAPTIC_LOOKAHEAD_SECONDS = 3.0
