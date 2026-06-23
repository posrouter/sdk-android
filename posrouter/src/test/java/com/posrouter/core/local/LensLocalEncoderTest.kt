package com.posrouter.core.local

import org.junit.Assert.assertEquals
import org.junit.Test

class LensLocalEncoderTest {

    @Test
    fun encodesStructureBreakingCharactersInOrder() {
        assertEquals("100%25off", LensLocalEncoder.encode("100%off"))
        assertEquals("Table%3D5%7CDinner", LensLocalEncoder.encode("Table=5|Dinner"))
        assertEquals("what%3F", LensLocalEncoder.encode("what?"))
        assertEquals("Table%205", LensLocalEncoder.encode("Table 5"))
    }

    @Test
    fun payDeepLinkUsesPipeSeparatorAndEncodedRemark() {
        val uri = LocalDeepLinkUriBuilder.buildPayUriString(
            com.posrouter.WirePaymentRequest(
                terminalId = "TID001",
                amount = 1000,
                currency = "NZD",
                targetPackageName = "com.ezypos.app",
                targetScheme = "ezypos://",
                acquirerCode = "SUPY",
                orderId = "GM001",
                attemptId = "GM001#1",
                attemptCode = "SUPY",
                remark = "Table=5|Dinner"
            ),
            com.posrouter.LocalParamSeparator.PIPE
        )
        assertEquals(
            "ezypos://pay?amount=10.00|currency=NZD|orderid=GM001|remark=Table%3D5%7CDinner",
            uri
        )
    }
}
