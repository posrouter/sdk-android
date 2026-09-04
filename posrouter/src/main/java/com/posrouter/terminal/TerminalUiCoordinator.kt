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
        request: com.posrouter.RemotePaymentRequest
    ) {
        val orderId = request.orderId
        val listener = TerminalEventDispatcher.listener
        val inForeground = TerminalUiForegroundTracker.isForeground

        if (inForeground && listener != null) {
            mainHandler.post { listener.onRemotePaymentReceived(request) }
            return
        }

        if (listener != null) {
            mainHandler.post { listener.onRemotePaymentReceived(request) }
        } else {
            PendingRemotePayStore.store(request)
            Log.i(TAG, "Remote pay queued until terminal UI binds — order=$orderId")
        }

        if (inForeground) return

        TerminalLaunchIntents.launch(
            context = requireContext(),
            orderId = orderId,
            amountCents = request.amountCents,
            currency = request.currency,
            remark = request.remark,
            method = request.method
        )
    }

    private fun requireContext() =
        com.posrouter.LensingContextHolder.applicationContext
            ?: throw IllegalStateException("POSRouter not initialized")
}
