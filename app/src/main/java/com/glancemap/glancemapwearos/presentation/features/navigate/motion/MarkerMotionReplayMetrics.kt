package com.glancemap.glancemapwearos.presentation.features.navigate.motion

import com.glancemap.glancemapwearos.core.service.location.policy.LocationSourceMode
import org.mapsforge.core.model.LatLong
import kotlin.math.cos
import kotlin.math.hypot

/**
 * A local-metre sample used to compare rendered motion with a deterministic reference trace.
 * This intentionally has no Android or Mapsforge dependency so recorded traces can be replayed
 * in unit tests.
 */
internal data class MarkerMotionReplaySample(
    val elapsedMs: Long,
    val renderedEastM: Double,
    val renderedNorthM: Double,
    val referenceEastM: Double,
    val referenceNorthM: Double,
)

internal data class MarkerMotionReplaySummary(
    val samples: Int,
    val positionErrorMeanM: Double?,
    val positionErrorP95M: Double?,
    val positionErrorMaxM: Double?,
    val renderStepMeanM: Double?,
    val renderStepP95M: Double?,
    val renderStepMaxM: Double?,
    val renderIntervalP50Ms: Long?,
    val renderIntervalP95Ms: Long?,
    val renderIntervalMaxMs: Long?,
    val directionReversals: Int,
)

internal object MarkerMotionReplayMetrics {
    fun evaluate(samples: List<MarkerMotionReplaySample>): MarkerMotionReplaySummary {
        val orderedSamples = samples.sortedBy(MarkerMotionReplaySample::elapsedMs)
        val positionErrorsM =
            orderedSamples.map { sample ->
                hypot(
                    sample.renderedEastM - sample.referenceEastM,
                    sample.renderedNorthM - sample.referenceNorthM,
                )
            }
        val renderStepsM = mutableListOf<Double>()
        val renderIntervalsMs = mutableListOf<Long>()
        var directionReversals = 0
        var previousVector: MotionVector? = null

        orderedSamples.zipWithNext().forEach { (previous, current) ->
            val intervalMs = current.elapsedMs - previous.elapsedMs
            if (intervalMs <= 0L) return@forEach

            val vector =
                MotionVector(
                    eastM = current.renderedEastM - previous.renderedEastM,
                    northM = current.renderedNorthM - previous.renderedNorthM,
                )
            val stepM = hypot(vector.eastM, vector.northM)
            renderStepsM += stepM
            renderIntervalsMs += intervalMs
            if (
                stepM >= MIN_REVERSAL_STEP_M &&
                previousVector?.isMeaningfulAndOpposes(vector) == true
            ) {
                directionReversals += 1
            }
            if (stepM >= MIN_REVERSAL_STEP_M) {
                previousVector = vector
            }
        }

        return MarkerMotionReplaySummary(
            samples = orderedSamples.size,
            positionErrorMeanM = positionErrorsM.meanOrNull(),
            positionErrorP95M = positionErrorsM.percentileOrNull(0.95),
            positionErrorMaxM = positionErrorsM.maxOrNull(),
            renderStepMeanM = renderStepsM.meanOrNull(),
            renderStepP95M = renderStepsM.percentileOrNull(0.95),
            renderStepMaxM = renderStepsM.maxOrNull(),
            renderIntervalP50Ms = renderIntervalsMs.longPercentileOrNull(0.50),
            renderIntervalP95Ms = renderIntervalsMs.longPercentileOrNull(0.95),
            renderIntervalMaxMs = renderIntervalsMs.maxOrNull(),
            directionReversals = directionReversals,
        )
    }
}

/** Timed provider/reference fix used by [MarkerMotionControllerReplay]. */
internal data class MarkerMotionReplayFix(
    val elapsedMs: Long,
    val providerEastM: Double,
    val providerNorthM: Double,
    val referenceEastM: Double = providerEastM,
    val referenceNorthM: Double = providerNorthM,
    val accuracyM: Float,
    val speedMps: Float?,
    val bearingDeg: Float?,
    val speedAccuracyMps: Float? = null,
    val bearingAccuracyDeg: Float? = null,
    val sourceMode: LocationSourceMode = LocationSourceMode.AUTO_FUSED,
)

