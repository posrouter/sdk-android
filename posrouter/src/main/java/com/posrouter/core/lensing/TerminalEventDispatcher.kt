package com.posrouter.core.lensing

import android.os.Handler
import android.os.Looper
import com.posrouter.LensingConnectionState
import com.posrouter.POSRouterError
import com.posrouter.POSRouterTerminalListener
import com.posrouter.PaymentResult
import com.posrouter.toLensingConnectionState

internal object TerminalEventDispatcher {

    var listener: POSRouterTerminalListener? = null
    private val mainHandler = Handler(Looper.getMainLooper())
    private var lastDispatchedPublicState: LensingConnectionState? = null

    fun dispatchLensingState(state: LensingState) {
        val publicState = state.toLensingConnectionState()
        if (publicState == lastDispatchedPublicState) return
        lastDispatchedPublicState = publicState
        when (state) {
            LensingState.CONNECTED -> PendingConnectRegistry.flush()
            LensingState.FAILED ->
                PendingConnectRegistry.failAll(
                    POSRouterError("CONNECT_FAILED", "Lensing engine connection failed")
                )
            else -> Unit
        }
        listener?.let { l ->
            mainHandler.post { l.onLensingStateChanged(publicState) }
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

    fun dispatchRemotePaymentVoided(orderId: String, attemptId: String, message: String?) {
        listener?.let { l ->
            mainHandler.post { l.onRemotePaymentVoided(orderId, attemptId, message) }
        }
    }
}
