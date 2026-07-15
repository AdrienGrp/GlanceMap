package com.glancemap.glancemapwearos.core.service.diagnostics

import kotlin.math.abs
import kotlin.math.sqrt

internal data class CompassDeepTraceProviderSample(
    val provider: String,
    val headingDeg: Float,
    val headingErrorDeg: Float?,
    val accuracy: Int,
    val startupWarmup: Boolean,
    val usable: Boolean,
    val atElapsedMs: Long,
)

internal data class CompassDeepTraceRenderSample(
    val targetHeadingDeg: Float,
    val renderedHeadingDeg: Float,
    val mapRotationDeg: Float,
    val continuityActive: Boolean,
    val continuityOffsetDeg: Float,
    val atElapsedMs: Long,
)

internal enum class CompassDeepTraceRawSensor {
    GYROSCOPE,
    ACCELEROMETER,
    MAGNETOMETER,
}

internal class CompassDeepTraceWindowAccumulator(
    val startedAtElapsedMs: Long,
) {
    private val fusedHeading = HeadingTraceStats()
    private val sensorManagerHeading = HeadingTraceStats()
    private val fusedError = ScalarTraceStats()
    private val sensorManagerError = ScalarTraceStats()
    private val gyroMagnitude = ScalarTraceStats()
    private val gyroZ = ScalarTraceStats()
    private val accelerometerMagnitude = ScalarTraceStats()
    private val magnetometerMagnitude = ScalarTraceStats()
    private val renderedHeading = HeadingTraceStats()
    private val mapRotation = HeadingTraceStats()
    private val targetRenderDelta = ScalarTraceStats()
    private var unusableProviderSamples = 0
    private var startupWarmupProviderSamples = 0
    private var continuityActiveRenderSamples = 0
    private val continuityOffset = ScalarTraceStats()
    private val providerAccuracyCounts = IntArray(4)

    val hasSamples: Boolean
        get() =
            fusedHeading.count > 0 ||
                sensorManagerHeading.count > 0 ||
                gyroMagnitude.count > 0 ||
                accelerometerMagnitude.count > 0 ||
                magnetometerMagnitude.count > 0 ||
                renderedHeading.count > 0

    fun recordProvider(sample: CompassDeepTraceProviderSample) {
        when (sample.provider) {
            "google_fused" -> {
                fusedHeading.add(sample.headingDeg, sample.atElapsedMs)
                sample.headingErrorDeg?.takeIf(Float::isFinite)?.let(fusedError::add)
            }
            "sensor_manager" -> {
                sensorManagerHeading.add(sample.headingDeg, sample.atElapsedMs)
                sample.headingErrorDeg?.takeIf(Float::isFinite)?.let(sensorManagerError::add)
            }
        }
        if (!sample.usable) unusableProviderSamples += 1
        if (sample.startupWarmup) startupWarmupProviderSamples += 1
        if (sample.accuracy in providerAccuracyCounts.indices) {
            providerAccuracyCounts[sample.accuracy] += 1
        }
    }

    fun recordRawSensor(
        sensor: CompassDeepTraceRawSensor,
        x: Float,
        y: Float,
        z: Float,
    ) {
        if (!x.isFinite() || !y.isFinite() || !z.isFinite()) return
        val magnitude = sqrt(x * x + y * y + z * z)
        when (sensor) {
            CompassDeepTraceRawSensor.GYROSCOPE -> {
                gyroMagnitude.add(magnitude)
                gyroZ.add(z)
            }
            CompassDeepTraceRawSensor.ACCELEROMETER -> accelerometerMagnitude.add(magnitude)
            CompassDeepTraceRawSensor.MAGNETOMETER -> magnetometerMagnitude.add(magnitude)
        }
    }

    fun recordRender(sample: CompassDeepTraceRenderSample) {
        renderedHeading.add(sample.renderedHeadingDeg, sample.atElapsedMs)
        mapRotation.add(sample.mapRotationDeg, sample.atElapsedMs)
        targetRenderDelta.add(abs(angleDeltaDeg(sample.targetHeadingDeg, sample.renderedHeadingDeg)))
        if (sample.continuityActive) continuityActiveRenderSamples += 1
        continuityOffset.add(abs(sample.continuityOffsetDeg))
    }

    fun toTelemetryLine(
        index: Int,
        endedAtElapsedMs: Long,
    ): String {
        val durationMs = (endedAtElapsedMs - startedAtElapsedMs).coerceAtLeast(0L)
        return buildString {
            append("window index=").append(index)
            append(" startElapsedMs=").append(startedAtElapsedMs)
            append(" durationMs=").append(durationMs)
            append(" providerSamples=").append(fusedHeading.count + sensorManagerHeading.count)
            append(" fusedSamples=").append(fusedHeading.count)
            append(" fusedIntervalAvgMs=").append(fusedHeading.averageIntervalMs.formatTrace(1))
            append(" fusedIntervalMaxMs=").append(fusedHeading.maximumIntervalMs.formatTrace(1))
            append(" fusedStepAvgDeg=").append(fusedHeading.averageStepDeg.formatTrace(1))
            append(" fusedStepMaxDeg=").append(fusedHeading.maximumStepDeg.formatTrace(1))
            append(" fusedRateMaxDegPerSec=").append(fusedHeading.maximumRateDegPerSec.formatTrace(1))
            append(" fusedReversals=").append(fusedHeading.reversalCount)
            append(" fusedErrorAvgDeg=").append(fusedError.average.formatTrace(1))
            append(" fusedErrorMaxDeg=").append(fusedError.maximum.formatTrace(1))
            append(" sensorManagerSamples=").append(sensorManagerHeading.count)
            append(" sensorManagerIntervalAvgMs=").append(sensorManagerHeading.averageIntervalMs.formatTrace(1))
            append(" sensorManagerStepAvgDeg=").append(sensorManagerHeading.averageStepDeg.formatTrace(1))
            append(" sensorManagerStepMaxDeg=").append(sensorManagerHeading.maximumStepDeg.formatTrace(1))
            append(" sensorManagerReversals=").append(sensorManagerHeading.reversalCount)
            append(" sensorManagerErrorAvgDeg=").append(sensorManagerError.average.formatTrace(1))
            append(" unusableProviderSamples=").append(unusableProviderSamples)
            append(" warmupProviderSamples=").append(startupWarmupProviderSamples)
            append(" accuracyUnreliable=").append(providerAccuracyCounts[0])
            append(" accuracyLow=").append(providerAccuracyCounts[1])
            append(" accuracyMedium=").append(providerAccuracyCounts[2])
            append(" accuracyHigh=").append(providerAccuracyCounts[3])
            append(" gyroSamples=").append(gyroMagnitude.count)
            append(" gyroMagnitudeAvgRadPerSec=").append(gyroMagnitude.average.formatTrace(3))
            append(" gyroMagnitudeMaxRadPerSec=").append(gyroMagnitude.maximum.formatTrace(3))
            append(" gyroZAvgRadPerSec=").append(gyroZ.average.formatTrace(3))
            append(" accelSamples=").append(accelerometerMagnitude.count)
            append(" accelMagnitudeAvg=").append(accelerometerMagnitude.average.formatTrace(2))
            append(" accelMagnitudeMax=").append(accelerometerMagnitude.maximum.formatTrace(2))
            append(" magSamples=").append(magnetometerMagnitude.count)
            append(" magMagnitudeAvgUt=").append(magnetometerMagnitude.average.formatTrace(1))
            append(" magMagnitudeMaxUt=").append(magnetometerMagnitude.maximum.formatTrace(1))
            append(" renderSamples=").append(renderedHeading.count)
            append(" renderStepAvgDeg=").append(renderedHeading.averageStepDeg.formatTrace(1))
            append(" renderStepMaxDeg=").append(renderedHeading.maximumStepDeg.formatTrace(1))
            append(" renderRateMaxDegPerSec=").append(renderedHeading.maximumRateDegPerSec.formatTrace(1))
            append(" renderReversals=").append(renderedHeading.reversalCount)
            append(" mapStepMaxDeg=").append(mapRotation.maximumStepDeg.formatTrace(1))
            append(" mapReversals=").append(mapRotation.reversalCount)
            append(" targetRenderDeltaAvgDeg=").append(targetRenderDelta.average.formatTrace(1))
            append(" targetRenderDeltaMaxDeg=").append(targetRenderDelta.maximum.formatTrace(1))
            append(" continuityRenderSamples=").append(continuityActiveRenderSamples)
            append(" continuityOffsetAvgDeg=").append(continuityOffset.average.formatTrace(1))
            append(" continuityOffsetMaxDeg=").append(continuityOffset.maximum.formatTrace(1))
        }
    }
}

