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

/**
 * Same-device POSRouter Kiosk deeplinks:
 * - `{scheme}://connect` — partner register + optional CONNECT relay
 * - `{scheme}://charge` — method selection
 */
internal object LocalKioskSelectionLauncher {
    private const val TAG = "POSRouter.LocalKiosk"
    const val DEFAULT_SCHEME = "posrouter-kiosk"
    const val HOST_CONNECT = "connect"
    const val HOST_CHARGE = "charge"
    /** Default kiosk application id (explicit Intent / install probe). */
    const val DEFAULT_PACKAGE = "com.posrouter.kiosk"

    fun isAvailable(context: Context, config: POSRouterConfig?): Boolean {
        if (isPackageInstalled(context, DEFAULT_PACKAGE)) return true
        val scheme = resolveScheme(config)
        // Prefer resolve without forcing BROWSABLE — same as an ACTION_VIEW launch path.
        val chargeUri = Uri.parse("$scheme://$HOST_CHARGE")
        val viewProbe = Intent(Intent.ACTION_VIEW, chargeUri).apply {
            addCategory(Intent.CATEGORY_DEFAULT)
        }
        if (viewProbe.resolveActivity(context.packageManager) != null) return true
        val browsableProbe = Intent(Intent.ACTION_VIEW, chargeUri).apply {
            addCategory(Intent.CATEGORY_DEFAULT)
            addCategory(Intent.CATEGORY_BROWSABLE)
        }
        return browsableProbe.resolveActivity(context.packageManager) != null
    }

    /**
     * Opens kiosk connect handshake. Kiosk relays
     * `{callbackUrl}?type=CONNECT&status=SUCCESS` when [notifyConnect] is true (default).
     * Uses `kiosk_lock=0` so companion POS apps stay unpinned.
     */
    fun launchConnect(
        activity: Activity,
        config: POSRouterConfig,
        notifyConnect: Boolean = true
    ): Boolean {
        val callbackUrl = config.callbackUrl?.trim()?.takeIf { it.isNotEmpty() }
        if (callbackUrl == null) {
            Log.e(TAG, "local_posrouter_kiosk connect requires POSRouterConfig.callbackUrl")
            return false
        }
        if (!isAvailable(activity, config)) {
            Log.e(TAG, "POSRouter Kiosk not available on this device")
            return false
        }

        val scheme = resolveScheme(config)
        val uri = Uri.parse("$scheme://$HOST_CONNECT").buildUpon()
            .appendQueryParameter("callback_url", callbackUrl)
            .appendQueryParameter("kiosk_lock", "0")
            .appendQueryParameter("caller_package", activity.packageName)
            .apply {
                if (!notifyConnect) appendQueryParameter("notify", "0")
            }
            .build()

        return startViewIntent(activity, uri, "$scheme://$HOST_CONNECT")
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
            .appendQueryParameter("caller_package", activity.packageName)
            .apply {
                if (partnerScheme.isNotEmpty()) {
                    appendQueryParameter("partner_scheme", partnerScheme)
                }
                wire.remark?.takeIf { it.isNotBlank() }?.let { appendQueryParameter("remark", it) }
                wire.attemptId.takeIf { it.isNotBlank() }?.let { appendQueryParameter("attemptid", it) }
            }
            .build()

        return startViewIntent(activity, uri, "$scheme://$HOST_CHARGE")
    }

    private fun startViewIntent(activity: Activity, uri: Uri, label: String): Boolean {
        val intent = Intent(Intent.ACTION_VIEW, uri).apply {
            addCategory(Intent.CATEGORY_DEFAULT)
            addCategory(Intent.CATEGORY_BROWSABLE)
            // NEW_TASK is what the Level 1 spec's launch snippet shows, and leaving it off is not a
            // cosmetic difference: the kiosk activity is singleTop, so without it a second kiosk
            // instance is created *inside this app's task*. When that instance later hands the
            // screen back it backgrounds the task it is sharing with us, taking this POS app down
            // with it, and the terminal's standby screen is what staff end up looking at.
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            // Names this app on the deeplink itself. getReferrer() cannot: a warm deeplink arrives
            // at the kiosk's onNewIntent, where the referrer still describes whoever launched it
            // originally, and the terminal is always already running.
            putExtra(Intent.EXTRA_REFERRER, Uri.parse("android-app://${activity.packageName}"))
        }
        if (intent.resolveActivity(activity.packageManager) == null) {
            Log.e(TAG, "No activity resolves $label")
            return false
        }
        return try {
            activity.startActivity(intent)
            Log.i(TAG, "Launched $uri")
            true
        } catch (e: ActivityNotFoundException) {
            Log.e(TAG, "POSRouter Kiosk not installed ($label)", e)
            false
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch $label", e)
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
