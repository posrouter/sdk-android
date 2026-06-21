package com.posrouter.core.local

import com.posrouter.POSRouterConfig
import com.posrouter.WirePaymentRequest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalRouteExecutorTest {

    @Test
    fun buildPayLensDataEncodesSpacesInRemark() {
        val data = LocalRouteExecutor.buildPayLensData(
            WirePaymentRequest(
                terminalId = "TID001",
                amount = 66666,
                currency = "NZD",
                targetPackageName = "com.ezypos.app",
                targetScheme = "ezypos://",
                acquirerCode = "SUPY",
                orderId = "GM20260602001",
                remark = "Table 5"
            ),
            com.posrouter.LocalParamSeparator.PIPE
        )
        assertEquals(
            "amount=666.66|orderid=GM20260602001|remark=Table%205",
            data
        )
    }

    @Test
    fun buildConnectLensDataIncludesMerchantAndCallback() {
        val data = LocalRouteExecutor.buildConnectLensData(
            POSRouterConfig(
                participantCode = "GPOS",
                participantKey = "key",
                terminalId = "TID001",
                acquirerCode = "SUPY",
                merchantId = "abc123",
                callbackUrl = "gomenu://pay_result"
            )
        )
        assertEquals(
            "action=connect|merchantid=abc123|callback_url=gomenu://pay_result",
            data
        )
    }

    @Test
    fun formatAmountDecimalUsesTwoFractionDigits() {
        assertEquals("12.50", LocalRouteExecutor.formatAmountDecimal(1250))
        assertEquals("66.00", LocalRouteExecutor.formatAmountDecimal(6600))
    }

    @Test
    fun buildPayDeepLinkUsesPipeSeparator() {
        val uriString = LocalDeepLinkUriBuilder.buildPayUriString(
            WirePaymentRequest(
                terminalId = "TID001",
                amount = 66666,
                currency = "NZD",
                targetPackageName = "com.ezypos.app",
                targetScheme = "ezypos://",
                acquirerCode = "SUPY",
                orderId = "GM20260602001",
                remark = "Table 5",
                method = "emv_card"
            ),
            com.posrouter.LocalParamSeparator.PIPE
        )
        assertEquals(
            "ezypos://pay?amount=666.66|currency=NZD|orderid=GM20260602001|remark=Table%205|method=emv_card",
            uriString
        )
        assertTrue(!uriString.contains("&"))
    }

    @Test
    fun buildConnectDeepLinkUsesPipeSeparator() {
        val uriString = LocalDeepLinkUriBuilder.buildConnectUriString(
            POSRouterConfig(
                participantCode = "GPOS",
                participantKey = "key",
                terminalId = "TID001",
                acquirerCode = "SUPY",
                merchantId = "abc123",
                callbackUrl = "gomenu://pay_result"
            )
        )
        assertEquals(
            "ezypos://connect?merchantid=abc123|callback_url=gomenu://pay_result",
            uriString
        )
    }

    @Test
    fun buildConnectLensDataUsesAmpersandForLegacyAcquirers() {
        val data = LocalRouteExecutor.buildConnectLensData(
            POSRouterConfig(
                participantCode = "GPOS",
                participantKey = "key",
                terminalId = "TID001",
                acquirerCode = "SUPY",
                merchantId = "1FRD9Z",
                callbackUrl = "gomenu://pay_result",
                localParamSeparator = com.posrouter.LocalParamSeparator.AMPERSAND
            )
        )
        assertEquals(
            "action=connect&merchantid=1FRD9Z&callback_url=gomenu://pay_result",
            data
        )
    }

    @Test
    fun buildConnectDeepLinkUsesAmpersandForLegacyAcquirers() {
        val uriString = LocalDeepLinkUriBuilder.buildConnectUriString(
            POSRouterConfig(
                participantCode = "GPOS",
                participantKey = "key",
                terminalId = "TID001",
                acquirerCode = "SUPY",
                merchantId = "1FRD9Z",
                callbackUrl = "gomenu://pay_result",
                localParamSeparator = com.posrouter.LocalParamSeparator.AMPERSAND
            )
        )
        assertEquals(
            "ezypos://connect?merchantid=1FRD9Z&callback_url=gomenu://pay_result",
            uriString
        )
    }
}
