package com.posrouter.core.local

import android.net.Uri
import com.posrouter.POSRouterConfig
import com.posrouter.PaymentCancelReason
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
        if (type.isNotEmpty() && !isPayCallbackType(type)) return null

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

        val message = uri.getQueryParameter("message") ?: statusRaw.ifBlank { null }
        val cancelReasonRaw = uri.getQueryParameter("cancel_reason")
            ?: uri.getQueryParameter("cancelReason")
        val metadata = buildMap {
            cancelReasonRaw?.trim()?.takeIf { it.isNotEmpty() }?.let { put("cancelReason", it) }
        }
        val status = resolvePayStatus(statusRaw, cancelReasonRaw, message)

        return PaymentResult(
            terminalId = session?.terminalId ?: config.terminalId,
            orderId = orderId,
            attemptId = attemptId,
            attemptCode = session?.attemptCode,
            subMerchantId = session?.subMerchantId,
            status = status,
            transactionId = transactionId,
            amount = session?.amount ?: 0L,
            currency = session?.currency ?: config.currency,
            message = message,
            metadata = metadata
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

    private fun isPayCallbackType(type: String): Boolean = when (type) {
        "PAY", "CANCEL", "CANCELED", "CANCELLED" -> true
        else -> false
    }

    private fun mapStatus(raw: String): PaymentStatus = when (raw.trim().uppercase()) {
        "SUCCESS", "APPROVED", "OK" -> PaymentStatus.APPROVED
        "DECLINED", "FAILED", "FAILURE", "FAIL" -> PaymentStatus.DECLINED
        "CANCELLED", "CANCELED", "CANCEL", "USER_CANCEL", "USERCANCEL" -> PaymentStatus.CANCELLED
        "" -> PaymentStatus.ERROR
        else -> PaymentStatus.ERROR
    }

    private fun resolvePayStatus(
        statusRaw: String,
        cancelReasonRaw: String?,
        message: String?
    ): PaymentStatus {
        val normalizedReason = cancelReasonRaw?.trim()?.lowercase().orEmpty()
        if (normalizedReason == PaymentCancelReason.USER_CANCEL ||
            normalizedReason == PaymentCancelReason.INITIATOR_VOID
        ) {
            return PaymentStatus.CANCELLED
        }
        val mapped = mapStatus(statusRaw)
        if (mapped == PaymentStatus.CANCELLED) return PaymentStatus.CANCELLED
        if (mapped != PaymentStatus.APPROVED && messageIndicatesUserCancel(message)) {
            return PaymentStatus.CANCELLED
        }
        return mapped
    }

    internal fun messageIndicatesUserCancel(message: String?): Boolean {
        val normalized = message?.trim()?.lowercase().orEmpty()
        if (normalized.isEmpty()) return false
        return CANCEL_MESSAGE_KEYWORDS.any { normalized.contains(it) }
    }

    private val CANCEL_MESSAGE_KEYWORDS = listOf(
        "cancel",
        "cancelled",
        "canceled",
        "user abort",
        "aborted",
        "trans cancel",
        "transaction cancel",
        "user cancel",
        "payment cancel",
        "操作取消",
        "用户取消",
        "交易取消"
    )

    private const val PAY_RESULT_HOST = "pay_result"
}
