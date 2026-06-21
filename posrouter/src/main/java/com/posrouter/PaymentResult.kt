package com.posrouter

enum class PaymentStatus {
    APPROVED,
    DECLINED,
    CANCELLED,
    ERROR
}

data class PaymentResult(
    val terminalId: String,
    val status: PaymentStatus,
    val transactionId: String?,
    val amount: Long,
    val currency: String,
    val message: String?,
    val orderId: String? = null,
    val localRouteMethod: LocalRouteMethod? = null
) {
    fun toJsonString(): String {
        val fields = mutableListOf(
            """"terminalId":"${escapeJson(terminalId)}"""",
            """"status":"${status.name.lowercase()}"""",
            """"amount":$amount""",
            """"currency":"${escapeJson(currency)}""""
        )
        orderId?.let { fields.add(""""orderId":"${escapeJson(it)}"""") }
        transactionId?.let { fields.add(""""transactionId":"${escapeJson(it)}"""") }
        message?.let { fields.add(""""message":"${escapeJson(it)}"""") }
        return "{${fields.joinToString(",")}}"
    }

    private fun escapeJson(value: String): String =
        value.replace("\\", "\\\\").replace("\"", "\\\"")

    companion object {
        fun fromJson(json: String): PaymentResult {
            fun extract(key: String): String? {
                val pattern = "\"$key\"\\s*:\\s*\"([^\"]*)\"".toRegex()
                return pattern.find(json)?.groupValues?.getOrNull(1)
            }
            fun extractLong(key: String): Long {
                val pattern = "\"$key\"\\s*:\\s*(\\d+)".toRegex()
                return pattern.find(json)?.groupValues?.getOrNull(1)?.toLongOrNull() ?: 0L
            }

            val statusStr = extract("status") ?: "error"
            val status = when (statusStr.lowercase()) {
                "approved" -> PaymentStatus.APPROVED
                "declined" -> PaymentStatus.DECLINED
                "cancelled" -> PaymentStatus.CANCELLED
                else -> PaymentStatus.ERROR
            }

            return PaymentResult(
                terminalId = extract("terminalId") ?: "",
                status = status,
                transactionId = extract("transactionId"),
                amount = extractLong("amount"),
                currency = extract("currency") ?: "",
                message = extract("message"),
                orderId = extract("orderId") ?: extract("orderid")
            )
        }
    }
}
