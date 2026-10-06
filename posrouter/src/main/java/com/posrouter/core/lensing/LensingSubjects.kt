package com.posrouter.core.lensing

import com.posrouter.POSRouterConfig
import com.posrouter.WirePaymentRequest

/** Fixed 6-token NATS subject namespace: lensing.{acquirer}.{merchant}.{sub}.{tid}.{verb} */
internal data class LensingSubjectScope(
    val acquirerCode: String,
    val merchantId: String,
    val subMerchantId: String?,
    val terminalId: String
) {
    companion object {
        fun fromConfig(config: POSRouterConfig): LensingSubjectScope =
            LensingSubjectScope(
                acquirerCode = config.acquirerCode,
                merchantId = config.merchantId,
                subMerchantId = config.subMerchantId,
                terminalId = config.terminalId
            )

        fun fromWire(wire: WirePaymentRequest): LensingSubjectScope =
            LensingSubjectScope(
                acquirerCode = wire.acquirerCode,
                merchantId = wire.merchantId,
                subMerchantId = wire.subMerchantId,
                terminalId = wire.terminalId
            )

        fun fromRefund(wire: com.posrouter.WireRefundRequest): LensingSubjectScope =
            wire.subjectScope()
    }
}

internal object LensingSubjects {
    /** Sentinel when [subMerchantId] is absent; real sub-merchant ids must not use this value. */
    const val SUB_MERCHANT_PLACEHOLDER = "_"

    /** Terminal segment addressing all terminals of a merchant; real terminal ids must not use this value. */
    const val BROADCAST_TERMINAL_ID = "_ALL"

    fun paySubject(scope: LensingSubjectScope): String = verbSubject(scope, "pay")

    fun resultSubject(scope: LensingSubjectScope): String = verbSubject(scope, "result")

    fun claimedSubject(scope: LensingSubjectScope): String = verbSubject(scope, "claimed")

    fun voidSubject(scope: LensingSubjectScope): String = verbSubject(scope, "void")

    fun refundSubject(scope: LensingSubjectScope): String = verbSubject(scope, "refund")

    /**
     * Status query. With [BROADCAST_TERMINAL_ID] as terminal segment the query targets every
     * terminal of the merchant, e.g. lensing.SUPY.abc123._._ALL.query
     */
    fun querySubject(scope: LensingSubjectScope): String = verbSubject(scope, "query")

    /**
     * Terminal-side subscription for merchant-wide queries regardless of sub-merchant segment,
     * e.g. lensing.SUPY.abc123.*._ALL.query
     */
    fun merchantQueryWildcard(scope: LensingSubjectScope): String =
        listOf(
            "lensing",
            sanitizeSegment(scope.acquirerCode.uppercase(), "acquirerCode"),
            sanitizeSegment(scope.merchantId, "merchantId"),
            "*",
            BROADCAST_TERMINAL_ID,
            "query"
        ).joinToString(".")

    /** Subscribe prefix for all verbs on one terminal namespace, e.g. lensing.SUPY.abc123._.TID001.> */
    fun terminalWildcard(scope: LensingSubjectScope): String =
        "${namespacePrefix(scope)}.>"

    fun subMerchantSegment(subMerchantId: String?): String {
        val trimmed = subMerchantId?.trim().orEmpty()
        require(trimmed != SUB_MERCHANT_PLACEHOLDER) {
            "subMerchantId must not be the reserved placeholder '$SUB_MERCHANT_PLACEHOLDER'"
        }
        return trimmed.ifEmpty { SUB_MERCHANT_PLACEHOLDER }
    }

    private fun verbSubject(scope: LensingSubjectScope, verb: String): String =
        "${namespacePrefix(scope)}.$verb"

    private fun namespacePrefix(scope: LensingSubjectScope): String =
        listOf(
            "lensing",
            sanitizeSegment(scope.acquirerCode.uppercase(), "acquirerCode"),
            sanitizeSegment(scope.merchantId, "merchantId"),
            subMerchantSegment(scope.subMerchantId),
            sanitizeSegment(scope.terminalId, "terminalId")
        ).joinToString(".")

    private fun sanitizeSegment(value: String, label: String): String {
        require(value.isNotBlank()) { "$label must not be blank" }
        require('.' !in value) { "$label must not contain '.'" }
        return value
    }
}
