package com.posrouter.core.lensing

data class PaymentClaim(
    val terminalId: String,
    val orderId: String,
    val attemptId: String,
    val claimedAt: Long = System.currentTimeMillis()
) {
    fun toJsonString(): String =
        """{"terminalId":"$terminalId","orderId":"$orderId","attemptId":"$attemptId","claimedAt":$claimedAt}"""

    companion object {
        fun fromJson(json: String): PaymentClaim? {
            fun extract(key: String): String? {
                val pattern = "\"$key\"\\s*:\\s*\"([^\"]*)\"".toRegex()
                return pattern.find(json)?.groupValues?.getOrNull(1)
            }
            fun extractLong(key: String): Long? {
                val pattern = "\"$key\"\\s*:\\s*(\\d+)".toRegex()
                return pattern.find(json)?.groupValues?.getOrNull(1)?.toLongOrNull()
            }

            val terminalId = extract("terminalId") ?: return null
            val orderId = extract("orderId") ?: return null
            val attemptId = extract("attemptId") ?: PaymentAttemptKey.defaultAttemptId(orderId)
            return PaymentClaim(
                terminalId = terminalId,
                orderId = orderId,
                attemptId = attemptId,
                claimedAt = extractLong("claimedAt") ?: System.currentTimeMillis()
            )
        }
    }
}

internal object PaymentClaimRegistry {
    private val claims = java.util.concurrent.ConcurrentHashMap<String, PaymentClaim>()

    /** In-flight routing guard; stale claims expire so cancelled payments can retry. */
    private const val CLAIM_TTL_MS = 5 * 60 * 1000L

    private fun key(terminalId: String, orderId: String, attemptId: String): String =
        PaymentAttemptKey(terminalId, orderId, attemptId).storageKey()

    private fun isExpired(claim: PaymentClaim): Boolean =
        System.currentTimeMillis() - claim.claimedAt > CLAIM_TTL_MS

    private fun pruneExpired(terminalId: String, orderId: String, attemptId: String) {
        val claimKey = key(terminalId, orderId, attemptId)
        val claim = claims[claimKey] ?: return
        if (isExpired(claim)) {
            claims.remove(claimKey)
        }
    }

    fun isClaimed(terminalId: String, orderId: String, attemptId: String): Boolean {
        pruneExpired(terminalId, orderId, attemptId)
        return claims.containsKey(key(terminalId, orderId, attemptId))
    }

    fun markClaimed(claim: PaymentClaim) {
        claims[key(claim.terminalId, claim.orderId, claim.attemptId)] = claim
    }

    /** Returns true if this caller acquired the claim (first writer wins locally). */
    fun tryAcquireClaim(terminalId: String, orderId: String, attemptId: String): Boolean {
        pruneExpired(terminalId, orderId, attemptId)
        val claimKey = key(terminalId, orderId, attemptId)
        val claim = PaymentClaim(terminalId, orderId, attemptId)
        return claims.putIfAbsent(claimKey, claim) == null
    }

    fun releaseClaim(terminalId: String, orderId: String, attemptId: String) {
        claims.remove(key(terminalId, orderId, attemptId))
    }
}
