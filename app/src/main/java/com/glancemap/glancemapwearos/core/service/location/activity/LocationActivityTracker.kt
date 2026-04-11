package com.glancemap.glancemapwearos.core.service.location.activity

import android.location.Location

internal data class LocationActivityTransition(
    val from: LocationActivityState,
    val to: LocationActivityState,
)

internal class LocationActivityTracker(
    private val movementHistoryDurationMs: Long,
    private val classifier: LocationActivityClassifier = LocationActivityClassifier(),
) {
    private data class MovementPoint(
        val location: Location,
        val elapsedMs: Long,
    )

    private val movementHistory = mutableListOf<MovementPoint>()

    val state: LocationActivityState
        get() = classifier.state

    fun onAcceptedLocation(
        location: Location,
        nowElapsedMs: Long,
    ): LocationActivityTransition? {
        updateMovementHistory(location, nowElapsedMs)

        val speed = if (location.hasSpeed()) location.speed else null

        val hasEnterWindowHistory = hasHistoryForWindow(nowElapsedMs, classifier.enterWindowMs)
        val enterDisplacement =
            displacementWithinWindowMeters(
                nowElapsedMs = nowElapsedMs,
                currentLocation = location,
                windowMs = classifier.enterWindowMs,
            )

        val hasExitWindowHistory = hasHistoryForWindow(nowElapsedMs, classifier.exitWindowMs)
        val exitDisplacement =
            displacementWithinWindowMeters(
                nowElapsedMs = nowElapsedMs,
                currentLocation = location,
                windowMs = classifier.exitWindowMs,
            )

        val previous = classifier.state
        val newState =
            classifier.evaluate(
                nowElapsedMs = nowElapsedMs,
                speedMps = speed,
                hasEnterWindowHistory = hasEnterWindowHistory,
                enterDisplacementMeters = enterDisplacement,
                hasExitWindowHistory = hasExitWindowHistory,
                exitDisplacementMeters = exitDisplacement,
            )

        return if (newState != previous) {
            LocationActivityTransition(from = previous, to = newState)
        } else {
            null
        }
    }

    private fun updateMovementHistory(
        location: Location,
        nowElapsedMs: Long,
    ) {
        movementHistory.add(MovementPoint(location, nowElapsedMs))
        movementHistory.removeAll { point -> nowElapsedMs - point.elapsedMs > movementHistoryDurationMs }
    }

    private fun hasHistoryForWindow(
        nowElapsedMs: Long,
        windowMs: Long,
    ): Boolean {
        val oldest = movementHistory.firstOrNull() ?: return false
        return nowElapsedMs - oldest.elapsedMs >= windowMs
    }

    private fun displacementWithinWindowMeters(
        nowElapsedMs: Long,
        currentLocation: Location,
        windowMs: Long,
    ): Float {
        val reference =
            movementHistory.firstOrNull { nowElapsedMs - it.elapsedMs <= windowMs }
                ?: movementHistory.firstOrNull()
                ?: return 0f
        return currentLocation.distanceTo(reference.location)
    }
}
