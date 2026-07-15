package com.glancemap.glancemapwearos.domain.sensors

import org.junit.Assert.assertEquals
import org.junit.Test

class FusedOrientationStartupReleaseTest {
    @Test
    fun unstableStartupExtendsSettlingInsteadOfReleasing() {
        assertEquals(
            FusedStartupReleaseAction.EXTEND_SETTLING,
            resolveFusedStartupReleaseAction(
                unstableStartup = true,
                extendedSettlingActive = false,
                extendedSettlingAgeMs = 0L,
                stableCandidateAgeMs = 180L,
                bootstrapDeltaDeg = 151f,
            ),
        )
    }

    @Test
    fun extendedStartupWaitsWhileBootstrapStillDisagrees() {
        assertEquals(
            FusedStartupReleaseAction.WAIT,
            resolveFusedStartupReleaseAction(
                unstableStartup = true,
                extendedSettlingActive = true,
                extendedSettlingAgeMs = 600L,
                stableCandidateAgeMs = 400L,
                bootstrapDeltaDeg = 120f,
            ),
        )
    }

    @Test
    fun extendedStartupReleasesAfterStableCrossSourceAgreement() {
        assertEquals(
            FusedStartupReleaseAction.RELEASE_AGREEMENT,
            resolveFusedStartupReleaseAction(
                unstableStartup = false,
                extendedSettlingActive = true,
                extendedSettlingAgeMs = FUSED_EXTENDED_SETTLING_MIN_AGE_MS,
                stableCandidateAgeMs = FUSED_EXTENDED_SETTLING_STABLE_AGE_MS,
                bootstrapDeltaDeg = FUSED_EXTENDED_SETTLING_MAX_BOOTSTRAP_DELTA_DEG,
            ),
        )
    }

    @Test
    fun extendedStartupEventuallyReleasesThroughControlledTimeout() {
        assertEquals(
            FusedStartupReleaseAction.RELEASE_TIMEOUT,
            resolveFusedStartupReleaseAction(
                unstableStartup = true,
                extendedSettlingActive = true,
                extendedSettlingAgeMs = FUSED_EXTENDED_SETTLING_TIMEOUT_MS,
                stableCandidateAgeMs = FUSED_EXTENDED_SETTLING_STABLE_AGE_MS,
                bootstrapDeltaDeg = 170f,
            ),
        )
    }
}
