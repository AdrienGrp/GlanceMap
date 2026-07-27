package com.glancemap.glancemapwearos.core.service.diagnostics.export

import com.glancemap.glancemapwearos.core.service.diagnostics.TelemetryFormatters
import kotlin.math.abs

internal data class CompassHeadingTelemetrySummary(
    val windowCount: Int = 0,
    val sampleCount: Int = 0,
    val liveErrorAverageDeg: Float? = null,
    val liveErrorMaximumDeg: Float? = null,
    val conservativeErrorAverageDeg: Float? = null,
    val conservativeErrorMaximumDeg: Float? = null,
    val magneticAverageUt: Float? = null,
    val magneticMinimumUt: Float? = null,
    val magneticMaximumUt: Float? = null,
    val disagreementAverageDeg: Float? = null,
    val disagreementMaximumDeg: Float? = null,
    val acquiringSamples: Int = 0,
    val trackingSamples: Int = 0,
    val degradedSamples: Int = 0,
    val magneticInterferenceSamples: Int = 0,
    val transitionCounts: Map<String, Int> = emptyMap(),
    val degradationReasonCounts: Map<String, Int> = emptyMap(),
    val quarantineStartCount: Int = 0,
    val quarantineEndCount: Int = 0,
    val recoveryStartCount: Int = 0,
    val recoveryEndCount: Int = 0,
    val firstRenderableCount: Int = 0,
    val firstRenderableLatencyMaxMs: Long? = null,
    val firstTrustedCount: Int = 0,
    val firstTrustedLatencyMaxMs: Long? = null,
    val referenceMarkerCount: Int = 0,
    val referenceTargetErrorAverageDeg: Float? = null,
    val referenceTargetErrorMaximumDeg: Float? = null,
    val lastProvider: String? = null,
    val lastNorthBasis: String? = null,
    val lastTrackingReason: String? = null,
)

internal fun deriveCompassHeadingTelemetrySummary(lines: List<String>): CompassHeadingTelemetrySummary {
    val accumulator = CompassHeadingTelemetryAccumulator()
    lines.forEach(accumulator::consume)
    return accumulator.toSummary()
}

internal fun Appendable.writeCompassHeadingTelemetrySummary(summary: CompassHeadingTelemetrySummary) {
    appendLine()
    appendLine("Compass Heading Engine Summary")
    appendLine("aggregateWindowCount=${summary.windowCount}")
    appendLine("engineSampleCount=${summary.sampleCount}")
    appendLine("fusedLiveErrorAverageDeg=${summary.liveErrorAverageDeg.formatOrNa(1)}")
    appendLine("fusedLiveErrorMaximumDeg=${summary.liveErrorMaximumDeg.formatOrNa(1)}")
    appendLine("fusedConservativeErrorAverageDeg=${summary.conservativeErrorAverageDeg.formatOrNa(1)}")
    appendLine("fusedConservativeErrorMaximumDeg=${summary.conservativeErrorMaximumDeg.formatOrNa(1)}")
    appendLine("magneticFieldAverageUt=${summary.magneticAverageUt.formatOrNa(1)}")
    appendLine("magneticFieldMinimumUt=${summary.magneticMinimumUt.formatOrNa(1)}")
    appendLine("magneticFieldMaximumUt=${summary.magneticMaximumUt.formatOrNa(1)}")
    appendLine("fusedRelativeDisagreementAverageDeg=${summary.disagreementAverageDeg.formatOrNa(1)}")
    appendLine("fusedRelativeDisagreementMaximumDeg=${summary.disagreementMaximumDeg.formatOrNa(1)}")
    appendLine("acquiringSamples=${summary.acquiringSamples}")
    appendLine("trackingSamples=${summary.trackingSamples}")
    appendLine("degradedSamples=${summary.degradedSamples}")
    appendLine("magneticInterferenceSamples=${summary.magneticInterferenceSamples}")
    appendLine("trackingTransitions=${summary.transitionCounts.formatCounts()}")
    appendLine("degradationReasons=${summary.degradationReasonCounts.formatCounts()}")
    appendLine("quarantineStartCount=${summary.quarantineStartCount}")
    appendLine("quarantineEndCount=${summary.quarantineEndCount}")
    appendLine("recoveryStartCount=${summary.recoveryStartCount}")
    appendLine("recoveryEndCount=${summary.recoveryEndCount}")
    appendLine("firstRenderableCount=${summary.firstRenderableCount}")
    appendLine("firstRenderableLatencyMaxMs=${summary.firstRenderableLatencyMaxMs ?: "na"}")
    appendLine("firstTrustedCount=${summary.firstTrustedCount}")
    appendLine("firstTrustedLatencyMaxMs=${summary.firstTrustedLatencyMaxMs ?: "na"}")
    appendLine("referenceMarkerCount=${summary.referenceMarkerCount}")
    appendLine("referenceTargetErrorAverageDeg=${summary.referenceTargetErrorAverageDeg.formatOrNa(1)}")
    appendLine("referenceTargetErrorMaximumDeg=${summary.referenceTargetErrorMaximumDeg.formatOrNa(1)}")
    appendLine("lastProvider=${summary.lastProvider ?: "na"}")
    appendLine("lastNorthBasis=${summary.lastNorthBasis ?: "na"}")
    appendLine("lastTrackingReason=${summary.lastTrackingReason ?: "na"}")
}

