package com.posrouter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PaymentResultTest {

    @Test
    fun jsonRoundTripIncludesAttemptFields() {
        val original = PaymentResult(
            terminalId = "TID001",
            orderId = "GM202606211347084",
            attemptId = "GM202606211347084#1",
            attemptCode = "SUPY",
            subMerchantId = "REST01",
            status = PaymentStatus.CANCELLED,
            transactionId = null,
            amount = 450L,
            currency = "NZD",
            message = "Voided by initiator",
            metadata = mapOf("cancelReason" to "initiator_void")
        )
        val parsed = PaymentResult.fromJson(original.toJsonString())
        assertEquals("GM202606211347084#1", parsed.attemptId)
        assertEquals("initiator_void", parsed.metadata["cancelReason"])
    }

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
    fun fromJsonParsesCancelledResultWithMetadataFromNatsWire() {
        val json = """
            {
              "terminalId": "TID001",
              "status": "cancelled",
              "amount": 551,
              "currency": "NZD",
              "orderId": "60690-1b69aaccad",
              "attemptId": "60690-1b69aaccad#1",
              "metadata": { "cancelReason": "user_cancel" }
            }
        """.trimIndent()

        val parsed = PaymentResult.fromJson(json)

        assertEquals("TID001", parsed.terminalId)
        assertEquals(PaymentStatus.CANCELLED, parsed.status)
        assertEquals(551L, parsed.amount)
        assertEquals("NZD", parsed.currency)
        assertEquals("60690-1b69aaccad", parsed.orderId)
        assertEquals("60690-1b69aaccad#1", parsed.attemptId)
        assertEquals("user_cancel", parsed.metadata["cancelReason"])
    }

    @Test
    fun fromJsonAcceptsLowercaseStatus() {
        val parsed = PaymentResult.fromJson(
            """{"terminalId":"TID001","status":"approved","orderId":"ORD1","amount":100,"currency":"NZD"}"""
        )
        assertEquals(PaymentStatus.APPROVED, parsed.status)
    }
}
