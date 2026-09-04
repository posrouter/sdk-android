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

    companion object {
        const val RETURN_TO_PICKER = "picker"
        const val RETURN_TO_STANDBY = "standby"
    }
}
