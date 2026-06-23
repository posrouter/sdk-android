package com.posrouter.core.local

import android.net.Uri
import com.posrouter.POSRouterConfig
import com.posrouter.PaymentResult
import com.posrouter.PaymentStatus
import com.posrouter.WirePaymentRequest

internal object AcquirerCallbackParser {

    fun parsePayCallback(
        uri: Uri,
        config: POSRouterConfig,
        session: WirePaymentRequest?
    ): PaymentResult? {
        if (uri.scheme != "gomenu" || uri.host != "pay_result") return null

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

    private fun mapStatus(raw: String): PaymentStatus = when (raw.uppercase()) {
        "SUCCESS", "APPROVED", "OK" -> PaymentStatus.APPROVED
        "DECLINED", "FAILED", "FAILURE", "FAIL" -> PaymentStatus.DECLINED
        "CANCELLED", "CANCELED", "CANCEL" -> PaymentStatus.CANCELLED
        "" -> PaymentStatus.ERROR
        else -> PaymentStatus.ERROR
    }
}
