package com.posrouter

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import com.posrouter.core.lensing.LensingProtocolEngine
import com.posrouter.core.lensing.LensingState
import com.posrouter.core.lensing.PendingConnectRegistry
import com.posrouter.core.lensing.PaymentAttemptIdResolver
import com.posrouter.core.lensing.PaymentAttemptKey
import com.posrouter.core.lensing.PaymentAttemptRegistry
import com.posrouter.core.lensing.PaymentClaimRegistry
import com.posrouter.core.lensing.PaymentResultDispatcher
import com.posrouter.core.lensing.PaymentResultSource
import com.posrouter.core.lensing.PaymentVoidRequest
import com.posrouter.core.lensing.RefundAttemptIdResolver
import com.posrouter.core.lensing.RefundAttemptRegistry
import com.posrouter.core.lensing.TerminalEventDispatcher
import com.posrouter.core.lensing.VoidedAttemptRegistry
import com.posrouter.core.local.AcquirerCallbackParser
import com.posrouter.core.local.LocalAcquirerLauncher
import com.posrouter.core.local.LocalLaunchMethod
import com.posrouter.core.local.RoutePreferencePolicy
import com.posrouter.core.registry.AcquirerRegistry
import com.posrouter.terminal.LensingTerminalService
import com.posrouter.terminal.PendingRemotePayStore
import com.posrouter.terminal.TerminalModeStore
import com.posrouter.terminal.TerminalOverlayWake
import com.posrouter.terminal.TerminalTaskRegistry
import com.posrouter.terminal.TerminalUiForegroundTracker

object POSRouter {

    fun initialize(context: Context, config: POSRouterConfig) {
        LensingContextHolder.applicationContext = context.applicationContext
        LensingContextHolder.config = config
        LensingContextHolder.routePreference = RoutePreference.AUTO
        if (config.terminalMode) {
            require(!config.terminalLaunchActivityClass.isNullOrBlank()) {
                "terminalLaunchActivityClass is required when terminalMode is true"
            }
            TerminalModeStore.setEnabled(context.applicationContext, true)
            LensingTerminalService.start(context.applicationContext)
        } else {
            TerminalModeStore.setEnabled(context.applicationContext, false)
            LensingProtocolEngine.start(config)
        }
    }

    /** Sets routing preference for [connect], [pay], and [refund]. Unknown values become [RoutePreference.AUTO]. */
    fun setRoutePreference(routePreference: String) {
        LensingContextHolder.routePreference = RoutePreference.normalize(routePreference)
    }

    fun getRoutePreference(): String = LensingContextHolder.routePreference

    fun connect(
        activity: Activity,
        callback: POSRouterCallback,
        routePreference: String? = null
    ) {
        routePreference?.let { setRoutePreference(it) }
        val config = requireConfig()
        val routing = AcquirerRegistry.resolve(config)
        val preference = LensingContextHolder.routePreference

        if (!RoutePreferencePolicy.skipsLocalAttempt(preference) &&
            RoutePreferencePolicy.shouldTryLocal(preference, routing.code)
        ) {
            val launch = LocalAcquirerLauncher.launchConnect(activity, config, routing)
            if (launch.success) {
                val method = requireNotNull(launch.method) { "Successful local launch must include method" }
                callback.onResult(connectResult(config, method))
                return
            }
        }

        if (!RoutePreferencePolicy.shouldFallbackToRemote(preference)) {
            callback.onError(
                POSRouterError("LOCAL_ACQUIRER_UNAVAILABLE", "Local acquirer is not available on this device")
            )
            return
        }

        when (LensingProtocolEngine.currentState()) {
            LensingState.CONNECTED -> callback.onResult(networkConnectResult(config))
            LensingState.DISCOVERING, LensingState.CONNECTING, LensingState.RECONNECTING ->
                PendingConnectRegistry.enqueue(callback)
            LensingState.FAILED ->
                callback.onError(POSRouterError("CONNECT_FAILED", "Lensing engine connection failed"))
            LensingState.IDLE ->
                callback.onError(POSRouterError("NOT_INITIALIZED", "Call initialize() first"))
        }
    }

