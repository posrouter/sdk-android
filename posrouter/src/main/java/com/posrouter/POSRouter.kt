package com.posrouter

import android.app.Activity
import android.content.Context
import com.posrouter.core.lensing.LensingProtocolEngine
import com.posrouter.core.lensing.LensingState
import com.posrouter.core.lensing.PaymentClaimRegistry
import com.posrouter.core.local.LocalAcquirerLauncher
import com.posrouter.core.local.LocalLaunchMethod
import com.posrouter.core.local.LocalReachabilityCache
import com.posrouter.core.registry.AcquirerRegistry

object POSRouter {

    fun initialize(context: Context, config: POSRouterConfig) {
        LensingContextHolder.applicationContext = context.applicationContext
        LensingContextHolder.config = config
        LensingProtocolEngine.start(config)
    }

    fun connect(activity: Activity, callback: POSRouterCallback) {
        val config = requireConfig()
        val routing = AcquirerRegistry.resolve(config)

        if (LocalReachabilityCache.shouldTryLocal(routing.code)) {
            val launch = LocalAcquirerLauncher.launchConnect(activity, config, routing)
            if (launch.success) {
                val method = requireNotNull(launch.method) { "Successful local launch must include method" }
                callback.onResult(connectResult(config, method))
                return
            }
        }

        when (LensingProtocolEngine.currentState()) {
            LensingState.CONNECTED -> callback.onResult(networkConnectResult(config))
            LensingState.DISCOVERING, LensingState.CONNECTING, LensingState.RECONNECTING ->
                callback.onError(POSRouterError("CONNECTING", "Lensing engine still connecting"))
            LensingState.FAILED ->
                callback.onError(POSRouterError("CONNECT_FAILED", "Lensing engine connection failed"))
            LensingState.IDLE ->
                callback.onError(POSRouterError("NOT_INITIALIZED", "Call initialize() first"))
        }
    }

    fun pay(activity: Activity, request: PaymentRequest, callback: POSRouterCallback) {
        val config = requireConfig()
        val routing = AcquirerRegistry.resolve(config)
        val wire = request.toWire(config, routing)

        if (PaymentClaimRegistry.isClaimed(request.terminalId, request.orderId)) {
            callback.onError(
                POSRouterError("ALREADY_CLAIMED", "Payment UI already claimed for order ${request.orderId}")
            )
            return
        }

        if (LocalReachabilityCache.shouldTryLocal(routing.code)) {
            if (!PaymentClaimRegistry.tryAcquireClaim(request.terminalId, request.orderId)) {
                callback.onError(
                    POSRouterError("ALREADY_CLAIMED", "Payment UI already claimed for order ${request.orderId}")
                )
                return
            }

            val launch = LocalAcquirerLauncher.launchPay(activity, config, routing, wire)
            if (launch.success) {
                LensingProtocolEngine.publishClaimed(request.terminalId, request.orderId)
                PaymentClaimRegistry.releaseClaim(request.terminalId, request.orderId)
                callback.onResult(
                    PaymentResult(
                        terminalId = request.terminalId,
                        status = PaymentStatus.APPROVED,
                        transactionId = null,
                        amount = request.amount,
                        currency = config.currency,
                        message = localLaunchMessage(launch.method, "pay"),
                        localRouteMethod = launch.method?.toPublicRouteMethod()
                    )
                )
                return
            }

            PaymentClaimRegistry.releaseClaim(request.terminalId, request.orderId)
        }

        LensingProtocolEngine.dispatchTransaction(wire, callback)
    }

    /** Clears an in-flight payment claim after callback or user cancel (same order retry). */
    fun releasePaymentClaim(orderId: String) {
        val config = LensingContextHolder.config ?: return
        PaymentClaimRegistry.releaseClaim(config.terminalId, orderId)
    }

    private fun connectResult(config: POSRouterConfig, method: LocalLaunchMethod) = PaymentResult(
        terminalId = config.terminalId,
        status = PaymentStatus.APPROVED,
        transactionId = null,
        amount = 0,
        currency = config.currency,
        message = localLaunchMessage(method, "connect"),
        localRouteMethod = method.toPublicRouteMethod()
    )

    private fun networkConnectResult(config: POSRouterConfig) = PaymentResult(
        terminalId = config.terminalId,
        status = PaymentStatus.APPROVED,
        transactionId = null,
        amount = 0,
        currency = config.currency,
        message = "Network track connected",
        localRouteMethod = LocalRouteMethod.NETWORK
    )

    private fun localLaunchMessage(method: LocalLaunchMethod?, action: String): String? = when (method) {
        LocalLaunchMethod.EXPLICIT_PACKAGE -> "Local $action launched via explicit intent"
        LocalLaunchMethod.DEEP_LINK -> "Local $action launched via deep link"
        null -> null
    }

    private fun LocalLaunchMethod.toPublicRouteMethod(): LocalRouteMethod = when (this) {
        LocalLaunchMethod.EXPLICIT_PACKAGE -> LocalRouteMethod.EXPLICIT_INTENT
        LocalLaunchMethod.DEEP_LINK -> LocalRouteMethod.DEEP_LINK
    }

    private fun requireConfig(): POSRouterConfig =
        LensingContextHolder.config
            ?: throw IllegalStateException("Call POSRouter.initialize() before connect() or pay()")
}

internal object LensingContextHolder {
    var applicationContext: Context? = null
    var config: POSRouterConfig? = null
}
