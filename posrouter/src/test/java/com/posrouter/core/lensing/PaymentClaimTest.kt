package com.posrouter.core.lensing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PaymentClaimTest {

    @Test
    fun claimedSubjectFollowsConvention() {
        assertEquals("lensing.terminal.TID001.claimed", LensingSubjects.claimedSubject("TID001"))
    }

    @Test
    fun claimJsonRoundTrip() {
        val claim = PaymentClaim("TID001", "GM20260602001", 1718800000000L)
        val parsed = PaymentClaim.fromJson(claim.toJsonString())
        assertEquals(claim.terminalId, parsed?.terminalId)
        assertEquals(claim.orderId, parsed?.orderId)
        assertEquals(claim.claimedAt, parsed?.claimedAt)
    }

    @Test
    fun tryAcquireClaimIsFirstWriterWins() {
        PaymentClaimRegistry.releaseClaim("TID001", "ORD1")
        assertTrue(PaymentClaimRegistry.tryAcquireClaim("TID001", "ORD1"))
        assertFalse(PaymentClaimRegistry.tryAcquireClaim("TID001", "ORD1"))
        assertTrue(PaymentClaimRegistry.isClaimed("TID001", "ORD1"))
        PaymentClaimRegistry.releaseClaim("TID001", "ORD1")
    }

    @Test
    fun expiredClaimCanBeReacquired() {
        PaymentClaimRegistry.markClaimed(
            PaymentClaim("TID001", "ORD2", System.currentTimeMillis() - 6 * 60 * 1000L)
        )
        assertFalse(PaymentClaimRegistry.isClaimed("TID001", "ORD2"))
        assertTrue(PaymentClaimRegistry.tryAcquireClaim("TID001", "ORD2"))
        PaymentClaimRegistry.releaseClaim("TID001", "ORD2")
    }
}
