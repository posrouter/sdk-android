package com.posrouter.core.lensing

import org.junit.Assert.assertEquals
import org.junit.Test

class GatewayEndpointsTest {

    @Test
    fun resolveInit_nullUsesDefault() {
        assertEquals(GatewayEndpoints.DEFAULT_INIT_URL, GatewayEndpoints.resolveInit(null))
    }

    @Test
    fun resolveInit_originAppendsInit() {
        assertEquals(
            "https://preview.vercel.app/init",
            GatewayEndpoints.resolveInit("https://preview.vercel.app")
        )
    }

    @Test
    fun resolveInit_fullInitUrlUnchanged() {
        val url = "https://preview.vercel.app/init"
        assertEquals(url, GatewayEndpoints.resolveInit(url))
    }

    @Test
    fun matrixUrl_derivesFromConfig() {
        assertEquals(
            "https://preview.vercel.app/matrix",
            GatewayEndpoints.matrixUrl(
                com.posrouter.POSRouterConfig(
                    participantCode = "GPOS",
                    participantKey = "k",
                    terminalId = "T1",
                    acquirerCode = "SUPY",
                    merchantId = "m",
                    gatewayBaseUrl = "https://preview.vercel.app"
                )
            )
        )
    }
}
