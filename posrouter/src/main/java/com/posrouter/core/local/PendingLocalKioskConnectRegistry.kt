package com.posrouter.core.local

import android.os.Handler
import android.os.Looper
import com.posrouter.POSRouterCallback
import com.posrouter.POSRouterError
import com.posrouter.PaymentResult
import java.util.concurrent.atomic.AtomicReference

/**
 * Awaits kiosk `…://pay_result?type=CONNECT&status=…` after
 * [LocalKioskSelectionLauncher.launchConnect], so partner apps that only dismiss
 * UI on the reverse deeplink (or [com.posrouter.POSRouter.deliverAcquirerCallback]) complete.
 */
internal object PendingLocalKioskConnectRegistry {
    private val pending = AtomicReference<POSRouterCallback?>(null)
    private val mainHandler = Handler(Looper.getMainLooper())

    fun enqueue(callback: POSRouterCallback) {
        pending.set(callback)
    }

    fun clear() {
        pending.set(null)
    }

    /** @return true if a pending connect callback was consumed */
    fun completeSuccess(result: PaymentResult): Boolean {
        val callback = pending.getAndSet(null) ?: return false
        mainHandler.post { callback.onResult(result) }
        return true
    }

    fun completeError(error: POSRouterError): Boolean {
        val callback = pending.getAndSet(null) ?: return false
        mainHandler.post { callback.onError(error) }
        return true
    }

    fun hasPending(): Boolean = pending.get() != null
}
