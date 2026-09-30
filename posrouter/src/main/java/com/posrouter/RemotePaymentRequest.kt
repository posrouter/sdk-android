package com.posrouter

/**
 * A pay request that arrived from a remote initiator (gateway/NATS) for this terminal to run.
 *
 * Carries [metadata] verbatim so an initiator can attach order-scoped instructions without another
 * wire field and another SDK release; [returnTo] is the first of those.
 */
data class RemotePaymentRequest(
    val orderId: String,
    val amountCents: Long,
    val currency: String,
    val remark: String? = null,
    val method: String? = null,
    val metadata: Map<String, String> = emptyMap()
) {
    /**
     * Where the terminal should land once this order is finished, since a remote order has no app
     * on this device to hand back to. [RETURN_TO_PICKER] keeps the method picker up so staff can
     * run another attempt on the same order; [RETURN_TO_STANDBY] drops to the idle screen. Null
     * means the initiator did not say — the terminal keeps its own default.
     */
    val returnTo: String?
        get() = (metadata["return_to"] ?: metadata["returnTo"])?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }

    /**
     * True when this is a NATS refund the SDK is already running on the local acquirer, not a pay.
     * [method] stays null on refunds so older terminals never mistake one for a purchase, which is
     * why the terminal has to check this before treating a null [method] as "open the picker".
     */
    val isRefund: Boolean
        get() = metadata[META_OPERATION].equals(OPERATION_REFUND, ignoreCase = true)

    /** Refund attempt id; the acquirer does not echo it back, so the terminal must keep it. */
    val attemptId: String?
        get() = metadata[META_ATTEMPT_ID]?.trim()?.takeIf { it.isNotEmpty() }

    companion object {
        const val RETURN_TO_PICKER = "picker"
        const val RETURN_TO_STANDBY = "standby"
        const val META_OPERATION = "operation"
        const val META_ATTEMPT_ID = "attemptId"
        const val OPERATION_REFUND = "refund"
    }
}
