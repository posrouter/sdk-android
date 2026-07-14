package com.posrouter

import com.posrouter.core.lensing.PaymentAttemptRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class POSRouterCallbackCancelEventsTest {

    @Test
    fun dispatchCallsOnUserCancelledBeforeOnResult() {
        val events = mutableListOf<String>()
        val callback = object : POSRouterCallback {
            override fun onResult(result: PaymentResult) {
                events.add("onResult")
            }

            override fun onError(error: POSRouterError) = Unit

            override fun onUserCancelled(result: PaymentResult) {
                events.add("onUserCancelled")
            }
        }
        PaymentAttemptRegistry.dispatchOptionalCancelEvents(
            callback,
            cancelledResult(PaymentCancelReason.USER_CANCEL)
        )
        callback.onResult(cancelledResult(PaymentCancelReason.USER_CANCEL))
        assertEquals(listOf("onUserCancelled", "onResult"), events)
    }

    @Test
    fun dispatchCallsOnInitiatorVoided() {
        var voided = false
        val callback = object : POSRouterCallback {
            override fun onResult(result: PaymentResult) = Unit
            override fun onError(error: POSRouterError) = Unit
            override fun onInitiatorVoided(result: PaymentResult) {
                voided = true
            }
        }
        PaymentAttemptRegistry.dispatchOptionalCancelEvents(
            callback,
            cancelledResult(PaymentCancelReason.INITIATOR_VOID)
        )
        assertTrue(voided)
    }

    @Test
    fun dispatchSkipsOptionalHooksForApproved() {
        var cancelled = false
        val callback = object : POSRouterCallback {
            override fun onResult(result: PaymentResult) = Unit
            override fun onError(error: POSRouterError) = Unit
            override fun onUserCancelled(result: PaymentResult) {
                cancelled = true
            }
        }
        PaymentAttemptRegistry.dispatchOptionalCancelEvents(
            callback,
            PaymentResult(
                terminalId = "T1",
                status = PaymentStatus.APPROVED,
                transactionId = "x",
                amount = 100,
                currency = "NZD",
                message = null,
                orderId = "o1",
                attemptId = "a1"
            )
        )
        assertEquals(false, cancelled)
    }

    private fun cancelledResult(reason: String) = PaymentResult(
        terminalId = "T1",
        status = PaymentStatus.CANCELLED,
        transactionId = null,
        amount = 100,
        currency = "NZD",
        message = "cancelled",
        orderId = "o1",
        attemptId = "a1",
        metadata = mapOf("cancelReason" to reason)
    )
}
