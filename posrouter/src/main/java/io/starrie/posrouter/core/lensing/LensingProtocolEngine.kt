package io.starrie.posrouter.core.lensing

import io.nats.client.Connection
import io.nats.client.Dispatcher
import io.nats.client.Nats
import io.nats.client.Options
import io.starrie.posrouter.PaymentRequest
import io.starrie.posrouter.POSRouterCallback
import io.starrie.posrouter.POSRouterError
import io.starrie.posrouter.PaymentResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.math.min

internal object LensingProtocolEngine {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var natsConnection: Connection? = null
    private var dispatcher: Dispatcher? = null
    private var state: LensingState = LensingState.IDLE
    private var participantCode: String? = null
    private var natsUrl: String? = null
    private var natsToken: String? = null

    private val fallbackQueue = ConcurrentLinkedQueue<QueuedMessage>()
    private var reconnectAttempt = 0
    private const val MAX_BACKOFF_MS = 30_000L

    fun start(code: String, key: String) {
        participantCode = code
        state = LensingState.DISCOVERING

        scope.launch {
            try {
                val credentials = LensingGatewayClient.fetchNatsCredentials(code, key)
                natsUrl = credentials.natsUrl
                natsToken = credentials.natsToken
                connectNats()
            } catch (e: Exception) {
                state = LensingState.FAILED
            }
        }
    }

    private fun connectNats() {
        val url = natsUrl ?: return
        val token = natsToken ?: return

        state = LensingState.CONNECTING
        try {
            val options = Options.builder()
                .server(url)
                .token(token)
                .connectionListener { _, type ->
                    when (type) {
                        io.nats.client.ConnectionListener.Events.DISCONNECTED -> {
                            state = LensingState.RECONNECTING
                            scheduleReconnect()
                        }
                        io.nats.client.ConnectionListener.Events.RECONNECTED -> {
                            state = LensingState.CONNECTED
                            reconnectAttempt = 0
                            flushFallbackQueue()
                        }
                        io.nats.client.ConnectionListener.Events.CONNECTED -> {
                            state = LensingState.CONNECTED
                        }
                    }
                }
                .build()

            natsConnection = Nats.connect(options)
            dispatcher = natsConnection?.createDispatcher { }
            state = LensingState.CONNECTED
            reconnectAttempt = 0
            flushFallbackQueue()
        } catch (e: Exception) {
            state = LensingState.RECONNECTING
            scheduleReconnect()
        }
    }

    private fun scheduleReconnect() {
        scope.launch {
            val delayMs = min(MAX_BACKOFF_MS, 1000L * (1 shl reconnectAttempt))
            reconnectAttempt++
            kotlinx.coroutines.delay(delayMs)
            connectNats()
        }
    }

    fun dispatchTransaction(request: PaymentRequest, callback: POSRouterCallback) {
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

    private fun flushFallbackQueue() {
        val connection = natsConnection ?: return
        while (true) {
            val queued = fallbackQueue.poll() ?: break
            try {
                connection.publish(queued.subject, queued.payload)
                subscribeForResult(connection, LensingSubjects.resultSubject(queued.request.terminalId), queued.callback)
            } catch (_: Exception) {
                fallbackQueue.add(queued)
                break
            }
        }
    }

    private data class QueuedMessage(
        val subject: String,
        val payload: ByteArray,
        val request: PaymentRequest,
        val callback: POSRouterCallback
    )
}
