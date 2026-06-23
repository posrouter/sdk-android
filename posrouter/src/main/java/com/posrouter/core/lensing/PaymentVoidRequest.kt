package com.posrouter.core.lensing

internal data class PaymentVoidRequest(
    val acquirerCode: String,
    val merchantId: String,
    val subMerchantId: String?,
    val terminalId: String,
    val orderId: String,
    val attemptId: String,
    val reason: String = REASON_INITIATOR_VOID,
    val voidedAt: Long = System.currentTimeMillis()
) {
    fun subjectScope(): LensingSubjectScope = LensingSubjectScope(
        acquirerCode = acquirerCode,
        merchantId = merchantId,
        subMerchantId = subMerchantId,
        terminalId = terminalId
    )

    fun toJsonString(): String {
        val fields = mutableListOf(
            """"acquirerCode":"$acquirerCode"""",
            """"merchantId":"$merchantId"""",
            """"terminalId":"$terminalId"""",
            """"orderId":"$orderId"""",
            """"attemptId":"$attemptId"""",
            """"reason":"$reason"""",
            """"voidedAt":$voidedAt"""
        )
        subMerchantId?.let { fields.add(""""subMerchantId":"$it"""") }
        return "{${fields.joinToString(",")}}"
    }

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
            val acquirerCode = extract("acquirerCode") ?: return null
            val merchantId = extract("merchantId") ?: return null
            return PaymentVoidRequest(
                acquirerCode = acquirerCode,
                merchantId = merchantId,
                subMerchantId = extract("subMerchantId"),
                terminalId = terminalId,
                orderId = orderId,
                attemptId = attemptId,
                reason = extract("reason") ?: REASON_INITIATOR_VOID,
                voidedAt = extractLong("voidedAt") ?: System.currentTimeMillis()
            )
        }
    }
}
