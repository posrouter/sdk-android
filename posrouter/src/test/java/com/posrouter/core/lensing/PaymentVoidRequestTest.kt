package com.posrouter.core.lensing

import org.junit.Assert.assertEquals
import org.junit.Test

class PaymentVoidRequestTest {

    @Test
    fun jsonRoundTrip() {
        val original = PaymentVoidRequest(
            acquirerCode = "SUPY",
            merchantId = "abc123",
            subMerchantId = "REST01",
            terminalId = "TID001",
            orderId = "GM001",
            attemptId = "GM001#1",
            reason = PaymentVoidRequest.REASON_INITIATOR_VOID,
            voidedAt = 1718800000000L
        )
        val parsed = PaymentVoidRequest.fromJson(original.toJsonString())
        assertEquals(original.acquirerCode, parsed?.acquirerCode)
        assertEquals(original.merchantId, parsed?.merchantId)
        assertEquals(original.subMerchantId, parsed?.subMerchantId)
        assertEquals(original.terminalId, parsed?.terminalId)
        assertEquals(original.orderId, parsed?.orderId)
        assertEquals(original.attemptId, parsed?.attemptId)
        assertEquals(original.reason, parsed?.reason)
        assertEquals(original.voidedAt, parsed?.voidedAt)
    }

    @Test
    fun subjectScopeUsesWireNamespace() {
        val request = PaymentVoidRequest(
            acquirerCode = "SUPY",
            merchantId = "abc123",
            subMerchantId = null,
            terminalId = "TID001",
            orderId = "GM001",
            attemptId = "GM001#1"
        )
        assertEquals(
            "lensing.SUPY.abc123._.TID001.void",
            LensingSubjects.voidSubject(request.subjectScope())
        )
    }
}
