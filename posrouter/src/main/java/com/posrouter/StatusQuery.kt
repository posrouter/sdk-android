package com.posrouter

import com.posrouter.core.lensing.LensingSubjectScope
import com.posrouter.core.lensing.LensingSubjects
import java.util.UUID

/**
 * Asks terminals for the final status of a transaction (see [POSRouter.queryStatus]).
 *
 * With [terminalId] `null` the query is broadcast to every terminal of the merchant;
 * otherwise only that terminal namespace receives it. Terminals that do not know the
 * transaction stay silent, so an empty [StatusQueryResult.answers] means "unknown", not "failed".
 */
data class StatusQueryRequest(
    val orderId: String,
    /** Target terminal; `null` broadcasts merchant-wide. */
    val terminalId: String? = null,
    /** Narrow to one try; `null` returns every recorded try of [orderId]. */
    val attemptId: String? = null,
    /** [OPERATION_PAY], [OPERATION_REFUND], or `null` for both. */
    val operation: String? = null,
    /**
     * Sub-merchant of the target terminal namespace. For merchant-wide broadcast it also
     * filters answers to terminals configured with this sub-merchant.
     */
    val subMerchantId: String? = null,
    /** How long to collect answers before [StatusQueryCallback.onComplete]. */
    val timeoutMs: Long = DEFAULT_TIMEOUT_MS
) {
    internal fun toWire(config: POSRouterConfig): WireStatusQuery {
        val normalizedOrderId = orderId.trim()
        require(normalizedOrderId.isNotEmpty()) { "orderId must not be blank" }
        require(timeoutMs > 0) { "timeoutMs must be positive" }
        val normalizedOperation = operation?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
        require(normalizedOperation == null || normalizedOperation in SUPPORTED_OPERATIONS) {
            "operation must be one of $SUPPORTED_OPERATIONS"
        }
        val target = terminalId?.trim()?.takeIf { it.isNotEmpty() }
        require(target != LensingSubjects.BROADCAST_TERMINAL_ID) {
            "terminalId '${LensingSubjects.BROADCAST_TERMINAL_ID}' is reserved; pass null to broadcast"
        }
        return WireStatusQuery(
            queryId = UUID.randomUUID().toString(),
            orderId = normalizedOrderId,
            attemptId = attemptId?.trim()?.takeIf { it.isNotEmpty() },
            operation = normalizedOperation,
            acquirerCode = config.acquirerCode,
            merchantId = config.merchantId,
            subMerchantId = subMerchantId?.trim()?.takeIf { it.isNotEmpty() },
            terminalId = target ?: LensingSubjects.BROADCAST_TERMINAL_ID,
            requestedBy = config.participantCode,
            requestedAt = System.currentTimeMillis()
        )
    }

    companion object {
        const val OPERATION_PAY = "pay"
        const val OPERATION_REFUND = "refund"
        const val DEFAULT_TIMEOUT_MS = 5_000L

        private val SUPPORTED_OPERATIONS = setOf(OPERATION_PAY, OPERATION_REFUND)
    }
}

/**
 * Answers collected for one [StatusQueryRequest]. Each answer is the final [PaymentResult]
 * a terminal recorded, carrying [META_QUERY_ID] and [META_FINALIZED_AT] in its metadata.
 * Refund answers have `attemptId` ending in `#refund` (or the caller-supplied refund attempt id).
 */
data class StatusQueryResult(
    val queryId: String,
    val orderId: String,
    val answers: List<PaymentResult>
) {
    val isAnswered: Boolean get() = answers.isNotEmpty()

    companion object {
        /** Correlates an answer (also broadcast on `.result`) with the query that triggered it. */
        const val META_QUERY_ID = "queryId"

        /** Epoch millis when the terminal recorded the final status. */
        const val META_FINALIZED_AT = "finalizedAt"
    }
}

interface StatusQueryCallback {
    /** Each terminal answer as it arrives (main thread). Answers are also delivered to pending pay/refund callbacks. */
    fun onAnswer(result: PaymentResult) = Unit

    /** Called once after [StatusQueryRequest.timeoutMs]; [StatusQueryResult.answers] may be empty. */
    fun onComplete(result: StatusQueryResult)

    fun onError(error: POSRouterError)
}

internal data class WireStatusQuery(
    val queryId: String,
    val orderId: String,
    val attemptId: String?,
    val operation: String?,
    val acquirerCode: String,
    val merchantId: String,
    val subMerchantId: String?,
    /** Target terminal or [LensingSubjects.BROADCAST_TERMINAL_ID]. */
    val terminalId: String,
    val requestedBy: String,
    val requestedAt: Long
) {
    val isBroadcast: Boolean get() = terminalId == LensingSubjects.BROADCAST_TERMINAL_ID

    /** Merchant-wide queries are published with the `_` sub segment; terminals subscribe with `*`. */
    fun subjectScope(): LensingSubjectScope = LensingSubjectScope(
        acquirerCode = acquirerCode,
        merchantId = merchantId,
        subMerchantId = if (isBroadcast) null else subMerchantId,
        terminalId = terminalId
    )

    fun toJsonString(): String {
        val fields = mutableListOf(
            """"queryId":"${escapeJson(queryId)}"""",
            """"orderId":"${escapeJson(orderId)}"""",
            """"acquirerCode":"${escapeJson(acquirerCode)}"""",
            """"merchantId":"${escapeJson(merchantId)}"""",
            """"terminalId":"${escapeJson(terminalId)}"""",
            """"requestedBy":"${escapeJson(requestedBy)}"""",
            """"requestedAt":$requestedAt"""
        )
        attemptId?.let { fields.add(""""attemptId":"${escapeJson(it)}"""") }
        operation?.let { fields.add(""""operation":"${escapeJson(it)}"""") }
        subMerchantId?.let { fields.add(""""subMerchantId":"${escapeJson(it)}"""") }
        return "{${fields.joinToString(",")}}"
    }

    private fun escapeJson(value: String): String =
        value.replace("\\", "\\\\").replace("\"", "\\\"")

    companion object {
        fun fromJson(json: String): WireStatusQuery? {
            fun extract(key: String): String? {
                val pattern = "\"$key\"\\s*:\\s*\"([^\"]*)\"".toRegex()
                return pattern.find(json)?.groupValues?.getOrNull(1)?.takeIf { it.isNotEmpty() }
            }
            fun extractLong(key: String): Long? {
                val pattern = "\"$key\"\\s*:\\s*(\\d+)".toRegex()
                return pattern.find(json)?.groupValues?.getOrNull(1)?.toLongOrNull()
            }

            return WireStatusQuery(
                queryId = extract("queryId") ?: return null,
                orderId = extract("orderId") ?: extract("orderid") ?: return null,
                attemptId = extract("attemptId"),
                operation = extract("operation")?.lowercase(),
                acquirerCode = extract("acquirerCode") ?: return null,
                merchantId = extract("merchantId") ?: return null,
                subMerchantId = extract("subMerchantId"),
                terminalId = extract("terminalId") ?: LensingSubjects.BROADCAST_TERMINAL_ID,
                requestedBy = extract("requestedBy") ?: "",
                requestedAt = extractLong("requestedAt") ?: 0L
            )
        }
    }
}
