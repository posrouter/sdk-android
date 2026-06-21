package com.posrouter.core.registry

import com.posrouter.POSRouterConfig
import org.junit.Assert.assertEquals
import org.junit.Test

class AcquirerRegistryTest {

    @Test
    fun resolvesBakedDefaultForSupy() {
        val routing = AcquirerRegistry.resolve(
            POSRouterConfig(
                participantCode = "GPOS",
                participantKey = "key",
                terminalId = "TID001",
                acquirerCode = "SUPY",
                merchantId = "abc123"
            )
        )
        assertEquals("SUPY", routing.code)
        assertEquals("ezypay.com.globe.cardpos", routing.packageName)
        assertEquals("ezypos://", routing.schemeUri)
    }

    @Test
    fun packageOverrideTakesPrecedence() {
        val routing = AcquirerRegistry.resolve(
            POSRouterConfig(
                participantCode = "GPOS",
                participantKey = "key",
                terminalId = "TID001",
                acquirerCode = "SUPY",
                merchantId = "abc123",
                acquirerPackageOverride = "com.custom.app",
                acquirerSchemeOverride = "custom://"
            )
        )
        assertEquals("com.custom.app", routing.packageName)
        assertEquals("custom://", routing.schemeUri)
    }
}
