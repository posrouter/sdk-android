package com.posrouter.core.local

import com.posrouter.core.registry.AcquirerRouting
import java.util.concurrent.ConcurrentHashMap

internal enum class LocalReachState {
    UNKNOWN,
    REACHABLE,
    UNREACHABLE
}

internal data class CachedLocalReach(
    val state: LocalReachState,
    val preferredMethod: LocalLaunchMethod? = null
)

/**
 * In-memory reachability per acquirer registry code.
 * After a successful optimistic launch, subsequent calls skip probing and reuse the cached method.
 */
internal object LocalReachabilityCache {

    private val cache = ConcurrentHashMap<String, CachedLocalReach>()

    fun get(acquirerCode: String): CachedLocalReach =
        cache[acquirerCode.uppercase()] ?: CachedLocalReach(LocalReachState.UNKNOWN)

    fun shouldTryLocal(acquirerCode: String): Boolean =
        get(acquirerCode).state != LocalReachState.UNREACHABLE

    fun markReachable(acquirerCode: String, method: LocalLaunchMethod) {
        cache[acquirerCode.uppercase()] = CachedLocalReach(LocalReachState.REACHABLE, method)
    }

    fun markUnreachable(acquirerCode: String) {
        cache[acquirerCode.uppercase()] = CachedLocalReach(LocalReachState.UNREACHABLE)
    }

    fun invalidate(acquirerCode: String) {
        cache.remove(acquirerCode.uppercase())
    }
}
