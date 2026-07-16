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

    @Test
    fun packageOverrideHonoredWhenAttemptCodeMatchesAcquirer() {
        // The terminal launch re-resolves with the resolved acquirer code as attemptCode; the override
        // must still win (else the deeplink scheme is overridden but the package falls back to the baked
        // default and mismatches).
        val routing = AcquirerRegistry.resolve(
            POSRouterConfig(
                participantCode = "GPOS",
                participantKey = "key",
                terminalId = "TID001",
                acquirerCode = "SUPY",
                merchantId = "abc123",
                acquirerPackageOverride = "nz.co.posrouter.remitpos",
                acquirerSchemeOverride = "remitpos://"
            ),
            attemptCode = "SUPY"
        )
        assertEquals("nz.co.posrouter.remitpos", routing.packageName)
        assertEquals("remitpos://", routing.schemeUri)
    }

    @Test
    fun crossAcquirerRetryBypassesOverride() {
        // A genuine fallback to a different acquirer pipeline must NOT be forced onto the override package.
        val routing = AcquirerRegistry.resolve(
            POSRouterConfig(
                participantCode = "GPOS",
                participantKey = "key",
                terminalId = "TID001",
                acquirerCode = "SUPY",
                merchantId = "abc123",
                acquirerPackageOverride = "nz.co.posrouter.remitpos",
                acquirerSchemeOverride = "remitpos://"
            ),
            attemptCode = "SKYZER"
        )
        assertEquals("SKYZER", routing.code)
        // Override bypassed: no baked default / cache for SKYZER → empty package (not the override).
        assertEquals("", routing.packageName)
    }
}
