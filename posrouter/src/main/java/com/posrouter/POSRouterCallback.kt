package com.posrouter

interface POSRouterCallback {
    fun onResult(result: PaymentResult)

    fun onError(error: POSRouterError)

    /**
     * Optional: terminal / acquirer user cancelled the in-flight pay
     * (`status=CANCELLED`, `cancelReason=user_cancel`).
     * Still followed by [onResult] with the same [result] for backward compatibility.
     */
    fun onUserCancelled(result: PaymentResult) = Unit

    /**
     * Optional: A-side [POSRouter.voidPayment] was acknowledged by the terminal
     * (`status=CANCELLED`, `cancelReason=initiator_void`).
     * Still followed by [onResult] with the same [result] for backward compatibility.
     */
    fun onInitiatorVoided(result: PaymentResult) = Unit

    /**
     * Optional: a remote terminal picked up (claimed) this pay over NATS — it is online and showing
     * the payment UI. Lets the initiator tell "terminal is working on it" apart from "nobody is
     * listening" (terminal offline): with core NATS an offline terminal never receives the pay, so
     * no claim within a few seconds means it was not delivered. May be invoked on a background thread.
     */
    fun onClaimed(orderId: String, attemptId: String) = Unit
}

data class POSRouterError(
    val code: String,
    val message: String
)