private class ScalarTraceStats {
    var count: Int = 0
        private set
    private var total = 0.0
    private var maxValue: Float? = null

    val average: Float?
        get() = if (count > 0) (total / count).toFloat() else null

    val maximum: Float?
        get() = maxValue

    fun add(value: Float) {
        if (!value.isFinite()) return
        count += 1
        total += value.toDouble()
        maxValue = maxOf(maxValue ?: value, value)
    }
}

private class HeadingTraceStats {
    var count: Int = 0
        private set
    var reversalCount: Int = 0
        private set
    private var lastHeadingDeg: Float? = null
    private var lastAtElapsedMs: Long = 0L
    private var previousDirection = 0
    private var intervalCount = 0
    private var intervalTotalMs = 0L
    private var intervalMaxMs = 0L
    private var stepCount = 0
    private var stepTotalDeg = 0.0
    private var stepMaxDeg = 0f
    private var rateMaxDegPerSec = 0f

    val averageIntervalMs: Float?
        get() = if (intervalCount > 0) intervalTotalMs.toFloat() / intervalCount else null

    val maximumIntervalMs: Float?
        get() = intervalMaxMs.takeIf { intervalCount > 0 }?.toFloat()

    val averageStepDeg: Float?
        get() = if (stepCount > 0) (stepTotalDeg / stepCount).toFloat() else null

