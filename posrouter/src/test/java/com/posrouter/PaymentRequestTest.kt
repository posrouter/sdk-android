package com.posrouter

import com.posrouter.core.registry.AcquirerRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PaymentRequestTest {

    private val config = POSRouterConfig(
        participantCode = "GPOS",
        participantKey = "test-key",
        terminalId = "TID001",
        acquirerCode = "SUPY",
        merchantId = "abc123",
        currency = "USD"
    )

    private val routing = AcquirerRegistry.resolve(config)

    @Test
    fun toWireUsesDefaultPayMethodWhenRequestOmitsMethod() {
        val config = POSRouterConfig(
            participantCode = "GPOS",
            participantKey = "test-key",
            terminalId = "TID001",
            acquirerCode = "SUPY",
            merchantId = "abc123",
            currency = "USD",
            defaultPayMethod = "emv_card"
        )
        val routing = AcquirerRegistry.resolve(config)
        val wire = PaymentRequest(
            terminalId = "TID001",
            amount = 1250,
            orderId = "ORD001"
        ).toWire(config, routing)

        assertEquals("emv_card", wire.method)
    }

    @Test
    fun toJsonStringContainsLensingFields() {
        val json = PaymentRequest(
            terminalId = "TID001",
            amount = 1250,
            orderId = "ORD001"
        ).toWire(config, routing).toJsonString()

        assertTrue(json.contains("\"terminalId\":\"TID001\""))
        assertTrue(json.contains("\"amount\":1250"))
        assertTrue(json.contains("\"currency\":\"USD\""))
        assertTrue(json.contains("\"targetPackageName\":\"ezypay.com.globe.cardpos\""))
        assertTrue(json.contains("\"targetScheme\":\"ezypos://\""))
        assertTrue(json.contains("\"orderId\":\"ORD001\""))
    }

    @Test
    fun amountFromDecimalParsesCents() {
        assertEquals(6600L, PaymentRequest.amountFromDecimal("66.00"))
        assertEquals(1250L, PaymentRequest.amountFromDecimal("12.50"))
    }

    @Test
    fun wirePaymentRequestJsonIncludesOrderId() {
        val wire = PaymentRequest(
            terminalId = "TID001",
            amount = 1250,
            orderId = "ORD001"
        ).toWire(config, routing)
        val parsed = WirePaymentRequest.fromJson(wire.toJsonString())
        assertEquals("ORD001", parsed?.orderId)
        assertEquals(1250L, parsed?.amount)
    }
}
