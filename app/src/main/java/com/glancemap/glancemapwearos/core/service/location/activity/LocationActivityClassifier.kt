package com.glancemap.glancemapwearos.core.service.location.activity

internal enum class LocationActivityState { ACTIVE, STATIONARY }

internal data class LocationActivityClassifierConfig(
    val enterStationaryWindowMs: Long = 30000L,
    val enterStationaryConfirmMs: Long = 8000L,
    val enterStationarySpeedThresholdMps: Float = 0.35f,
    val enterStationaryDistanceThresholdMeters: Float = 12.0f,
    val exitActiveWindowMs: Long = 12000L,
    val exitActiveConfirmMs: Long = 4000L,
    val exitActiveSpeedThresholdMps: Float = 0.70f,
    val exitActiveDistanceThresholdMeters: Float = 18.0f
)

internal class LocationActivityClassifier(
    private val config: LocationActivityClassifierConfig = LocationActivityClassifierConfig()
) {
    var state: LocationActivityState = LocationActivityState.ACTIVE
        private set

    private var stationaryCandidateSinceMs: Long = 0L
    private var activeCandidateSinceMs: Long = 0L

    val enterWindowMs: Long get() = config.enterStationaryWindowMs
    val exitWindowMs: Long get() = config.exitActiveWindowMs

    fun evaluate(
        nowElapsedMs: Long,
        speedMps: Float?,
        hasEnterWindowHistory: Boolean,
        enterDisplacementMeters: Float,
        hasExitWindowHistory: Boolean,
        exitDisplacementMeters: Float
    ): LocationActivityState {
        val enterSpeedOk =
            speedMps == null || speedMps <= config.enterStationarySpeedThresholdMps
        val enterStationaryCondition =
            hasEnterWindowHistory &&
                    enterSpeedOk &&
                    enterDisplacementMeters <= config.enterStationaryDistanceThresholdMeters

        val exitActiveBySpeed =
            speedMps != null && speedMps >= config.exitActiveSpeedThresholdMps
        val exitActiveByDisplacement =
            hasExitWindowHistory &&
                    exitDisplacementMeters >= config.exitActiveDistanceThresholdMeters
        val exitToActiveCondition = exitActiveBySpeed || exitActiveByDisplacement

        state = when (state) {
            LocationActivityState.ACTIVE -> {
                activeCandidateSinceMs = 0L
                if (enterStationaryCondition) {
                    if (stationaryCandidateSinceMs == 0L) {
                        stationaryCandidateSinceMs = nowElapsedMs
                    }
                    val enterReady =
                        nowElapsedMs - stationaryCandidateSinceMs >= config.enterStationaryConfirmMs
                    if (enterReady) LocationActivityState.STATIONARY else LocationActivityState.ACTIVE
                } else {
                    stationaryCandidateSinceMs = 0L
                    LocationActivityState.ACTIVE
                }
            }

            LocationActivityState.STATIONARY -> {
                stationaryCandidateSinceMs = 0L
                if (exitToActiveCondition) {
                    if (activeCandidateSinceMs == 0L) {
                        activeCandidateSinceMs = nowElapsedMs
                    }
                    val exitReady =
                        nowElapsedMs - activeCandidateSinceMs >= config.exitActiveConfirmMs
                    if (exitReady) LocationActivityState.ACTIVE else LocationActivityState.STATIONARY
                } else {
                    activeCandidateSinceMs = 0L
                    LocationActivityState.STATIONARY
                }
            }
        }

        return state
    }

    fun reset(initialState: LocationActivityState = LocationActivityState.ACTIVE) {
        state = initialState
        stationaryCandidateSinceMs = 0L
        activeCandidateSinceMs = 0L
    }
}
