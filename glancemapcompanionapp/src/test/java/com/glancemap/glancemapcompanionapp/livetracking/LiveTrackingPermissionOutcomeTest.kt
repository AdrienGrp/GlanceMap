package com.glancemap.glancemapcompanionapp.livetracking

import org.junit.Assert.assertEquals
import org.junit.Test

class LiveTrackingPermissionOutcomeTest {
    @Test
    fun locationDenialAlwaysBlocksStarting() {
        assertEquals(
            LiveTrackingPermissionOutcome.LOCATION_REQUIRED,
            liveTrackingPermissionOutcome(
                locationGranted = false,
                notificationGranted = true,
            ),
        )
        assertEquals(
            LiveTrackingPermissionOutcome.LOCATION_REQUIRED,
            liveTrackingPermissionOutcome(
                locationGranted = false,
                notificationGranted = false,
            ),
        )
    }

    @Test
    fun notificationDenialRequiresAnExplicitWarningDecision() {
        assertEquals(
            LiveTrackingPermissionOutcome.NOTIFICATION_WARNING,
            liveTrackingPermissionOutcome(
                locationGranted = true,
                notificationGranted = false,
            ),
        )
    }

    @Test
    fun bothPermissionsAllowStarting() {
        assertEquals(
            LiveTrackingPermissionOutcome.CONTINUE,
            liveTrackingPermissionOutcome(
                locationGranted = true,
                notificationGranted = true,
            ),
        )
    }

    @Test
    fun backgroundProtectionIsRequestedOnlyWhenForegroundLocationExists() {
        assertEquals(
            true,
            backgroundLocationProtectionRequired(
                supportsBackgroundLocation = true,
                foregroundLocationGranted = true,
                backgroundLocationGranted = false,
            ),
        )
        assertEquals(
            false,
            backgroundLocationProtectionRequired(
                supportsBackgroundLocation = true,
                foregroundLocationGranted = false,
                backgroundLocationGranted = false,
            ),
        )
        assertEquals(
            false,
            backgroundLocationProtectionRequired(
                supportsBackgroundLocation = false,
                foregroundLocationGranted = true,
                backgroundLocationGranted = false,
            ),
        )
    }
}
