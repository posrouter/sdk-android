package com.posrouter.core.lensing

import com.posrouter.PaymentResult
import com.posrouter.PaymentStatus

/** Ensures each attempt receives at most one terminal payment result delivery. */
internal object PaymentResultLedger {
    private const val TTL_MS = 30 * 60 * 1000L

    private data class Entry(val status: PaymentStatus, val recordedAt: Long)

    private val delivered = java.util.concurrent.ConcurrentHashMap<String, Entry>()

    fun deliveryKey(result: PaymentResult): String? {
        val orderId = result.orderId ?: return null
        val attemptId = result.attemptId ?: PaymentAttemptKey.defaultAttemptId(orderId)
        return "${result.terminalId}:$orderId:$attemptId"
    }

    fun markIfFirst(result: PaymentResult): Boolean {
        pruneExpired()
        val key = deliveryKey(result) ?: return false
        val entry = Entry(result.status, System.currentTimeMillis())
        return delivered.putIfAbsent(key, entry) == null
    }

    private fun pruneExpired() {
        val now = System.currentTimeMillis()
        delivered.entries.removeIf { (_, entry) -> now - entry.recordedAt > TTL_MS }
    }
}
