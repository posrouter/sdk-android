package io.starrie.posrouter

import android.app.Activity
import android.content.Context

/**
 * Outward-facing POSRouter facade. All Lensing Protocol internals are isolated
 * behind [io.starrie.posrouter.core.lensing.LensingProtocolEngine].
 */
object POSRouter {

    fun initialize(context: Context, code: String, key: String) {
        LensingContextHolder.applicationContext = context.applicationContext
        io.starrie.posrouter.core.lensing.LensingProtocolEngine.start(code, key)
    }

    fun pay(activity: Activity, request: PaymentRequest, callback: POSRouterCallback) {
        if (io.starrie.posrouter.core.local.LocalRouteScanner.checkAcquirerInstalled(
                activity,
                request.targetPackageName
            )
        ) {
            io.starrie.posrouter.core.local.LocalRouteExecutor.launchViaIntent(activity, request)
            callback.onResult(
                PaymentResult(
                    terminalId = request.terminalId,
                    status = PaymentStatus.APPROVED,
                    transactionId = null,
                    amount = request.amount,
                    currency = request.currency,
                    message = "Local track launched"
                )
            )
        } else {
            io.starrie.posrouter.core.lensing.LensingProtocolEngine.dispatchTransaction(request, callback)
        }
    }
}

internal object LensingContextHolder {
    var applicationContext: Context? = null
}
