package com.posrouter.core.lensing

import android.util.Log
import com.posrouter.LensingContextHolder
import com.posrouter.POSRouterConfig
import com.posrouter.WirePaymentRequest
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
    private var terminalId: String? = null

    private val fallbackQueue = ConcurrentLinkedQueue<QueuedMessage>()
    private var reconnectAttempt = 0
    private const val MAX_BACKOFF_MS = 30_000L

    fun start(config: POSRouterConfig) {
        terminalId = config.terminalId
        state = LensingState.DISCOVERING

        scope.launch {
            try {
                val credentials = LensingGatewayClient.fetchNatsCredentials(
                    config.participantCode,
                    config.participantKey
                )
                AcquirerRegistry.prefetch(config)
                connectNats(credentials.natsUrl, credentials.natsToken, config.terminalId)
            } catch (e: Exception) {
                state = LensingState.FAILED
                Log.e(TAG, "Gateway discovery failed", e)
            }
        }
    }

    private fun connectNats(url: String, token: String, tid: String) {
        state = LensingState.CONNECTING
        try {
            val options = Options.builder()
                .server(url)
                .token(token)
                .connectionListener { _, type ->
                    when (type) {
                        io.nats.client.ConnectionListener.Events.DISCONNECTED -> {
                            state = LensingState.RECONNECTING
                            scheduleReconnect(url, token, tid)
                        }
                        io.nats.client.ConnectionListener.Events.RECONNECTED -> {
                            state = LensingState.CONNECTED
                            reconnectAttempt = 0
                            flushFallbackQueue()
                        }
                        io.nats.client.ConnectionListener.Events.CONNECTED -> {
                            state = LensingState.CONNECTED
                        }
                        else -> Unit
                    }
                }
                .build()

            natsConnection = Nats.connect(options)
            dispatcher = natsConnection?.createDispatcher { }
            state = LensingState.CONNECTED
            reconnectAttempt = 0
            setupTerminalSubscriptions(tid)
            flushFallbackQueue()
        } catch (e: Exception) {
            state = LensingState.RECONNECTING
            scheduleReconnect(url, token, tid)
        }
    }

    private fun scheduleReconnect(url: String, token: String, tid: String) {
        scope.launch {
            val delayMs = min(MAX_BACKOFF_MS, 1000L * (1 shl reconnectAttempt))
            reconnectAttempt++
            kotlinx.coroutines.delay(delayMs)
            connectNats(url, token, tid)
        }
    }

    fun currentState(): LensingState = state

    fun publishClaimed(terminalId: String, orderId: String) {
        val claim = PaymentClaim(terminalId, orderId)
        PaymentClaimRegistry.markClaimed(claim)
        publishToSubject(LensingSubjects.claimedSubject(terminalId), claim.toJsonString())
    }

    fun dispatchTransaction(request: WirePaymentRequest, callback: POSRouterCallback) {
        val targetSubject = LensingSubjects.paySubject(request.terminalId)
        val resultSubject = LensingSubjects.resultSubject(request.terminalId)
        val payloadBytes = request.toJsonString().toByteArray(Charsets.UTF_8)

        val connection = natsConnection
        if (connection == null || state != LensingState.CONNECTED) {
            fallbackQueue.add(QueuedMessage(targetSubject, payloadBytes, request, callback))
            if (state == LensingState.IDLE || state == LensingState.FAILED) {
                callback.onError(POSRouterError("NOT_INITIALIZED", "Lensing engine not connected"))
            }
            return
        }

        subscribeForResult(connection, resultSubject, callback)

        try {
            connection.publish(targetSubject, payloadBytes)
        } catch (e: Exception) {
            fallbackQueue.add(QueuedMessage(targetSubject, payloadBytes, request, callback))
            callback.onError(POSRouterError("PUBLISH_FAILED", e.message ?: "Failed to publish"))
        }
    }

    private fun setupTerminalSubscriptions(tid: String) {
        val connection = natsConnection ?: return
        val subDispatcher = dispatcher ?: connection.createDispatcher { }.also { dispatcher = it }

        subDispatcher.subscribe(LensingSubjects.claimedSubject(tid)) { msg ->
            PaymentClaim.fromJson(String(msg.data, Charsets.UTF_8))?.let { claim ->
                PaymentClaimRegistry.markClaimed(claim)
                Log.i(TAG, "Payment UI claimed by remote for order ${claim.orderId}")
            }
        }

        subDispatcher.subscribe(LensingSubjects.paySubject(tid)) { msg ->
            handleIncomingPay(String(msg.data, Charsets.UTF_8))
        }
    }

    private fun handleIncomingPay(json: String) {
        val wire = WirePaymentRequest.fromJson(json) ?: run {
            Log.w(TAG, "Ignoring unparseable pay payload")
            return
        }

        if (PaymentClaimRegistry.isClaimed(wire.terminalId, wire.orderId)) {
            Log.i(TAG, "Pay ignored: order ${wire.orderId} already claimed")
            return
        }

        val context = LensingContextHolder.applicationContext ?: return
        val config = LensingContextHolder.config ?: return
        val routing = AcquirerRegistry.resolve(config)

        if (!LocalReachabilityCache.shouldTryLocal(routing.code)) {
            Log.d(TAG, "Pay received on NATS but local acquirer ${routing.code} cached unreachable")
            return
        }

        mainScope.launch {
            if (PaymentClaimRegistry.isClaimed(wire.terminalId, wire.orderId)) {
                Log.i(TAG, "Pay aborted before launch: order ${wire.orderId} claimed by peer")
                return@launch
            }
            if (!PaymentClaimRegistry.tryAcquireClaim(wire.terminalId, wire.orderId)) {
                Log.i(TAG, "Pay aborted: failed to acquire claim for order ${wire.orderId}")
                return@launch
            }

            publishClaimed(wire.terminalId, wire.orderId)

            val launch = LocalAcquirerLauncher.launchPay(context, config, routing, wire)
            if (!launch.success) {
                PaymentClaimRegistry.releaseClaim(wire.terminalId, wire.orderId)
                Log.w(TAG, "Terminal-side local pay launch failed for order ${wire.orderId}")
            } else {
                PaymentClaimRegistry.releaseClaim(wire.terminalId, wire.orderId)
                Log.i(TAG, "Terminal-side pay launched (${launch.method}) for order ${wire.orderId}")
            }
        }
    }

    private fun subscribeForResult(
        connection: Connection,
        resultSubject: String,
        callback: POSRouterCallback
    ) {
        val subDispatcher = dispatcher ?: connection.createDispatcher { msg ->
            val json = String(msg.data, Charsets.UTF_8)
            try {
                val result = PaymentResult.fromJson(json)
                callback.onResult(result)
            } catch (e: Exception) {
                callback.onError(POSRouterError("PARSE_ERROR", e.message ?: "Invalid result payload"))
            }
        }
        subDispatcher.subscribe(resultSubject)
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
                connection.publish(queued.subject, queued.payload)
                subscribeForResult(
                    connection,
                    LensingSubjects.resultSubject(queued.request.terminalId),
                    queued.callback
                )
            } catch (_: Exception) {
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
