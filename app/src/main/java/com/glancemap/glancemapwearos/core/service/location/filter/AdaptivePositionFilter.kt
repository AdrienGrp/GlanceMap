package com.glancemap.glancemapwearos.core.service.location.filter

import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sqrt

internal data class PositionMeasurement(
    val xMeters: Double,
    val yMeters: Double,
    val accuracyMeters: Float,
    val elapsedMs: Long,
    val speedMps: Float? = null
)

internal data class FilteredPositionEstimate(
    val xMeters: Double,
    val yMeters: Double,
    val vxMps: Double,
    val vyMps: Double,
    val positionStdDevMeters: Float,
    val elapsedMs: Long
) {
    val speedMps: Float
        get() = hypot(vxMps, vyMps).toFloat()

    val bearingDeg: Float
        get() = normalizeBearingDegrees(Math.toDegrees(atan2(vxMps, vyMps)).toFloat())
}

internal class AdaptivePositionFilter {
    private val kalman = PositionKalmanFilter()
    private var lastMeasurementElapsedMs: Long = 0L

    fun reset() {
        kalman.reset()
        lastMeasurementElapsedMs = 0L
    }

    fun update(measurement: PositionMeasurement): FilteredPositionEstimate {
        val accuracyMeters = sanitizeAccuracy(measurement.accuracyMeters)
        if (!kalman.isInitialized()) {
            kalman.initialize(
                xMeters = measurement.xMeters,
                yMeters = measurement.yMeters,
                positionVariance = accuracyMeters.toDouble() * accuracyMeters.toDouble(),
                velocityVariance = initialVelocityVariance(accuracyMeters)
            )
            lastMeasurementElapsedMs = measurement.elapsedMs.coerceAtLeast(0L)
            return estimateFor(elapsedMs = lastMeasurementElapsedMs)
        }

        val sanitizedElapsedMs = measurement.elapsedMs
            .coerceAtLeast(lastMeasurementElapsedMs)
            .coerceAtLeast(0L)
        val dtMs = (sanitizedElapsedMs - lastMeasurementElapsedMs).coerceAtLeast(0L)
        val boundedDtSec = dtMs.coerceIn(MIN_DT_MS, MAX_DT_MS).toDouble() / 1_000.0

        if (dtMs > 0L) {
            kalman.predict(
                dtSec = boundedDtSec,
                accelerationSigmaMps2 = processNoiseAccelerationSigma(
                    speedMps = measurement.speedMps,
                    accuracyMeters = accuracyMeters
                )
            )
        }

        val innovationDistance = hypot(
            measurement.xMeters - kalman.positionX(),
            measurement.yMeters - kalman.positionY()
        )
        kalman.update(
            measuredXMeters = measurement.xMeters,
            measuredYMeters = measurement.yMeters,
            measurementVariance = measurementVariance(
                accuracyMeters = accuracyMeters,
                innovationDistanceMeters = innovationDistance
            )
        )
        lastMeasurementElapsedMs = sanitizedElapsedMs
        return estimateFor(elapsedMs = sanitizedElapsedMs)
    }

    private fun estimateFor(elapsedMs: Long): FilteredPositionEstimate {
        return FilteredPositionEstimate(
            xMeters = kalman.positionX(),
            yMeters = kalman.positionY(),
            vxMps = kalman.velocityX(),
            vyMps = kalman.velocityY(),
            positionStdDevMeters = kalman.positionStdDevMeters(),
            elapsedMs = elapsedMs
        )
    }

    private fun processNoiseAccelerationSigma(speedMps: Float?, accuracyMeters: Float): Double {
        val speed = sanitizeSpeed(speedMps)
        val base = when {
            speed < 0.4f -> 0.9
            speed < 1.4f -> 1.5
            speed < 2.6f -> 2.3
            else -> 3.2
        }
        val accuracyBoost = (accuracyMeters / 25f).coerceIn(0f, 1.8f)
        return base + accuracyBoost
    }

    private fun measurementVariance(
        accuracyMeters: Float,
        innovationDistanceMeters: Double
    ): Double {
        val clampedAccuracy = accuracyMeters.coerceAtLeast(MIN_MEASUREMENT_ACCURACY_M)
        val baseVariance = clampedAccuracy.toDouble() * clampedAccuracy.toDouble()
        val expectedInnovationMeters = max(
            clampedAccuracy.toDouble() * EXPECTED_INNOVATION_ACCURACY_MULTIPLIER,
            MIN_EXPECTED_INNOVATION_M
        )
        val innovationScale = max(
            1.0,
            innovationDistanceMeters / expectedInnovationMeters
        ).coerceAtMost(MAX_INNOVATION_SCALE)
        return baseVariance * innovationScale * innovationScale
    }