private class CompassHeadingTelemetryAccumulator {
    private var windowCount = 0
    private var sampleCount = 0
    private val liveError = WeightedAverageMaximum()
    private val conservativeError = WeightedAverageMaximum()
    private val magneticField = WeightedAverageRange()
    private val disagreement = WeightedAverageMaximum()
    private var acquiringSamples = 0
    private var trackingSamples = 0
    private var degradedSamples = 0
    private var magneticInterferenceSamples = 0
    private val transitionCounts = mutableMapOf<String, Int>()
    private val degradationReasonCounts = mutableMapOf<String, Int>()
    private var quarantineStartCount = 0
    private var quarantineEndCount = 0
    private var recoveryStartCount = 0
    private var recoveryEndCount = 0
    private var firstRenderableCount = 0
    private var firstRenderableLatencyMaxMs: Long? = null
    private var firstTrustedCount = 0
    private var firstTrustedLatencyMaxMs: Long? = null
    private var referenceMarkerCount = 0
    private val referenceTargetError = RunningAverageMaximum()
    private var lastProvider: String? = null
    private var lastNorthBasis: String? = null
    private var lastTrackingReason: String? = null

    fun consume(line: String) {
        if ("[CompassTelemetry]" !in line) return
        when {
            "heading_engine window" in line -> consumeWindow(line)
            "heading_engine transition" in line -> consumeTransition(line)
            "heading_engine quarantine stage=start" in line -> quarantineStartCount += 1
            "heading_engine quarantine stage=end" in line -> quarantineEndCount += 1
            "heading_engine recovery stage=start" in line -> recoveryStartCount += 1
            "heading_engine recovery stage=end" in line -> recoveryEndCount += 1
            "wake_session stage=first_renderable" in line -> consumeFirstRenderable(line)
            "wake_session stage=first_trusted" in line -> consumeFirstTrusted(line)
            "user_report heading_reference" in line -> consumeReferenceMarker(line)
        }
    }

    fun toSummary(): CompassHeadingTelemetrySummary =
        CompassHeadingTelemetrySummary(
            windowCount = windowCount,
            sampleCount = sampleCount,
            liveErrorAverageDeg = liveError.average,
            liveErrorMaximumDeg = liveError.maximum,
            conservativeErrorAverageDeg = conservativeError.average,
            conservativeErrorMaximumDeg = conservativeError.maximum,
            magneticAverageUt = magneticField.average,
            magneticMinimumUt = magneticField.minimum,
            magneticMaximumUt = magneticField.maximum,
            disagreementAverageDeg = disagreement.average,
            disagreementMaximumDeg = disagreement.maximum,
            acquiringSamples = acquiringSamples,
            trackingSamples = trackingSamples,
            degradedSamples = degradedSamples,
            magneticInterferenceSamples = magneticInterferenceSamples,
            transitionCounts = transitionCounts.toMap(),
            degradationReasonCounts = degradationReasonCounts.toMap(),
            quarantineStartCount = quarantineStartCount,
            quarantineEndCount = quarantineEndCount,
            recoveryStartCount = recoveryStartCount,
            recoveryEndCount = recoveryEndCount,
            firstRenderableCount = firstRenderableCount,
            firstRenderableLatencyMaxMs = firstRenderableLatencyMaxMs,
            firstTrustedCount = firstTrustedCount,
            firstTrustedLatencyMaxMs = firstTrustedLatencyMaxMs,
            referenceMarkerCount = referenceMarkerCount,
            referenceTargetErrorAverageDeg = referenceTargetError.average,
            referenceTargetErrorMaximumDeg = referenceTargetError.maximum,
            lastProvider = lastProvider,
            lastNorthBasis = lastNorthBasis,
            lastTrackingReason = lastTrackingReason,
        )

    private fun consumeWindow(line: String) {
        windowCount += 1
        sampleCount += parseIntToken(line, "samples=") ?: 0
        liveError.add(
            average = parseFloatToken(line, "liveErrorAvgDeg="),
            maximum = parseFloatToken(line, "liveErrorMaxDeg="),
            count = parseIntToken(line, "liveErrorSamples=") ?: 0,
        )
        conservativeError.add(
            average = parseFloatToken(line, "conservativeErrorAvgDeg="),
            maximum = parseFloatToken(line, "conservativeErrorMaxDeg="),
            count = parseIntToken(line, "conservativeErrorSamples=") ?: 0,
        )
        magneticField.add(
            average = parseFloatToken(line, "magneticAvgUt="),
            minimum = parseFloatToken(line, "magneticMinUt="),
            maximum = parseFloatToken(line, "magneticMaxUt="),
            count = parseIntToken(line, "magneticSamples=") ?: 0,
        )
        disagreement.add(
            average = parseFloatToken(line, "disagreementAvgDeg="),
            maximum = parseFloatToken(line, "disagreementMaxDeg="),
            count = parseIntToken(line, "disagreementSamples=") ?: 0,
        )
        acquiringSamples += parseIntToken(line, "acquiringSamples=") ?: 0
        trackingSamples += parseIntToken(line, "trackingSamples=") ?: 0
        degradedSamples += parseIntToken(line, "degradedSamples=") ?: 0
        magneticInterferenceSamples += parseIntToken(line, "magInterferenceSamples=") ?: 0
        lastProvider = token(line, "provider=") ?: lastProvider
        lastNorthBasis = token(line, "northBasis=") ?: lastNorthBasis
        lastTrackingReason = token(line, "lastReason=") ?: lastTrackingReason
    }

