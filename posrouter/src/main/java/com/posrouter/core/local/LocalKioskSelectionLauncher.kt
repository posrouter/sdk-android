package com.posrouter.core.local

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log
import com.posrouter.POSRouterConfig
import com.posrouter.PaymentRequest
import com.posrouter.WirePaymentRequest

/** Launches same-device POSRouter Kiosk method selection (`{scheme}://charge`). */
internal object LocalKioskSelectionLauncher {
    private const val TAG = "POSRouter.LocalKiosk"
    const val DEFAULT_SCHEME = "posrouter-kiosk"
    const val HOST_CHARGE = "charge"
    /** Default kiosk application id (explicit Intent / install probe). */
    const val DEFAULT_PACKAGE = "com.posrouter.kiosk"

    fun isAvailable(context: Context, config: POSRouterConfig?): Boolean {
        if (isPackageInstalled(context, DEFAULT_PACKAGE)) return true
        val scheme = resolveScheme(config)
        val probe = Intent(Intent.ACTION_VIEW, Uri.parse("$scheme://$HOST_CHARGE")).apply {
            addCategory(Intent.CATEGORY_DEFAULT)
            addCategory(Intent.CATEGORY_BROWSABLE)
        }
        return probe.resolveActivity(context.packageManager) != null
    }

    fun launchCharge(
        activity: Activity,
        config: POSRouterConfig,
        wire: WirePaymentRequest
    ): Boolean {
        val callbackUrl = config.callbackUrl?.trim()?.takeIf { it.isNotEmpty() }
        if (callbackUrl == null) {
            Log.e(TAG, "local_posrouter_kiosk requires POSRouterConfig.callbackUrl")
            return false
        }
        if (!isAvailable(activity, config)) {
            Log.e(TAG, "POSRouter Kiosk not available on this device")
            return false
        }

        val scheme = resolveScheme(config)
        val partnerScheme = Uri.parse(callbackUrl).scheme?.trim().orEmpty()
        val uri = Uri.parse("$scheme://$HOST_CHARGE").buildUpon()
            .appendQueryParameter("amount", wire.amount.toString())
            .appendQueryParameter("currency", wire.currency.ifBlank { config.currency })
            .appendQueryParameter("orderid", wire.orderId)
            .appendQueryParameter("method", PaymentRequest.METHOD_SELECTION)
            .appendQueryParameter("callback_url", callbackUrl)
            .apply {
                if (partnerScheme.isNotEmpty()) {
                    appendQueryParameter("partner_scheme", partnerScheme)
                }
                wire.remark?.takeIf { it.isNotBlank() }?.let { appendQueryParameter("remark", it) }
                wire.attemptId.takeIf { it.isNotBlank() }?.let { appendQueryParameter("attemptid", it) }
            }
            .build()

        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            addCategory(Intent.CATEGORY_DEFAULT)
            addCategory(Intent.CATEGORY_BROWSABLE)
        }
        if (intent.resolveActivity(activity.packageManager) == null) {
            Log.e(TAG, "No activity resolves $scheme://$HOST_CHARGE")
            return false
        }

        return try {
            activity.startActivity(intent)
            Log.i(TAG, "Launched kiosk charge $uri")
            true
        } catch (e: ActivityNotFoundException) {
            Log.e(TAG, "POSRouter Kiosk not installed (scheme=$scheme)", e)
            false
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch kiosk charge", e)
            false
        }
    }

    private fun resolveScheme(config: POSRouterConfig?): String =
        config?.localKioskScheme?.trim()?.takeIf { it.isNotEmpty() } ?: DEFAULT_SCHEME

    private fun isPackageInstalled(context: Context, packageName: String): Boolean =
        try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
}
