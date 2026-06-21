package com.posrouter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PaymentResultTest {

    @Test
    fun jsonRoundTripIncludesOrderId() {
        val original = PaymentResult(
            terminalId = "TID001",
            orderId = "GM202606211347084",
            status = PaymentStatus.APPROVED,
            transactionId = "TXN123",
            amount = 450L,
            currency = "NZD",
            message = "SUCCESS"
        )
        val parsed = PaymentResult.fromJson(original.toJsonString())
        assertEquals("TID001", parsed.terminalId)
        assertEquals("GM202606211347084", parsed.orderId)
        assertEquals(PaymentStatus.APPROVED, parsed.status)
        assertEquals("TXN123", parsed.transactionId)
        assertEquals(450L, parsed.amount)
        assertEquals("NZD", parsed.currency)
        assertEquals("SUCCESS", parsed.message)
    }

    @Test
    fun fromJsonAcceptsLowercaseStatus() {
        val parsed = PaymentResult.fromJson(
            """{"terminalId":"TID001","status":"approved","orderId":"ORD1","amount":100,"currency":"NZD"}"""
        )
        assertEquals(PaymentStatus.APPROVED, parsed.status)
    }
}