    private fun initialVelocityVariance(accuracyMeters: Float): Double {
        val sigma = max(
            MIN_INITIAL_VELOCITY_SIGMA_MPS.toDouble(),
            accuracyMeters.toDouble() / INITIAL_VELOCITY_ACCURACY_DIVISOR
        )
        return sigma * sigma
    }

    private fun sanitizeAccuracy(accuracyMeters: Float): Float {
        if (!accuracyMeters.isFinite()) return DEFAULT_UNKNOWN_ACCURACY_M
        return accuracyMeters.coerceAtLeast(MIN_MEASUREMENT_ACCURACY_M)
    }

    private fun sanitizeSpeed(speedMps: Float?): Float {
        if (speedMps == null || !speedMps.isFinite()) return 0f
        return speedMps.coerceAtLeast(0f)
    }
}

private class PositionKalmanFilter {
    private val state = DoubleArray(STATE_DIMENSION)
    private val covariance = Array(STATE_DIMENSION) { DoubleArray(STATE_DIMENSION) }
    private var initialized = false

    fun reset() {
        for (row in covariance) {
            row.fill(0.0)
        }
        state.fill(0.0)
        initialized = false
    }

    fun isInitialized(): Boolean = initialized

    fun initialize(
        xMeters: Double,
        yMeters: Double,
        positionVariance: Double,
        velocityVariance: Double
    ) {
        reset()
        state[STATE_X] = xMeters
        state[STATE_Y] = yMeters
        covariance[STATE_X][STATE_X] = positionVariance.coerceAtLeast(MIN_VARIANCE)
        covariance[STATE_Y][STATE_Y] = positionVariance.coerceAtLeast(MIN_VARIANCE)
        covariance[STATE_VX][STATE_VX] = velocityVariance.coerceAtLeast(MIN_VARIANCE)
        covariance[STATE_VY][STATE_VY] = velocityVariance.coerceAtLeast(MIN_VARIANCE)
        initialized = true
    }

    fun predict(dtSec: Double, accelerationSigmaMps2: Double) {
        if (!initialized) return
        if (dtSec <= 0.0) return

        val transition = arrayOf(
            doubleArrayOf(1.0, 0.0, dtSec, 0.0),
            doubleArrayOf(0.0, 1.0, 0.0, dtSec),
            doubleArrayOf(0.0, 0.0, 1.0, 0.0),
            doubleArrayOf(0.0, 0.0, 0.0, 1.0)
        )
        val predictedState = DoubleArray(STATE_DIMENSION)
        for (row in 0 until STATE_DIMENSION) {
            for (col in 0 until STATE_DIMENSION) {
                predictedState[row] += transition[row][col] * state[col]
            }
        }
        copyVector(source = predictedState, target = state)

        val temp = Array(STATE_DIMENSION) { DoubleArray(STATE_DIMENSION) }
        for (row in 0 until STATE_DIMENSION) {
            for (col in 0 until STATE_DIMENSION) {
                for (k in 0 until STATE_DIMENSION) {
                    temp[row][col] += transition[row][k] * covariance[k][col]
                }
            }
        }
        val predictedCovariance = Array(STATE_DIMENSION) { DoubleArray(STATE_DIMENSION) }
        for (row in 0 until STATE_DIMENSION) {
            for (col in 0 until STATE_DIMENSION) {
                for (k in 0 until STATE_DIMENSION) {
                    predictedCovariance[row][col] += temp[row][k] * transition[col][k]
                }
            }
        }

        val processNoise = processNoiseMatrix(
            dtSec = dtSec,
            accelerationSigmaMps2 = accelerationSigmaMps2
        )
        for (row in 0 until STATE_DIMENSION) {
            for (col in 0 until STATE_DIMENSION) {
                covariance[row][col] = predictedCovariance[row][col] + processNoise[row][col]
            }
        }
        stabilizeCovariance()
    }