    private fun consumeTransition(line: String) {
        token(line, "toState=")?.let { increment(transitionCounts, it) }
        token(line, "toReason=")?.let { transitionReason ->
            if (transitionReason != "stable" && transitionReason != "startup") {
                increment(degradationReasonCounts, transitionReason)
            }
            lastTrackingReason = transitionReason
        }
        lastNorthBasis = token(line, "northBasis=") ?: lastNorthBasis
    }

    private fun consumeFirstRenderable(line: String) {
        firstRenderableCount += 1
        firstRenderableLatencyMaxMs = maximumLatency(firstRenderableLatencyMaxMs, line)
    }

    private fun consumeFirstTrusted(line: String) {
        firstTrustedCount += 1
        firstTrustedLatencyMaxMs = maximumLatency(firstTrustedLatencyMaxMs, line)
    }

    private fun consumeReferenceMarker(line: String) {
        referenceMarkerCount += 1
        referenceTargetError.add(parseFloatToken(line, "targetSignedErrorDeg=")?.let(::abs))
    }

    private fun maximumLatency(
        current: Long?,
        line: String,
    ): Long? {
        val latency = parseLongToken(line, "latencyMs=") ?: return current
        return maxOf(current ?: latency, latency)
    }
}

private class WeightedAverageMaximum {
    private var weightedTotal = 0.0
    private var count = 0
    var maximum: Float? = null
        private set

    val average: Float?
        get() = if (count > 0) (weightedTotal / count).toFloat() else null

    fun add(
        average: Float?,
        maximum: Float?,
        count: Int,
    ) {
        if (average != null && average.isFinite() && count > 0) {
            weightedTotal += average.toDouble() * count
            this.count += count
        }
        maximum?.takeIf(Float::isFinite)?.let { this.maximum = maxOf(this.maximum ?: it, it) }
    }
}

private class WeightedAverageRange {
    private var weightedTotal = 0.0
    private var count = 0
    var minimum: Float? = null
        private set
    var maximum: Float? = null
        private set

    val average: Float?
        get() = if (count > 0) (weightedTotal / count).toFloat() else null

    fun add(
        average: Float?,
        minimum: Float?,
        maximum: Float?,
        count: Int,
    ) {
        if (average != null && average.isFinite() && count > 0) {
            weightedTotal += average.toDouble() * count
            this.count += count
        }
        minimum?.takeIf(Float::isFinite)?.let { this.minimum = minOf(this.minimum ?: it, it) }
        maximum?.takeIf(Float::isFinite)?.let { this.maximum = maxOf(this.maximum ?: it, it) }
    }
}

private class RunningAverageMaximum {
    private var total = 0.0
    private var count = 0
    var maximum: Float? = null
        private set

    val average: Float?
        get() = if (count > 0) (total / count).toFloat() else null

    fun add(value: Float?) {
        if (value == null || !value.isFinite()) return
        total += value
        count += 1
        maximum = maxOf(maximum ?: value, value)
    }
}

private fun increment(
    counts: MutableMap<String, Int>,
    key: String,
) {
    counts[key] = (counts[key] ?: 0) + 1
}

private fun token(
    line: String,
    key: String,
): String? {
    val start = line.indexOf(key).takeIf { it >= 0 }?.plus(key.length) ?: return null
    val end = line.indexOf(' ', start).let { if (it < 0) line.length else it }
    return line.substring(start, end).trim().takeIf { it.isNotEmpty() && it != "na" }
}

private fun parseIntToken(
    line: String,
    key: String,
): Int? = token(line, key)?.toIntOrNull()

private fun parseLongToken(
    line: String,
    key: String,
): Long? = token(line, key)?.toLongOrNull()

private fun parseFloatToken(
    line: String,
    key: String,
): Float? = token(line, key)?.toFloatOrNull()

private fun Map<String, Int>.formatCounts(): String =
    if (isEmpty()) {
        "na"
    } else {
        entries.sortedBy(Map.Entry<String, Int>::key).joinToString(",") { "${it.key}:${it.value}" }
    }

private fun Float?.formatOrNa(decimals: Int): String =
    this?.takeIf(Float::isFinite)?.let {
        TelemetryFormatters.decimal(it, decimals)
    } ?: "na"
