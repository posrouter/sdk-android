package io.starrie.posrouter

import org.junit.Assert.assertTrue
import org.junit.Test

class PaymentRequestTest {
    @Test
    fun toJsonStringContainsLensingFields() {
        val json = PaymentRequest(
            terminalId = "TID001",
            amount = 1250,
            currency = "USD",
            targetPackageName = "com.ezypos.app"
        ).toJsonString()

        assertTrue(json.contains("\"terminalId\":\"TID001\""))
        assertTrue(json.contains("\"amount\":1250"))
        assertTrue(json.contains("\"currency\":\"USD\""))
        assertTrue(json.contains("\"targetPackageName\":\"com.ezypos.app\""))
    }
}
