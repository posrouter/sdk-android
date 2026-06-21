package com.posrouter.core.local

import android.net.Uri
import com.posrouter.LocalParamSeparator
import com.posrouter.POSRouterConfig
import com.posrouter.WirePaymentRequest
import com.posrouter.core.registry.AcquirerRegistry

internal object LocalDeepLinkUriBuilder {

    fun buildPayUri(request: WirePaymentRequest, config: POSRouterConfig): Uri =
        Uri.parse(buildPayUriString(request, config.localParamSeparator, config))

    fun buildConnectUri(config: POSRouterConfig): Uri =
        Uri.parse(buildConnectUriString(config))

    internal fun buildPayUriString(
        request: WirePaymentRequest,
        separator: LocalParamSeparator = LocalParamSeparator.PIPE,
        config: POSRouterConfig? = null
    ): String {
        val scheme = parseScheme(request.targetScheme)
        val pairs = mutableListOf(
            LensLocalEncoder.pair("amount", LocalRouteExecutor.formatAmountDecimal(request.amount), separator),
            LensLocalEncoder.pair("currency", request.currency, separator),
            LensLocalEncoder.pair("orderid", request.orderId, separator)
        )
        request.remark?.let { pairs.add(LensLocalEncoder.pair("remark", it, separator)) }
        request.method?.let { pairs.add(LensLocalEncoder.pair("method", it, separator)) }
        config?.callbackUrl?.let { pairs.add(LensLocalEncoder.pair("callback_url", it, separator)) }
        return "$scheme://pay?${LensLocalEncoder.joinPairs(pairs, separator)}"
    }

    internal fun buildConnectUriString(config: POSRouterConfig): String {
        val routing = AcquirerRegistry.resolve(config)
        val scheme = parseScheme(routing.schemeUri)
        val separator = config.localParamSeparator
        val pairs = mutableListOf(LensLocalEncoder.pair("merchantid", config.merchantId, separator))
        config.callbackUrl?.let { pairs.add(LensLocalEncoder.pair("callback_url", it, separator)) }
        return "$scheme://connect?${LensLocalEncoder.joinPairs(pairs, separator)}"
    }

    private fun parseScheme(targetScheme: String): String {
        val normalized = if (targetScheme.contains("://")) targetScheme else "$targetScheme://"
        return normalized.substringBefore("://").ifBlank { "ezypos" }
    }
}
