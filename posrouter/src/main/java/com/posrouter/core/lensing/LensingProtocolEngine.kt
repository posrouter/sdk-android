package com.posrouter.core.lensing

import android.util.Log
import com.posrouter.LensingContextHolder
import com.posrouter.POSRouterConfig
import com.posrouter.WirePaymentRequest
import com.posrouter.WireRefundRequest
import com.posrouter.core.local.LocalAcquirerLauncher
import com.posrouter.core.local.LocalReachabilityCache
import com.posrouter.core.registry.AcquirerRegistry
import io.nats.client.Connection
import io.nats.client.Dispatcher
import io.nats.client.Nats
import io.nats.client.Options
import com.posrouter.POSRouterCallback
import com.posrouter.POSRouterError
import com.posrouter.PaymentResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.min

internal object LensingProtocolEngine {
    private const val TAG = "POSRouter.Lensing"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mainScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var natsConnection: Connection? = null
    private var dispatcher: Dispatcher? = null
    private var state: LensingState = LensingState.IDLE
    private var subscriptionScope: LensingSubjectScope? = null

    private val fallbackQueue = ConcurrentLinkedQueue<QueuedMessage>()
    private var reconnectAttempt = 0
    private const val MAX_BACKOFF_MS = 30_000L
    /** Suppress marking claims from our own NATS publish (subscriber echo). */
    private val ownClaimedEchoKeys = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private val subscribedResultScopes = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    fun start(config: POSRouterConfig) {
        subscriptionScope = LensingSubjectScope.fromConfig(config)
        state = LensingState.DISCOVERING
        TerminalEventDispatcher.dispatchNatsState(state)

        scope.launch {
            try {
                val credentials = LensingGatewayClient.fetchNatsCredentials(
                    config.participantCode,
                    config.participantKey,
                    GatewayEndpoints.initUrl(config)
                )
                AcquirerRegistry.prefetch(config)
                connectNats(
                    credentials.natsUrl,
                    credentials.natsToken,
                    config.participantCode,
                    LensingSubjectScope.fromConfig(config)
                )
            } catch (e: Exception) {
                state = LensingState.FAILED
                TerminalEventDispatcher.dispatchNatsState(state)
                Log.e(TAG, "Gateway discovery failed", e)
            }
        }
    }

    private fun connectNats(
        url: String,
        token: String,
        participantCode: String,
        subjectScope: LensingSubjectScope
    ) {
        subscriptionScope = subjectScope
        state = LensingState.CONNECTING
        TerminalEventDispatcher.dispatchNatsState(state)
        try {
            val options = Options.builder()
                .server(url)
                .userInfo(participantCode, token)
                .connectionListener { _, type ->
                    when (type) {
                        io.nats.client.ConnectionListener.Events.DISCONNECTED -> {
                            state = LensingState.RECONNECTING
                            TerminalEventDispatcher.dispatchNatsState(state)
                            scheduleReconnect(url, token, participantCode, subjectScope)
                        }
                        io.nats.client.ConnectionListener.Events.RECONNECTED -> {
                            state = LensingState.CONNECTED
                            TerminalEventDispatcher.dispatchNatsState(state)
                            reconnectAttempt = 0
                            flushFallbackQueue()
                        }
                        io.nats.client.ConnectionListener.Events.CONNECTED -> {
                            state = LensingState.CONNECTED
                            TerminalEventDispatcher.dispatchNatsState(state)
                        }
                        else -> Unit
                    }
                }
                .build()

            natsConnection = Nats.connect(options)
            dispatcher = natsConnection?.createDispatcher { }
            state = LensingState.CONNECTED
            TerminalEventDispatcher.dispatchNatsState(state)
            reconnectAttempt = 0
            setupTerminalSubscriptions(subjectScope)
            flushFallbackQueue()
        } catch (e: Exception) {
            state = LensingState.RECONNECTING
            TerminalEventDispatcher.dispatchNatsState(state)
            scheduleReconnect(url, token, participantCode, subjectScope)
        }
    }

