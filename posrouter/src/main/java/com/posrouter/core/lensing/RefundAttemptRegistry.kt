package com.posrouter.core.lensing

import com.posrouter.POSRouterCallback
import com.posrouter.PaymentResult
import com.posrouter.WireRefundRequest
import java.util.concurrent.ConcurrentHashMap

internal object RefundAttemptRegistry {
    private data class PendingRefund(
        val wire: WireRefundRequest,
        val callback: POSRouterCallback
    )

    private val attempts = ConcurrentHashMap<String, PendingRefund>()

    private fun storageKey(wire: WireRefundRequest): String =
        PaymentAttemptKey(wire.terminalId, wire.orderId, wire.attemptId).storageKey()

    fun store(wire: WireRefundRequest, callback: POSRouterCallback) {
        attempts[storageKey(wire)] = PendingRefund(wire, callback)
    }

    fun lookup(terminalId: String, orderId: String, attemptId: String): WireRefundRequest? =
        attempts[PaymentAttemptKey(terminalId, orderId, attemptId).storageKey()]?.wire

    fun deliverCallback(result: PaymentResult): Boolean {
        val orderId = result.orderId ?: return false
        val attemptId = result.attemptId ?: return false
        val key = PaymentAttemptKey(result.terminalId, orderId, attemptId).storageKey()
        val pending = attempts.remove(key) ?: return false
        pending.callback.onResult(result)
        return true
    }

    fun close(wire: WireRefundRequest) {
        attempts.remove(storageKey(wire))
    }
}