    fun pay(
        activity: Activity,
        request: PaymentRequest,
        callback: POSRouterCallback,
        routePreference: String? = null
    ) {
        routePreference?.let { setRoutePreference(it) }
        val config = requireConfig()
        val resolvedAttemptId = PaymentAttemptIdResolver.resolve(request.orderId, request.attemptId)
        val routing = AcquirerRegistry.resolve(config, request.attemptCode)
        val wire = request.toWire(config, routing, resolvedAttemptId)
        val preference = LensingContextHolder.routePreference

        if (PaymentClaimRegistry.isClaimed(wire.terminalId, wire.orderId, wire.attemptId)) {
            callback.onError(
                POSRouterError("ALREADY_CLAIMED", "Payment UI already claimed for order ${wire.orderId}")
            )
            return
        }

        // Terminal method selection is rendered on a remote terminal (NATS), not a local acquirer deeplink.
        if (PaymentRequest.requiresTerminalMethodSelection(request.method)) {
            if (RoutePreference.normalize(preference) == RoutePreference.LOCAL_ONLY) {
                callback.onError(
                    POSRouterError(
                        "LOCAL_TERMINAL_REQUIRED",
                        "Method selection requires a remote terminal or kiosk deeplink"
                    )
                )
                return
            }
            LensingProtocolEngine.dispatchTransaction(wire, callback)
            return
        }

        if (!RoutePreferencePolicy.skipsLocalAttempt(preference) &&
            RoutePreferencePolicy.shouldTryLocal(preference, routing.code)
        ) {
            if (!PaymentClaimRegistry.tryAcquireClaim(wire.terminalId, wire.orderId, wire.attemptId)) {
                callback.onError(
                    POSRouterError("ALREADY_CLAIMED", "Payment UI already claimed for order ${wire.orderId}")
                )
                return
            }

            val launch = LocalAcquirerLauncher.launchPay(activity, config, routing, wire)
            if (launch.success) {
                LensingProtocolEngine.publishClaimed(wire)
                PaymentClaimRegistry.releaseClaim(wire.terminalId, wire.orderId, wire.attemptId)
                PaymentAttemptRegistry.store(wire, callback)
                return
            }

            PaymentClaimRegistry.releaseClaim(wire.terminalId, wire.orderId, wire.attemptId)
        }

        if (!RoutePreferencePolicy.shouldFallbackToRemote(preference)) {
            callback.onError(
                POSRouterError("LOCAL_ACQUIRER_UNAVAILABLE", "Local acquirer is not available on this device")
            )
            return
        }

        LensingProtocolEngine.dispatchTransaction(wire, callback)
    }

    fun refund(
        activity: Activity,
        request: RefundRequest,
        callback: POSRouterCallback,
        routePreference: String? = null
    ) {
        routePreference?.let { setRoutePreference(it) }
        val config = requireConfig()
        val resolvedAttemptId = RefundAttemptIdResolver.resolve(request.orderId, request.attemptId)
        val routing = AcquirerRegistry.resolve(config, request.attemptCode)
        val wire = request.toWire(config, routing, resolvedAttemptId)
        val preference = LensingContextHolder.routePreference

        if (!RoutePreferencePolicy.skipsLocalAttempt(preference) &&
            RoutePreferencePolicy.shouldTryLocal(preference, routing.code)
        ) {
            val launch = LocalAcquirerLauncher.launchRefund(activity, config, routing, wire)
            if (launch.success) {
                RefundAttemptRegistry.store(wire, callback)
                return
            }
        }

        if (!RoutePreferencePolicy.shouldFallbackToRemote(preference)) {
            callback.onError(
                POSRouterError("LOCAL_ACQUIRER_UNAVAILABLE", "Local acquirer is not available on this device")
            )
            return
        }

        LensingProtocolEngine.dispatchRefund(wire, callback)
    }

    /** Clears routing claim only; does not cancel a pending pay callback. */
    fun releasePaymentClaim(orderId: String, attemptId: String? = null) {
        val config = LensingContextHolder.config ?: return
        if (attemptId != null) {
            PaymentClaimRegistry.releaseClaim(config.terminalId, orderId, attemptId)
            return
        }
        PaymentAttemptRegistry.lookupOpenByOrder(config.terminalId, orderId)?.let { wire ->
            PaymentClaimRegistry.releaseClaim(wire.terminalId, wire.orderId, wire.attemptId)
        }
    }

    /** Cancels a pending [pay] callback locally without notifying the remote terminal. */
    fun cancelPendingPayment(orderId: String, attemptId: String? = null) {
        val config = LensingContextHolder.config ?: return
        if (attemptId != null) {
            PaymentAttemptRegistry.cancel(config.terminalId, orderId, attemptId)
            PaymentClaimRegistry.releaseClaim(config.terminalId, orderId, attemptId)
        } else {
            PaymentAttemptRegistry.cancelLatestOpen(config.terminalId, orderId)
            releasePaymentClaim(orderId)
        }
    }