    private fun scheduleReconnect(
        url: String,
        token: String,
        participantCode: String,
        subjectScope: LensingSubjectScope
    ) {
        scope.launch {
            val delayMs = min(MAX_BACKOFF_MS, 1000L * (1 shl reconnectAttempt))
            reconnectAttempt++
            kotlinx.coroutines.delay(delayMs)
            connectNats(url, token, participantCode, subjectScope)
        }
    }

    fun currentState(): LensingState = state

    fun publishClaimed(wire: WirePaymentRequest) {
        val claim = PaymentClaim(wire.terminalId, wire.orderId, wire.attemptId)
        PaymentClaimRegistry.markClaimed(claim)
        val echoKey = PaymentAttemptKey(wire.terminalId, wire.orderId, wire.attemptId).storageKey()
        ownClaimedEchoKeys.add(echoKey)
        publishToSubject(
            LensingSubjects.claimedSubject(LensingSubjectScope.fromWire(wire)),
            claim.toJsonString()
        )
    }

    fun dispatchTransaction(request: WirePaymentRequest, callback: POSRouterCallback) {
        val targetSubject = LensingSubjects.paySubject(LensingSubjectScope.fromWire(request))
        val payloadBytes = request.toJsonString().toByteArray(Charsets.UTF_8)

        val connection = natsConnection
        if (connection == null || state != LensingState.CONNECTED) {
            fallbackQueue.add(QueuedMessage(targetSubject, payloadBytes, request, callback))
            if (state == LensingState.IDLE || state == LensingState.FAILED) {
                callback.onError(POSRouterError("NOT_INITIALIZED", "Lensing engine not connected"))
            }
            return
        }

        PaymentAttemptRegistry.store(request, callback)

        ensureSubscriptionsForWire(request)

        try {
            connection.publish(targetSubject, payloadBytes)
            Log.i(TAG, "Pay dispatched on NATS for order ${request.orderId} attempt ${request.attemptId}")
        } catch (e: Exception) {
            PaymentAttemptRegistry.close(request.terminalId, request.orderId, request.attemptId)
            fallbackQueue.add(QueuedMessage(targetSubject, payloadBytes, request, callback))
            callback.onError(POSRouterError("PUBLISH_FAILED", e.message ?: "Failed to publish"))
        }
    }

    fun dispatchRefund(request: WireRefundRequest, callback: POSRouterCallback) {
        val targetSubject = LensingSubjects.refundSubject(request.subjectScope())
        val payloadBytes = request.toJsonString().toByteArray(Charsets.UTF_8)

        val connection = natsConnection
        if (connection == null || state != LensingState.CONNECTED) {
            if (state == LensingState.IDLE || state == LensingState.FAILED) {
                callback.onError(POSRouterError("NOT_INITIALIZED", "Lensing engine not connected"))
            } else {
                RefundAttemptRegistry.store(request, callback)
                callback.onError(POSRouterError("CONNECTING", "Refund queued until NATS reconnects"))
            }
            return
        }

        RefundAttemptRegistry.store(request, callback)

        try {
            connection.publish(targetSubject, payloadBytes)
            Log.i(TAG, "Refund dispatched on NATS for order ${request.orderId} attempt ${request.attemptId}")
        } catch (e: Exception) {
            RefundAttemptRegistry.close(request)
            callback.onError(POSRouterError("PUBLISH_FAILED", e.message ?: "Failed to publish refund"))
        }
    }

    fun publishPaymentResult(result: PaymentResult) {
        val subjectScope = resolveResultScope(result) ?: return
        publishToSubject(LensingSubjects.resultSubject(subjectScope), result.toJsonString())
        Log.i(TAG, "Payment result published for order ${result.orderId} attempt=${result.attemptId} status=${result.status}")
    }

