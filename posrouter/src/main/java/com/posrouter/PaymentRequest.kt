package com.posrouter

import com.posrouter.core.lensing.PaymentAttemptKey
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Per-transaction payment payload. Acquirer routing targets come from registry [POSRouterConfig.acquirerCode]
 * unless [attemptCode] overrides the pipeline for this try.
 */
data class PaymentRequest(
    val terminalId: String,
    val amount: Long,
    val orderId: String,
    val remark: String? = null,
    val method: String? = null,
    val metadata: Map<String, String> = emptyMap(),
    /** Unique id for this pay try; SDK auto-generates orderId#N when omitted. */
    val attemptId: String? = null,
    /** Pipeline / provider code for this try, e.g. EZYPOS, SKYZER. Defaults to config acquirerCode. */
    val attemptCode: String? = null,
    /** Platform sub-merchant (e.g. restaurant on a ordering platform). */
    val subMerchantId: String? = null
) {
    internal fun toWire(
        config: POSRouterConfig,
        routing: com.posrouter.core.registry.AcquirerRouting,
        resolvedAttemptId: String
    ): WirePaymentRequest =
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
            metadata = metadata,
            attemptId = resolvedAttemptId,
            attemptCode = routing.code,
            merchantId = config.merchantId,
            subMerchantId = subMerchantId
        )

    companion object {
        const val METHOD_EMV_CARD = "emv_card"
        const val METHOD_SHOW_QR_CODE = "show_qr_code"
        const val METHOD_SKYZER = "skyzer"

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
    val attemptId: String,
    val attemptCode: String,
    val merchantId: String,
    val remark: String? = null,
    val method: String? = null,
    val subMerchantId: String? = null,
    val metadata: Map<String, String> = emptyMap()
) {
    fun toJsonString(): String {
        val fields = mutableListOf(
            """"terminalId":"${escapeJson(terminalId)}"""",
            """"amount":$amount""",
            """"currency":"${escapeJson(currency)}"""",
            """"targetPackageName":"${escapeJson(targetPackageName)}"""",
            """"targetScheme":"${escapeJson(targetScheme)}"""",
            """"orderId":"${escapeJson(orderId)}"""",
            """"attemptId":"${escapeJson(attemptId)}"""",
            """"attemptCode":"${escapeJson(attemptCode)}"""",
            """"acquirerCode":"${escapeJson(acquirerCode)}"""",
            """"merchantId":"${escapeJson(merchantId)}""""
        )
        remark?.let { fields.add(""""remark":"${escapeJson(it)}"""") }
        method?.let { fields.add(""""method":"${escapeJson(it)}"""") }
        subMerchantId?.let { fields.add(""""subMerchantId":"${escapeJson(it)}"""") }
        if (metadata.isNotEmpty()) {
            val metadataJson = metadata.entries.joinToString(",") { (k, v) ->
                """"${escapeJson(k)}":"${escapeJson(v)}""""
            }
            fields.add(""""metadata":{$metadataJson}""")
        } else {
            fields.add(""""metadata":{}""")
        }
        return "{${fields.joinToString(",")}}"
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
            val orderId = extract("orderid") ?: extract("orderId") ?: return null
            val attemptId = extract("attemptId") ?: PaymentAttemptKey.defaultAttemptId(orderId)
            val attemptCode = extract("attemptCode")
                ?: extract("acquirerCode")
                ?: ""
            val merchantId = extract("merchantId") ?: return null

            return WirePaymentRequest(
                terminalId = terminalId,
                amount = amount,
                currency = currency,
                targetPackageName = targetPackageName,
                targetScheme = targetScheme,
                acquirerCode = extract("acquirerCode") ?: attemptCode,
                merchantId = merchantId,
                orderId = orderId,
                attemptId = attemptId,
                attemptCode = attemptCode,
                remark = extract("remark"),
                method = extract("method"),
                subMerchantId = extract("subMerchantId")
            )
        }
    }
}
