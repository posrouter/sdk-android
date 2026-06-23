package com.posrouter.core.lensing

/** Tracks attempts voided by the initiator; late acquirer callbacks are ignored. */
internal object VoidedAttemptRegistry {
    private const val TTL_MS = 30 * 60 * 1000L

    private val voidedAt = java.util.concurrent.ConcurrentHashMap<String, Long>()

    private fun key(terminalId: String, orderId: String, attemptId: String): String =
        PaymentAttemptKey(terminalId, orderId, attemptId).storageKey()

    fun mark(terminalId: String, orderId: String, attemptId: String) {
        pruneExpired()
        voidedAt[key(terminalId, orderId, attemptId)] = System.currentTimeMillis()
    }

    fun isVoided(terminalId: String, orderId: String, attemptId: String): Boolean {
        pruneExpired()
        return voidedAt.containsKey(key(terminalId, orderId, attemptId))
    }

    private fun pruneExpired() {
        val now = System.currentTimeMillis()
        voidedAt.entries.removeIf { (_, recordedAt) -> now - recordedAt > TTL_MS }
    }
}
