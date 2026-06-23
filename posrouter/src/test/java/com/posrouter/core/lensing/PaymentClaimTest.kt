package com.posrouter.core.lensing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PaymentClaimTest {

    private val attemptId = "ORD1#1"

    @Test
    fun claimedSubjectFollowsConvention() {
        assertEquals("lensing.terminal.TID001.claimed", LensingSubjects.claimedSubject("TID001"))
    }

    @Test
    fun claimJsonRoundTrip() {
        val claim = PaymentClaim("TID001", "GM20260602001", "GM20260602001#1", 1718800000000L)
        val parsed = PaymentClaim.fromJson(claim.toJsonString())
        assertEquals(claim.terminalId, parsed?.terminalId)
        assertEquals(claim.orderId, parsed?.orderId)
        assertEquals(claim.attemptId, parsed?.attemptId)
        assertEquals(claim.claimedAt, parsed?.claimedAt)
    }

    @Test
    fun claimJsonDefaultsAttemptIdWhenMissing() {
        val json = """{"terminalId":"TID001","orderId":"ORD1","claimedAt":123}"""
        assertEquals("ORD1#1", PaymentClaim.fromJson(json)?.attemptId)
    }

    @Test
    fun tryAcquireClaimIsFirstWriterWins() {
        PaymentClaimRegistry.releaseClaim("TID001", "ORD1", attemptId)
        assertTrue(PaymentClaimRegistry.tryAcquireClaim("TID001", "ORD1", attemptId))
        assertFalse(PaymentClaimRegistry.tryAcquireClaim("TID001", "ORD1", attemptId))
        assertTrue(PaymentClaimRegistry.isClaimed("TID001", "ORD1", attemptId))
        PaymentClaimRegistry.releaseClaim("TID001", "ORD1", attemptId)
    }

    @Test
    fun expiredClaimCanBeReacquired() {
        PaymentClaimRegistry.markClaimed(
            PaymentClaim("TID001", "ORD2", "ORD2#1", System.currentTimeMillis() - 6 * 60 * 1000L)
        )
        assertFalse(PaymentClaimRegistry.isClaimed("TID001", "ORD2", "ORD2#1"))
        assertTrue(PaymentClaimRegistry.tryAcquireClaim("TID001", "ORD2", "ORD2#1"))
        PaymentClaimRegistry.releaseClaim("TID001", "ORD2", "ORD2#1")
    }
}
