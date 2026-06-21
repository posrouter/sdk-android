package com.posrouter.core.local

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalReachabilityCacheTest {

    @Test
    fun cachesReachableMethodAndSkipsUnreachable() {
        LocalReachabilityCache.invalidate("SUPY")
        assertTrue(LocalReachabilityCache.shouldTryLocal("SUPY"))

        LocalReachabilityCache.markReachable("SUPY", LocalLaunchMethod.EXPLICIT_PACKAGE)
        assertTrue(LocalReachabilityCache.shouldTryLocal("SUPY"))
        assertTrue(
            LocalReachabilityCache.get("SUPY").preferredMethod == LocalLaunchMethod.EXPLICIT_PACKAGE
        )

        LocalReachabilityCache.markUnreachable("SUPY")
        assertFalse(LocalReachabilityCache.shouldTryLocal("SUPY"))
        LocalReachabilityCache.invalidate("SUPY")
    }
}