internal data class MarkerMotionReplayConfig(
    val expectedGpsIntervalMs: Long,
    val predictionFreshnessMaxAgeMs: Long,
    val maxAcceptedFixAgeMs: Long,
    val serviceFreshnessMaxAgeMs: Long = predictionFreshnessMaxAgeMs,
    val renderTickMs: Long = 100L,
    val minimumRenderDisplacementM: Float = 0.12f,
    val isBikeActivityProfile: Boolean = false,
)

/**
 * Deterministically replays timed GPS inputs through the real controller. It deliberately excludes
 * Android and Mapsforge rendering so the same captured trace can be compared in JVM tests.
 */
internal object MarkerMotionControllerReplay {
    fun evaluate(
        fixes: List<MarkerMotionReplayFix>,
        config: MarkerMotionReplayConfig,
    ): MarkerMotionReplaySummary = MarkerMotionReplayMetrics.evaluate(render(fixes, config))

    fun render(
        fixes: List<MarkerMotionReplayFix>,
        config: MarkerMotionReplayConfig,
    ): List<MarkerMotionReplaySample> {
        val orderedFixes = fixes.sortedBy(MarkerMotionReplayFix::elapsedMs)
        if (orderedFixes.isEmpty()) return emptyList()
        return MarkerMotionReplaySession(orderedFixes, config).render()
    }
}

private class MarkerMotionReplaySession(
    private val orderedFixes: List<MarkerMotionReplayFix>,
    private val config: MarkerMotionReplayConfig,
) {
    private val controller =
        MarkerMotionController(
            predictionFreshnessMaxAgeMs = config.predictionFreshnessMaxAgeMs,
            maxAcceptedFixAgeMs = config.maxAcceptedFixAgeMs,
            expectedGpsIntervalMs = config.expectedGpsIntervalMs,
        ).apply {
            updateActivityProfile(config.isBikeActivityProfile)
        }
    private val origin = ReplayCoordinateOrigin()
    private val renderedSamples = mutableListOf<MarkerMotionReplaySample>()
    private var lastRenderedLatLong: LatLong? = null
    private var fixIndex = 0
    private var nextTickAtMs = orderedFixes.first().elapsedMs
    private val replayEndAtMs = orderedFixes.last().elapsedMs

    fun render(): List<MarkerMotionReplaySample> {
        while (hasPendingEvent()) {
            val eventAtMs = nextEventAtMs()
            if (eventAtMs == Long.MAX_VALUE) break
            processFixesAt(eventAtMs)
            processRenderTickAt(eventAtMs)
        }
        return renderedSamples
    }

    private fun hasPendingEvent(): Boolean = fixIndex < orderedFixes.size || nextTickAtMs <= replayEndAtMs

    private fun nextEventAtMs(): Long {
        val nextFixAtMs = orderedFixes.getOrNull(fixIndex)?.elapsedMs ?: Long.MAX_VALUE
        return minOf(nextFixAtMs, nextTickAtMs)
    }

    private fun processFixesAt(eventAtMs: Long) {
        while (orderedFixes.getOrNull(fixIndex)?.elapsedMs == eventAtMs) {
            val fix = orderedFixes[fixIndex]
            val update = controller.onGpsFix(fix.toControllerFix(origin))
            appendRenderedSampleIfNeeded(update.displayedLatLong, eventAtMs)
            fixIndex += 1
        }
    }

    private fun processRenderTickAt(eventAtMs: Long) {
        if (nextTickAtMs != eventAtMs) return
        controller
            .predict(
                nowElapsedMs = eventAtMs,
                serviceFreshnessMaxAgeMs = config.serviceFreshnessMaxAgeMs,
                watchGpsDegraded = false,
            )?.let { predicted ->
                appendRenderedSampleIfNeeded(predicted, eventAtMs)
            }
        nextTickAtMs += config.renderTickMs.coerceAtLeast(1L)
    }

    private fun appendRenderedSampleIfNeeded(
        candidate: LatLong,
        eventAtMs: Long,
    ) {
        val previousRendered = lastRenderedLatLong
        if (
            previousRendered != null &&
            markerMotionDistanceMeters(previousRendered, candidate) < config.minimumRenderDisplacementM
        ) {
            return
        }
        val renderedCoordinate = origin.fromLatLong(candidate)
        val referenceCoordinate = interpolateReferenceCoordinate(orderedFixes, eventAtMs)
        renderedSamples +=
            MarkerMotionReplaySample(
                elapsedMs = eventAtMs,
                renderedEastM = renderedCoordinate.eastM,
                renderedNorthM = renderedCoordinate.northM,
                referenceEastM = referenceCoordinate.eastM,
                referenceNorthM = referenceCoordinate.northM,
            )
        lastRenderedLatLong = candidate
    }
}

