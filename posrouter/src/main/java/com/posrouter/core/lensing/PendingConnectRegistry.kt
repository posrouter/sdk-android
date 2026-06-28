package com.posrouter.core.lensing

import android.os.Handler
import android.os.Looper
import com.posrouter.LensingContextHolder
import com.posrouter.LocalRouteMethod
import com.posrouter.POSRouterCallback
import com.posrouter.POSRouterError
import com.posrouter.PaymentResult
import com.posrouter.PaymentStatus
import java.util.concurrent.ConcurrentLinkedQueue

internal object PendingConnectRegistry {
    private val callbacks = ConcurrentLinkedQueue<POSRouterCallback>()
    private val mainHandler = Handler(Looper.getMainLooper())

    fun enqueue(callback: POSRouterCallback) {
        callbacks.add(callback)
        if (LensingProtocolEngine.currentState() == LensingState.CONNECTED) {
            flush()
        }
    }

    fun flush() {
        val config = LensingContextHolder.config ?: return
        val result = networkConnectResult(config)
        while (true) {
            val callback = callbacks.poll() ?: break
            mainHandler.post { callback.onResult(result) }
        }
    }

    fun failAll(error: POSRouterError) {
        while (true) {
            val callback = callbacks.poll() ?: break
            mainHandler.post { callback.onError(error) }
        }
    }

    private fun networkConnectResult(config: com.posrouter.POSRouterConfig) = PaymentResult(
        terminalId = config.terminalId,
        status = PaymentStatus.APPROVED,
        transactionId = null,
        amount = 0,
        currency = config.currency,
        message = "Network track connected",
        localRouteMethod = LocalRouteMethod.NETWORK
    )
}