    private fun resolveResultScope(result: PaymentResult): LensingSubjectScope? {
        val config = LensingContextHolder.config
        val tid = result.terminalId.ifBlank { config?.terminalId.orEmpty() }
        if (tid.isBlank()) return null
        val orderId = result.orderId
        val attemptId = result.attemptId
        if (orderId != null && attemptId != null) {
            PaymentAttemptRegistry.lookup(PaymentAttemptKey(tid, orderId, attemptId))?.let {
                return LensingSubjectScope.fromWire(it)
            }
            RefundAttemptRegistry.lookup(tid, orderId, attemptId)?.let {
                return it.subjectScope()
            }
        }
        val cfg = config ?: return null
        return LensingSubjectScope(
            acquirerCode = cfg.acquirerCode,
            merchantId = cfg.merchantId,
            subMerchantId = result.subMerchantId ?: cfg.subMerchantId,
            terminalId = tid
        )
    }

    fun publishVoid(request: PaymentVoidRequest): Boolean {
        val connection = natsConnection
        if (connection == null || state != LensingState.CONNECTED) {
            Log.w(TAG, "Cannot publish void: NATS not connected")
            return false
        }
        VoidedAttemptRegistry.mark(request.terminalId, request.orderId, request.attemptId)
        return try {
            publishToSubject(LensingSubjects.voidSubject(request.subjectScope()), request.toJsonString())
            Log.i(
                TAG,
                "Void published for order ${request.orderId} attempt ${request.attemptId}"
            )
            true
        } catch (e: Exception) {
            Log.w(TAG, "Failed to publish void for order ${request.orderId}", e)
            false
        }
    }

    internal fun acknowledgeInitiatorVoid(voidReq: PaymentVoidRequest, session: WirePaymentRequest?) {
        val config = LensingContextHolder.config
        val result = PaymentResult(
            terminalId = voidReq.terminalId,
            orderId = voidReq.orderId,
            attemptId = voidReq.attemptId,
            attemptCode = session?.attemptCode,
            status = com.posrouter.PaymentStatus.CANCELLED,
            transactionId = null,
            amount = session?.amount ?: 0L,
            currency = session?.currency ?: config?.currency.orEmpty(),
            message = "Voided by initiator",
            metadata = mapOf("cancelReason" to com.posrouter.PaymentCancelReason.INITIATOR_VOID)
        )
        PaymentResultDispatcher.deliver(
            result,
            PaymentResultSource.VOID_ACK,
            dispatchTerminal = false
        )
        TerminalEventDispatcher.dispatchRemotePaymentVoided(
            voidReq.orderId,
            voidReq.attemptId,
            result.message
        )
        Log.i(TAG, "Void ack published for order ${voidReq.orderId} attempt ${voidReq.attemptId}")
    }

    private fun setupTerminalSubscriptions(subjectScope: LensingSubjectScope) {
        val connection = natsConnection ?: return
        val subDispatcher = dispatcher ?: connection.createDispatcher { }.also { dispatcher = it }

        subDispatcher.subscribe(LensingSubjects.claimedSubject(subjectScope)) { msg ->
            PaymentClaim.fromJson(String(msg.data, Charsets.UTF_8))?.let { claim ->
                val echoKey = PaymentAttemptKey(claim.terminalId, claim.orderId, claim.attemptId)
                    .storageKey()
                if (ownClaimedEchoKeys.remove(echoKey)) {
                    Log.d(TAG, "Ignored own claimed echo for order ${claim.orderId}")
                    return@let
                }
                PaymentClaimRegistry.markClaimed(claim)
                Log.i(TAG, "Payment UI claimed by remote for order ${claim.orderId} attempt ${claim.attemptId}")
            }
        }

        subDispatcher.subscribe(LensingSubjects.paySubject(subjectScope)) { msg ->
            handleIncomingPay(String(msg.data, Charsets.UTF_8))
        }

        subDispatcher.subscribe(LensingSubjects.resultSubject(subjectScope)) { msg ->
            handleIncomingResult(String(msg.data, Charsets.UTF_8))
        }

        subDispatcher.subscribe(LensingSubjects.voidSubject(subjectScope)) { msg ->
            handleIncomingVoid(String(msg.data, Charsets.UTF_8))
        }

        subDispatcher.subscribe(LensingSubjects.refundSubject(subjectScope)) { msg ->
            handleIncomingRefund(String(msg.data, Charsets.UTF_8))
        }

        subscribedResultScopes.add(resultScopeKey(subjectScope))
    }