    fun update(
        measuredXMeters: Double,
        measuredYMeters: Double,
        measurementVariance: Double
    ) {
        if (!initialized) {
            initialize(
                xMeters = measuredXMeters,
                yMeters = measuredYMeters,
                positionVariance = measurementVariance,
                velocityVariance = measurementVariance
            )
            return
        }

        val prior = copyMatrix(covariance)
        val safeMeasurementVariance = measurementVariance.coerceAtLeast(MIN_VARIANCE)
        val s00 = prior[STATE_X][STATE_X] + safeMeasurementVariance
        val s01 = prior[STATE_X][STATE_Y]
        val s10 = prior[STATE_Y][STATE_X]
        val s11 = prior[STATE_Y][STATE_Y] + safeMeasurementVariance
        val determinant = s00 * s11 - s01 * s10
        if (determinant <= MIN_VARIANCE) return

        val invS00 = s11 / determinant
        val invS01 = -s01 / determinant
        val invS10 = -s10 / determinant
        val invS11 = s00 / determinant

        val kalmanGain = Array(STATE_DIMENSION) { DoubleArray(MEASUREMENT_DIMENSION) }
        for (row in 0 until STATE_DIMENSION) {
            val p0 = prior[row][STATE_X]
            val p1 = prior[row][STATE_Y]
            kalmanGain[row][0] = p0 * invS00 + p1 * invS10
            kalmanGain[row][1] = p0 * invS01 + p1 * invS11
        }

        val innovationX = measuredXMeters - state[STATE_X]
        val innovationY = measuredYMeters - state[STATE_Y]
        for (row in 0 until STATE_DIMENSION) {
            state[row] += kalmanGain[row][0] * innovationX + kalmanGain[row][1] * innovationY
        }

        for (row in 0 until STATE_DIMENSION) {
            for (col in 0 until STATE_DIMENSION) {
                covariance[row][col] = prior[row][col] -
                    kalmanGain[row][0] * prior[STATE_X][col] -
                    kalmanGain[row][1] * prior[STATE_Y][col]
            }
        }
        stabilizeCovariance()
    }

    fun positionX(): Double = state[STATE_X]

    fun positionY(): Double = state[STATE_Y]

    fun velocityX(): Double = state[STATE_VX]

    fun velocityY(): Double = state[STATE_VY]

    fun positionStdDevMeters(): Float {
        val variance = (
            covariance[STATE_X][STATE_X] +
                covariance[STATE_Y][STATE_Y]
            ) / 2.0
        return sqrt(variance.coerceAtLeast(MIN_VARIANCE)).toFloat()
    }

    private fun processNoiseMatrix(
        dtSec: Double,
        accelerationSigmaMps2: Double
    ): Array<DoubleArray> {
        val dt2 = dtSec * dtSec
        val dt3 = dt2 * dtSec
        val dt4 = dt2 * dt2
        val sigma2 = accelerationSigmaMps2 * accelerationSigmaMps2
        val qPos = 0.25 * dt4 * sigma2
        val qCross = 0.5 * dt3 * sigma2
        val qVelocity = dt2 * sigma2
        return arrayOf(
            doubleArrayOf(qPos, 0.0, qCross, 0.0),
            doubleArrayOf(0.0, qPos, 0.0, qCross),
            doubleArrayOf(qCross, 0.0, qVelocity, 0.0),
            doubleArrayOf(0.0, qCross, 0.0, qVelocity)
        )
    }

    private fun stabilizeCovariance() {
        for (row in 0 until STATE_DIMENSION) {
            covariance[row][row] = covariance[row][row].coerceAtLeast(MIN_VARIANCE)
        }
        for (row in 0 until STATE_DIMENSION) {
            for (col in row + 1 until STATE_DIMENSION) {
                val average = (covariance[row][col] + covariance[col][row]) / 2.0
                covariance[row][col] = average
                covariance[col][row] = average
            }
        }
    }

    private fun copyVector(source: DoubleArray, target: DoubleArray) {
        for (index in source.indices) {
            target[index] = source[index]
        }
    }

    private fun copyMatrix(source: Array<DoubleArray>): Array<DoubleArray> {
        return Array(source.size) { source[it].copyOf() }
    }
}

private fun normalizeBearingDegrees(bearingDeg: Float): Float {
    var normalized = bearingDeg % 360f
    if (normalized < 0f) normalized += 360f
    return normalized
}

private const val STATE_DIMENSION = 4
private const val MEASUREMENT_DIMENSION = 2
private const val STATE_X = 0
private const val STATE_Y = 1
private const val STATE_VX = 2
private const val STATE_VY = 3
private const val MIN_DT_MS = 500L
private const val MAX_DT_MS = 20_000L
private const val MIN_VARIANCE = 1e-3
private const val MIN_MEASUREMENT_ACCURACY_M = 2f
private const val DEFAULT_UNKNOWN_ACCURACY_M = 40f
private const val EXPECTED_INNOVATION_ACCURACY_MULTIPLIER = 3.0
private const val MIN_EXPECTED_INNOVATION_M = 12.0
private const val MAX_INNOVATION_SCALE = 4.0
private const val MIN_INITIAL_VELOCITY_SIGMA_MPS = 1.0f
private const val INITIAL_VELOCITY_ACCURACY_DIVISOR = 6.0
