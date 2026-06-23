package com.posrouter

/** Values for [PaymentResult.metadata] key `cancelReason`. */
object PaymentCancelReason {
    /** Initiator (A-side) voided the payment request over NATS. */
    const val INITIATOR_VOID = "initiator_void"

    /** User cancelled in the acquirer UI (Ezypos). */
    const val USER_CANCEL = "user_cancel"
}
