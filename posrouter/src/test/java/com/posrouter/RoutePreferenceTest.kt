package com.posrouter

import com.posrouter.core.local.LocalReachabilityCache
import com.posrouter.core.local.LocalLaunchMethod
import com.posrouter.core.local.RoutePreferencePolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RoutePreferenceTest {

    @Before
    fun resetCache() {
        LocalReachabilityCache.invalidate("SUPY")
    }

    @Test
    fun normalizeBlankOrUnknownDefaultsToAuto() {
        assertEquals(RoutePreference.AUTO, RoutePreference.normalize(null))
        assertEquals(RoutePreference.AUTO, RoutePreference.normalize(""))
        assertEquals(RoutePreference.AUTO, RoutePreference.normalize("  "))
        assertEquals(RoutePreference.AUTO, RoutePreference.normalize("unknown"))
    }

    @Test
    fun normalizeAcceptsHyphenAndCaseVariants() {
        assertEquals(RoutePreference.REMOTE_FIRST, RoutePreference.normalize("Remote-First"))
        assertEquals(RoutePreference.LOCAL_ONLY, RoutePreference.normalize("LOCAL-ONLY"))
        assertEquals(
            RoutePreference.LOCAL_POSROUTER_KIOSK,
            RoutePreference.normalize("Local-Posrouter-Kiosk")
        )
    }

    @Test
    fun policySkipsLocalForRemoteModes() {
        assertTrue(RoutePreferencePolicy.skipsLocalAttempt(RoutePreference.REMOTE_FIRST))
        assertTrue(RoutePreferencePolicy.skipsLocalAttempt(RoutePreference.REMOTE_ONLY))
        assertTrue(RoutePreferencePolicy.skipsLocalAttempt(RoutePreference.LOCAL_POSROUTER_KIOSK))
        assertFalse(RoutePreferencePolicy.skipsLocalAttempt(RoutePreference.AUTO))
    }

    @Test
    fun policyLocalFirstIgnoresUnreachableCache() {
        LocalReachabilityCache.markUnreachable("SUPY")
        assertFalse(RoutePreferencePolicy.shouldTryLocal(RoutePreference.AUTO, "SUPY"))
        assertTrue(RoutePreferencePolicy.shouldTryLocal(RoutePreference.LOCAL_FIRST, "SUPY"))
    }

    @Test
    fun policyLocalOnlyDoesNotFallbackToRemote() {
        assertFalse(RoutePreferencePolicy.shouldFallbackToRemote(RoutePreference.LOCAL_ONLY))
        assertFalse(RoutePreferencePolicy.shouldFallbackToRemote(RoutePreference.LOCAL_POSROUTER_KIOSK))
        assertTrue(RoutePreferencePolicy.shouldFallbackToRemote(RoutePreference.AUTO))
        assertTrue(RoutePreferencePolicy.shouldFallbackToRemote(RoutePreference.LOCAL_FIRST))
    }

    @Test
    fun policyLocalPosrouterKioskIsDedicatedMode() {
        assertTrue(RoutePreferencePolicy.isLocalPosrouterKiosk(RoutePreference.LOCAL_POSROUTER_KIOSK))
        assertFalse(RoutePreferencePolicy.shouldTryLocal(RoutePreference.LOCAL_POSROUTER_KIOSK, "SUPY"))
        assertFalse(RoutePreferencePolicy.isLocalPosrouterKiosk(RoutePreference.LOCAL_ONLY))
    }

    @Test
    fun markReachableRestoresAutoLocalAttempt() {
        LocalReachabilityCache.markUnreachable("SUPY")
        assertFalse(RoutePreferencePolicy.shouldTryLocal(RoutePreference.AUTO, "SUPY"))
        LocalReachabilityCache.markReachable("SUPY", LocalLaunchMethod.DEEP_LINK)
        assertTrue(RoutePreferencePolicy.shouldTryLocal(RoutePreference.AUTO, "SUPY"))
    }
}
