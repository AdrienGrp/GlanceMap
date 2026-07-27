package com.glancemap.glancemapwearos.core.service.location.filter

/** Fixed-memory diagnostics for the difference between provider and output-filter motion data. */
internal object LocationOutputFilterTelemetry {
    private val lock = Any()
    private val positionOffsetsM = MetricAccumulator(DISTANCE_BUCKETS_M)
    private val speedOffsetsMps = MetricAccumulator(SPEED_BUCKETS_MPS)
    private val bearingOffsetsDeg = MetricAccumulator(BEARING_BUCKETS_DEG)

    fun clear() {
        synchronized(lock) {
            positionOffsetsM.clear()
            speedOffsetsMps.clear()
            bearingOffsetsDeg.clear()
        }
    }

    fun record(
        positionOffsetM: Float? = null,
        speedOffsetMps: Float? = null,
        bearingOffsetDeg: Float? = null,
    ) {
        synchronized(lock) {
            positionOffsetsM.recordAbsolute(positionOffsetM)
            speedOffsetsMps.recordAbsolute(speedOffsetMps)
            bearingOffsetsDeg.recordAbsolute(bearingOffsetDeg)
        }
    }

    fun summary(): LocationOutputFilterTelemetrySummary =
        synchronized(lock) {
            LocationOutputFilterTelemetrySummary(
                positionOffsetM = positionOffsetsM.summary(),
                speedOffsetMps = speedOffsetsMps.summary(),
                bearingOffsetDeg = bearingOffsetsDeg.summary(),
            )
        }
}

internal data class LocationOutputFilterTelemetrySummary(
    val positionOffsetM: LocationOutputFilterMetricSummary = LocationOutputFilterMetricSummary(),
    val speedOffsetMps: LocationOutputFilterMetricSummary = LocationOutputFilterMetricSummary(),
    val bearingOffsetDeg: LocationOutputFilterMetricSummary = LocationOutputFilterMetricSummary(),
)

internal data class LocationOutputFilterMetricSummary(
    val samples: Int = 0,
    val mean: Float? = null,
    val p50: Float? = null,
    val p95: Float? = null,
    val max: Float? = null,
)

private class MetricAccumulator(
    private val upperBounds: FloatArray,
) {
    private val bucketCounts = IntArray(upperBounds.size + 1)
    private var samples = 0
    private var total = 0.0
    private var max = 0f

    fun clear() {
        bucketCounts.fill(0)
        samples = 0
        total = 0.0
        max = 0f
    }

    fun recordAbsolute(value: Float?) {
        val absoluteValue = value?.takeIf(Float::isFinite)?.let { kotlin.math.abs(it) } ?: return
        val bucketIndex =
            upperBounds
                .indexOfFirst { absoluteValue <= it }
                .takeIf { it >= 0 }
                ?: upperBounds.size
        bucketCounts[bucketIndex] += 1
        samples += 1
        total += absoluteValue.toDouble()
        max = maxOf(max, absoluteValue)
    }

    fun summary(): LocationOutputFilterMetricSummary =
        LocationOutputFilterMetricSummary(
            samples = samples,
            mean = if (samples > 0) (total / samples).toFloat() else null,
            p50 = percentile(0.50f),
            p95 = percentile(0.95f),
            max = max.takeIf { samples > 0 },
        )

    private fun percentile(fraction: Float): Float? =
        if (samples <= 0) {
            null
        } else {
            percentileWithSamples(fraction)
        }

    private fun percentileWithSamples(fraction: Float): Float {
        val targetRank =
            kotlin.math
                .ceil(samples * fraction.coerceIn(0f, 1f))
                .toInt()
                .coerceAtLeast(1)
        var cumulativeSamples = 0
        var index = 0
        while (index < bucketCounts.size && cumulativeSamples < targetRank) {
            cumulativeSamples += bucketCounts[index]
            index += 1
        }
        return upperBounds.getOrElse((index - 1).coerceAtLeast(0)) { max }
    }
}

private val DISTANCE_BUCKETS_M =
    floatArrayOf(0.05f, 0.1f, 0.25f, 0.5f, 1f, 2f, 3f, 5f, 8f, 13f, 20f, 30f, 50f, 100f, 200f)
private val SPEED_BUCKETS_MPS =
    floatArrayOf(0.05f, 0.1f, 0.2f, 0.35f, 0.5f, 0.75f, 1f, 1.5f, 2f, 3f, 5f, 8f, 12f, 20f)
private val BEARING_BUCKETS_DEG =
    floatArrayOf(1f, 2f, 3f, 5f, 10f, 15f, 30f, 45f, 60f, 90f, 120f, 180f)
