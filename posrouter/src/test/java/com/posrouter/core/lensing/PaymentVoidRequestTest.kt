package com.posrouter.core.lensing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PaymentVoidRequestTest {

    @Test
    fun jsonRoundTrip() {
        val original = PaymentVoidRequest(
            terminalId = "TID001",
            orderId = "GM001",
            attemptId = "GM001#1",
            reason = PaymentVoidRequest.REASON_INITIATOR_VOID,
            voidedAt = 1718800000000L
        )
        val parsed = PaymentVoidRequest.fromJson(original.toJsonString())
        assertEquals(original.terminalId, parsed?.terminalId)
        assertEquals(original.orderId, parsed?.orderId)
        assertEquals(original.attemptId, parsed?.attemptId)
        assertEquals(original.reason, parsed?.reason)
        assertEquals(original.voidedAt, parsed?.voidedAt)
    }
}
