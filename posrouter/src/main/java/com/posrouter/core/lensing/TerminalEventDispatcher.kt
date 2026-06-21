package com.posrouter.core.lensing

import android.os.Handler
import android.os.Looper
import com.posrouter.POSRouterTerminalListener
import com.posrouter.PaymentResult
import com.posrouter.toNatsConnectionState

internal object TerminalEventDispatcher {

    var listener: POSRouterTerminalListener? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    fun dispatchNatsState(state: LensingState) {
        val publicState = state.toNatsConnectionState()
        listener?.let { l ->
            mainHandler.post { l.onNatsStateChanged(publicState) }
        }
    }

    fun dispatchRemotePaymentReceived(
        orderId: String,
        amountCents: Long,
        currency: String,
        remark: String?,
        method: String?
    ) {
        listener?.let { l ->
            mainHandler.post {
                l.onRemotePaymentReceived(orderId, amountCents, currency, remark, method)
            }
        }
    }

    fun dispatchRemotePaymentLaunchFailed(orderId: String, message: String) {
        listener?.let { l ->
            mainHandler.post { l.onRemotePaymentLaunchFailed(orderId, message) }
        }
    }

    fun dispatchPaymentCompleted(result: PaymentResult) {
        listener?.let { l ->
            mainHandler.post { l.onPaymentCompleted(result) }
        }
    }
}