    val maximumStepDeg: Float?
        get() = stepMaxDeg.takeIf { stepCount > 0 }

    val maximumRateDegPerSec: Float?
        get() = rateMaxDegPerSec.takeIf { stepCount > 0 }

    fun add(
        headingDeg: Float,
        atElapsedMs: Long,
    ) {
        if (!headingDeg.isFinite()) return
        val previousHeading = lastHeadingDeg
        if (previousHeading != null && lastAtElapsedMs > 0L) {
            val intervalMs = (atElapsedMs - lastAtElapsedMs).coerceAtLeast(0L)
            val signedStep = angleDeltaDeg(headingDeg, previousHeading)
            val step = abs(signedStep)
            intervalCount += 1
            intervalTotalMs += intervalMs
            intervalMaxMs = maxOf(intervalMaxMs, intervalMs)
            stepCount += 1
            stepTotalDeg += step.toDouble()
            stepMaxDeg = maxOf(stepMaxDeg, step)
            if (intervalMs > 0L) {
                rateMaxDegPerSec = maxOf(rateMaxDegPerSec, step * 1_000f / intervalMs)
            }
            val direction =
                when {
                    signedStep >= TRACE_REVERSAL_MIN_STEP_DEG -> 1
                    signedStep <= -TRACE_REVERSAL_MIN_STEP_DEG -> -1
                    else -> 0
                }
            if (direction != 0) {
                if (previousDirection != 0 && direction != previousDirection) reversalCount += 1
                previousDirection = direction
            }
        }
        count += 1
        lastHeadingDeg = headingDeg
        lastAtElapsedMs = atElapsedMs
    }
}

private fun angleDeltaDeg(
    targetDeg: Float,
    currentDeg: Float,
): Float = ((targetDeg - currentDeg + 540f) % 360f) - 180f

private fun Float?.formatTrace(decimals: Int): String = this?.let { TelemetryFormatters.decimal(it, decimals) } ?: "na"

private const val TRACE_REVERSAL_MIN_STEP_DEG = 1f
