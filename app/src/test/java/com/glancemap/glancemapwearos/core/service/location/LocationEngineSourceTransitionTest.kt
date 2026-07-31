package com.glancemap.glancemapwearos.core.service.location

import com.glancemap.glancemapwearos.core.service.location.engine.shouldResetOutputFilterForSourceTransition
import com.glancemap.glancemapwearos.core.service.location.policy.LocationSourceMode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocationEngineSourceTransitionTest {
    @Test
    fun doesNotResetOutputFilterForInitialSourceApplication() {
        assertFalse(
            shouldResetOutputFilterForSourceTransition(
                previousSourceMode = null,
                nextSourceMode = LocationSourceMode.AUTO_FUSED,
            ),
        )
    }

    @Test
    fun doesNotResetOutputFilterWhenSourceModeIsUnchanged() {
        assertFalse(
            shouldResetOutputFilterForSourceTransition(
                previousSourceMode = LocationSourceMode.WATCH_GPS,
                nextSourceMode = LocationSourceMode.WATCH_GPS,
            ),
        )
    }

    @Test
    fun resetsOutputFilterWhenSourceModeChanges() {
        assertTrue(
            shouldResetOutputFilterForSourceTransition(
                previousSourceMode = LocationSourceMode.AUTO_FUSED,
                nextSourceMode = LocationSourceMode.WATCH_GPS,
            ),
        )
    }
}
