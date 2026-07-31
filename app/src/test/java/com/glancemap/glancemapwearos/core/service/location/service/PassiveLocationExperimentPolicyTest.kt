package com.glancemap.glancemapwearos.core.service.location.service

import com.glancemap.glancemapwearos.core.service.location.policy.LocationSourceMode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PassiveLocationExperimentPolicyTest {
    @Test
    fun suppressesActiveImmediateRequestsOnlyForPassiveExternalExperiment() {
        assertTrue(
            shouldSuppressActiveImmediateLocationForPassiveExperiment(
                passiveLocationExperiment = true,
                sourceMode = LocationSourceMode.PASSIVE_EXTERNAL,
            ),
        )
    }

    @Test
    fun keepsImmediateRequestsForAutoFusedEvenWhenPassiveExperimentIsEnabled() {
        assertFalse(
            shouldSuppressActiveImmediateLocationForPassiveExperiment(
                passiveLocationExperiment = true,
                sourceMode = LocationSourceMode.AUTO_FUSED,
            ),
        )
    }

    @Test
    fun keepsImmediateRequestsForWatchGpsEvenWhenPassiveExperimentIsEnabled() {
        assertFalse(
            shouldSuppressActiveImmediateLocationForPassiveExperiment(
                passiveLocationExperiment = true,
                sourceMode = LocationSourceMode.WATCH_GPS,
            ),
        )
    }

    @Test
    fun keepsImmediateRequestsWhenPassiveExperimentIsDisabled() {
        assertFalse(
            shouldSuppressActiveImmediateLocationForPassiveExperiment(
                passiveLocationExperiment = false,
                sourceMode = LocationSourceMode.AUTO_FUSED,
            ),
        )
    }
}
