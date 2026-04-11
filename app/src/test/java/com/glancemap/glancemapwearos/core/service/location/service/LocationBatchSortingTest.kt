package com.glancemap.glancemapwearos.core.service.location.service

import org.junit.Assert.assertEquals
import org.junit.Test

class LocationBatchSortingTest {

    @Test
    fun sortsItemsByElapsedRealtimeWhenAvailable() {
        val oldest = TimestampedCandidate(id = "oldest", elapsedRealtimeNanos = 1_000_000_000L, timeMs = 3_000L)
        val newest = TimestampedCandidate(id = "newest", elapsedRealtimeNanos = 3_000_000_000L, timeMs = 1_000L)
        val middle = TimestampedCandidate(id = "middle", elapsedRealtimeNanos = 2_000_000_000L, timeMs = 2_000L)

        val sorted = sortBatchByTimestamp(
            items = listOf(newest, oldest, middle),
            elapsedRealtimeNanosOf = { it.elapsedRealtimeNanos },
            wallClockTimeMsOf = { it.timeMs }
        )

        assertEquals(listOf(oldest, middle, newest), sorted)
    }

    @Test
    fun sortsItemsByWallClockTimeWhenElapsedRealtimeIsUnavailable() {
        val oldest = TimestampedCandidate(id = "oldest", elapsedRealtimeNanos = 0L, timeMs = 1_000L)
        val newest = TimestampedCandidate(id = "newest", elapsedRealtimeNanos = 0L, timeMs = 3_000L)
        val middle = TimestampedCandidate(id = "middle", elapsedRealtimeNanos = 0L, timeMs = 2_000L)

        val sorted = sortBatchByTimestamp(
            items = listOf(newest, oldest, middle),
            elapsedRealtimeNanosOf = { it.elapsedRealtimeNanos },
            wallClockTimeMsOf = { it.timeMs }
        )

        assertEquals(listOf(oldest, middle, newest), sorted)
    }

    @Test
    fun keepsOriginalOrderWhenBatchHasMixedTimestampKinds() {
        val elapsed = TimestampedCandidate(id = "elapsed", elapsedRealtimeNanos = 2_000_000_000L, timeMs = 0L)
        val wallClock = TimestampedCandidate(id = "wall", elapsedRealtimeNanos = 0L, timeMs = 1_000L)

        val sorted = sortBatchByTimestamp(
            items = listOf(elapsed, wallClock),
            elapsedRealtimeNanosOf = { it.elapsedRealtimeNanos },
            wallClockTimeMsOf = { it.timeMs }
        )

        assertEquals(listOf(elapsed, wallClock), sorted)
    }
}

private data class TimestampedCandidate(
    val id: String,
    val elapsedRealtimeNanos: Long,
    val timeMs: Long
)
