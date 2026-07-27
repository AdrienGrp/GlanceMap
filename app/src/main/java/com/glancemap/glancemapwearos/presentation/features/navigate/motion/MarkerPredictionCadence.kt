package com.glancemap.glancemapwearos.presentation.features.navigate.motion

/** Robustly adapts the visual prediction horizon to the cadence actually delivered by GPS. */
internal class MarkerPredictionCadence(
    configuredIntervalMs: Long,
) {
    private val recentIntervalsMs = LongArray(MAX_INTERVAL_SAMPLES)
    private var sampleCount = 0
    private var nextSampleIndex = 0
    private var configuredIntervalMs = sanitizeInterval(configuredIntervalMs)
    private var observedMedianMs: Long? = null

    fun updateConfiguredInterval(intervalMs: Long) {
        val sanitizedIntervalMs = sanitizeInterval(intervalMs)
        if (sanitizedIntervalMs == configuredIntervalMs) return
        configuredIntervalMs = sanitizedIntervalMs
        resetObservedIntervals()
    }

    fun resetObservedIntervals() {
        sampleCount = 0
        nextSampleIndex = 0
        observedMedianMs = null
        recentIntervalsMs.fill(0L)
    }

    fun recordAcceptedFixGap(
        gapMs: Long,
        sourceChanged: Boolean,
    ) {
        if (sourceChanged) {
            resetObservedIntervals()
            return
        }
        if (gapMs !in MIN_OBSERVED_INTERVAL_MS..MAX_OBSERVED_INTERVAL_MS) return
        recentIntervalsMs[nextSampleIndex] = gapMs
        nextSampleIndex = (nextSampleIndex + 1) % recentIntervalsMs.size
        sampleCount = (sampleCount + 1).coerceAtMost(recentIntervalsMs.size)
        val sorted = LongArray(sampleCount) { recentIntervalsMs[it] }.sortedArray()
        observedMedianMs = sorted[sorted.size / 2]
    }

    fun expectedIntervalMs(): Long {
        val observedMedian = observedMedianMs
        return if (sampleCount < MIN_INTERVAL_SAMPLES_FOR_ADAPTATION || observedMedian == null) {
            configuredIntervalMs
        } else {
            observedMedian.coerceIn(
                configuredIntervalMs / 2L,
                configuredIntervalMs * 2L,
            )
        }
    }

    fun predictionWindow(
        configuredFreshnessMaxAgeMs: Long,
        serviceFreshnessMaxAgeMs: Long,
        startDelayMs: Long,
    ): MarkerPredictionWindow {
        val expectedIntervalMs = expectedIntervalMs()
        val freshnessLimitMs =
            minOf(
                configuredFreshnessMaxAgeMs.takeIf { it > 0L } ?: Long.MAX_VALUE,
                serviceFreshnessMaxAgeMs.takeIf { it > 0L } ?: Long.MAX_VALUE,
            ).coerceAtLeast(startDelayMs)
        val observedHorizonMs =
            expectedIntervalMs + maxOf(MIN_PREDICTION_GRACE_MS, expectedIntervalMs / 2L)
        val stopAtMs =
            minOf(freshnessLimitMs, observedHorizonMs)
                .coerceAtLeast(startDelayMs)
        val fullSpeedUntilMs =
            minOf(
                expectedIntervalMs,
                (stopAtMs - MIN_EASING_WINDOW_MS).coerceAtLeast(startDelayMs),
            )
        return MarkerPredictionWindow(
            startDelayMs = startDelayMs,
            fullSpeedUntilMs = fullSpeedUntilMs,
            stopAtMs = stopAtMs,
        )
    }

    private fun sanitizeInterval(intervalMs: Long): Long = intervalMs.coerceIn(CONFIGURED_INTERVAL_RANGE)
}

private const val MAX_INTERVAL_SAMPLES = 5
private const val MIN_INTERVAL_SAMPLES_FOR_ADAPTATION = 3
private const val MIN_CONFIGURED_INTERVAL_MS = 1_000L
private const val MAX_CONFIGURED_INTERVAL_MS = 30_000L
private val CONFIGURED_INTERVAL_RANGE = MIN_CONFIGURED_INTERVAL_MS..MAX_CONFIGURED_INTERVAL_MS
private const val MIN_OBSERVED_INTERVAL_MS = 500L
private const val MAX_OBSERVED_INTERVAL_MS = 60_000L
private const val MIN_PREDICTION_GRACE_MS = 500L
private const val MIN_EASING_WINDOW_MS = 400L
