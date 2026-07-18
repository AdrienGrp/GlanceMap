package com.glancemap.glancemapwearos.domain.sensors

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class FusedHeadingIntegrityEngineTest {
    @Test
    fun normalMagneticWarmupIsNotReportedAsInterference() {
        val engine = integrityEngine()
        val replay = Replay(engine)

        replay.magnetic(42f)
        val snapshot = replay.absolute(headingDeg = 32f)

        assertEquals(CompassTrackingState.ACQUIRING, snapshot.state)
        assertEquals(CompassTrackingReason.RECOVERING, snapshot.reason)
        assertEquals(CompassMagneticQuality.RECOVERING, snapshot.magneticQuality)
        assertFalse(snapshot.magneticQuality == CompassMagneticQuality.INTERFERENCE)
    }

    @Test
    fun stableAbsoluteAndRelativeEvidenceCompletesAcquisition() {
        val engine = integrityEngine()
        val replay = Replay(engine)

        val snapshot = replay.acquireStableHeading(headingDeg = 32f)

        assertEquals(CompassTrackingState.TRACKING, snapshot.state)
        assertEquals(CompassTrackingReason.STABLE, snapshot.reason)
        assertTrue(snapshot.renderable)
        assertTrue(snapshot.trusted)
        assertEquals(32f, requireNotNull(snapshot.renderHeadingDeg), ANGLE_TOLERANCE_DEG)
        assertEquals(0f, requireNotNull(snapshot.residualSpreadDeg), ANGLE_TOLERANCE_DEG)
        assertFalse(snapshot.quarantineActive)
    }

    @Test
    fun ninetyDegreeFusedRelockWithoutRelativeTurnRemainsQuarantined() {
        val engine = integrityEngine()
        val replay = Replay(engine)
        replay.acquireStableHeading(headingDeg = 10f)

        replay.advance(20L)
        replay.relative(headingDeg = 0f)
        val relock = replay.absolute(headingDeg = 100f, liveErrorDeg = 25f, conservativeErrorDeg = 180f)

        assertEquals(CompassTrackingState.DEGRADED, relock.state)
        assertEquals(CompassTrackingReason.ABSOLUTE_RELATIVE_DISAGREEMENT, relock.reason)
        assertEquals(90f, requireNotNull(relock.absoluteRelativeDisagreementDeg), ANGLE_TOLERANCE_DEG)
        assertEquals(10f, requireNotNull(relock.renderHeadingDeg), ANGLE_TOLERANCE_DEG)
        assertEquals(100f, requireNotNull(relock.quarantinedAbsoluteHeadingDeg), ANGLE_TOLERANCE_DEG)
        assertTrue(relock.quarantineActive)

        var latest = relock
        repeat(25) {
            replay.advance(100L)
            replay.magnetic(42f)
            replay.relative(0f)
            latest = replay.absolute(headingDeg = 100f, liveErrorDeg = 25f, conservativeErrorDeg = 180f)
        }

        assertEquals(CompassTrackingState.DEGRADED, latest.state)
        assertTrue(latest.quarantineActive)
        assertEquals(10f, requireNotNull(latest.renderHeadingDeg), ANGLE_TOLERANCE_DEG)
    }

    @Test
    fun stableHighConfidenceRelockEventuallyRecoversWithBoundedMovement() {
        val engine = integrityEngine()
        val replay = Replay(engine)
        replay.acquireStableHeading(headingDeg = 10f)

        replay.advance(20L)
        replay.relative(0f)
        var latest =
            replay.absolute(
                headingDeg = 100f,
                liveErrorDeg = 25f,
                conservativeErrorDeg = 180f,
            )
        assertEquals(CompassTrackingState.DEGRADED, latest.state)

        var previousHeadingDeg = requireNotNull(latest.renderHeadingDeg)
        repeat(35) {
            replay.advance(100L)
            replay.magnetic(42f)
            replay.relative(0f)
            latest = replay.absolute(headingDeg = 100f, liveErrorDeg = 8f, conservativeErrorDeg = 30f)
            val currentHeadingDeg = requireNotNull(latest.renderHeadingDeg)
            assertTrue(
                abs(shortestAngleDiffDeg(currentHeadingDeg, previousHeadingDeg)) <=
                    MAX_100_MS_RECOVERY_CORRECTION_DEG + ANGLE_TOLERANCE_DEG,
            )
            previousHeadingDeg = currentHeadingDeg
        }

        assertEquals(CompassTrackingState.TRACKING, latest.state)
        assertEquals(CompassTrackingReason.STABLE, latest.reason)
        assertEquals(100f, requireNotNull(latest.renderHeadingDeg), ANGLE_TOLERANCE_DEG)
    }

    @Test
    fun gradualWeakConfidenceRelockIsStoppedBeforeItCanRotateTheMapFar() {
        val engine = integrityEngine()
        val replay = Replay(engine)
        replay.acquireStableHeading(headingDeg = 10f)

        var latest: FusedHeadingIntegritySnapshot? = null
        listOf(14f, 18f, 22f, 26f, 30f, 34f, 38f).forEach { absoluteHeadingDeg ->
            replay.advance(20L)
            replay.magnetic(42f)
            replay.relative(0f)
            latest =
                replay.absolute(
                    headingDeg = absoluteHeadingDeg,
                    liveErrorDeg = 25f,
                    conservativeErrorDeg = 180f,
                )
        }

        val snapshot = requireNotNull(latest)
        assertEquals(CompassTrackingState.DEGRADED, snapshot.state)
        assertEquals(CompassTrackingReason.ABSOLUTE_RELATIVE_DISAGREEMENT, snapshot.reason)
        assertTrue(snapshot.quarantineActive)
        assertTrue(
            abs(shortestAngleDiffDeg(requireNotNull(snapshot.renderHeadingDeg), 10f)) <=
                20f + ANGLE_TOLERANCE_DEG,
        )
    }

    @Test
    fun genuineFullTurnStaysTrackingAndReturnsToStart() {
        val engine = integrityEngine()
        val replay = Replay(engine)
        var latest = replay.acquireStableHeading(headingDeg = 0f)
        var renderedTurnDeg = 0f
        var previousRenderedHeadingDeg = requireNotNull(latest.renderHeadingDeg)

        val headings = (12..348 step 12).map(Int::toFloat) + 0f
        headings.forEach { headingDeg ->
            replay.advance(20L)
            replay.magnetic(42f)
            replay.relative(headingDeg)
            latest = replay.absolute(headingDeg = headingDeg)

            assertEquals(CompassTrackingState.TRACKING, latest.state)
            assertFalse(latest.quarantineActive)
            assertTrue((latest.absoluteRelativeDisagreementDeg ?: 0f) <= ANGLE_TOLERANCE_DEG)
            val renderedHeadingDeg = requireNotNull(latest.renderHeadingDeg)
            renderedTurnDeg += shortestAngleDiffDeg(renderedHeadingDeg, previousRenderedHeadingDeg)
            previousRenderedHeadingDeg = renderedHeadingDeg
        }

        assertEquals(360f, renderedTurnDeg, ANGLE_TOLERANCE_DEG)
        assertEquals(0f, requireNotNull(latest.renderHeadingDeg), ANGLE_TOLERANCE_DEG)
    }

    @Test
    fun magneticInterferenceUsesRelativeContinuityAndRecoveryIsBounded() {
        val engine = integrityEngine()
        val replay = Replay(engine)
        replay.acquireStableHeading(headingDeg = 40f)

        replay.advance(20L)
        val interference = replay.magnetic(2_000f)
        assertEquals(CompassTrackingState.DEGRADED, interference.state)
        assertEquals(CompassTrackingReason.MAGNETIC_INTERFERENCE, interference.reason)
        assertEquals(CompassMagneticQuality.INTERFERENCE, interference.magneticQuality)

        replay.advance(20L)
        replay.relative(20f)
        var latest = replay.absolute(headingDeg = 60f)
        assertEquals(60f, requireNotNull(latest.renderHeadingDeg), ANGLE_TOLERANCE_DEG)
        assertEquals(CompassTrackingState.DEGRADED, latest.state)

        val renderedDuringRecovery = mutableListOf<Float>()
        val correctionDuringRecovery = mutableListOf<Float>()
        repeat(24) {
            replay.advance(75L)
            replay.magnetic(42f)
            replay.relative(20f)
            latest = replay.absolute(headingDeg = 80f)
            renderedDuringRecovery += requireNotNull(latest.renderHeadingDeg)
            if (latest.recoveryActive || abs(latest.recoveryCorrectionDeg) > 0f) {
                correctionDuringRecovery += latest.recoveryCorrectionDeg
            }
        }

        assertTrue(correctionDuringRecovery.isNotEmpty())
        correctionDuringRecovery.forEach { correctionDeg ->
            assertTrue(abs(correctionDeg) <= MAX_75_MS_RECOVERY_CORRECTION_DEG + ANGLE_TOLERANCE_DEG)
        }
        renderedDuringRecovery.zipWithNext().forEach { (previous, current) ->
            assertTrue(shortestAngleDiffDeg(current, previous) >= -ANGLE_TOLERANCE_DEG)
        }
        assertEquals(CompassTrackingState.TRACKING, latest.state)
        assertEquals(CompassTrackingReason.STABLE, latest.reason)
        assertEquals(80f, requireNotNull(latest.renderHeadingDeg), ANGLE_TOLERANCE_DEG)
        assertFalse(latest.quarantineActive)
    }

    @Test
    fun circularEvidenceAndTrackingHandleNorthWraparound() {
        assertEquals(
            4f,
            requireNotNull(circularWindowSpreadDeg(listOf(358f, 359f, 0f, 1f, 2f))),
            ANGLE_TOLERANCE_DEG,
        )

        val engine = integrityEngine()
        val replay = Replay(engine)
        replay.acquireStableHeading(headingDeg = 358f)

        replay.advance(20L)
        replay.relative(4f)
        val wrapped = replay.absolute(headingDeg = 2f)

        assertEquals(CompassTrackingState.TRACKING, wrapped.state)
        assertEquals(2f, requireNotNull(wrapped.renderHeadingDeg), ANGLE_TOLERANCE_DEG)
        assertTrue((wrapped.absoluteRelativeDisagreementDeg ?: 0f) <= ANGLE_TOLERANCE_DEG)
        assertFalse(wrapped.quarantineActive)
    }

    @Test
    fun unavailableRelativeSensorStillAllowsStableAbsoluteAcquisition() {
        val engine =
            FusedHeadingIntegrityEngine(
                relativeSensorAvailable = false,
                magnetometerAvailable = false,
            )
        val replay = Replay(engine, relativeSensorAvailable = false, magnetometerAvailable = false)

        val first = replay.absolute(headingDeg = 75f)
        assertEquals(CompassTrackingState.ACQUIRING, first.state)
        assertTrue(first.renderable)
        assertNull(first.relativeHeadingDeg)

        var latest = first
        repeat(8) {
            replay.advance(50L)
            latest = replay.absolute(headingDeg = 75f)
        }

        assertEquals(CompassTrackingState.TRACKING, latest.state)
        assertEquals(CompassTrackingReason.STABLE, latest.reason)
        assertEquals(CompassMagneticQuality.UNAVAILABLE, latest.magneticQuality)
        assertEquals(75f, requireNotNull(latest.renderHeadingDeg), ANGLE_TOLERANCE_DEG)
        assertFalse(latest.quarantineActive)
    }

    @Test
    fun recoveryWithoutRelativeSensorAppliesOnlyOneBoundedCorrection() {
        val engine =
            FusedHeadingIntegrityEngine(
                relativeSensorAvailable = false,
                magnetometerAvailable = false,
            )
        val replay = Replay(engine, relativeSensorAvailable = false, magnetometerAvailable = false)
        replay.acquireStableHeading(headingDeg = 75f)

        replay.advance(20L)
        var latest = replay.absolute(headingDeg = 165f)
        assertEquals(CompassTrackingState.DEGRADED, latest.state)
        assertEquals(75f, requireNotNull(latest.renderHeadingDeg), ANGLE_TOLERANCE_DEG)

        var previousHeadingDeg = requireNotNull(latest.renderHeadingDeg)
        repeat(35) {
            replay.advance(100L)
            latest = replay.absolute(headingDeg = 165f)
            val currentHeadingDeg = requireNotNull(latest.renderHeadingDeg)
            assertTrue(
                abs(shortestAngleDiffDeg(currentHeadingDeg, previousHeadingDeg)) <=
                    MAX_100_MS_RECOVERY_CORRECTION_DEG + ANGLE_TOLERANCE_DEG,
            )
            previousHeadingDeg = currentHeadingDeg
        }

        assertEquals(CompassTrackingState.TRACKING, latest.state)
        assertEquals(165f, requireNotNull(latest.renderHeadingDeg), ANGLE_TOLERANCE_DEG)
    }

    @Test
    fun stalledMagnetometerBecomesUnavailableInsteadOfBlockingForever() {
        val engine = integrityEngine()
        val replay = Replay(engine)
        replay.acquireStableHeading(headingDeg = 75f)

        replay.advance(1_100L)
        replay.relative(0f)
        val stale = replay.absolute(headingDeg = 75f)
        assertEquals(CompassMagneticQuality.UNKNOWN, stale.magneticQuality)
        assertEquals(CompassTrackingState.DEGRADED, stale.state)

        replay.advance(2_000L)
        replay.relative(0f)
        val unavailable = replay.absolute(headingDeg = 75f)

        assertEquals(CompassMagneticQuality.UNAVAILABLE, unavailable.magneticQuality)
        assertFalse(unavailable.reason == CompassTrackingReason.MAGNETIC_INTERFERENCE)
        assertTrue(unavailable.renderable)
    }

    private fun integrityEngine(): FusedHeadingIntegrityEngine =
        FusedHeadingIntegrityEngine(
            relativeSensorAvailable = true,
            magnetometerAvailable = true,
        )

    private class Replay(
        private val engine: FusedHeadingIntegrityEngine,
        private val relativeSensorAvailable: Boolean = true,
        private val magnetometerAvailable: Boolean = true,
        var nowElapsedMs: Long = 1_000L,
    ) {
        fun advance(durationMs: Long) {
            nowElapsedMs += durationMs
        }

        fun relative(headingDeg: Float): FusedHeadingIntegritySnapshot =
            engine.onRelativeHeading(
                headingDeg = headingDeg,
                atElapsedMs = nowElapsedMs,
            )

        fun magnetic(strengthUt: Float): FusedHeadingIntegritySnapshot =
            engine.onMagneticField(
                strengthUt = strengthUt,
                atElapsedMs = nowElapsedMs,
            )

        fun absolute(
            headingDeg: Float,
            liveErrorDeg: Float = 8f,
            conservativeErrorDeg: Float = 30f,
        ): FusedHeadingIntegritySnapshot =
            engine.onAbsoluteHeading(
                FusedAbsoluteHeadingSample(
                    headingDeg = headingDeg,
                    liveErrorDeg = liveErrorDeg,
                    conservativeErrorDeg = conservativeErrorDeg,
                    atElapsedMs = nowElapsedMs,
                ),
            )

        fun acquireStableHeading(headingDeg: Float): FusedHeadingIntegritySnapshot {
            if (magnetometerAvailable) {
                magnetic(42f)
                advance(200L)
                magnetic(42f)
            }
            var latest: FusedHeadingIntegritySnapshot? = null
            repeat(9) { index ->
                if (index > 0) advance(50L)
                if (magnetometerAvailable) magnetic(42f)
                if (relativeSensorAvailable) relative(0f)
                latest = absolute(headingDeg = headingDeg)
            }
            return requireNotNull(latest)
        }
    }

    private companion object {
        const val ANGLE_TOLERANCE_DEG = 0.01f
        const val MAX_75_MS_RECOVERY_CORRECTION_DEG = 3.375f
        const val MAX_100_MS_RECOVERY_CORRECTION_DEG = 4.5f
    }
}
