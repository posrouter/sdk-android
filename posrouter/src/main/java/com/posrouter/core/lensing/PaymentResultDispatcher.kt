package com.posrouter.core.lensing

import android.util.Log
import com.posrouter.PaymentResult

internal enum class PaymentResultSource {
    /** Parsed from acquirer deeplink on this device. */
    LOCAL_CALLBACK,
    /** Received on NATS result subject. */
    NATS_INBOUND,
    /** Caller invoked [com.posrouter.POSRouter.publishPaymentResult]. */
    MANUAL_PUBLISH,
    /** Terminal ack after initiator void (soft void, no acquirer deeplink). */
    VOID_ACK
}

internal object PaymentResultDispatcher {
    private const val TAG = "POSRouter.Result"

    fun deliver(
        result: PaymentResult,
        source: PaymentResultSource,
        publishNats: Boolean = source != PaymentResultSource.NATS_INBOUND,
        dispatchTerminal: Boolean = true
    ): Boolean {
        if (!PaymentResultLedger.markIfFirst(result)) {
            Log.d(TAG, "Duplicate result ignored order=${result.orderId} attempt=${result.attemptId}")
            return false
        }

        val orderId = result.orderId
        val attemptId = result.attemptId
        if (orderId != null && attemptId != null) {
            PaymentAttemptRegistry.close(result.terminalId, orderId, attemptId)
            PaymentClaimRegistry.releaseClaim(result.terminalId, orderId, attemptId)
        }

        val deliveredLocally = PaymentAttemptRegistry.deliverCallback(result)
        if (deliveredLocally) {
            Log.i(TAG, "Payment result delivered to pay callback order=$orderId attempt=$attemptId")
        }

        if (publishNats) {
            LensingProtocolEngine.publishPaymentResult(result)
        }

        if (dispatchTerminal) {
            TerminalEventDispatcher.dispatchPaymentCompleted(result)
        }

        return true
    }
}
