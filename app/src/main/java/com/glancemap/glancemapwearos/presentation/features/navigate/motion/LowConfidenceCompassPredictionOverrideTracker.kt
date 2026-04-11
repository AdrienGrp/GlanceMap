package com.glancemap.glancemapwearos.presentation.features.navigate.motion

import com.glancemap.glancemapwearos.domain.sensors.CompassProviderType
import com.glancemap.glancemapwearos.domain.sensors.CompassRenderState
import com.glancemap.glancemapwearos.domain.sensors.HeadingSource

internal data class CompassPredictionOverrideEvaluation(
    val active: Boolean,
    val reason: String,
    val sampleCount: Int,
    val spanMs: Long,
    val spreadDeg: Float? = null,
    val headingErrorDeg: Float? = null
)

internal class LowConfidenceCompassPredictionOverrideTracker(
    private val observationWindowMs: Long = DEFAULT_OBSERVATION_WINDOW_MS,
    private val minObservationSpanMs: Long = DEFAULT_MIN_OBSERVATION_SPAN_MS,
    private val minSamples: Int = DEFAULT_MIN_SAMPLES,
    private val lowBandMinErrorDeg: Float = DEFAULT_LOW_BAND_MIN_ERROR_DEG,
    private val lowBandMaxErrorDeg: Float = DEFAULT_LOW_BAND_MAX_ERROR_DEG,
    private val maxErrorSpreadDeg: Float = DEFAULT_MAX_ERROR_SPREAD_DEG,
    private val maxSampleAgeMs: Long = DEFAULT_MAX_SAMPLE_AGE_MS
) {
    // Some fused providers pin uncertainty in the low band (often ~25 deg) even when heading is
    // usable, so we only override the LOW-quality prediction gate after that pattern repeats.
    private data class ErrorSample(
        val elapsedMs: Long,
        val errorDeg: Float
    )

    private val samples = ArrayDeque<ErrorSample>()
    private var lastSampleElapsedMs: Long = Long.MIN_VALUE

    var isActive: Boolean = false
        private set

    fun update(
        renderState: CompassRenderState,
        nowElapsedMs: Long
    ): CompassPredictionOverrideEvaluation {
        val sample = resolveEligibleSample(renderState = renderState, nowElapsedMs = nowElapsedMs)
            ?: return reset(reason = resolveIneligibleReason(renderState, nowElapsedMs))

        if (sample.elapsedMs != lastSampleElapsedMs) {
            samples.addLast(sample)
            lastSampleElapsedMs = sample.elapsedMs
        } else if (samples.isNotEmpty()) {
            samples.removeLast()
            samples.addLast(sample)
        }

        trim(referenceElapsedMs = sample.elapsedMs)
        val sampleCount = samples.size
        val spanMs = resolveSpanMs()
        val spreadDeg = resolveSpreadDeg()
        isActive = sampleCount >= minSamples &&
            spanMs >= minObservationSpanMs &&
            spreadDeg != null &&
            spreadDeg <= maxErrorSpreadDeg
        return CompassPredictionOverrideEvaluation(
            active = isActive,
            reason = if (isActive) "stable_low_band" else "warming_up",
            sampleCount = sampleCount,
            spanMs = spanMs,
            spreadDeg = spreadDeg,
            headingErrorDeg = sample.errorDeg
        )
    }

    fun reset(reason: String = "reset"): CompassPredictionOverrideEvaluation {
        samples.clear()
        lastSampleElapsedMs = Long.MIN_VALUE
        isActive = false
        return CompassPredictionOverrideEvaluation(
            active = false,
            reason = reason,
            sampleCount = 0,
            spanMs = 0L,
            spreadDeg = null,
            headingErrorDeg = null
        )
    }

    private fun resolveEligibleSample(
        renderState: CompassRenderState,
        nowElapsedMs: Long
    ): ErrorSample? {
        if (renderState.providerType != CompassProviderType.GOOGLE_FUSED) return null
        if (renderState.headingSource != HeadingSource.FUSED_ORIENTATION) return null
        if (renderState.headingSampleStale) return null
        if (renderState.magneticInterference) return null

        val sampleElapsedMs = renderState.headingSampleElapsedRealtimeMs
            ?.takeIf { it > 0L }
            ?: return null
        val sampleAgeMs = (nowElapsedMs - sampleElapsedMs).coerceAtLeast(0L)
        if (sampleAgeMs > maxSampleAgeMs) return null

        val headingErrorDeg = renderState.headingErrorDeg
            ?.takeIf { it.isFinite() && it in lowBandMinErrorDeg..lowBandMaxErrorDeg }
            ?: return null

        return ErrorSample(
            elapsedMs = sampleElapsedMs,
            errorDeg = headingErrorDeg
        )
    }

    private fun trim(referenceElapsedMs: Long) {
        val cutoffElapsedMs = referenceElapsedMs - observationWindowMs
        while (samples.isNotEmpty() && samples.first().elapsedMs < cutoffElapsedMs) {
            samples.removeFirst()
        }
    }

    private fun resolveSpanMs(): Long {
        val first = samples.firstOrNull() ?: return 0L
        val last = samples.lastOrNull() ?: return 0L
        return (last.elapsedMs - first.elapsedMs).coerceAtLeast(0L)
    }

    private fun resolveSpreadDeg(): Float? {
        if (samples.isEmpty()) return null
        val minErrorDeg = samples.minOf { it.errorDeg }
        val maxErrorDeg = samples.maxOf { it.errorDeg }
        return maxErrorDeg - minErrorDeg
    }

    private fun resolveIneligibleReason(
        renderState: CompassRenderState,
        nowElapsedMs: Long
    ): String {
        if (renderState.providerType != CompassProviderType.GOOGLE_FUSED) return "provider_changed"
        if (renderState.headingSource != HeadingSource.FUSED_ORIENTATION) return "source_changed"
        if (renderState.headingSampleStale) return "stale_sample"
        if (renderState.magneticInterference) return "magnetic_interference"

        val sampleElapsedMs = renderState.headingSampleElapsedRealtimeMs
            ?.takeIf { it > 0L }
            ?: return "missing_sample"
        val sampleAgeMs = (nowElapsedMs - sampleElapsedMs).coerceAtLeast(0L)
        if (sampleAgeMs > maxSampleAgeMs) return "stale_sample"

        val headingErrorDeg = renderState.headingErrorDeg
            ?.takeIf { it.isFinite() && it >= 0f }
            ?: return "missing_uncertainty"
        return if (headingErrorDeg < lowBandMinErrorDeg || headingErrorDeg > lowBandMaxErrorDeg) {
            "uncertainty_out_of_band"
        } else {
            "reset"
        }
    }
}

private const val DEFAULT_OBSERVATION_WINDOW_MS = 6_000L
private const val DEFAULT_MIN_OBSERVATION_SPAN_MS = 4_000L
private const val DEFAULT_MIN_SAMPLES = 4
private const val DEFAULT_LOW_BAND_MIN_ERROR_DEG = 18f
private const val DEFAULT_LOW_BAND_MAX_ERROR_DEG = 30f
private const val DEFAULT_MAX_ERROR_SPREAD_DEG = 2.5f
private const val DEFAULT_MAX_SAMPLE_AGE_MS = 2_500L
