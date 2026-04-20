package com.glancemap.glancemapwearos.core.service.location.service

import com.glancemap.glancemapwearos.core.service.location.adapters.LocationSettingsPreflightResult
import com.glancemap.glancemapwearos.core.service.location.adapters.WatchGpsAvailabilityReason
import com.glancemap.glancemapwearos.core.service.location.model.GpsEnvironmentWarning
import com.glancemap.glancemapwearos.core.service.location.policy.LocationSourceMode
import org.junit.Assert.assertEquals
import org.junit.Test

class LocationEnvironmentResolverTest {
    @Test
    fun autoFusedPhoneDisconnectedWithWatchGpsRestartsOnWatchGps() {
        val decision =
            resolveLocationEnvironmentDecision(
                sourceMode = LocationSourceMode.AUTO_FUSED,
                watchOnlyRequested = false,
                watchGpsAvailability = WatchGpsAvailabilityReason.AVAILABLE,
                phoneConnected = false,
                locationSettings = satisfiedLocationSettings,
            )

        assertEquals(GpsEnvironmentWarning.AUTO_PHONE_DISCONNECTED_USING_WATCH_GPS, decision.warning)
        assertEquals(LocationEnvironmentAction.RESTART_REQUEST, decision.action)
    }

    @Test
    fun autoFusedPhoneDisconnectedWithoutWatchGpsShowsUnavailableWarning() {
        val decision =
            resolveLocationEnvironmentDecision(
                sourceMode = LocationSourceMode.AUTO_FUSED,
                watchOnlyRequested = false,
                watchGpsAvailability = WatchGpsAvailabilityReason.HARDWARE_MISSING,
                phoneConnected = false,
                locationSettings = satisfiedLocationSettings,
            )

        assertEquals(GpsEnvironmentWarning.AUTO_PHONE_DISCONNECTED_NO_WATCH_GPS, decision.warning)
        assertEquals(LocationEnvironmentAction.CONTINUE, decision.action)
    }

    @Test
    fun locationSettingsFailureWinsOverPhoneFallback() {
        val decision =
            resolveLocationEnvironmentDecision(
                sourceMode = LocationSourceMode.AUTO_FUSED,
                watchOnlyRequested = false,
                watchGpsAvailability = WatchGpsAvailabilityReason.AVAILABLE,
                phoneConnected = false,
                locationSettings =
                    LocationSettingsPreflightResult(
                        satisfied = false,
                        statusCode = 6,
                        detail = "resolution_required",
                    ),
            )

        assertEquals(GpsEnvironmentWarning.LOCATION_SETTINGS_UNSATISFIED, decision.warning)
        assertEquals(LocationEnvironmentAction.CONTINUE, decision.action)
    }

    @Test
    fun unknownLocationSettingsCheckDoesNotMaskPhoneFallback() {
        val decision =
            resolveLocationEnvironmentDecision(
                sourceMode = LocationSourceMode.AUTO_FUSED,
                watchOnlyRequested = false,
                watchGpsAvailability = WatchGpsAvailabilityReason.AVAILABLE,
                phoneConnected = false,
                locationSettings =
                    LocationSettingsPreflightResult(
                        satisfied = null,
                        statusCode = 10,
                        detail = "developer_error",
                    ),
            )

        assertEquals(GpsEnvironmentWarning.AUTO_PHONE_DISCONNECTED_USING_WATCH_GPS, decision.warning)
        assertEquals(LocationEnvironmentAction.RESTART_REQUEST, decision.action)
    }

    @Test
    fun unknownLocationSettingsCheckDoesNotWarnWhenPhoneConnected() {
        val decision =
            resolveLocationEnvironmentDecision(
                sourceMode = LocationSourceMode.AUTO_FUSED,
                watchOnlyRequested = false,
                watchGpsAvailability = WatchGpsAvailabilityReason.AVAILABLE,
                phoneConnected = true,
                locationSettings =
                    LocationSettingsPreflightResult(
                        satisfied = null,
                        statusCode = 10,
                        detail = "developer_error",
                    ),
            )

        assertEquals(GpsEnvironmentWarning.NONE, decision.warning)
        assertEquals(LocationEnvironmentAction.CONTINUE, decision.action)
    }

    @Test
    fun passiveExperimentDoesNotForceWatchGpsWhenPhoneDisconnected() {
        val decision =
            resolveLocationEnvironmentDecision(
                sourceMode = LocationSourceMode.AUTO_FUSED,
                watchOnlyRequested = false,
                watchGpsAvailability = WatchGpsAvailabilityReason.AVAILABLE,
                phoneConnected = false,
                locationSettings = null,
                passiveLocationExperiment = true,
            )

        assertEquals(GpsEnvironmentWarning.NONE, decision.warning)
        assertEquals(LocationEnvironmentAction.CONTINUE, decision.action)
    }

    @Test
    fun watchGpsFallbackKeepsPhoneDisconnectedWarning() {
        val decision =
            resolveLocationEnvironmentDecision(
                sourceMode = LocationSourceMode.WATCH_GPS,
                watchOnlyRequested = false,
                watchGpsAvailability = WatchGpsAvailabilityReason.AVAILABLE,
                phoneConnected = false,
                locationSettings = null,
            )

        assertEquals(GpsEnvironmentWarning.AUTO_PHONE_DISCONNECTED_USING_WATCH_GPS, decision.warning)
        assertEquals(LocationEnvironmentAction.CONTINUE, decision.action)
    }

    @Test
    fun userRequestedWatchGpsDoesNotShowPhoneWarning() {
        val decision =
            resolveLocationEnvironmentDecision(
                sourceMode = LocationSourceMode.WATCH_GPS,
                watchOnlyRequested = true,
                watchGpsAvailability = WatchGpsAvailabilityReason.AVAILABLE,
                phoneConnected = false,
                locationSettings = null,
            )

        assertEquals(GpsEnvironmentWarning.NONE, decision.warning)
        assertEquals(LocationEnvironmentAction.CONTINUE, decision.action)
    }

    private companion object {
        val satisfiedLocationSettings =
            LocationSettingsPreflightResult(
                satisfied = true,
                statusCode = null,
                detail = null,
            )
    }
}
