package com.glancemap.glancemapwearos.presentation.features.recording

import com.glancemap.glancemapwearos.data.repository.SettingsRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mapsforge.core.model.LatLong
import kotlin.math.cos

class RecordingTrackFilterTest {
    @Test
    fun qualityGateRejectsNonMonotonicFixes() {
        val gate = RecordingFixQualityGate()
        assertTrue(gate.evaluate(sample(x = 0.0, elapsedMillis = 1_000L), HIKE).accepted)

        val result = gate.evaluate(sample(x = 1.0, elapsedMillis = 1_000L), HIKE)

        assertEquals(RecordingFixQualityStatus.REJECTED, result.status)
        assertEquals(RecordingFixQualityReason.NON_MONOTONIC, result.reason)
    }

    @Test
    fun qualityGateRejectsExtremelyPoorAccuracy() {
        val gate = RecordingFixQualityGate()

        val result =
            gate.evaluate(
                sample(x = 0.0, elapsedMillis = 1_000L, accuracyMeters = 120f),
                HIKE,
            )

        assertEquals(RecordingFixQualityStatus.REJECTED, result.status)
        assertEquals(RecordingFixQualityReason.POOR_ACCURACY, result.reason)
    }

    @Test
    fun qualityGateRejectsPoorUrbanAccuracyBeforeItBecomesTheFirstPoint() {
        val hikeGate = RecordingFixQualityGate()
        val bikeGate = RecordingFixQualityGate()

        val hikeResult =
            hikeGate.evaluate(
                sample(x = 0.0, elapsedMillis = 1_000L, accuracyMeters = 36f),
                HIKE,
            )
        val bikeResult =
            bikeGate.evaluate(
                sample(x = 0.0, elapsedMillis = 1_000L, accuracyMeters = 51f),
                BIKE,
            )

        assertEquals(RecordingFixQualityReason.POOR_ACCURACY, hikeResult.reason)
        assertEquals(RecordingFixQualityReason.POOR_ACCURACY, bikeResult.reason)
    }

    @Test
    fun isolatedJumpIsHeldAndFollowingGoodFixIsAccepted() {
        val gate = RecordingFixQualityGate()
        assertTrue(gate.evaluate(sample(x = 0.0, elapsedMillis = 1_000L), HIKE).accepted)

        val jump = gate.evaluate(sample(x = 500.0, elapsedMillis = 4_000L), HIKE)
        val recovered = gate.evaluate(sample(x = 6.0, elapsedMillis = 7_000L), HIKE)

        assertEquals(RecordingFixQualityStatus.HELD, jump.status)
        assertTrue(recovered.accepted)
        assertFalse(recovered.startsNewSegment)
    }

    @Test
    fun reportedWalkingSpeedHelpsRejectShortGpsSpike() {
        val gate = RecordingFixQualityGate()
        assertTrue(gate.evaluate(sample(x = 0.0, elapsedMillis = 1_000L), HIKE).accepted)

        val result = gate.evaluate(sample(x = 35.0, elapsedMillis = 4_000L), HIKE)

        assertEquals(RecordingFixQualityStatus.HELD, result.status)
        assertEquals(RecordingFixQualityReason.IMPLAUSIBLE_JUMP, result.reason)
    }

    @Test
    fun twoConsistentFixesAfterJumpStartNewSegment() {
        val gate = RecordingFixQualityGate()
        assertTrue(gate.evaluate(sample(x = 0.0, elapsedMillis = 1_000L), HIKE).accepted)
        assertEquals(
            RecordingFixQualityStatus.HELD,
            gate.evaluate(sample(x = 500.0, elapsedMillis = 4_000L), HIKE).status,
        )

        val confirmed = gate.evaluate(sample(x = 504.0, elapsedMillis = 7_000L), HIKE)

        assertTrue(confirmed.accepted)
        assertTrue(confirmed.startsNewSegment)
        assertEquals(RecordingFixQualityReason.CONFIRMED_RELOCATION, confirmed.reason)
    }

    @Test
    fun adaptiveSmoothingReducesSmallLateralZigzag() {
        val before = point(x = 0.0, y = 0.0, timeMillis = 1_000L, accuracyMeters = 5f)
        val middle = point(x = 10.0, y = 2.0, timeMillis = 4_000L, accuracyMeters = 12f)
        val after = point(x = 20.0, y = 0.0, timeMillis = 7_000L, accuracyMeters = 5f)

        val result =
            smoothRecordingMiddlePoint(
                before = before,
                middle = middle,
                after = after,
                mode = SettingsRepository.RECORDING_TRACK_SMOOTHING_ADAPTIVE,
                activityProfile = HIKE,
            )

        assertNotNull(result)
        assertTrue(result!!.adjustmentMeters > 0.35)
        assertTrue(
            haversineMeters(before.latLong, result.point.latLong) <
                haversineMeters(before.latLong, middle.latLong),
        )
    }

    @Test
    fun strongSmoothingAdjustsMoreThanAdaptive() {
        val before = point(x = 0.0, y = 0.0, timeMillis = 1_000L, accuracyMeters = 5f)
        val middle = point(x = 10.0, y = 2.0, timeMillis = 4_000L, accuracyMeters = 12f)
        val after = point(x = 20.0, y = 0.0, timeMillis = 7_000L, accuracyMeters = 5f)

        val adaptive =
            smoothRecordingMiddlePoint(
                before,
                middle,
                after,
                SettingsRepository.RECORDING_TRACK_SMOOTHING_ADAPTIVE,
                HIKE,
            )
        val strong =
            smoothRecordingMiddlePoint(
                before,
                middle,
                after,
                SettingsRepository.RECORDING_TRACK_SMOOTHING_STRONG,
                HIKE,
            )

        assertNotNull(adaptive)
        assertNotNull(strong)
        assertTrue(strong!!.adjustmentMeters > adaptive!!.adjustmentMeters)
    }

