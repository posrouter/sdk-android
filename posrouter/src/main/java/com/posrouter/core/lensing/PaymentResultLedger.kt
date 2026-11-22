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

    /** True when an outcome has already been delivered for this terminal/order/attempt. */
    fun isDelivered(terminalId: String, orderId: String, attemptId: String?): Boolean {
        pruneExpired()
        val key = "$terminalId:$orderId:${attemptId ?: PaymentAttemptKey.defaultAttemptId(orderId)}"
        return delivered.containsKey(key)
    }

    /**
     * First outcome per attempt wins — except that an APPROVED always gets through over an earlier non-APPROVED
     * (money moved; a late approval after a relayed not-found/decline must reach the initiator and the CRM, Fable B-C1).
     */
    fun markIfFirst(result: PaymentResult): Boolean {
        pruneExpired()
        val key = deliveryKey(result) ?: return false
        val entry = Entry(result.status, System.currentTimeMillis())
        var admitted = false
        delivered.compute(key) { _, prev ->
            when {
                prev == null -> { admitted = true; entry }
                result.status == PaymentStatus.APPROVED && prev.status != PaymentStatus.APPROVED -> { admitted = true; entry }
                else -> prev
            }
        }
        return admitted
    }

    /** Status already delivered for this attempt, if any. */
    fun deliveredStatus(result: PaymentResult): PaymentStatus? {
        pruneExpired()
        return deliveryKey(result)?.let { delivered[it]?.status }
    }

    private fun pruneExpired() {
        val now = System.currentTimeMillis()
        delivered.entries.removeIf { (_, entry) -> now - entry.recordedAt > TTL_MS }
    }
}
