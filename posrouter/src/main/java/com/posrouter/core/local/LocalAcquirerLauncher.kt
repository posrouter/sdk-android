package com.posrouter.core.local

import android.content.Context
import android.util.Log
import com.posrouter.POSRouterConfig
import com.posrouter.WirePaymentRequest
import com.posrouter.WireRefundRequest
import com.posrouter.core.registry.AcquirerRouting

/**
 * Registry-driven local launcher: optimistic startActivity (no resolveActivity / getPackageInfo).
 * Caches successful method per acquirer code to skip re-probing on subsequent calls.
 */
internal object LocalAcquirerLauncher {

    private const val TAG = "POSRouter.LocalLaunch"

    fun launchConnect(
        context: Context,
        config: POSRouterConfig,
        routing: AcquirerRouting
    ): LocalLaunchResult {
        tryCachedMethod(routing.code, cachedMethod = LocalReachabilityCache.get(routing.code).preferredMethod) {
            when (it) {
                LocalLaunchMethod.EXPLICIT_PACKAGE ->
                    LocalRouteExecutor.launchConnectViaExplicitIntent(context, config, routing)
                LocalLaunchMethod.DEEP_LINK ->
                    LocalRouteExecutor.launchConnectViaDeepLink(context, config, routing)
            }
        }?.let { return it }

        if (LocalRouteExecutor.launchConnectViaExplicitIntent(context, config, routing)) {
            return success(routing.code, LocalLaunchMethod.EXPLICIT_PACKAGE)
        }
        Log.w(TAG, "Explicit connect failed for ${routing.code}, falling back to deep link")

        if (LocalRouteExecutor.launchConnectViaDeepLink(context, config, routing)) {
            return success(routing.code, LocalLaunchMethod.DEEP_LINK)
        }

        LocalReachabilityCache.markUnreachable(routing.code)
        return LocalLaunchResult(false)
    }

    fun launchPay(
        context: Context,
        config: POSRouterConfig,
        routing: AcquirerRouting,
        request: WirePaymentRequest
    ): LocalLaunchResult {
        tryCachedMethod(routing.code, cachedMethod = LocalReachabilityCache.get(routing.code).preferredMethod) {
            when (it) {
                LocalLaunchMethod.EXPLICIT_PACKAGE ->
                    LocalRouteExecutor.launchPayViaExplicitIntent(context, config, routing, request)
                LocalLaunchMethod.DEEP_LINK ->
                    LocalRouteExecutor.launchPayViaDeepLink(context, config, routing, request)
            }
        }?.let { return it }

        if (LocalRouteExecutor.launchPayViaExplicitIntent(context, config, routing, request)) {
            return success(routing.code, LocalLaunchMethod.EXPLICIT_PACKAGE)
        }
        Log.w(TAG, "Explicit pay failed for ${routing.code}, falling back to deep link")

        if (LocalRouteExecutor.launchPayViaDeepLink(context, config, routing, request)) {
            return success(routing.code, LocalLaunchMethod.DEEP_LINK)
        }

        LocalReachabilityCache.markUnreachable(routing.code)
        return LocalLaunchResult(false)
    }

    fun launchRefund(
        context: Context,
        config: POSRouterConfig,
        routing: AcquirerRouting,
        request: WireRefundRequest
    ): LocalLaunchResult {
        tryCachedMethod(routing.code, cachedMethod = LocalReachabilityCache.get(routing.code).preferredMethod) {
            when (it) {
                LocalLaunchMethod.EXPLICIT_PACKAGE ->
                    LocalRouteExecutor.launchRefundViaExplicitIntent(context, config, routing, request)
                LocalLaunchMethod.DEEP_LINK ->
                    LocalRouteExecutor.launchRefundViaDeepLink(context, config, routing, request)
            }
        }?.let { return it }

        if (LocalRouteExecutor.launchRefundViaExplicitIntent(context, config, routing, request)) {
            return success(routing.code, LocalLaunchMethod.EXPLICIT_PACKAGE)
        }
        Log.w(TAG, "Explicit refund failed for ${routing.code}, falling back to deep link")

        if (LocalRouteExecutor.launchRefundViaDeepLink(context, config, routing, request)) {
            return success(routing.code, LocalLaunchMethod.DEEP_LINK)
        }

        LocalReachabilityCache.markUnreachable(routing.code)
        return LocalLaunchResult(false)
    }

    // Void：拉起收单端真停在途收款的硬件（停 NFC/EMV + 回 CANCEL）。规范 level-1 §5.3。
    fun launchVoid(
        context: Context,
        config: POSRouterConfig,
        routing: AcquirerRouting,
        orderId: String,
        attemptId: String?,
        reason: String
    ): LocalLaunchResult {
        tryCachedMethod(routing.code, cachedMethod = LocalReachabilityCache.get(routing.code).preferredMethod) {
            when (it) {
                LocalLaunchMethod.EXPLICIT_PACKAGE ->
                    LocalRouteExecutor.launchVoidViaExplicitIntent(context, config, routing, orderId, attemptId, reason)
                LocalLaunchMethod.DEEP_LINK ->
                    LocalRouteExecutor.launchVoidViaDeepLink(context, config, routing, orderId, attemptId, reason)
            }
        }?.let { return it }

        if (LocalRouteExecutor.launchVoidViaExplicitIntent(context, config, routing, orderId, attemptId, reason)) {
            return success(routing.code, LocalLaunchMethod.EXPLICIT_PACKAGE)
        }
        Log.w(TAG, "Explicit void failed for ${routing.code}, falling back to deep link")

        if (LocalRouteExecutor.launchVoidViaDeepLink(context, config, routing, orderId, attemptId, reason)) {
            return success(routing.code, LocalLaunchMethod.DEEP_LINK)
        }

        LocalReachabilityCache.markUnreachable(routing.code)
        return LocalLaunchResult(false)
    }

    private inline fun tryCachedMethod(
        acquirerCode: String,
        cachedMethod: LocalLaunchMethod?,
        attempt: (LocalLaunchMethod) -> Boolean
    ): LocalLaunchResult? {
        val method = cachedMethod ?: return null
        if (attempt(method)) {
            return LocalLaunchResult(true, method)
        }
        Log.w(TAG, "Cached $method launch failed for $acquirerCode, retrying full chain")
        LocalReachabilityCache.invalidate(acquirerCode)
        return null
    }

    private fun success(code: String, method: LocalLaunchMethod): LocalLaunchResult {
        LocalReachabilityCache.markReachable(code, method)
        return LocalLaunchResult(true, method)
    }
}
