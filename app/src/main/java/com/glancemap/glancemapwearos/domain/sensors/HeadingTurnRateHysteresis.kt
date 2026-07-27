package com.glancemap.glancemapwearos.domain.sensors

import kotlin.math.abs

/**
 * Tracks deliberate heading movement without repeatedly switching between steady and turning
 * modes when the measured angular speed sits close to a threshold.
 */
internal class HeadingTurnRateHysteresis(
    private val enterRateDegPerSec: Float,
    private val exitRateDegPerSec: Float,
    private val exitHoldMs: Long,
    private val minimumEntryStepDeg: Float,
    private val maximumSampleGapMs: Long,
) {
    @Volatile
    var active: Boolean = false
        private set

    private var previousHeadingDeg: Float? = null
    private var previousSampleAtElapsedMs: Long = 0L
    private var belowExitRateSinceElapsedMs: Long = 0L

    init {
        require(enterRateDegPerSec > exitRateDegPerSec)
        require(exitRateDegPerSec >= 0f)
        require(exitHoldMs >= 0L)
        require(minimumEntryStepDeg >= 0f)
        require(maximumSampleGapMs > 0L)
    }

    fun update(
        headingDeg: Float,
        atElapsedMs: Long,
    ): Boolean {
        if (!headingDeg.isFinite()) return active
        val previousHeading = previousHeadingDeg
        val elapsedMs = atElapsedMs - previousSampleAtElapsedMs
        previousHeadingDeg = headingDeg
        previousSampleAtElapsedMs = atElapsedMs

        if (previousHeading == null || elapsedMs !in 1L..maximumSampleGapMs) {
            active = false
            belowExitRateSinceElapsedMs = 0L
        } else {
            val stepDeg = abs(shortestAngleDiffDeg(headingDeg, previousHeading))
            val rateDegPerSec = stepDeg * MILLIS_PER_SECOND / elapsedMs.toFloat()
            when {
                !active && stepDeg >= minimumEntryStepDeg && rateDegPerSec >= enterRateDegPerSec -> {
                    active = true
                    belowExitRateSinceElapsedMs = 0L
                }
                active && rateDegPerSec >= exitRateDegPerSec -> {
                    belowExitRateSinceElapsedMs = 0L
                }
                active && belowExitRateSinceElapsedMs == 0L -> {
                    belowExitRateSinceElapsedMs = atElapsedMs
                }
                active && atElapsedMs - belowExitRateSinceElapsedMs >= exitHoldMs -> {
                    active = false
                    belowExitRateSinceElapsedMs = 0L
                }
            }
            if (!active) {
                belowExitRateSinceElapsedMs = 0L
            }
        }
        return active
    }

    fun reset() {
        active = false
        previousHeadingDeg = null
        previousSampleAtElapsedMs = 0L
        belowExitRateSinceElapsedMs = 0L
    }

    private companion object {
        const val MILLIS_PER_SECOND = 1_000f
    }
}
