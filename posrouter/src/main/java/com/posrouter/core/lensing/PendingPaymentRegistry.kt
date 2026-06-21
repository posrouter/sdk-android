package com.posrouter.core.lensing

import com.posrouter.POSRouterCallback
import com.posrouter.PaymentResult
import java.util.concurrent.ConcurrentHashMap

internal object PendingPaymentRegistry {

    private val callbacks = ConcurrentHashMap<String, POSRouterCallback>()

    fun register(orderId: String, callback: POSRouterCallback) {
        callbacks[orderId] = callback
    }

    fun deliver(result: PaymentResult): Boolean {
        val orderId = result.orderId ?: return false
        return callbacks.remove(orderId)?.let { callback ->
            callback.onResult(result)
            true
        } ?: false
    }

    fun cancel(orderId: String) {
        callbacks.remove(orderId)
    }
}
