package com.posrouter

/**
 * Optional callbacks for payment-terminal (device B) UIs.
 * Invoked on the main thread when a listener is registered via [POSRouter.setTerminalListener].
 */
interface POSRouterTerminalListener {
    fun onNatsStateChanged(state: NatsConnectionState) {}

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
}
