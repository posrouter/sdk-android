package com.posrouter.core.lensing

import android.util.Log
import com.posrouter.POSRouterCallback
import com.posrouter.PaymentCancelReason
import com.posrouter.PaymentResult
import com.posrouter.PaymentStatus
import com.posrouter.WirePaymentRequest

internal data class PaymentAttempt(
    val wire: WirePaymentRequest,
    val callback: POSRouterCallback?,
    val startedAt: Long = System.currentTimeMillis()
)

/** Merged in-flight pay session + optional local callback waiter. */
internal object PaymentAttemptRegistry {
    private const val TAG = "POSRouter.Attempt"
    private const val ATTEMPT_TTL_MS = 30 * 60 * 1000L

    private val attempts = java.util.concurrent.ConcurrentHashMap<String, PaymentAttempt>()
    private val openByOrder = java.util.concurrent.ConcurrentHashMap<String, String>()

    private fun orderKey(terminalId: String, orderId: String): String = "$terminalId:$orderId"

    fun store(wire: WirePaymentRequest, callback: POSRouterCallback?) {
        pruneExpired()
        val key = PaymentAttemptKey.fromWire(wire)
        attempts[key.storageKey()] = PaymentAttempt(wire, callback)
        openByOrder[orderKey(wire.terminalId, wire.orderId)] = wire.attemptId
    }

    fun lookup(key: PaymentAttemptKey): WirePaymentRequest? {
        pruneExpired()
        return attempts[key.storageKey()]?.wire
    }

    fun lookupOpenByOrder(terminalId: String, orderId: String): WirePaymentRequest? {
        pruneExpired()
        val attemptId = openByOrder[orderKey(terminalId, orderId)] ?: return null
        return lookup(PaymentAttemptKey(terminalId, orderId, attemptId))
    }

    /** True when this device initiated pay and is waiting for a remote/local callback. */
    fun hasInitiatorCallback(terminalId: String, orderId: String, attemptId: String): Boolean {
        pruneExpired()
        val storageKey = PaymentAttemptKey(terminalId, orderId, attemptId).storageKey()
        return attempts[storageKey]?.callback != null
    }

    fun hasInitiatorCallback(wire: WirePaymentRequest): Boolean =
        hasInitiatorCallback(wire.terminalId, wire.orderId, wire.attemptId)

    fun deliverCallback(result: PaymentResult): Boolean {
        val orderId = result.orderId ?: return false
        val attemptId = result.attemptId ?: openByOrder[orderKey(result.terminalId, orderId)] ?: return false
        val storageKey = PaymentAttemptKey(result.terminalId, orderId, attemptId).storageKey()
        return attempts.remove(storageKey)?.callback?.let { callback ->
            dispatchOptionalCancelEvents(callback, result)
            callback.onResult(result)
            openByOrder.remove(orderKey(result.terminalId, orderId), attemptId)
            true
        } ?: false
    }

    /** Optional typed cancel hooks; always followed by [POSRouterCallback.onResult]. */
    internal fun dispatchOptionalCancelEvents(callback: POSRouterCallback, result: PaymentResult) {
        if (result.status != PaymentStatus.CANCELLED) return
        when (result.metadata["cancelReason"]) {
            PaymentCancelReason.USER_CANCEL -> callback.onUserCancelled(result)
            PaymentCancelReason.INITIATOR_VOID -> callback.onInitiatorVoided(result)
        }
    }

    fun close(terminalId: String, orderId: String, attemptId: String) {
        val storageKey = PaymentAttemptKey(terminalId, orderId, attemptId).storageKey()
        attempts.remove(storageKey)
        openByOrder.remove(orderKey(terminalId, orderId), attemptId)
    }

    fun cancel(terminalId: String, orderId: String, attemptId: String) {
        close(terminalId, orderId, attemptId)
    }

    fun cancelLatestOpen(terminalId: String, orderId: String) {
        val attemptId = openByOrder.remove(orderKey(terminalId, orderId)) ?: return
        attempts.remove(PaymentAttemptKey(terminalId, orderId, attemptId).storageKey())
    }

    private fun pruneExpired() {
        val now = System.currentTimeMillis()
        val expired = attempts.entries.filter { (_, attempt) ->
            now - attempt.startedAt > ATTEMPT_TTL_MS
        }
        for ((storageKey, attempt) in expired) {
            attempts.remove(storageKey)
            openByOrder.remove(
                orderKey(attempt.wire.terminalId, attempt.wire.orderId),
                attempt.wire.attemptId
            )
            Log.d(TAG, "Expired attempt ${attempt.wire.attemptId} for order ${attempt.wire.orderId}")
        }
    }
}
