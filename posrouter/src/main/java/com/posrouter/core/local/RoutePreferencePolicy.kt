package com.posrouter.core.local

import com.posrouter.RoutePreference

internal object RoutePreferencePolicy {

    fun shouldTryLocal(preference: String, acquirerCode: String): Boolean =
        when (RoutePreference.normalize(preference)) {
            RoutePreference.REMOTE_FIRST, RoutePreference.REMOTE_ONLY -> false
            RoutePreference.LOCAL_FIRST, RoutePreference.LOCAL_ONLY -> true
            RoutePreference.AUTO -> LocalReachabilityCache.shouldTryLocal(acquirerCode)
            else -> LocalReachabilityCache.shouldTryLocal(acquirerCode)
        }

    fun shouldFallbackToRemote(preference: String): Boolean =
        when (RoutePreference.normalize(preference)) {
            RoutePreference.LOCAL_ONLY -> false
            else -> true
        }

    fun skipsLocalAttempt(preference: String): Boolean =
        when (RoutePreference.normalize(preference)) {
            RoutePreference.REMOTE_FIRST, RoutePreference.REMOTE_ONLY -> true
            else -> false
        }
}
