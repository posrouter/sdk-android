package com.posrouter.core.local

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import com.posrouter.POSRouterConfig
import com.posrouter.WirePaymentRequest
import com.posrouter.WireRefundRequest
import com.posrouter.core.registry.AcquirerRouting

internal object LocalRouteExecutor {

    const val LENS_DATA_KEY = "LENS_DATA"

    fun launchConnectViaExplicitIntent(
        context: Context,
        config: POSRouterConfig,
        routing: AcquirerRouting
    ): Boolean = launchExplicitIntent(context, routing.packageName, buildConnectLensData(config))

    fun launchPayViaExplicitIntent(
        context: Context,
        config: POSRouterConfig,
        routing: AcquirerRouting,
        request: WirePaymentRequest
    ): Boolean = launchExplicitIntent(
        context,
        routing.packageName,
        buildPayLensData(request, config)
    )

    fun launchConnectViaDeepLink(
        context: Context,
        config: POSRouterConfig,
        routing: AcquirerRouting
    ): Boolean = launchDeepLink(context, LocalDeepLinkUriBuilder.buildConnectUri(config), routing.packageName)

    fun launchPayViaDeepLink(
        context: Context,
        config: POSRouterConfig,
        routing: AcquirerRouting,
        request: WirePaymentRequest
    ): Boolean = launchDeepLink(
        context,
        LocalDeepLinkUriBuilder.buildPayUri(request, config),
        routing.packageName
    )

    fun launchRefundViaExplicitIntent(
        context: Context,
        config: POSRouterConfig,
        routing: AcquirerRouting,
        request: WireRefundRequest
    ): Boolean = launchExplicitIntent(
        context,
        routing.packageName,
        buildRefundLensData(request, config)
    )

    fun launchRefundViaDeepLink(
        context: Context,
        config: POSRouterConfig,
        routing: AcquirerRouting,
        request: WireRefundRequest
    ): Boolean = launchDeepLink(
        context,
        LocalDeepLinkUriBuilder.buildRefundUri(request, config),
        routing.packageName
    )

    internal fun buildConnectLensData(config: POSRouterConfig): String {
        val separator = config.localParamSeparator
        val parts = mutableListOf(
            "action=connect",
            LensLocalEncoder.pair("merchantid", config.merchantId, separator)
        )
        config.callbackUrl?.let { parts.add(LensLocalEncoder.pair("callback_url", it, separator)) }
        return LensLocalEncoder.joinPairs(parts, separator)
    }

    internal fun buildPayLensData(
        request: WirePaymentRequest,
        config: POSRouterConfig
    ): String {
        val separator = config.localParamSeparator
        // action=pay 显式标明操作（与 connect/refund 对称）。本地显式-intent 轨道只靠 lens data 区分操作，
        // 不带 action 时收单 App 按默认当付款——pay 本就是默认，但显式带上更稳、也让所有操作一致。
        val parts = mutableListOf(
            "action=pay",
            LensLocalEncoder.pair("amount", formatAmountDecimal(request.amount), separator),
            LensLocalEncoder.pair("currency", request.currency, separator),
            LensLocalEncoder.pair("orderid", request.orderId, separator)
        )
        request.remark?.let { parts.add(LensLocalEncoder.pair("remark", it, separator)) }
        request.method?.let { parts.add(LensLocalEncoder.pair("method", it, separator)) }
        config.callbackUrl?.let { parts.add(LensLocalEncoder.pair("callback_url", it, separator)) }
        return LensLocalEncoder.joinPairs(parts, separator)
    }

    internal fun buildRefundLensData(
        request: WireRefundRequest,
        config: POSRouterConfig
    ): String {
        val separator = config.localParamSeparator
        // action=refund 是收单 App 本地显式-intent 轨道区分「退款 vs 付款」的唯一依据——缺了它退款会被当付款执行（真实事故）。
        val parts = listOf(
            "action=refund",
            LensLocalEncoder.pair("amount", formatAmountDecimal(request.amount), separator),
            LensLocalEncoder.pair("orderid", request.orderId, separator)
        )
        return LensLocalEncoder.joinPairs(parts, separator)
    }

    private fun launchExplicitIntent(
        context: Context,
        targetPackageName: String,
        lensData: String
    ): Boolean {
        if (targetPackageName.isBlank()) return false
        val intent = Intent(Intent.ACTION_MAIN).apply {
            setPackage(targetPackageName)
            addCategory(Intent.CATEGORY_LAUNCHER)
            putExtra(LENS_DATA_KEY, lensData)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return try {
            context.startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            false
        }
    }

    private fun launchDeepLink(
        context: Context,
        uri: android.net.Uri,
        targetPackageName: String
    ): Boolean {
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (targetPackageName.isNotBlank()) setPackage(targetPackageName)
        }
        return try {
            context.startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            false
        }
    }

    internal fun formatAmountDecimal(amountCents: Long): String =
        java.util.Locale.US.let { locale ->
            String.format(locale, "%.2f", amountCents / 100.0)
        }
}
