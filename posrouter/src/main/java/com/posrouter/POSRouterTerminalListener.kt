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

    /**
     * Same event as the five-argument form, with the initiator's [RemotePaymentRequest.metadata]
     * intact. The SDK calls this one; the default body forwards to the older signature so existing
     * implementations keep working untouched. Override this instead to read metadata-carried
     * instructions such as [RemotePaymentRequest.returnTo].
     */
    fun onRemotePaymentReceived(request: RemotePaymentRequest) {
        onRemotePaymentReceived(
            request.orderId,
            request.amountCents,
            request.currency,
            request.remark,
            request.method
        )
    }

    fun onRemotePaymentLaunchFailed(orderId: String, message: String) {}

    /** Acquirer callback processed (local device completed or cancelled the payment UI). */
    fun onPaymentCompleted(result: PaymentResult) {}

    /** Initiator voided the payment; terminal soft-acknowledged (no forced acquirer exit). */
    fun onRemotePaymentVoided(orderId: String, attemptId: String, message: String?) {}
}
