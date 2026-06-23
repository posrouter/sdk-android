package com.posrouter.core.lensing

import com.posrouter.WirePaymentRequest

internal data class PaymentAttemptKey(
    val terminalId: String,
    val orderId: String,
    val attemptId: String
) {
    fun storageKey(): String = "$terminalId:$orderId:$attemptId"

    companion object {
        fun fromWire(wire: WirePaymentRequest) = PaymentAttemptKey(
            terminalId = wire.terminalId,
            orderId = wire.orderId,
            attemptId = wire.attemptId
        )

        fun legacy(terminalId: String, orderId: String) = PaymentAttemptKey(
            terminalId = terminalId,
            orderId = orderId,
            attemptId = defaultAttemptId(orderId)
        )

        fun defaultAttemptId(orderId: String): String = "$orderId#1"
    }
}

internal object PaymentAttemptIdResolver {
    private val counters = java.util.concurrent.ConcurrentHashMap<String, java.util.concurrent.atomic.AtomicInteger>()

    fun resolve(orderId: String, explicit: String?): String {
        explicit?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        val seq = counters.computeIfAbsent(orderId) { java.util.concurrent.atomic.AtomicInteger(0) }
            .incrementAndGet()
        return "$orderId#$seq"
    }
}