    /** Initiator must listen on the pay wire namespace (V1.6 subject) for remote `.result`. */
    private fun ensureSubscriptionsForWire(wire: WirePaymentRequest) {
        val scope = LensingSubjectScope.fromWire(wire)
        val key = resultScopeKey(scope)
        if (!subscribedResultScopes.add(key)) return
        val connection = natsConnection ?: return
        val subDispatcher = dispatcher ?: connection.createDispatcher { }.also { dispatcher = it }
        subDispatcher.subscribe(LensingSubjects.resultSubject(scope)) { msg ->
            handleIncomingResult(String(msg.data, Charsets.UTF_8))
        }
        Log.i(TAG, "Subscribed result subject for initiator scope $key")
    }

    private fun resultScopeKey(scope: LensingSubjectScope): String =
        LensingSubjects.resultSubject(scope)

    private fun handleIncomingResult(json: String) {
        try {
            val result = PaymentResult.fromJson(json)
            if (PaymentResultDispatcher.deliver(
                    result,
                    PaymentResultSource.NATS_INBOUND,
                    publishNats = false,
                    dispatchTerminal = false
                )
            ) {
                Log.i(TAG, "Payment result handled from NATS order=${result.orderId} attempt=${result.attemptId}")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Ignoring unparseable result payload", e)
        }
    }

    private fun handleIncomingVoid(json: String) {
        val voidReq = PaymentVoidRequest.fromJson(json) ?: run {
            Log.w(TAG, "Ignoring unparseable void payload")
            return
        }

        if (PaymentAttemptRegistry.hasInitiatorCallback(
                voidReq.terminalId,
                voidReq.orderId,
                voidReq.attemptId
            )
        ) {
            Log.i(
                TAG,
                "Void ignored on initiator device for order ${voidReq.orderId} attempt ${voidReq.attemptId}"
            )
            return
        }

        if (VoidedAttemptRegistry.isVoided(voidReq.terminalId, voidReq.orderId, voidReq.attemptId)) {
            Log.d(TAG, "Void ignored: attempt already voided order=${voidReq.orderId}")
            return
        }

        val session = PaymentAttemptRegistry.lookup(
            PaymentAttemptKey(voidReq.terminalId, voidReq.orderId, voidReq.attemptId)
        ) ?: PaymentAttemptRegistry.lookupOpenByOrder(voidReq.terminalId, voidReq.orderId)

        VoidedAttemptRegistry.mark(voidReq.terminalId, voidReq.orderId, voidReq.attemptId)
        PaymentAttemptRegistry.close(voidReq.terminalId, voidReq.orderId, voidReq.attemptId)
        PaymentClaimRegistry.releaseClaim(voidReq.terminalId, voidReq.orderId, voidReq.attemptId)

        acknowledgeInitiatorVoid(voidReq, session)
    }

    private fun handleIncomingRefund(json: String) {
        val wire = WireRefundRequest.fromJson(json) ?: run {
            Log.w(TAG, "Ignoring unparseable refund payload")
            return
        }

        val context = LensingContextHolder.applicationContext ?: return
        val config = LensingContextHolder.config ?: return
        val routing = AcquirerRegistry.resolve(config, wire.attemptCode)

        if (!LocalReachabilityCache.shouldTryLocal(routing.code)) {
            Log.d(TAG, "Refund received on NATS but local acquirer ${routing.code} cached unreachable")
            return
        }

        mainScope.launch {
            TerminalEventDispatcher.dispatchRemotePaymentReceived(
                orderId = wire.orderId,
                amountCents = wire.amount,
                currency = wire.currency,
                remark = "Refund",
                method = null
            )

            val launch = LocalAcquirerLauncher.launchRefund(context, config, routing, wire)
            if (!launch.success) {
                TerminalEventDispatcher.dispatchRemotePaymentLaunchFailed(
                    wire.orderId,
                    "Could not launch local acquirer refund"
                )
                Log.w(TAG, "Terminal-side local refund launch failed for order ${wire.orderId}")
            } else {
                Log.i(TAG, "Terminal-side refund launched (${launch.method}) for order ${wire.orderId}")
            }
        }
    }

    private fun handleIncomingPay(json: String) {
        val wire = WirePaymentRequest.fromJson(json) ?: run {
            Log.w(TAG, "Ignoring unparseable pay payload")
            return
        }

        if (PaymentAttemptRegistry.hasInitiatorCallback(wire)) {
            Log.i(
                TAG,
                "Pay ignored on initiator device for order ${wire.orderId} attempt ${wire.attemptId}"
            )
            return
        }

        if (PaymentClaimRegistry.isClaimed(wire.terminalId, wire.orderId, wire.attemptId)) {
            Log.i(TAG, "Pay ignored: order ${wire.orderId} attempt ${wire.attemptId} already claimed")
            return
        }

        if (VoidedAttemptRegistry.isVoided(wire.terminalId, wire.orderId, wire.attemptId)) {
            Log.i(TAG, "Pay ignored: order ${wire.orderId} attempt ${wire.attemptId} already voided")
            return
        }

        val context = LensingContextHolder.applicationContext ?: return
        val config = LensingContextHolder.config ?: return
        val routing = AcquirerRegistry.resolve(config, wire.attemptCode)

        if (!LocalReachabilityCache.shouldTryLocal(routing.code)) {
            Log.d(TAG, "Pay received on NATS but local acquirer ${routing.code} cached unreachable")
            return
        }

        mainScope.launch {
            if (PaymentClaimRegistry.isClaimed(wire.terminalId, wire.orderId, wire.attemptId)) {
                Log.i(TAG, "Pay aborted before launch: order ${wire.orderId} attempt ${wire.attemptId} claimed")
                return@launch
            }
            if (!PaymentClaimRegistry.tryAcquireClaim(wire.terminalId, wire.orderId, wire.attemptId)) {
                Log.i(TAG, "Pay aborted: failed to acquire claim for order ${wire.orderId}")
                return@launch
            }

            publishClaimed(wire)

            PaymentAttemptRegistry.store(wire, callback = null)

            TerminalEventDispatcher.dispatchRemotePaymentReceived(
                orderId = wire.orderId,
                amountCents = wire.amount,
                currency = wire.currency,
                remark = wire.remark,
                method = wire.method
            )

            val launch = LocalAcquirerLauncher.launchPay(context, config, routing, wire)
            if (!launch.success) {
                PaymentAttemptRegistry.close(wire.terminalId, wire.orderId, wire.attemptId)
                PaymentClaimRegistry.releaseClaim(wire.terminalId, wire.orderId, wire.attemptId)
                TerminalEventDispatcher.dispatchRemotePaymentLaunchFailed(
                    wire.orderId,
                    "Could not launch local acquirer"
                )
                Log.w(TAG, "Terminal-side local pay launch failed for order ${wire.orderId}")
            } else {
                Log.i(TAG, "Terminal-side pay launched (${launch.method}) for order ${wire.orderId}")
            }
        }
    }

    private fun publishToSubject(subject: String, payload: String) {
        val connection = natsConnection ?: return
        try {
            connection.publish(subject, payload.toByteArray(Charsets.UTF_8))
        } catch (e: Exception) {
            Log.w(TAG, "Failed to publish to $subject", e)
        }
    }

    private fun flushFallbackQueue() {
        val connection = natsConnection ?: return
        while (true) {
            val queued = fallbackQueue.poll() ?: break
            try {
                PaymentAttemptRegistry.store(queued.request, queued.callback)
                connection.publish(queued.subject, queued.payload)
            } catch (_: Exception) {
                PaymentAttemptRegistry.close(
                    queued.request.terminalId,
                    queued.request.orderId,
                    queued.request.attemptId
                )
                fallbackQueue.add(queued)
                break
            }
        }
    }

    private data class QueuedMessage(
        val subject: String,
        val payload: ByteArray,
        val request: WirePaymentRequest,
        val callback: POSRouterCallback
    )
}
