package com.posrouter.core.lensing

import com.posrouter.POSRouterCallback
import com.posrouter.POSRouterError
import com.posrouter.PaymentResult
import com.posrouter.WirePaymentRequest
import org.junit.Assert.assertEquals
import org.junit.Test

class RemoteClaimCallbackTest {

    private fun wire(orderId: String) = WirePaymentRequest(
        terminalId = "TID001",
        amount = 459,
        currency = "NZD",
        targetPackageName = "ezypay.com.globe.cardpos",
        targetScheme = "ezypos://",
        acquirerCode = "SUPY",
        merchantId = "JFW0DO",
        orderId = orderId,
        attemptId = "$orderId#1",
        attemptCode = "SUPY"
    )

    @Test
    fun claimSeenByTerminalRoleReachesSameLaneInitiator() {
        val claimed = mutableListOf<String>()
        val w = wire("CLAIM_SAME_LANE")
        PaymentAttemptRegistry.store(w, object : POSRouterCallback {
            override fun onResult(result: PaymentResult) = Unit
            override fun onError(error: POSRouterError) = Unit
            override fun onClaimed(orderId: String, attemptId: String) {
                claimed += "$orderId/$attemptId"
            }
        })
        LensingProtocolEngine.handleRemoteClaim(PaymentClaim("TID001", w.orderId, w.attemptId))
        assertEquals(listOf("CLAIM_SAME_LANE/CLAIM_SAME_LANE#1"), claimed)
        PaymentAttemptRegistry.close("TID001", w.orderId, w.attemptId)
    }

    @Test
    fun claimWithoutInitiatorCallbackIsHarmless() {
        val w = wire("CLAIM_TERMINAL_ONLY")
        PaymentAttemptRegistry.store(w, callback = null)
        LensingProtocolEngine.handleRemoteClaim(PaymentClaim("TID001", w.orderId, w.attemptId))
        PaymentAttemptRegistry.close("TID001", w.orderId, w.attemptId)
    }
}
