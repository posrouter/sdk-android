package com.posrouter.core.local

import android.net.Uri
import com.posrouter.POSRouterConfig
import com.posrouter.PaymentResult
import com.posrouter.PaymentStatus
import com.posrouter.WirePaymentRequest
import com.posrouter.core.lensing.RefundAttemptIdResolver
import com.posrouter.core.lensing.RefundAttemptRegistry

internal object AcquirerCallbackParser {

    /** Level 1: host and query are normative; scheme varies by terminal app (e.g. gomenu, posrouter-kiosk). */
    fun isPayResultCallback(uri: Uri): Boolean =
        uri.host.equals(PAY_RESULT_HOST, ignoreCase = true)

    fun parsePayCallback(
        uri: Uri,
        config: POSRouterConfig,
        session: WirePaymentRequest?
    ): PaymentResult? {
        if (!isPayResultCallback(uri)) return null

        val type = uri.getQueryParameter("type")?.uppercase().orEmpty()
        if (type.isNotEmpty() && type != "PAY") return null

        val orderId = uri.getQueryParameter("orderid")
            ?: uri.getQueryParameter("orderId")
            ?: return null

        val statusRaw = uri.getQueryParameter("status").orEmpty()
        val transactionId = uri.getQueryParameter("transactionid")
            ?: uri.getQueryParameter("transactionId")
            ?: uri.getQueryParameter("trxid")

        val attemptId = uri.getQueryParameter("attemptid")
            ?: uri.getQueryParameter("attemptId")
            ?: session?.attemptId

        return PaymentResult(
            terminalId = session?.terminalId ?: config.terminalId,
            orderId = orderId,
            attemptId = attemptId,
            attemptCode = session?.attemptCode,
            subMerchantId = session?.subMerchantId,
            status = mapStatus(statusRaw),
            transactionId = transactionId,
            amount = session?.amount ?: 0L,
            currency = session?.currency ?: config.currency,
            message = uri.getQueryParameter("message") ?: statusRaw.ifBlank { null }
        )
    }

    fun parseRefundCallback(
        uri: Uri,
        config: POSRouterConfig
    ): PaymentResult? {
        if (!isPayResultCallback(uri)) return null

        val type = uri.getQueryParameter("type")?.uppercase().orEmpty()
        if (type != "REFUND") return null

        val orderId = uri.getQueryParameter("orderid")
            ?: uri.getQueryParameter("orderId")
            ?: return null

        val statusRaw = uri.getQueryParameter("status").orEmpty()
        val transactionId = uri.getQueryParameter("transactionid")
            ?: uri.getQueryParameter("transactionId")
            ?: uri.getQueryParameter("trxid")

        val attemptId = uri.getQueryParameter("attemptid")
            ?: uri.getQueryParameter("attemptId")
            ?: RefundAttemptIdResolver.defaultAttemptId(orderId)

        val pending = RefundAttemptRegistry.lookup(config.terminalId, orderId, attemptId)

        return PaymentResult(
            terminalId = pending?.terminalId ?: config.terminalId,
            orderId = orderId,
            attemptId = attemptId,
            attemptCode = pending?.attemptCode,
            subMerchantId = pending?.subMerchantId,
            status = mapStatus(statusRaw),
            transactionId = transactionId,
            amount = pending?.amount ?: 0L,
            currency = pending?.currency ?: config.currency,
            message = uri.getQueryParameter("message") ?: statusRaw.ifBlank { null },
            metadata = mapOf("operation" to "refund")
        )
    }

    private fun mapStatus(raw: String): PaymentStatus = when (raw.uppercase()) {
        "SUCCESS", "APPROVED", "OK" -> PaymentStatus.APPROVED
        "DECLINED", "FAILED", "FAILURE", "FAIL" -> PaymentStatus.DECLINED
        "CANCELLED", "CANCELED", "CANCEL" -> PaymentStatus.CANCELLED
        "" -> PaymentStatus.ERROR
        else -> PaymentStatus.ERROR
    }

    private const val PAY_RESULT_HOST = "pay_result"
}