private fun MarkerMotionReplayFix.toControllerFix(origin: ReplayCoordinateOrigin): MarkerMotionGpsFix =
    MarkerMotionGpsFix(
        latLong = origin.toLatLong(providerEastM, providerNorthM),
        nowElapsedMs = elapsedMs,
        reading =
            MarkerMotionReading(
                fixElapsedMs = elapsedMs,
                accuracyM = accuracyM,
                speedMps = speedMps,
                bearingDeg = bearingDeg,
                speedAccuracyMps = speedAccuracyMps,
                bearingAccuracyDeg = bearingAccuracyDeg,
            ),
        sourceMode = sourceMode,
    )

private fun interpolateReferenceCoordinate(
    fixes: List<MarkerMotionReplayFix>,
    elapsedMs: Long,
): ReplayCoordinate {
    val followingIndex = fixes.indexOfFirst { it.elapsedMs >= elapsedMs }
    return when {
        followingIndex == 0 -> fixes.first().referencePoint()
        followingIndex < 0 -> fixes.last().referencePoint()
        else -> interpolateReferenceCoordinate(fixes, followingIndex, elapsedMs)
    }
}

private fun interpolateReferenceCoordinate(
    fixes: List<MarkerMotionReplayFix>,
    followingIndex: Int,
    elapsedMs: Long,
): ReplayCoordinate {
    val previous = fixes[followingIndex - 1]
    val following = fixes[followingIndex]
    val intervalMs = (following.elapsedMs - previous.elapsedMs).coerceAtLeast(1L)
    val fraction =
        ((elapsedMs - previous.elapsedMs).toDouble() / intervalMs.toDouble()).coerceIn(0.0, 1.0)
    return ReplayCoordinate(
        eastM = previous.referenceEastM + (following.referenceEastM - previous.referenceEastM) * fraction,
        northM = previous.referenceNorthM + (following.referenceNorthM - previous.referenceNorthM) * fraction,
    )
}

private fun MarkerMotionReplayFix.referencePoint(): ReplayCoordinate = ReplayCoordinate(referenceEastM, referenceNorthM)

private data class ReplayCoordinate(
    val eastM: Double,
    val northM: Double,
)

private class ReplayCoordinateOrigin {
    private val latitudeDeg = 45.0
    private val longitudeDeg = 6.0
    private val metersPerLongitudeDegree = METERS_PER_LATITUDE_DEGREE * cos(Math.toRadians(latitudeDeg))

    fun toLatLong(
        eastM: Double,
        northM: Double,
    ): LatLong =
        LatLong(
            latitudeDeg + northM / METERS_PER_LATITUDE_DEGREE,
            longitudeDeg + eastM / metersPerLongitudeDegree,
        )

    fun fromLatLong(latLong: LatLong): ReplayCoordinate =
        ReplayCoordinate(
            eastM = (latLong.longitude - longitudeDeg) * metersPerLongitudeDegree,
            northM = (latLong.latitude - latitudeDeg) * METERS_PER_LATITUDE_DEGREE,
        )
}

private data class MotionVector(
    val eastM: Double,
    val northM: Double,
) {
    fun isMeaningfulAndOpposes(other: MotionVector): Boolean =
        hypot(eastM, northM) >= MIN_REVERSAL_STEP_M &&
            eastM * other.eastM + northM * other.northM < 0.0
}

private const val MIN_REVERSAL_STEP_M = 0.1
private const val METERS_PER_LATITUDE_DEGREE = 111_320.0

private fun List<Double>.meanOrNull(): Double? = if (isEmpty()) null else average()

private fun List<Double>.percentileOrNull(fraction: Double): Double? {
    if (isEmpty()) return null
    val sorted = sorted()
    val index =
        kotlin.math
            .ceil(sorted.size * fraction.coerceIn(0.0, 1.0))
            .toInt()
            .coerceAtLeast(1) - 1
    return sorted[index]
}

private fun List<Long>.longPercentileOrNull(fraction: Double): Long? {
    if (isEmpty()) return null
    val sorted = sorted()
    val index =
        kotlin.math
            .ceil(sorted.size * fraction.coerceIn(0.0, 1.0))
            .toInt()
            .coerceAtLeast(1) - 1
    return sorted[index]
}
