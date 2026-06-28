package com.posrouter

/**
 * Optional callbacks for payment-terminal (device B) UIs.
 * Invoked on the main thread when a listener is registered via [POSRouter.setTerminalListener].
 */
interface POSRouterTerminalListener {
    fun onLensingStateChanged(state: LensingConnectionState) {}

    /** Remote pay received on NATS and about to launch the local acquirer. */
    fun onRemotePaymentReceived(
        orderId: String,
        amountCents: Long,
        currency: String,
        remark: String?,
        method: String?
    ) {}

    fun onRemotePaymentLaunchFailed(orderId: String, message: String) {}

    /** Acquirer callback processed (local device completed or cancelled the payment UI). */
    fun onPaymentCompleted(result: PaymentResult) {}

    /** Initiator voided the payment; terminal soft-acknowledged (no forced acquirer exit). */
    fun onRemotePaymentVoided(orderId: String, attemptId: String, message: String?) {}
}
