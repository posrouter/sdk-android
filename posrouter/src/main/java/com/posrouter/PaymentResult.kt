package com.posrouter

import com.posrouter.core.lensing.PaymentAttemptKey

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
    val attemptId: String? = null,
    val attemptCode: String? = null,
    val subMerchantId: String? = null,
    val localRouteMethod: LocalRouteMethod? = null,
    val metadata: Map<String, String> = emptyMap()
) {
    fun toJsonString(): String {
        val fields = mutableListOf(
            """"terminalId":"${escapeJson(terminalId)}"""",
            """"status":"${status.name.lowercase()}"""",
            """"amount":$amount""",
            """"currency":"${escapeJson(currency)}""""
        )
        orderId?.let { fields.add(""""orderId":"${escapeJson(it)}"""") }
        attemptId?.let { fields.add(""""attemptId":"${escapeJson(it)}"""") }
        attemptCode?.let { fields.add(""""attemptCode":"${escapeJson(it)}"""") }
        subMerchantId?.let { fields.add(""""subMerchantId":"${escapeJson(it)}"""") }
        transactionId?.let { fields.add(""""transactionId":"${escapeJson(it)}"""") }
        message?.let { fields.add(""""message":"${escapeJson(it)}"""") }
        if (metadata.isNotEmpty()) {
            val metadataJson = metadata.entries.joinToString(",") { (k, v) ->
                """"${escapeJson(k)}":"${escapeJson(v)}""""
            }
            fields.add(""""metadata":{$metadataJson}""")
        }
        return "{${fields.joinToString(",")}}"
    }

    private fun escapeJson(value: String): String =
        value.replace("\\", "\\\\").replace("\"", "\\\"")

    companion object {
        private fun parseMetadataObject(json: String): Map<String, String> {
            val key = "\"metadata\""
            val keyIndex = json.indexOf(key)
            if (keyIndex < 0) return emptyMap()
            val braceStart = json.indexOf('{', keyIndex + key.length)
            if (braceStart < 0) return emptyMap()

            var depth = 0
            for (i in braceStart until json.length) {
                when (json[i]) {
                    '{' -> depth++
                    '}' -> {
                        depth--
                        if (depth == 0) {
                            val body = json.substring(braceStart + 1, i)
                            val metadata = mutableMapOf<String, String>()
                            val entryPattern = "\"([^\"]+)\"\\s*:\\s*\"([^\"]*)\"".toRegex()
                            for (match in entryPattern.findAll(body)) {
                                metadata[match.groupValues[1]] = match.groupValues[2]
                            }
                            return metadata
                        }
                    }
                }
            }
            return emptyMap()
        }

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

            val orderId = extract("orderId") ?: extract("orderid")

            val metadata = parseMetadataObject(json)

            return PaymentResult(
                terminalId = extract("terminalId") ?: "",
                status = status,
                transactionId = extract("transactionId"),
                amount = extractLong("amount"),
                currency = extract("currency") ?: "",
                message = extract("message"),
                orderId = orderId,
                attemptId = extract("attemptId")
                    ?: orderId?.let { PaymentAttemptKey.defaultAttemptId(it) },
                attemptCode = extract("attemptCode"),
                subMerchantId = extract("subMerchantId"),
                metadata = metadata
            )
        }
    }
}
