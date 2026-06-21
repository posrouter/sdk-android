package com.posrouter

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Per-transaction payment payload. Acquirer routing targets come from registry [POSRouterConfig.acquirerCode].
 */
data class PaymentRequest(
    val terminalId: String,
    val amount: Long,
    val orderId: String,
    val remark: String? = null,
    val method: String? = null,
    val metadata: Map<String, String> = emptyMap()
) {
    internal fun toWire(config: POSRouterConfig, routing: com.posrouter.core.registry.AcquirerRouting): WirePaymentRequest =
        WirePaymentRequest(
            terminalId = terminalId,
            amount = amount,
            currency = config.currency,
            targetPackageName = routing.packageName,
            targetScheme = routing.schemeUri,
            acquirerCode = routing.code,
            orderId = orderId,
            remark = remark,
            method = method ?: config.defaultPayMethod,
            metadata = metadata
        )

    companion object {
        /** Parse a decimal amount string (e.g. "66.00") into smallest currency units (cents). */
        fun amountFromDecimal(decimal: String): Long =
            BigDecimal(decimal)
                .setScale(2, RoundingMode.HALF_UP)
                .movePointRight(2)
                .longValueExact()
    }
}

internal data class WirePaymentRequest(
    val terminalId: String,
    val amount: Long,
    val currency: String,
    val targetPackageName: String,
    val targetScheme: String,
    val acquirerCode: String,
    val orderId: String,
    val remark: String? = null,
    val method: String? = null,
    val metadata: Map<String, String> = emptyMap()
) {
    fun toJsonString(): String {
        val metadataJson = metadata.entries.joinToString(",") { (k, v) ->
            "\"$k\":\"${escapeJson(v)}\""
        }
        return """{"terminalId":"$terminalId","amount":$amount,"currency":"$currency","targetPackageName":"$targetPackageName","targetScheme":"$targetScheme","orderId":"$orderId","metadata":{$metadataJson}}"""
    }

    private fun escapeJson(value: String): String =
        value.replace("\\", "\\\\").replace("\"", "\\\"")

    companion object {
        fun fromJson(json: String): WirePaymentRequest? {
            fun extract(key: String): String? {
                val pattern = "\"$key\"\\s*:\\s*\"([^\"]*)\"".toRegex()
                return pattern.find(json)?.groupValues?.getOrNull(1)
            }
            fun extractLong(key: String): Long? {
                val pattern = "\"$key\"\\s*:\\s*(\\d+)".toRegex()
                return pattern.find(json)?.groupValues?.getOrNull(1)?.toLongOrNull()
            }

            val terminalId = extract("terminalId") ?: return null
            val amount = extractLong("amount") ?: return null
            val currency = extract("currency") ?: return null
            val targetPackageName = extract("targetPackageName") ?: return null
            val targetScheme = extract("targetScheme") ?: "ezypos://"
            val orderId = extract("orderId")
                ?: extract("orderid")
                ?: return null

            return WirePaymentRequest(
                terminalId = terminalId,
                amount = amount,
                currency = currency,
                targetPackageName = targetPackageName,
                targetScheme = targetScheme,
                acquirerCode = extract("acquirerCode") ?: "",
                orderId = orderId
            )
        }
    }
}
