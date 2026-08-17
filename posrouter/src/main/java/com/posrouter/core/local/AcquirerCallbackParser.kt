package com.posrouter.core.local

import android.net.Uri
import com.posrouter.POSRouterConfig
import com.posrouter.PaymentCancelReason
import com.posrouter.PaymentResult
import com.posrouter.PaymentStatus
import com.posrouter.WirePaymentRequest
import com.posrouter.core.lensing.RefundAttemptIdResolver
import com.posrouter.core.lensing.RefundAttemptRegistry
import org.json.JSONObject
import java.math.BigDecimal
import java.math.RoundingMode

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
        // On SUCCESS the acquirer app returns the full order object as the `order` param. It carries the
        // charged total (inclusive of any surcharge the terminal added) and the surcharge itself — the
        // requested `session.amount` does not, so trust the returned total when present.
        val orderJson = uri.getQueryParameter("order")
        val orderAmounts = parseEzyposOrderAmounts(orderJson)
        val metadata = buildMap {
            cancelReasonRaw?.trim()?.takeIf { it.isNotEmpty() }?.let { put("cancelReason", it) }
            orderAmounts?.surchargeCents?.let { put("surcharge", it.toString()) }
            putAll(parseEzyposCardDetails(orderJson, uri.getQueryParameter(PARAM_CARD_NUMBER)))
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
            amount = orderAmounts?.totalCents?.takeIf { it > 0 } ?: session?.amount ?: 0L,
            currency = session?.currency ?: config.currency,
            message = message,
            metadata = metadata
        )
    }

    /** Total (inclusive) and surcharge, in minor units, pulled from the acquirer's returned order JSON. */
    private data class EzyposOrderAmounts(val totalCents: Long?, val surchargeCents: Long?)

    /**
     * The `order` callback param is the acquirer order object serialised as JSON. `total_amount_minor`
     * is the charged total in cents; `total_amount` / `surcharge` are decimal-dollar strings. Surcharge
     * is only reported when actually applied (`retail_surcharge`), so a bare/zero value is dropped.
     */
    private fun parseEzyposOrderAmounts(orderJson: String?): EzyposOrderAmounts? {
        if (orderJson.isNullOrBlank()) return null
        return try {
            val obj = JSONObject(orderJson)
            val totalCents = obj.optLong("total_amount_minor", -1L)
                .takeIf { it > 0 }
                ?: decimalToMinor(obj.optString("total_amount"))
            val surchargeCents = decimalToMinor(obj.optString("surcharge"))
                ?.takeIf { it > 0 && obj.optBoolean("retail_surcharge", true) }
            EzyposOrderAmounts(totalCents, surchargeCents)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Card details the acquirer reports for an EMV/card sale, so the terminal can print a standard EFTPOS
     * slip instead of a bare amount. Everything here is optional: a QR / wallet sale carries none of it,
     * and the receipt simply omits what is absent — no field is ever synthesised.
     *
     * The PAN is deliberately reduced to its last four digits **here, at the boundary**, and the full value
     * is never copied into [PaymentResult]: metadata is published over Lensing and forwarded to the back
     * office, so a full PAN entering it would spread cardholder data well past this process.
     */
    private fun parseEzyposCardDetails(orderJson: String?, cardNumberParam: String?): Map<String, String> {
        val obj = orderJson?.takeIf { it.isNotBlank() }?.let {
            try {
                JSONObject(it)
            } catch (e: Exception) {
                null
            }
        }
        fun field(name: String): String? =
            obj?.optString(name)?.trim()?.takeIf { it.isNotEmpty() && !it.equals("null", ignoreCase = true) }

        return buildMap {
            // Each value is capped: it crosses a trust boundary from a third-party app straight into
            // metadata that is published and forwarded, and none of these fields is ever long.
            fun expose(key: String, value: String?) {
                value?.trim()?.takeIf { it.isNotEmpty() && it.length <= MAX_CARD_FIELD_LENGTH }
                    ?.let { put(key, it) }
            }
            expose("cardScheme", field("card_scheme")?.uppercase())
            expose("cardLast4", lastFourDigits(field("card_number") ?: cardNumberParam))
            expose("cardEntryMode", field("card_pan_entry_mode")?.uppercase())
            expose("cardAppLabel", field("card_app_label")?.let { applicationLabel(it) })
            expose("cardAid", field("card_aid")?.uppercase())
            expose("cardPanSeqNo", field("card_pan_seq_no"))
            expose("authCode", field("auth_code"))
        }
    }

    /** The last four PAN digits, ignoring the masking characters the acquirer pads the value with. */
    private fun lastFourDigits(pan: String?): String? {
        val digits = pan?.filter { it in '0'..'9' }.orEmpty()
        return digits.takeIf { it.length >= 4 }?.takeLast(4)
    }

    /**
     * The EMV tag 50 application label as text. The acquirer sends it hex-encoded, so hex-shaped input is
     * decoded — and dropped if it does not decode to something printable, because a value that was meant
     * to be hex but is not readable text is garbage, and garbage must not reach a receipt. Input that was
     * never hex-shaped is already a plain label and is used as-is.
     */
    private fun applicationLabel(raw: String): String? =
        if (isHexShaped(raw)) decodeHexAscii(raw) else raw

    private fun isHexShaped(value: String): Boolean =
        value.length >= 2 && value.length % 2 == 0 &&
            value.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }

    /** Hex-encoded printable ASCII → text, or null when it does not decode to printable text. */
    private fun decodeHexAscii(value: String): String? {
        val decoded = buildString {
            for (i in value.indices step 2) {
                val code = value.substring(i, i + 2).toInt(16)
                // Printable ASCII only: anything else means this was not a text label after all.
                if (code < 0x20 || code > 0x7E) return null
                append(code.toChar())
            }
        }
        return decoded.trim().takeIf { it.isNotEmpty() }
    }

    private fun decimalToMinor(value: String?): Long? {
        val trimmed = value?.trim().orEmpty()
        if (trimmed.isEmpty()) return null
        return try {
            BigDecimal(trimmed).multiply(BigDecimal(100)).setScale(0, RoundingMode.HALF_UP).longValueExact()
        } catch (e: Exception) {
            null
        }
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

    /** Acquirer callback param carrying the PAN; only its last four digits are ever kept. */
    private const val PARAM_CARD_NUMBER = "card_number"

    /**
     * Longest card field accepted from the acquirer. These values cross a trust boundary from a
     * third-party app into metadata that is published and forwarded, and the real ones are short — the
     * longest, a 16-byte AID, is 32 hex characters.
     */
    private const val MAX_CARD_FIELD_LENGTH = 64
}