    /**
     * Void an in-flight payment on the remote terminal (soft void — no forced acquirer exit).
     * Publishes [LensingSubjects.voidSubject] and keeps the local [pay] callback until the
     * terminal acks with a cancelled [PaymentResult] (`cancelReason=initiator_void`).
     */
    fun voidPayment(orderId: String, attemptId: String? = null): Boolean {
        val config = LensingContextHolder.config ?: return false
        val wire = when {
            attemptId != null -> PaymentAttemptRegistry.lookup(
                PaymentAttemptKey(config.terminalId, orderId, attemptId)
            )
            else -> PaymentAttemptRegistry.lookupOpenByOrder(config.terminalId, orderId)
        } ?: return false

        val voidReq = PaymentVoidRequest(
            acquirerCode = wire.acquirerCode,
            merchantId = wire.merchantId,
            subMerchantId = wire.subMerchantId,
            terminalId = wire.terminalId,
            orderId = wire.orderId,
            attemptId = wire.attemptId
        )
        return LensingProtocolEngine.publishVoid(voidReq)
    }

    /**
     * Handle acquirer callback URI (e.g. posrouter-kiosk://pay_result?status=SUCCESS&orderid=...&type=PAY).
     * Delivers to a pending [pay] callback on this device and publishes to NATS for remote initiators.
     */
    fun deliverAcquirerCallback(uri: Uri): PaymentResult? {
        val config = LensingContextHolder.config ?: return null

        AcquirerCallbackParser.parseRefundCallback(uri, config)?.let { parsed ->
            val enriched = parsed.copy(
                metadata = parsed.metadata + ("operation" to "refund")
            )
            PaymentResultDispatcher.deliver(enriched, PaymentResultSource.LOCAL_CALLBACK)
            return enriched
        }

        val orderId = uri.getQueryParameter("orderid")
            ?: uri.getQueryParameter("orderId")
            ?: return null
        val session = PaymentAttemptRegistry.lookupOpenByOrder(config.terminalId, orderId)
        val attemptId = session?.attemptId
            ?: uri.getQueryParameter("attemptid")
            ?: uri.getQueryParameter("attemptId")
        if (attemptId != null &&
            VoidedAttemptRegistry.isVoided(config.terminalId, orderId, attemptId)
        ) {
            android.util.Log.i(
                "POSRouter",
                "Ignoring acquirer callback for voided attempt order=$orderId attempt=$attemptId"
            )
            return null
        }

        var result = AcquirerCallbackParser.parsePayCallback(uri, config, session) ?: return null

        if (result.status == PaymentStatus.CANCELLED &&
            !result.metadata.containsKey("cancelReason")
        ) {
            result = result.copy(
                metadata = result.metadata + ("cancelReason" to PaymentCancelReason.USER_CANCEL)
            )
        }

        PaymentResultDispatcher.deliver(result, PaymentResultSource.LOCAL_CALLBACK)
        return result
    }

    /**
     * Parses an acquirer reverse callback without publishing or dispatching terminal events.
     * Scheme-agnostic: any registered terminal app may use its own scheme with host [pay_result].
     */
    fun parseAcquirerCallback(uri: Uri): PaymentResult? {
        val config = LensingContextHolder.config ?: return null
        AcquirerCallbackParser.parseRefundCallback(uri, config)?.let { return it }
        val orderId = uri.getQueryParameter("orderid")
            ?: uri.getQueryParameter("orderId")
            ?: return null
        val session = PaymentAttemptRegistry.lookupOpenByOrder(config.terminalId, orderId)
        return AcquirerCallbackParser.parsePayCallback(uri, config, session)
    }

    /**
     * Registers a terminal-side pay attempt awaiting method selection (no acquirer launch).
     * Used when payment is initiated via terminal deeplink (e.g. posrouter-kiosk://charge).
     */
    fun registerTerminalPaySelection(request: PaymentRequest): Boolean {
        val config = requireConfig()
        val resolvedAttemptId = PaymentAttemptIdResolver.resolve(request.orderId, request.attemptId)
        val routing = AcquirerRegistry.resolve(config, request.attemptCode)
        val wire = request.toWire(config, routing, resolvedAttemptId).copy(
            method = PaymentRequest.METHOD_SELECTION
        )
        if (PaymentClaimRegistry.isClaimed(wire.terminalId, wire.orderId, wire.attemptId)) {
            return false
        }
        PaymentAttemptRegistry.store(wire, callback = null)
        return true
    }

