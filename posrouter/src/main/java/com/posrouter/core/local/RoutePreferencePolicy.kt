package com.posrouter.core.local

import com.posrouter.RoutePreference

internal object RoutePreferencePolicy {

    fun shouldTryLocal(preference: String, acquirerCode: String): Boolean =
        when (RoutePreference.normalize(preference)) {
            RoutePreference.REMOTE_FIRST,
            RoutePreference.REMOTE_ONLY,
            RoutePreference.LOCAL_POSROUTER_KIOSK -> false
            RoutePreference.LOCAL_FIRST, RoutePreference.LOCAL_ONLY -> true
            RoutePreference.AUTO -> LocalReachabilityCache.shouldTryLocal(acquirerCode)
            else -> LocalReachabilityCache.shouldTryLocal(acquirerCode)
        }

    fun shouldFallbackToRemote(preference: String): Boolean =
        when (RoutePreference.normalize(preference)) {
            RoutePreference.LOCAL_ONLY,
            RoutePreference.LOCAL_POSROUTER_KIOSK -> false
            else -> true
        }

    fun skipsLocalAttempt(preference: String): Boolean =
        when (RoutePreference.normalize(preference)) {
            RoutePreference.REMOTE_FIRST,
            RoutePreference.REMOTE_ONLY,
            RoutePreference.LOCAL_POSROUTER_KIOSK -> true
            else -> false
        }

    fun isLocalPosrouterKiosk(preference: String): Boolean =
        RoutePreference.normalize(preference) == RoutePreference.LOCAL_POSROUTER_KIOSK
}
