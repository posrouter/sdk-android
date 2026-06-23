package com.posrouter.core.lensing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class LensingSubjectsTest {

    private val scope = LensingSubjectScope(
        acquirerCode = "supy",
        merchantId = "abc123",
        subMerchantId = null,
        terminalId = "TID001"
    )

    private val scopedWithSub = scope.copy(subMerchantId = "REST01")

    @Test
    fun paySubjectUsesFixedSixSegmentNamespace() {
        assertEquals(
            "lensing.SUPY.abc123._.TID001.pay",
            LensingSubjects.paySubject(scope)
        )
    }

    @Test
    fun resultSubjectUsesSubMerchantSegment() {
        assertEquals(
            "lensing.SUPY.abc123.REST01.TID001.result",
            LensingSubjects.resultSubject(scopedWithSub)
        )
    }

    @Test
    fun voidSubjectFormat() {
        assertEquals(
            "lensing.SUPY.abc123._.TID001.void",
            LensingSubjects.voidSubject(scope)
        )
    }

    @Test
    fun claimedSubjectFormat() {
        assertEquals(
            "lensing.SUPY.abc123._.TID001.claimed",
            LensingSubjects.claimedSubject(scope)
        )
    }

    @Test
    fun terminalWildcardFormat() {
        assertEquals(
            "lensing.SUPY.abc123._.TID001.>",
            LensingSubjects.terminalWildcard(scope)
        )
    }

    @Test
    fun refundSubjectFormat() {
        assertEquals(
            "lensing.SUPY.abc123._.TID001.refund",
            LensingSubjects.refundSubject(scope)
        )
    }

    @Test
    fun subMerchantPlaceholderIsReserved() {
        assertThrows(IllegalArgumentException::class.java) {
            LensingSubjects.subMerchantSegment("_")
        }
    }
}
