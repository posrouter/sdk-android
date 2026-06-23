package com.posrouter.core.lensing

internal data class PaymentVoidRequest(
    val terminalId: String,
    val orderId: String,
    val attemptId: String,
    val reason: String = REASON_INITIATOR_VOID,
    val voidedAt: Long = System.currentTimeMillis()
) {
    fun toJsonString(): String =
        """{"terminalId":"$terminalId","orderId":"$orderId","attemptId":"$attemptId","reason":"$reason","voidedAt":$voidedAt}"""

    companion object {
        const val REASON_INITIATOR_VOID = "initiator_void"

        fun fromJson(json: String): PaymentVoidRequest? {
            fun extract(key: String): String? {
                val pattern = "\"$key\"\\s*:\\s*\"([^\"]*)\"".toRegex()
                return pattern.find(json)?.groupValues?.getOrNull(1)
            }
            fun extractLong(key: String): Long? {
                val pattern = "\"$key\"\\s*:\\s*(\\d+)".toRegex()
                return pattern.find(json)?.groupValues?.getOrNull(1)?.toLongOrNull()
            }

            val terminalId = extract("terminalId") ?: return null
            val orderId = extract("orderId") ?: extract("orderid") ?: return null
            val attemptId = extract("attemptId")
                ?: PaymentAttemptKey.defaultAttemptId(orderId)
            return PaymentVoidRequest(
                terminalId = terminalId,
                orderId = orderId,
                attemptId = attemptId,
                reason = extract("reason") ?: REASON_INITIATOR_VOID,
                voidedAt = extractLong("voidedAt") ?: System.currentTimeMillis()
            )
        }
    }
}
