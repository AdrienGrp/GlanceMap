package com.glancemap.glancemapwearos.core.service.location.service

import com.glancemap.glancemapwearos.core.service.location.engine.LocationEngine
import com.glancemap.glancemapwearos.core.service.location.engine.RequestSpec
import com.glancemap.glancemapwearos.core.service.location.policy.LocationRuntimeMode
import com.glancemap.glancemapwearos.core.service.location.policy.LocationSourceMode
import com.glancemap.glancemapwearos.core.service.location.telemetry.LocationServiceTelemetry
import com.google.android.gms.location.Priority
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SelfHealFailoverCoordinatorTest {
    @Test
    fun returnsNullBelowAccuracyThresholds() {
        val requiredStreak =
            resolveAutoFusedAccuracyFailoverRequiredStreak(
                accuracyM = 80f,
                fixGapMs = 10_000L,
                expectedIntervalMs = 3_000L,
            )

        assertNull(requiredStreak)
    }

    @Test
    fun usesSeverePlateauStreakWhenAccuracyIsVeryPoorAndFixGapIsLarge() {
        val requiredStreak =
            resolveAutoFusedAccuracyFailoverRequiredStreak(
                accuracyM = 100f,
                fixGapMs = 7_000L,
                expectedIntervalMs = 3_000L,
            )

        assertEquals(3, requiredStreak)
        assertEquals(100f, resolveAutoFusedFailoverThresholdM(requiredStreak ?: 0), 0.001f)
    }

    @Test
    fun usesStandardStreakWhenFixGapIsStillShort() {
        val requiredStreak =
            resolveAutoFusedAccuracyFailoverRequiredStreak(
                accuracyM = 125f,
                fixGapMs = 2_000L,
                expectedIntervalMs = 3_000L,
            )

        assertEquals(4, requiredStreak)
        assertEquals(120f, resolveAutoFusedFailoverThresholdM(requiredStreak ?: 0), 0.001f)
    }

    @Test
    fun usesSeverePlateauStreakForVeryPoorAccuracyWhenNoAcceptedFixesArrive() {
        val requiredStreak =
            resolveAutoFusedAccuracyFailoverRequiredStreak(
                accuracyM = 117f,
                fixGapMs = 12_000L,
                expectedIntervalMs = 3_000L,
            )

        assertEquals(3, requiredStreak)
    }

    @Test
    fun noFixRecoveryStartsProbeBeforeFailover() {
        val action =
            resolveAutoFusedNoFixRecoveryAction(
                fixGapMs = 12_500L,
                thresholdMs = 12_000L,
                nowElapsedMs = 30_000L,
                probeUntilElapsedMs = 0L,
            )

        assertEquals(AutoFusedNoFixRecoveryAction.START_PROBE, action)
    }

    @Test
    fun noFixRecoveryWaitsWhileProbeWindowIsActive() {
        val action =
            resolveAutoFusedNoFixRecoveryAction(
                fixGapMs = 12_500L,
                thresholdMs = 12_000L,
                nowElapsedMs = 30_000L,
                probeUntilElapsedMs = 33_000L,
            )

        assertEquals(AutoFusedNoFixRecoveryAction.WAIT_FOR_PROBE, action)
    }

    @Test
    fun noFixRecoveryFailsOverAfterProbeWindowExpires() {
        val action =
            resolveAutoFusedNoFixRecoveryAction(
                fixGapMs = 16_500L,
                thresholdMs = 12_000L,
                nowElapsedMs = 34_500L,
                probeUntilElapsedMs = 34_000L,
            )

        assertEquals(AutoFusedNoFixRecoveryAction.FAILOVER, action)
    }

    @Test
    fun passiveExperimentUsesShorterNoFixFailoverThreshold() {
        assertEquals(8_000L, resolvePassiveExperimentNoFixFailoverThresholdMs(12_000L))
        assertEquals(6_000L, resolvePassiveExperimentNoFixFailoverThresholdMs(6_000L))
    }

    @Test
    fun passiveExperimentNoFixFallsBackToWatchGpsWithoutGenericRefreshLoop() {
        val telemetry = LocationServiceTelemetry(tag = "LocTelemetryTest", summaryIntervalMs = 60_000L)
        telemetry.setDebugEnabled(false)
        val engine = LocationEngine(telemetry)
        engine.markRequestApplied(
            RequestSpec(
                priority = Priority.PRIORITY_PASSIVE,
                intervalMs = 3_000L,
                minDistanceMeters = 1f,
                mode = LocationRuntimeMode.INTERACTIVE,
                sourceMode = LocationSourceMode.AUTO_FUSED,
            ),
        )
        var requestRefreshes = 0
        val coordinator =
            SelfHealFailoverCoordinator(
                serviceScope = CoroutineScope(SupervisorJob()),
                isServiceActive = { true },
                engine = engine,
                telemetry = telemetry,
                requestLocationUpdateIfNeeded = { requestRefreshes += 1 },
                requestImmediateLocation = {},
                trackingEnabled = { true },
                ambientModeActive = { false },
                hasFinePermission = { true },
                hasCoarsePermission = { true },
                watchGpsOnly = { false },
                passiveLocationExperiment = { true },
                lastAnyAcceptedFixAtElapsedMs = { 0L },
                lastCallbackAcceptedFixAtElapsedMs = { 0L },
                lastRequestAppliedAtElapsedMs = { 1_000L },
                expectedIntervalMs = { 3_000L },
                strictFreshMaxAgeMs = { 6_000L },
            )

        coordinator.maybeTriggerInteractiveSelfHealNow(
            nowElapsedMs = 10_000L,
            interactiveTracking = true,
            expectedIntervalMs = 3_000L,
        )

        assertTrue(coordinator.isAutoFusedFallbackToWatchGps())
        assertEquals(1, requestRefreshes)
    }

    @Test
    fun knownWatchGpsAccuracyFloorDoesNotCountAsAutoFusedRecovery() {
        assertFalse(isWatchGpsGoodEnoughForAutoFusedRecovery(125f))
    }

    @Test
    fun goodWatchGpsAccuracyCountsAsAutoFusedRecovery() {
        assertTrue(isWatchGpsGoodEnoughForAutoFusedRecovery(35f))
    }
}
