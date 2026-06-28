package com.posrouter

import org.junit.Assert.assertEquals
import org.junit.Test

class LensingConnectionIndicatorTest {

    @Test
    fun colorArgb_matchesCanonicalPalette() {
        assertEquals(LensingConnectionIndicator.COLOR_CONNECTED, LensingConnectionState.CONNECTED.indicatorColorArgb())
        assertEquals(LensingConnectionIndicator.COLOR_CONNECTING, LensingConnectionState.DISCOVERING.indicatorColorArgb())
        assertEquals(LensingConnectionIndicator.COLOR_CONNECTING, LensingConnectionState.CONNECTING.indicatorColorArgb())
        assertEquals(LensingConnectionIndicator.COLOR_CONNECTING, LensingConnectionState.RECONNECTING.indicatorColorArgb())
        assertEquals(LensingConnectionIndicator.COLOR_FAILED, LensingConnectionState.FAILED.indicatorColorArgb())
        assertEquals(LensingConnectionIndicator.COLOR_OFFLINE, LensingConnectionState.OFFLINE.indicatorColorArgb())
    }

    @Test
    fun posRouter_delegatesToIndicator() {
        assertEquals(
            LensingConnectionState.CONNECTED.indicatorColorArgb(),
            LensingConnectionIndicator.colorArgb(LensingConnectionState.CONNECTED)
        )
    }
}