    @Test
    fun smoothingPreservesSharpTurn() {
        val before = point(x = 0.0, y = 0.0, timeMillis = 1_000L)
        val middle = point(x = 10.0, y = 0.0, timeMillis = 4_000L)
        val after = point(x = 10.0, y = 10.0, timeMillis = 7_000L)

        assertNull(
            smoothRecordingMiddlePoint(
                before,
                middle,
                after,
                SettingsRepository.RECORDING_TRACK_SMOOTHING_STRONG,
                HIKE,
            ),
        )
    }

    @Test
    fun adaptiveSmoothingPullsLikelyIsolatedGpsSpikeTowardTravelLine() {
        val before = point(x = 0.0, y = 0.0, timeMillis = 1_000L, accuracyMeters = 6f)
        val middle = point(x = 10.0, y = 18.0, timeMillis = 4_000L, accuracyMeters = 16f)
        val after = point(x = 20.0, y = 0.0, timeMillis = 7_000L, accuracyMeters = 6f)

        val result =
            smoothRecordingMiddlePoint(
                before = before,
                middle = middle,
                after = after,
                mode = SettingsRepository.RECORDING_TRACK_SMOOTHING_ADAPTIVE,
                activityProfile = HIKE,
            )

        assertNotNull(result)
        assertTrue(result!!.adjustmentMeters >= 5.5)
        assertTrue(
            haversineMeters(result.point.latLong, after.latLong) <
                haversineMeters(middle.latLong, after.latLong),
        )
    }

    @Test
    fun smoothingSkipsGapBeyondActiveRecordingCadence() {
        val result =
            smoothRecordingMiddlePoint(
                before = point(x = 0.0, y = 0.0, timeMillis = 1_000L, accuracyMeters = 5f),
                middle = point(x = 10.0, y = 2.0, timeMillis = 4_000L, accuracyMeters = 12f),
                after = point(x = 20.0, y = 0.0, timeMillis = 10_000L, accuracyMeters = 5f),
                options =
                    RecordingPointSmoothingOptions(
                        mode = SettingsRepository.RECORDING_TRACK_SMOOTHING_ADAPTIVE,
                        activityProfile = HIKE,
                        sampleIntervalSeconds = 1,
                    ),
            )

        assertNull(result)
    }

    @Test
    fun smoothingAllowsExpectedGapForSlowerRecordingCadence() {
        val result =
            smoothRecordingMiddlePoint(
                before = point(x = 0.0, y = 0.0, timeMillis = 1_000L, accuracyMeters = 5f),
                middle = point(x = 10.0, y = 2.0, timeMillis = 7_000L, accuracyMeters = 12f),
                after = point(x = 20.0, y = 0.0, timeMillis = 13_000L, accuracyMeters = 5f),
                options =
                    RecordingPointSmoothingOptions(
                        mode = SettingsRepository.RECORDING_TRACK_SMOOTHING_ADAPTIVE,
                        activityProfile = HIKE,
                        sampleIntervalSeconds = 5,
                    ),
            )

        assertNotNull(result)
    }

    @Test
    fun smoothingOffLeavesPointUntouched() {
        assertNull(
            smoothRecordingMiddlePoint(
                before = point(x = 0.0, y = 0.0, timeMillis = 1_000L),
                middle = point(x = 10.0, y = 2.0, timeMillis = 4_000L),
                after = point(x = 20.0, y = 0.0, timeMillis = 7_000L),
                mode = SettingsRepository.RECORDING_TRACK_SMOOTHING_OFF,
                activityProfile = HIKE,
            ),
        )
    }

    private fun sample(
        x: Double,
        elapsedMillis: Long,
        accuracyMeters: Float = 5f,
    ): RecordingFixSample =
        RecordingFixSample(
            latLong = latLongFromMeters(x = x, y = 0.0),
            timeMillis = elapsedMillis,
            elapsedRealtimeMillis = elapsedMillis,
            accuracyMeters = accuracyMeters,
            speedMps = 1.2f,
            speedAccuracyMps = 0.2f,
        )

    private fun point(
        x: Double,
        y: Double,
        timeMillis: Long,
        accuracyMeters: Float = 8f,
    ): RecordedTracePoint =
        RecordedTracePoint(
            latLong = latLongFromMeters(x, y),
            elevationMeters = null,
            timeMillis = timeMillis,
            accuracyMeters = accuracyMeters,
            speedMps = 1.2f,
        )

    private fun latLongFromMeters(
        x: Double,
        y: Double,
    ): LatLong {
        val latitude = ORIGIN.latitude + Math.toDegrees(y / EARTH_RADIUS_METERS)
        val longitude =
            ORIGIN.longitude +
                Math.toDegrees(x / (EARTH_RADIUS_METERS * cos(Math.toRadians(ORIGIN.latitude))))
        return LatLong(latitude, longitude)
    }

    private companion object {
        val ORIGIN = LatLong(45.0, 6.0)
        const val HIKE = SettingsRepository.ACTIVITY_PROFILE_HIKE
        const val BIKE = SettingsRepository.ACTIVITY_PROFILE_BIKE
    }
}
