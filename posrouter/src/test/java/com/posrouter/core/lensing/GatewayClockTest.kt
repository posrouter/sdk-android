package com.posrouter.core.lensing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GatewayClockTest {
    @Test
    fun clockSkewBodyRecognizesTheStableCode() {
        val body = """{"error":"Invalid or expired timestamp","code":"CLOCK_SKEW","server_time":1000}"""
        assertTrue(isClockSkewBody(body))
    }

    @Test
    fun clockSkewBodyRecognizesTheLegacyErrorText() {
        assertTrue(isClockSkewBody("""{"error":"Invalid or expired timestamp"}"""))
    }

    @Test
    fun invalidSignatureIsNotClockSkew() {
        assertFalse(isClockSkewBody("""{"error":"Invalid signature"}"""))
    }

    @Test
    fun messageStatesHowFarBehindTheClockIs() {
        val body = """{"code":"CLOCK_SKEW","server_time":5000000}"""
        val message = clockSkewMessage(body, localNowMs = 5000000 - 52 * 60_000L)
        assertEquals(
            "This device's clock is about 52 minutes behind. Sign-in is refused until the date and time are corrected.",
            message
        )
    }

    @Test
    fun messageStatesHowFarAheadTheClockIs() {
        val body = """{"code":"CLOCK_SKEW","server_time":1000}"""
        val message = clockSkewMessage(body, localNowMs = 1000 + 3 * 60_000L)
        assertTrue(message.contains("3 minutes ahead"))
    }

    @Test
    fun gatewayExceptionUsesClockSkewCodeOnlyForThat401() {
        val skew = gatewayException(401, """{"code":"CLOCK_SKEW","server_time":1}""", localNowMs = 1)
        assertEquals(CLOCK_SKEW, skew.code)

        val signature = gatewayException(401, """{"error":"Invalid signature"}""")
        assertEquals("GATEWAY_ERROR", signature.code)
    }
}
