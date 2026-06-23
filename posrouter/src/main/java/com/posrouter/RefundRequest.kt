package com.posrouter

import com.posrouter.core.lensing.LensingSubjectScope
import com.posrouter.core.lensing.RefundAttemptIdResolver
import com.posrouter.core.registry.AcquirerRouting
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Refund payload for a prior approved payment. Cross-device delivery uses NATS `.refund`;
 * same-device uses `ezypos://refund`.
 */
data class RefundRequest(
    val terminalId: String,
    /** Original pay [orderId]. */
    val orderId: String,
    val amount: Long,
    val attemptId: String? = null,
    val attemptCode: String? = null,
    val subMerchantId: String? = null
) {
    internal fun toWire(
        config: POSRouterConfig,
        routing: AcquirerRouting,
        resolvedAttemptId: String
    ): WireRefundRequest =
        WireRefundRequest(
            terminalId = terminalId,
            orderId = orderId,
            amount = amount,
            currency = config.currency,
            targetPackageName = routing.packageName,
            targetScheme = routing.schemeUri,
            acquirerCode = routing.code,
            merchantId = config.merchantId,
            attemptId = resolvedAttemptId,
            attemptCode = routing.code,
            subMerchantId = subMerchantId
        )

    companion object {
        fun amountFromDecimal(decimal: String): Long =
            BigDecimal(decimal)
                .setScale(2, RoundingMode.HALF_UP)
                .movePointRight(2)
                .longValueExact()
    }
}

internal data class WireRefundRequest(
    val terminalId: String,
    val orderId: String,
    val amount: Long,
    val currency: String,
    val targetPackageName: String,
    val targetScheme: String,
    val acquirerCode: String,
    val merchantId: String,
    val attemptId: String,
    val attemptCode: String,
    val subMerchantId: String? = null
) {
    fun subjectScope(): LensingSubjectScope = LensingSubjectScope(
        acquirerCode = acquirerCode,
        merchantId = merchantId,
        subMerchantId = subMerchantId,
        terminalId = terminalId
    )

    fun toJsonString(): String {
        val fields = mutableListOf(
            """"terminalId":"${escapeJson(terminalId)}"""",
            """"orderId":"${escapeJson(orderId)}"""",
            """"amount":$amount""",
            """"currency":"${escapeJson(currency)}"""",
            """"targetPackageName":"${escapeJson(targetPackageName)}"""",
            """"targetScheme":"${escapeJson(targetScheme)}"""",
            """"attemptId":"${escapeJson(attemptId)}"""",
            """"attemptCode":"${escapeJson(attemptCode)}"""",
            """"acquirerCode":"${escapeJson(acquirerCode)}"""",
            """"merchantId":"${escapeJson(merchantId)}""""
        )
        subMerchantId?.let { fields.add(""""subMerchantId":"${escapeJson(it)}"""") }
        return "{${fields.joinToString(",")}}"
    }

    private fun escapeJson(value: String): String =
        value.replace("\\", "\\\\").replace("\"", "\\\"")

    companion object {
        fun fromJson(json: String): WireRefundRequest? {
            fun extract(key: String): String? {
                val pattern = "\"$key\"\\s*:\\s*\"([^\"]*)\"".toRegex()
                return pattern.find(json)?.groupValues?.getOrNull(1)
            }
            fun extractLong(key: String): Long? {
                val pattern = "\"$key\"\\s*:\\s*(\\d+)".toRegex()
                return pattern.find(json)?.groupValues?.getOrNull(1)?.toLongOrNull()
            }

            val terminalId = extract("terminalId") ?: return null
            val orderId = extract("orderid") ?: extract("orderId") ?: return null
            val amount = extractLong("amount") ?: return null
            val currency = extract("currency") ?: return null
            val targetPackageName = extract("targetPackageName") ?: return null
            val targetScheme = extract("targetScheme") ?: "ezypos://"
            val merchantId = extract("merchantId") ?: return null
            val attemptCode = extract("attemptCode") ?: extract("acquirerCode") ?: ""
            val attemptId = extract("attemptId") ?: RefundAttemptIdResolver.defaultAttemptId(orderId)

            return WireRefundRequest(
                terminalId = terminalId,
                orderId = orderId,
                amount = amount,
                currency = currency,
                targetPackageName = targetPackageName,
                targetScheme = targetScheme,
                acquirerCode = extract("acquirerCode") ?: attemptCode,
                merchantId = merchantId,
                attemptId = attemptId,
                attemptCode = attemptCode,
                subMerchantId = extract("subMerchantId")
            )
        }
    }
}
