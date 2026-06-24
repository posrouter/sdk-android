package com.posrouter.core.lensing

import com.posrouter.PaymentStatus
import com.posrouter.WirePaymentRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PaymentAttemptRegistryTest {

    private fun sampleWire(attemptId: String = "ORD1#1") = WirePaymentRequest(
        terminalId = "TID001",
        amount = 500,
        currency = "NZD",
        targetPackageName = "com.ezypos.app",
        targetScheme = "ezypos://",
        acquirerCode = "SUPY",
        merchantId = "abc123",
        orderId = "ORD1",
        attemptId = attemptId,
        attemptCode = "SUPY"
    )

    @Test
    fun attemptIdResolverIncrementsWhenOmitted() {
        assertEquals("RESOLVE_ORD#1", PaymentAttemptIdResolver.resolve("RESOLVE_ORD", null))
        assertEquals("RESOLVE_ORD#2", PaymentAttemptIdResolver.resolve("RESOLVE_ORD", null))
        assertEquals("custom", PaymentAttemptIdResolver.resolve("RESOLVE_ORD", "custom"))
    }

    @Test
    fun storeAndLookupOpenByOrder() {
        val wire = sampleWire()
        PaymentAttemptRegistry.store(wire, callback = null)
        assertEquals(wire.orderId, PaymentAttemptRegistry.lookupOpenByOrder("TID001", "ORD1")?.orderId)
        PaymentAttemptRegistry.close("TID001", "ORD1", "ORD1#1")
    }

    @Test
    fun hasInitiatorCallbackWhenPayCallbackRegistered() {
        val wire = sampleWire()
        assertFalse(PaymentAttemptRegistry.hasInitiatorCallback(wire))
        PaymentAttemptRegistry.store(wire, object : com.posrouter.POSRouterCallback {
            override fun onResult(result: com.posrouter.PaymentResult) = Unit
            override fun onError(error: com.posrouter.POSRouterError) = Unit
        })
        assertTrue(PaymentAttemptRegistry.hasInitiatorCallback(wire))
        PaymentAttemptRegistry.close("TID001", "ORD1", "ORD1#1")
    }

    @Test
    fun deliverCallbackRunsOnce() {
        val wire = sampleWire()
        var count = 0
        val callback = object : com.posrouter.POSRouterCallback {
            override fun onResult(result: com.posrouter.PaymentResult) {
                count++
            }

            override fun onError(error: com.posrouter.POSRouterError) = Unit
        }
        PaymentAttemptRegistry.store(wire, callback)
        val result = com.posrouter.PaymentResult(
            terminalId = "TID001",
            orderId = "ORD1",
            attemptId = "ORD1#1",
            status = PaymentStatus.APPROVED,
            transactionId = null,
            amount = 500,
            currency = "NZD",
            message = null
        )
        assertTrue(PaymentAttemptRegistry.deliverCallback(result))
        assertFalse(PaymentAttemptRegistry.deliverCallback(result))
        assertEquals(1, count)
    }

    @Test
    fun closeBeforeDeliverCallbackDropsInitiatorNotification() {
        val wire = sampleWire("ORD-close-first")
        var count = 0
        PaymentAttemptRegistry.store(
            wire,
            object : com.posrouter.POSRouterCallback {
                override fun onResult(result: com.posrouter.PaymentResult) {
                    count++
                }

                override fun onError(error: com.posrouter.POSRouterError) = Unit
            }
        )
        val result = com.posrouter.PaymentResult(
            terminalId = "TID001",
            orderId = "ORD-close-first",
            attemptId = "ORD-close-first#1",
            status = PaymentStatus.CANCELLED,
            transactionId = null,
            amount = 500,
            currency = "NZD",
            message = "CANCEL",
            metadata = mapOf("cancelReason" to "user_cancel")
        )
        PaymentAttemptRegistry.close("TID001", "ORD-close-first", "ORD-close-first#1")
        assertFalse(PaymentAttemptRegistry.deliverCallback(result))
        assertEquals(0, count)
    }
}
