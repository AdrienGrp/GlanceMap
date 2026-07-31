package com.glancemap.glancemapwearos.presentation.features.navigate

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.assertEquals
import org.junit.Test

class AmbientScreenSupportTest {
    @Test
    fun burnInOffsetStaysCenteredWhenProtectionIsNotRequired() {
        assertEquals(
            Offset.Zero,
            resolveAmbientBurnInOffset(
                ambientTickMs = 600_000L,
                shiftRange = 5,
                required = false,
            ),
        )
    }

    @Test
    fun burnInOffsetMovesThroughBoundedGridOncePerMinute() {
        assertEquals(
            Offset(-2f, -2f),
            resolveAmbientBurnInOffset(
                ambientTickMs = 0L,
                shiftRange = 2,
                required = true,
            ),
        )
        assertEquals(
            Offset(-1f, -2f),
            resolveAmbientBurnInOffset(
                ambientTickMs = 60_000L,
                shiftRange = 2,
                required = true,
            ),
        )
        assertEquals(
            Offset(-2f, -1f),
            resolveAmbientBurnInOffset(
                ambientTickMs = 300_000L,
                shiftRange = 2,
                required = true,
            ),
        )
    }
}
