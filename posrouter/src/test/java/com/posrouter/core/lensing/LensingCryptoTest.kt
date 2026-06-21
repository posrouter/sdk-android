package com.posrouter.core.lensing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Cross-platform HMAC consistency: must match gateway src/lib/crypto/hmac.ts
 * Signature = HmacSHA256(key, key + timestamp)
 */
class LensingCryptoTest {
    private val key = "GPOS_TEST_SECRET_KEY"
    private val timestamp = "1718800000000"

    @Test
    fun computeSignatureIsDeterministic() {
        val sig1 = LensingCrypto.computeSignature(key, timestamp)
        val sig2 = LensingCrypto.computeSignature(key, timestamp)
        assertEquals(sig1, sig2)
        assertTrue(sig1.matches(Regex("^[0-9a-f]{64}$")))
    }

    @Test
    fun computeSignatureDiffersForDifferentKeys() {
        val sig1 = LensingCrypto.computeSignature(key, timestamp)
        val sig2 = LensingCrypto.computeSignature("OTHER_KEY", timestamp)
        assertNotEquals(sig1, sig2)
    }

    @Test
    fun goldenSignatureMatchesGatewayReference() {
        val expected = "f4635e7c3db0a72a87b69be7000023ae9fe3e4b7ec87e83047b7a90e17fb876a"
        val actual = LensingCrypto.computeSignature(key, timestamp)
        assertEquals(expected, actual)
    }
}
