package io.starrie.posrouter

data class PaymentRequest(
    val terminalId: String,
    val amount: Long,
    val currency: String,
    val targetPackageName: String,
    val metadata: Map<String, String> = emptyMap()
) {
    fun toJsonString(): String {
        val metadataJson = metadata.entries.joinToString(",") { (k, v) ->
            "\"$k\":\"${escapeJson(v)}\""
        }
        return """{"terminalId":"$terminalId","amount":$amount,"currency":"$currency","targetPackageName":"$targetPackageName","metadata":{$metadataJson}}"""
    }

    private fun escapeJson(value: String): String =
        value.replace("\\", "\\\\").replace("\"", "\\\"")
}
