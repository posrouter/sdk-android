package com.posrouter.terminal

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.posrouter.core.lensing.TerminalEventDispatcher

internal object TerminalUiCoordinator {
    private const val TAG = "POSRouter.TerminalUi"

    const val EXTRA_REMOTE_PAY_ORDER_ID = "com.posrouter.extra.REMOTE_PAY_ORDER_ID"
    const val EXTRA_REMOTE_PAY_AMOUNT_CENTS = "com.posrouter.extra.REMOTE_PAY_AMOUNT_CENTS"
    const val EXTRA_REMOTE_PAY_CURRENCY = "com.posrouter.extra.REMOTE_PAY_CURRENCY"
    const val EXTRA_REMOTE_PAY_REMARK = "com.posrouter.extra.REMOTE_PAY_REMARK"
    const val EXTRA_REMOTE_PAY_METHOD = "com.posrouter.extra.REMOTE_PAY_METHOD"

    private val mainHandler = Handler(Looper.getMainLooper())

    fun dispatchRemotePaymentReceived(
        orderId: String,
        amountCents: Long,
        currency: String,
        remark: String?,
        method: String?
    ) {
        val listener = TerminalEventDispatcher.listener
        val inForeground = TerminalUiForegroundTracker.isForeground

        if (inForeground && listener != null) {
            mainHandler.post {
                listener.onRemotePaymentReceived(orderId, amountCents, currency, remark, method)
            }
            return
        }

        if (listener != null) {
            mainHandler.post {
                listener.onRemotePaymentReceived(orderId, amountCents, currency, remark, method)
            }
        } else {
            PendingRemotePayStore.store(orderId, amountCents, currency, remark, method)
            Log.i(TAG, "Remote pay queued until terminal UI binds — order=$orderId")
        }

        if (inForeground) return

        TerminalLaunchIntents.launch(
            context = requireContext(),
            orderId = orderId,
            amountCents = amountCents,
            currency = currency,
            remark = remark,
            method = method
        )
    }

    private fun requireContext() =
        com.posrouter.LensingContextHolder.applicationContext
            ?: throw IllegalStateException("POSRouter not initialized")
}
