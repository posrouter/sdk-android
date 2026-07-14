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
}

data class POSRouterError(
    val code: String,
    val message: String
)