    /**
     * Publish a payment result to NATS when no acquirer deeplink callback is available.
     * Dedupes by (terminalId, orderId, attemptId).
     */
    fun publishPaymentResult(result: PaymentResult): Boolean {
        val config = LensingContextHolder.config
        val enriched = when {
            result.terminalId.isNotBlank() -> result
            config != null -> result.copy(terminalId = config.terminalId)
            else -> result
        }
        return PaymentResultDispatcher.deliver(
            enriched,
            PaymentResultSource.MANUAL_PUBLISH,
            publishNats = true,
            dispatchTerminal = true
        )
    }

    fun setTerminalListener(listener: POSRouterTerminalListener?) {
        TerminalEventDispatcher.listener = listener
        listener?.onLensingStateChanged(LensingProtocolEngine.currentState().toLensingConnectionState())
        listener?.let { PendingRemotePayStore.drain(it) }
    }

    /**
     * B-side terminal UI visibility. Call from Activity [android.app.Activity.onStart] / [android.app.Activity.onStop]
     * so remote pays skip alert notifications while the terminal screen is already visible.
     */
    fun setTerminalUiForeground(foreground: Boolean) {
        if (LensingContextHolder.config?.terminalMode != true) return
        TerminalUiForegroundTracker.isForeground = foreground
    }

    /**
     * Report the terminal Activity task id (call from [android.app.Activity.onResume]) so background
     * remote pays can [android.app.ActivityManager.moveTaskToFront] without a cold start.
     */
    fun reportTerminalTaskId(taskId: Int) {
        if (LensingContextHolder.config?.terminalMode != true) return
        TerminalTaskRegistry.taskId = taskId
    }

    /** Whether the app can start the terminal UI from background (requires Display over other apps). */
    fun canLaunchTerminalFromBackground(context: Context): Boolean =
        LensingContextHolder.config?.terminalMode == true &&
            TerminalOverlayWake.isPrivileged(context.applicationContext)

    /** Opens system settings for Display over other apps — required on most devices for background wake. */
    fun openTerminalOverlayPermissionSettings(context: Context) {
        if (LensingContextHolder.config?.terminalMode != true) return
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    fun currentLensingState(): LensingConnectionState =
        LensingProtocolEngine.currentState().toLensingConnectionState()

    /** Canonical ARGB color for a Lensing status indicator (see [LensingConnectionIndicator]). */
    fun lensingIndicatorColor(state: LensingConnectionState = currentLensingState()): Int =
        LensingConnectionIndicator.colorArgb(state)

    /**
     * Re-runs Gateway discovery and opens a fresh NATS session.
     * Use from Setup / Reconnect when the UI is stuck non-CONNECTED.
     */
    fun reconnectLensing() {
        val config = LensingContextHolder.config ?: return
        LensingProtocolEngine.start(config, force = true)
    }

    /**
     * After returning from background, pass [backgroundMs] from [android.os.SystemClock.elapsedRealtime].
     * Refreshes when the socket is dead while state is CONNECTED, or after long idle while not CONNECTED.
     */
    fun refreshLensingConnection(backgroundMs: Long = 0L) {
        LensingProtocolEngine.refreshConnectionIfNeeded(force = false, backgroundMs = backgroundMs)
    }

    /**
     * Launches the local acquirer for an in-flight terminal pay (e.g. after the user picks a method).
     * Returns false when no open attempt exists for [orderId] or the acquirer could not be opened.
     */
    fun launchPendingLocalPay(context: Context, orderId: String, method: String): Boolean {
        val config = LensingContextHolder.config ?: return false
        val wire = PaymentAttemptRegistry.lookupOpenByOrder(config.terminalId, orderId) ?: return false
        val routing = AcquirerRegistry.resolve(config, wire.attemptCode)
        val launch = LocalAcquirerLauncher.launchPay(
            context,
            config,
            routing,
            wire.copy(method = method)
        )
        if (!launch.success) {
            PaymentAttemptRegistry.close(wire.terminalId, wire.orderId, wire.attemptId)
            com.posrouter.core.lensing.PaymentClaimRegistry.releaseClaim(
                wire.terminalId,
                wire.orderId,
                wire.attemptId
            )
            TerminalEventDispatcher.dispatchRemotePaymentLaunchFailed(
                orderId,
                "Could not launch local acquirer"
            )
        }
        return launch.success
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
    var routePreference: String = RoutePreference.AUTO
}
