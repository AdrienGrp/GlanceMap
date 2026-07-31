package com.glancemap.glancemapwearos.core.service.location.model

data class GpsSignalSnapshot(
    val lastFixElapsedRealtimeMs: Long = 0L,
    val lastFixAgeMs: Long = Long.MAX_VALUE,
    val lastFixAccuracyM: Float = Float.POSITIVE_INFINITY,
    val lastFixFresh: Boolean = false,
    val lastFixFreshMaxAgeMs: Long = 0L,
    val isLocationAvailable: Boolean = true,
    val unavailableSinceElapsedMs: Long = 0L,
    val watchGpsOnlyActive: Boolean = false,
    val watchGpsDegraded: Boolean = false,
    val watchGpsDegradedFixStreak: Int = 0,
    val watchGpsDegradedSinceElapsedMs: Long = 0L,
    val environmentWarning: GpsEnvironmentWarning = GpsEnvironmentWarning.NONE,
    val environmentWarningSinceElapsedMs: Long = 0L,
)
